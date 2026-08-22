package com.library.management.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.loan.LoanRepository;
import com.library.management.backend.loan.MemberOpenLoanCount;
import com.library.management.backend.member.dto.MemberRequest;
import com.library.management.backend.member.dto.MemberResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for the member business rules.
 *
 * <p>Plain JUnit + Mockito, no Spring context: these rules are pure logic over two
 * repositories, so a container would only make the suite slower.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private MemberService memberService;

    private static Member member(Long id, String name) {
        Member member = new Member();
        member.setId(id);
        member.setName(name);
        member.setEmail("ion.popescu@example.ro");
        member.setAddress("Str. Victoriei 14, Timișoara");
        member.setPhoneNumber("+40 721 000 000");
        member.setCreatedAt(Instant.parse("2026-02-01T08:00:00Z"));
        return member;
    }

    private static MemberRequest request(String name) {
        return new MemberRequest(name, "ion.popescu@example.ro", "Str. Victoriei 14", "+40 721 000 000");
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        void returnsTheMemberWithTheirOpenLoanCount() {
            when(memberRepository.findByIdAndDeletedFalse(12L))
                    .thenReturn(Optional.of(member(12L, "Popescu Ion")));
            when(loanRepository.countLoansByMemberIn(anyCollection(), eq(LoanRepository.OPEN_STATES)))
                    .thenReturn(List.of(new MemberOpenLoanCount(12L, 2L)));

            MemberResponse result = memberService.findById(12L);

            assertThat(result.id()).isEqualTo(12L);
            assertThat(result.name()).isEqualTo("Popescu Ion");
            assertThat(result.openLoanCount()).isEqualTo(2L);
            assertThat(result.createdAt()).isEqualTo(Instant.parse("2026-02-01T08:00:00Z"));
        }

        @Test
        void reportsZeroWhenTheMemberHoldsNothing() {
            when(memberRepository.findByIdAndDeletedFalse(12L))
                    .thenReturn(Optional.of(member(12L, "Popescu Ion")));
            when(loanRepository.countLoansByMemberIn(anyCollection(), eq(LoanRepository.OPEN_STATES)))
                    .thenReturn(List.of());

            assertThat(memberService.findById(12L).openLoanCount()).isZero();
        }

        @Test
        void throwsNotFoundWhenMissingOrArchived() {
            // An archived member is not addressable: the repository filters on
            // deleted = false, so both cases arrive here as an empty Optional.
            when(memberRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.findById(99L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void savesTrimmedFieldsAndReportsZeroOpenLoans() {
            when(memberRepository.existsByNameIgnoreCaseAndDeletedFalse("Popescu Ion")).thenReturn(false);
            when(memberRepository.saveAndFlush(any(Member.class)))
                    .thenAnswer(invocation -> {
                        Member saved = invocation.getArgument(0);
                        saved.setId(12L);
                        return saved;
                    });

            MemberResponse result = memberService.create(new MemberRequest(
                    "  Popescu Ion  ", " ion.popescu@example.ro ", "  Str. Victoriei 14  ", "  "));

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).saveAndFlush(captor.capture());
            Member saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("Popescu Ion");
            assertThat(saved.getEmail()).isEqualTo("ion.popescu@example.ro");
            assertThat(saved.getAddress()).isEqualTo("Str. Victoriei 14");
            assertThat(saved.getPhoneNumber()).isNull();
            assertThat(saved.isDeleted()).isFalse();

            assertThat(result.id()).isEqualTo(12L);
            assertThat(result.openLoanCount()).isZero();
            // A brand new member cannot hold a book, so no query is worth making.
            verify(loanRepository, never()).countLoansByMemberIn(any(), any());
        }

        @Test
        void throwsWhenAnActiveMemberAlreadyUsesTheName() {
            when(memberRepository.existsByNameIgnoreCaseAndDeletedFalse("Popescu Ion")).thenReturn(true);

            assertThatThrownBy(() -> memberService.create(request("Popescu Ion")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NAME_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("name");
                    });

            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        void mapsAConcurrentUniqueIndexViolationOntoTheSameCode() {
            when(memberRepository.existsByNameIgnoreCaseAndDeletedFalse("Popescu Ion")).thenReturn(false);
            when(memberRepository.saveAndFlush(any(Member.class)))
                    .thenThrow(new DataIntegrityViolationException("ux_member_name_active"));

            assertThatThrownBy(() -> memberService.create(request("Popescu Ion")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NAME_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("name");
                    });
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void changesTheContactFieldsAndReportsTheCurrentOpenLoanCount() {
            Member existing = member(12L, "Popescu Ion");
            when(memberRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(existing));
            when(memberRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Popescu Ioana", 12L))
                    .thenReturn(false);
            when(memberRepository.saveAndFlush(existing)).thenReturn(existing);
            when(loanRepository.countLoansByMemberIn(anyCollection(), eq(LoanRepository.OPEN_STATES)))
                    .thenReturn(List.of(new MemberOpenLoanCount(12L, 3L)));

            MemberResponse result = memberService.update(12L, new MemberRequest(
                    "Popescu Ioana", "ioana@example.ro", "Str. Noua 2", "0721000001"));

            assertThat(existing.getName()).isEqualTo("Popescu Ioana");
            assertThat(existing.getEmail()).isEqualTo("ioana@example.ro");
            assertThat(existing.getAddress()).isEqualTo("Str. Noua 2");
            assertThat(existing.getPhoneNumber()).isEqualTo("0721000001");
            assertThat(result.openLoanCount()).isEqualTo(3L);
        }

        @Test
        void neverUnArchivesTheMember() {
            Member existing = member(12L, "Popescu Ion");
            when(memberRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(existing));
            when(memberRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Popescu Ion", 12L))
                    .thenReturn(false);
            when(memberRepository.saveAndFlush(existing)).thenReturn(existing);

            memberService.update(12L, request("Popescu Ion"));

            // Restore is explicit and members have none: an edit must not touch the flag.
            assertThat(existing.isDeleted()).isFalse();
        }

        @Test
        void ignoresTheMemberItselfWhenCheckingForDuplicates() {
            Member existing = member(12L, "Popescu Ion");
            when(memberRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(existing));
            when(memberRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Popescu Ion", 12L))
                    .thenReturn(false);
            when(memberRepository.saveAndFlush(existing)).thenReturn(existing);

            memberService.update(12L, request("Popescu Ion"));

            verify(memberRepository, never()).existsByNameIgnoreCaseAndDeletedFalse(any());
        }

        @Test
        void throwsWhenAnotherActiveMemberUsesTheName() {
            when(memberRepository.findByIdAndDeletedFalse(12L))
                    .thenReturn(Optional.of(member(12L, "Popescu Ion")));
            when(memberRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Ionescu Maria", 12L))
                    .thenReturn(true);

            assertThatThrownBy(() -> memberService.update(12L, request("Ionescu Maria")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NAME_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("name");
                    });

            verify(memberRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsNotFoundWhenMissingOrArchived() {
            when(memberRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.update(99L, request("Popescu Ion")))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void softDeletesAMemberHoldingNothing() {
            Member existing = member(12L, "Popescu Ion");
            when(memberRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(existing));
            when(loanRepository.existsByMemberIdAndStateIn(12L, LoanRepository.OPEN_STATES))
                    .thenReturn(false);

            memberService.delete(12L);

            assertThat(existing.isDeleted()).isTrue();
            verify(memberRepository).save(existing);
            verify(memberRepository, never()).delete(any());
        }

        @Test
        void throwsWhenTheMemberStillHoldsABook() {
            Member existing = member(12L, "Popescu Ion");
            when(memberRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(existing));
            when(loanRepository.existsByMemberIdAndStateIn(12L, LoanRepository.OPEN_STATES))
                    .thenReturn(true);

            assertThatThrownBy(() -> memberService.delete(12L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_HAS_OPEN_LOANS);
                        // A state conflict, not a bad field: nothing in the request is wrong.
                        assertThat(ex.getField()).isNull();
                    });

            assertThat(existing.isDeleted()).isFalse();
            verify(memberRepository, never()).save(any());
        }

        @Test
        void throwsNotFoundWhenMissingOrArchived() {
            when(memberRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.delete(99L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);

            verify(loanRepository, never()).existsByMemberIdAndStateIn(eq(99L), any());
        }
    }

    @Nested
    @DisplayName("list")
    class ListMembers {

        private static final Pageable FIRST_PAGE = PageRequest.of(0, 20, Sort.by("name"));

        private void stubSearch(Page<Member> page) {
            when(memberRepository.search(any(), any())).thenReturn(page);
        }

        private Pageable capturedPageable() {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(memberRepository).search(any(), captor.capture());
            return captor.getValue();
        }

        @Test
        void foldsTheGroupedCountsOntoTheRowsDefaultingToZero() {
            List<Member> members = List.of(member(12L, "Popescu Ion"), member(13L, "Ionescu Maria"));
            stubSearch(new PageImpl<>(members, FIRST_PAGE, 2));
            when(loanRepository.countLoansByMemberIn(anyCollection(), eq(LoanRepository.OPEN_STATES)))
                    .thenReturn(List.of(new MemberOpenLoanCount(13L, 2L)));

            PagedResponse<MemberResponse> result = memberService.list(null, FIRST_PAGE);

            assertThat(result.content())
                    .extracting(MemberResponse::id, MemberResponse::openLoanCount)
                    .containsExactly(tuple(12L, 0L), tuple(13L, 2L));
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @Test
        void skipsTheLoanCountQueryForAnEmptyPage() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            PagedResponse<MemberResponse> result = memberService.list(null, FIRST_PAGE);

            assertThat(result.content()).isEmpty();
            // An empty IN list is not portable -- and a query here would be pure waste.
            verify(loanRepository, never()).countLoansByMemberIn(any(), any());
        }

        @Test
        void passesNoPatternForABlankTerm() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            memberService.list("   ", FIRST_PAGE);

            verify(memberRepository).search(eq(null), any(Pageable.class));
        }

        @Test
        void lowercasesAndWrapsTheSearchTerm() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            memberService.list("  Popescu  ", FIRST_PAGE);

            verify(memberRepository).search(eq("%popescu%"), any(Pageable.class));
        }

        @Test
        void keepsAWhitelistedSort() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            memberService.list(null, PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt")));

            Pageable used = capturedPageable();
            assertThat(used.getPageNumber()).isEqualTo(1);
            assertThat(used.getPageSize()).isEqualTo(5);
            assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        @Test
        void fallsBackToNameAscendingForAnUnknownSortProperty() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            // openLoanCount is deliberately not sortable -- it is not a column but the
            // result of a separate grouped query, and an unfiltered property would blow
            // up at query-build time.
            memberService.list(null, PageRequest.of(0, 20, Sort.by("openLoanCount")));

            assertThat(capturedPageable().getSort()).isEqualTo(Sort.by("name").ascending());
        }
    }
}
