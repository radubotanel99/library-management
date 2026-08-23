package com.library.management.backend.loan;

import com.library.management.backend.book.Book;
import com.library.management.backend.book.BookRepository;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.loan.dto.LoanRequest;
import com.library.management.backend.loan.dto.LoanResponse;
import com.library.management.backend.member.Member;
import com.library.management.backend.member.MemberRepository;
import com.library.management.backend.parameter.SettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for lending and returning.
 *
 * <p>Two safeguards guard {@link #create}: a copy that is already out cannot be lent
 * again, and a member cannot exceed {@code MAX_BOOKS_PER_MEMBER} open loans. Both
 * are re-checked here even where another service has checked something similar,
 * because this is the only place that inserts a loan.
 *
 * <p>Everything about time is derived rather than stored. There is no {@code due_at}
 * column ({@code DATA_MODEL.md} §6), and the persisted {@code state} is treated as a
 * hint, never as the answer -- see {@link #toResponse(Loan, Instant, int)} and
 * {@link #list}.
 *
 * <p>The service returns DTOs, so entities stop here and never reach a controller.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class LoanService {

    /**
     * Columns a client may sort by.
     *
     * <p>A whitelist rather than a pass-through: an unknown property reaches
     * Hibernate as a JPQL fragment and fails at query-build time, so unfiltered
     * client input would turn a typo into a 500.
     *
     * <p>The book and member fields are absent on purpose. The search query
     * fetch-joins both, so ordering by title or member name would cost nothing --
     * but the catalogue deliberately refuses to sort by its joined
     * {@code categoryName}, and the loans list stays consistent with that rather
     * than quietly setting a different precedent. {@code state} is absent too, for a
     * different reason: the stored column is the same "hint, never the answer" value
     * {@link #toResponse(Loan, Instant, int)} refuses to trust for display, so
     * ordering by it can visibly disagree with the {@code LATE}/{@code ACTIVE} badge
     * shown next to it. {@code dueAt} and {@code daysOverdue} cannot be sorted on at
     * all: they are not columns, and sorting by {@code createdAt} orders open loans
     * identically anyway.
     */
    private static final Set<String> SORTABLE = Set.of("createdAt", "finishedAt");

    /** Newest first: the loans screen is a worklist, not an archive. */
    private static final Sort DEFAULT_SORT = Sort.by("createdAt").descending();

    /** No {@code state} filter means every loan, open or not. */
    private static final Set<LoanState> ALL_STATES = EnumSet.allOf(LoanState.class);

    private static final Set<LoanState> FINISHED_STATES = EnumSet.of(LoanState.FINISHED);

    /** A loan minutes past due is still a day late -- never zero, never negative. */
    private static final int MIN_DAYS_OVERDUE = 1;

    /** A forgotten loan reports "a year or more" rather than an absurd number. */
    private static final int MAX_DAYS_OVERDUE = 365;

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public LoanService(LoanRepository loanRepository,
                       BookRepository bookRepository,
                       MemberRepository memberRepository,
                       SettingsService settingsService,
                       Clock clock) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    /**
     * One page of loans ({@code API_CONTRACT.md} §8).
     *
     * <p>The {@code state} filter is <strong>not</strong> translated into
     * {@code where state = :state} for {@code ACTIVE} and {@code LATE}. Lateness is a
     * function of {@code createdAt} and the current lending period, and the stored
     * column only catches up when the re-evaluation job runs -- during this phase
     * nothing ever writes {@code LATE} at all, so a column filter would return an
     * empty page forever. Instead the filter becomes a cut-off instant, and the
     * database answers the same question the response derivation does:
     *
     * <table>
     *   <caption>state filter to query parameters</caption>
     *   <tr><th>{@code state}</th><th>states</th><th>lateBefore</th><th>notLateFrom</th></tr>
     *   <tr><td>{@code ACTIVE}</td><td>open</td><td>--</td><td>cut-off</td></tr>
     *   <tr><td>{@code LATE}</td><td>open</td><td>cut-off</td><td>--</td></tr>
     *   <tr><td>{@code OPEN}</td><td>open</td><td>--</td><td>--</td></tr>
     *   <tr><td>{@code FINISHED}</td><td>finished</td><td>--</td><td>--</td></tr>
     *   <tr><td>absent</td><td>all</td><td>--</td><td>--</td></tr>
     * </table>
     *
     * <p>where the cut-off is {@code now - DAYS_TO_KEEP_A_BOOK}: a loan is late
     * exactly when it was borrowed before it. {@code FINISHED} is the one case that
     * <em>is</em> a plain column filter, because a returned loan is a fact rather
     * than a calculation.
     *
     * <p>The lending period is read once for the whole page, not once per row.
     */
    public PagedResponse<LoanResponse> list(LoanStateFilter state,
                                            Long memberId,
                                            Long bookId,
                                            String search,
                                            Pageable pageable) {
        Instant now = Instant.now(clock);
        int daysToKeep = settingsService.getDaysToKeepABook();
        Instant cutOff = now.minus(daysToKeep, ChronoUnit.DAYS);

        Collection<LoanState> states = statesFor(state);
        Instant lateBefore = state == LoanStateFilter.LATE ? cutOff : null;
        Instant notLateFrom = state == LoanStateFilter.ACTIVE ? cutOff : null;

        String term = normalise(search);
        String pattern = term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        Integer searchNumber = parseBookNumber(term);

        Page<Loan> loans = loanRepository.search(
                states, lateBefore, notLateFrom, memberId, bookId, pattern, searchNumber,
                sanitise(pageable));

        return PagedResponse.of(loans.map(loan -> toResponse(loan, now, daysToKeep)));
    }

    public LoanResponse findById(Long id) {
        return toResponse(requireById(id));
    }

    /**
     * Lends a copy to a member ({@code API_CONTRACT.md} §8).
     *
     * <p>Both ids are re-resolved and re-validated here rather than trusted. The
     * member check in particular is not redundant: {@code MemberService.delete}
     * refuses to archive a member holding books but does not lock the row, so a
     * member could be archived between a client loading the picker and this insert.
     * That check is documented there as belonging on this side, and this is it.
     * An archived member -- like a non-{@code ACTIVE} copy -- is reported as missing,
     * the same way every other endpoint treats them.
     *
     * <p><strong>Only one of the two safeguards has a database backstop.</strong>
     * {@code ux_loan_one_open_per_book} makes double-lending a copy impossible even
     * under a race, which is why {@link #save} maps its violation. The per-member
     * limit has no equivalent index -- it counts rows rather than constraining one --
     * so two requests submitted at the same instant could both pass the count check
     * and leave a member one book over. Accepted, not overlooked: this is a
     * two-librarian system where the two would have to click within milliseconds of
     * each other, the overshoot is one book, and a librarian can return it. Locking
     * the member row on every lend would cost more than the problem is worth. The
     * same trade-off, written down for the same reason, as the race
     * {@code MemberService.delete} documents.
     *
     * <p>The borrow date is stamped from the injected clock, never taken from the
     * request: a client-supplied date would move the due date.
     */
    @Transactional
    public LoanResponse create(LoanRequest request) {
        Book book = bookRepository.findById(request.bookId())
                .filter(candidate -> candidate.getStatus() == BookStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND, "bookId"));

        Member member = memberRepository.findByIdAndDeletedFalse(request.memberId())
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND, "memberId"));

        if (loanRepository.existsByBookIdAndStateIn(book.getId(), LoanRepository.OPEN_STATES)) {
            // No field: both ids are fine, the copy's situation is not.
            throw new ApiException(ErrorCode.BOOK_ALREADY_ON_LOAN);
        }

        if (loanRepository.countByMemberIdAndStateIn(member.getId(), LoanRepository.OPEN_STATES)
                >= settingsService.getMaxBooksPerMember()) {
            throw new ApiException(ErrorCode.MEMBER_TOO_MANY_LOANS);
        }

        Loan loan = new Loan();
        loan.setBook(book);
        loan.setMember(member);
        loan.setState(LoanState.ACTIVE);
        // Set explicitly as well as by JPA auditing, so the borrow date comes from the
        // same clock the due date is derived from and never from the request.
        loan.setCreatedAt(Instant.now(clock));

        return toResponse(save(loan));
    }

    /**
     * Takes a copy back ({@code API_CONTRACT.md} §8).
     *
     * <p>{@code finishedAt} is the test rather than {@code state}, because it is the
     * column that actually records the event: the re-evaluation job rewrites
     * {@code state} between {@code ACTIVE} and {@code LATE}, but nothing moves a loan
     * out of {@code FINISHED}, and a returned loan always carries a return date.
     *
     * <p>Saves without flushing: returning claims no unique index, so there is no
     * collision to map.
     */
    @Transactional
    public LoanResponse returnLoan(Long id) {
        Loan loan = requireById(id);

        if (loan.getFinishedAt() != null) {
            // No field: the request is fine, the loan's situation is not.
            throw new ApiException(ErrorCode.LOAN_ALREADY_FINISHED);
        }

        loan.setState(LoanState.FINISHED);
        loan.setFinishedAt(Instant.now(clock));

        return toResponse(loanRepository.save(loan));
    }

    /**
     * Brings the stored {@code state} column back in line with the dates
     * ({@code API_CONTRACT.md} §11).
     *
     * <p>Called from two places, neither of which is a request: a daily scheduled job,
     * so a loan that quietly came due overnight is flagged; and immediately after the
     * lending period is saved, so raising it from 14 to 21 days flips affected loans
     * back out of {@code LATE} at once ({@code DATA_MODEL.md} §6). Both directions run
     * every time -- a single sweep cannot know which way the parameter moved, and
     * running the pair is two indexed statements.
     *
     * <p><strong>This keeps a cache warm; it does not decide anything.</strong> The
     * answer to "is this loan late?" is and remains
     * {@link #toResponse(Loan, Instant, int)}'s date arithmetic, which is recomputed on
     * every read and is therefore never stale. The column exists for the things a
     * derivation cannot serve -- {@code ix_loan_state}, reporting, and anyone reading
     * the table directly -- and between two runs of this method it is simply out of
     * date. Nothing may start trusting it because this method now writes it.
     *
     * <p>The cut-off is computed here rather than shared with {@link #list}: that
     * method deliberately reads the lending period exactly once per page, and routing
     * it through a common helper would make it read twice. One line of duplicated
     * arithmetic is the cheaper of the two.
     */
    @Transactional
    public void reevaluateOverdue() {
        Instant cutOff = Instant.now(clock).minus(settingsService.getDaysToKeepABook(), ChronoUnit.DAYS);

        int markedLate = loanRepository.markLateBorrowedBefore(cutOff);
        int markedActive = loanRepository.markActiveBorrowedFrom(cutOff);

        log.info("Overdue re-evaluation: {} loan(s) marked LATE, {} marked ACTIVE", markedLate, markedActive);
    }

    private Loan requireById(Long id) {
        return loanRepository.findWithBookAndMemberById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.LOAN_NOT_FOUND));
    }

    private LoanResponse toResponse(Loan loan) {
        return toResponse(loan, Instant.now(clock), settingsService.getDaysToKeepABook());
    }

    /**
     * Derives the three values the response carries that the row does not: the due
     * date, the effective state, and how many days a late loan is overdue.
     *
     * <p><strong>This derivation is permanent, not a stand-in for the overdue
     * engine.</strong> When the scheduled re-evaluation job arrives it will keep
     * {@code loan.state} roughly in step, but "roughly" is the point: the stored
     * value is always as old as the last run, so between a change to
     * {@code DAYS_TO_KEEP_A_BOOK} and the next run it is simply wrong.
     * {@code DATA_MODEL.md} §6 requires the due date to reflect the parameter's
     * <em>current</em> value -- raising the lending period must flip affected loans
     * back from late to active immediately, which is the behaviour the missing
     * {@code due_at} column exists to guarantee. Reading {@code loan.getState()} here
     * instead would reintroduce exactly the staleness the schema was designed to
     * avoid. Do not "simplify" this away once the job exists.
     *
     * <p>{@code now} and {@code daysToKeep} are parameters rather than read here, so
     * a whole page is derived from one clock reading and one settings read.
     */
    private LoanResponse toResponse(Loan loan, Instant now, int daysToKeep) {
        Instant dueAt = loan.getCreatedAt().plus(daysToKeep, ChronoUnit.DAYS);

        if (loan.getFinishedAt() != null) {
            // A returned loan is history: how late it was is no longer actionable.
            return LoanMapper.toResponse(loan, LoanState.FINISHED, dueAt, 0);
        }
        if (dueAt.isBefore(now)) {
            return LoanMapper.toResponse(loan, LoanState.LATE, dueAt, daysOverdue(dueAt, now));
        }
        return LoanMapper.toResponse(loan, LoanState.ACTIVE, dueAt, 0);
    }

    /**
     * Whole days between the due date and now, rounded <em>up</em> and clamped to
     * {@code [1, 365]} ({@code API_CONTRACT.md} §8).
     *
     * <p>Rounding up so a loan an hour past due reads as "1 day overdue" rather than
     * "0 days", which would contradict the row being flagged late at all. The upper
     * bound keeps a copy written off years ago from reporting a four-digit number
     * that tells a librarian nothing the "over a year" reading does not.
     *
     * <p>Integer arithmetic on seconds rather than {@code Duration.toDays}, which
     * truncates, and rather than floating point, which would round the boundary
     * unpredictably. Only called when {@code dueAt} is strictly before {@code now},
     * so the difference is always positive.
     */
    private int daysOverdue(Instant dueAt, Instant now) {
        long secondsPerDay = Duration.ofDays(1).toSeconds();
        long overdueSeconds = Duration.between(dueAt, now).toSeconds();
        long days = (overdueSeconds + secondsPerDay - 1) / secondsPerDay;
        return Math.clamp(days, MIN_DAYS_OVERDUE, MAX_DAYS_OVERDUE);
    }

    /**
     * The states the query may return for a given filter.
     *
     * <p>{@code ACTIVE} and {@code LATE} both map to the open pair: which of the two
     * a row actually is gets decided by the cut-off instant, not by the column. Never
     * null and never empty -- an empty {@code IN} list is not portable.
     */
    private Collection<LoanState> statesFor(LoanStateFilter filter) {
        if (filter == null) {
            return ALL_STATES;
        }
        return switch (filter) {
            case ACTIVE, LATE, OPEN -> LoanRepository.OPEN_STATES;
            case FINISHED -> FINISHED_STATES;
        };
    }

    /**
     * Flushes so the partial unique index reports a copy that is already out here,
     * inside the service, rather than as a raw database error later.
     *
     * <p>The pre-check above catches the ordinary case; this catch is the
     * race-condition net for two requests lending the same copy at once, and it is
     * the reason {@code ux_loan_one_open_per_book} exists ({@code DATA_MODEL.md} §6).
     */
    private Loan save(Loan loan) {
        try {
            return loanRepository.saveAndFlush(loan);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.BOOK_ALREADY_ON_LOAN, null, ex);
        }
    }

    /**
     * Drops any sort order the loans list cannot honour, falling back to newest
     * first when nothing survives, so a bad {@code sort} parameter degrades instead
     * of failing.
     */
    private Pageable sanitise(Pageable pageable) {
        List<Sort.Order> allowed = pageable.getSort().stream()
                .filter(order -> SORTABLE.contains(order.getProperty()))
                .toList();
        Sort sort = allowed.isEmpty() ? DEFAULT_SORT : Sort.by(allowed);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /** A search term that is not a whole number simply does not match on number. */
    private Integer parseBookNumber(String term) {
        if (term == null) {
            return null;
        }
        try {
            return Integer.valueOf(term);
        } catch (NumberFormatException ex) {
            return null;
        }
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
