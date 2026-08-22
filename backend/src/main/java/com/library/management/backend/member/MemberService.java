package com.library.management.backend.member;

import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.loan.LoanRepository;
import com.library.management.backend.loan.MemberOpenLoanCount;
import com.library.management.backend.member.dto.MemberRequest;
import com.library.management.backend.member.dto.MemberResponse;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for members.
 *
 * <p>Two rules live here rather than in the database ({@code DATA_MODEL.md} §5):
 * a name must be unique among active members (case-insensitively), and a member
 * still holding books cannot be archived.
 *
 * <p>Archiving is a soft delete, and there is no restore: the row stays only so old
 * loans remain resolvable. {@link #update} therefore never touches {@code deleted} --
 * the previous system un-archived rows as a side effect of editing, and that is not
 * carried over ({@code DATA_MODEL.md} §4.1). An archived member is reported as
 * missing everywhere, {@link #findById} included.
 *
 * <p>The service returns DTOs, so entities stop here and never reach a controller.
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    /**
     * Columns a client may sort by.
     *
     * <p>A whitelist rather than a pass-through: an unknown property reaches
     * Hibernate as a JPQL fragment and fails at query-build time, so unfiltered
     * client input would turn a typo into a 500.
     *
     * <p>{@code openLoanCount} is absent on purpose -- it is not a column but the
     * result of a separate grouped query, so the database cannot order by it.
     */
    private static final Set<String> SORTABLE = Set.of("name", "email", "phoneNumber", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by("name").ascending();

    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;

    public MemberService(MemberRepository memberRepository, LoanRepository loanRepository) {
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
    }

    /**
     * One page of active members ({@code API_CONTRACT.md} §7).
     *
     * <p>{@code openLoanCount} for the whole page comes from a single grouped query
     * rather than a count per row, which would be N+1.
     */
    public PagedResponse<MemberResponse> list(String search, Pageable pageable) {
        String term = normalise(search);
        String pattern = term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";

        Page<Member> members = memberRepository.search(pattern, sanitise(pageable));

        Map<Long, Long> openLoanCounts =
                countOpenLoans(members.getContent().stream().map(Member::getId).toList());

        return PagedResponse.of(members.map(member ->
                MemberMapper.toResponse(member, openLoanCounts.getOrDefault(member.getId(), 0L))));
    }

    /** An archived member is indistinguishable from a missing one to the API. */
    public MemberResponse findById(Long id) {
        Member member = requireActive(id);
        return MemberMapper.toResponse(member, countOpenLoans(List.of(id)).getOrDefault(id, 0L));
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        String name = normalise(request.name());
        if (memberRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw new ApiException(ErrorCode.MEMBER_NAME_ALREADY_EXISTS, "name");
        }

        Member member = new Member();
        apply(member, request, name);

        // A brand new member cannot hold a book yet, so the count is known without a query.
        return MemberMapper.toResponse(save(member), 0L);
    }

    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        Member member = requireActive(id);

        String name = normalise(request.name());
        if (memberRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(name, id)) {
            throw new ApiException(ErrorCode.MEMBER_NAME_ALREADY_EXISTS, "name");
        }

        apply(member, request, name);

        return MemberMapper.toResponse(save(member), countOpenLoans(List.of(id)).getOrDefault(id, 0L));
    }

    /**
     * Archives a member, provided they are not still holding a book.
     *
     * <p>Soft delete only: finished loans keep pointing at the member, so a physical
     * delete would break history (and the FK is {@code NO ACTION}).
     *
     * <p>Known limitation: this read-then-write does not by itself stop a loan being
     * created for the member between the check and the commit -- nothing here locks
     * the member row. Closing it belongs on the other side, so the loan-creation path
     * in the loans phase must re-check that the member is still active before
     * inserting.
     */
    @Transactional
    public void delete(Long id) {
        Member member = requireActive(id);

        if (loanRepository.existsByMemberIdAndStateIn(id, LoanRepository.OPEN_STATES)) {
            // No field: the request is fine, the member's situation is not.
            throw new ApiException(ErrorCode.MEMBER_HAS_OPEN_LOANS);
        }

        member.setDeleted(true);
        memberRepository.save(member);
    }

    /** Never touches {@code deleted}: archiving and editing are separate operations. */
    private void apply(Member member, MemberRequest request, String name) {
        member.setName(name);
        member.setEmail(normalise(request.email()));
        member.setAddress(normalise(request.address()));
        member.setPhoneNumber(normalise(request.phoneNumber()));
    }

    private Member requireActive(Long id) {
        return memberRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * Open loans per member, for one or many members, in a single query.
     *
     * <p>Members with none are absent from the result, so callers default to zero.
     */
    private Map<Long, Long> countOpenLoans(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            // An empty IN list is not portable, and the answer is known anyway.
            return Map.of();
        }
        return loanRepository.countLoansByMemberIn(memberIds, LoanRepository.OPEN_STATES).stream()
                .collect(Collectors.toMap(MemberOpenLoanCount::memberId, MemberOpenLoanCount::count));
    }

    /**
     * Flushes so the partial unique index reports a duplicate name here, inside the
     * service, rather than as a raw database error later.
     *
     * <p>The pre-check above catches ordinary duplicates; this catch is the
     * race-condition net for two concurrent requests that both pass it.
     */
    private Member save(Member member) {
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.MEMBER_NAME_ALREADY_EXISTS, "name", ex);
        }
    }

    /**
     * Drops any sort order the member list cannot honour, falling back to name
     * ascending when nothing survives, so a bad {@code sort} parameter degrades
     * instead of failing.
     */
    private Pageable sanitise(Pageable pageable) {
        List<Sort.Order> allowed = pageable.getSort().stream()
                .filter(order -> SORTABLE.contains(order.getProperty()))
                .toList();
        Sort sort = allowed.isEmpty() ? DEFAULT_SORT : Sort.by(allowed);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /** Trims incidental whitespace and treats an empty string as absent. */
    private String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
