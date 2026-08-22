package com.library.management.backend.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.management.backend.book.Book;
import com.library.management.backend.book.BookRepository;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.category.Category;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for the lending business rules.
 *
 * <p>Plain JUnit + Mockito, no Spring context: these rules are pure logic over three
 * repositories, a settings reader and a clock, so a container would only make the
 * suite slower.
 *
 * <p>The clock is fixed rather than mocked. Whether a loan is active or late, and by
 * how many days, is arithmetic on "now" -- pinning it is what makes the boundary
 * testable at all, and a real {@code Clock.fixed} keeps the arithmetic honest where
 * a stubbed one would only return what the test told it to.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    /** The seeded lending period; every test stubs this value explicitly. */
    private static final int DAYS_TO_KEEP = 14;

    /** A loan borrowed exactly here comes due exactly now. */
    private static final Instant CUT_OFF = NOW.minus(DAYS_TO_KEEP, ChronoUnit.DAYS);

    private static final LoanRequest REQUEST = new LoanRequest(41L, 12L);

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SettingsService settingsService;

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks
    private LoanService loanService;

    private static Book book(Long id, BookStatus status) {
        Category category = new Category();
        category.setId(3L);
        category.setName("Fiction");

        Book book = new Book();
        book.setId(id);
        book.setTitle("Amintiri din copilărie");
        book.setAuthor("Ion Creangă");
        book.setBookNumber(1201);
        book.setCategory(category);
        book.setStatus(status);
        return book;
    }

    private static Member member(Long id) {
        Member member = new Member();
        member.setId(id);
        member.setName("Popescu Ion");
        return member;
    }

    private static Loan loan(Long id, Instant borrowedAt, Instant finishedAt) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setBook(book(41L, BookStatus.ACTIVE));
        loan.setMember(member(12L));
        loan.setState(finishedAt == null ? LoanState.ACTIVE : LoanState.FINISHED);
        loan.setCreatedAt(borrowedAt);
        loan.setFinishedAt(finishedAt);
        return loan;
    }

    /** An open loan borrowed {@code age} before now. */
    private static Loan openLoan(Duration age) {
        return loan(508L, NOW.minus(age), null);
    }

    private void stubLendingPeriod() {
        when(settingsService.getDaysToKeepABook()).thenReturn(DAYS_TO_KEEP);
    }

    @Nested
    @DisplayName("create")
    class Create {

        private void stubLendableBookAndMember() {
            when(bookRepository.findById(41L)).thenReturn(Optional.of(book(41L, BookStatus.ACTIVE)));
            when(memberRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(member(12L)));
        }

        private void stubBothSafeguardsPass() {
            when(loanRepository.existsByBookIdAndStateIn(41L, LoanRepository.OPEN_STATES))
                    .thenReturn(false);
            when(loanRepository.countByMemberIdAndStateIn(12L, LoanRepository.OPEN_STATES))
                    .thenReturn(1L);
            when(settingsService.getMaxBooksPerMember()).thenReturn(3);
        }

        @Test
        void lendsTheCopyStampingTheBorrowDateFromTheClock() {
            stubLendableBookAndMember();
            stubBothSafeguardsPass();
            stubLendingPeriod();
            when(loanRepository.saveAndFlush(any(Loan.class)))
                    .thenAnswer(invocation -> {
                        Loan saved = invocation.getArgument(0);
                        saved.setId(508L);
                        return saved;
                    });

            LoanResponse result = loanService.create(REQUEST);

            ArgumentCaptor<Loan> captor = ArgumentCaptor.forClass(Loan.class);
            verify(loanRepository).saveAndFlush(captor.capture());
            Loan saved = captor.getValue();
            assertThat(saved.getBook().getId()).isEqualTo(41L);
            assertThat(saved.getMember().getId()).isEqualTo(12L);
            assertThat(saved.getState()).isEqualTo(LoanState.ACTIVE);
            // Server-side, from the injected clock -- never from the request.
            assertThat(saved.getCreatedAt()).isEqualTo(NOW);
            assertThat(saved.getFinishedAt()).isNull();

            assertThat(result.id()).isEqualTo(508L);
            assertThat(result.bookTitle()).isEqualTo("Amintiri din copilărie");
            assertThat(result.bookAuthor()).isEqualTo("Ion Creangă");
            assertThat(result.bookNumber()).isEqualTo(1201);
            assertThat(result.memberName()).isEqualTo("Popescu Ion");
            assertThat(result.state()).isEqualTo(LoanState.ACTIVE);
            assertThat(result.borrowedAt()).isEqualTo(NOW);
            assertThat(result.dueAt()).isEqualTo(NOW.plus(DAYS_TO_KEEP, ChronoUnit.DAYS));
            assertThat(result.daysOverdue()).isZero();
            assertThat(result.finishedAt()).isNull();
        }

        @Test
        void throwsBookNotFoundWhenTheCopyDoesNotExist() {
            when(bookRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.create(new LoanRequest(99L, 12L)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND);
                        assertThat(ex.getField()).isEqualTo("bookId");
                    });

            verify(loanRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsBookNotFoundForAnArchivedCopy() {
            // An archived copy is treated as not existing, exactly as PUT and
            // GET /api/books/{id} already treat one -- not as a conflict.
            when(bookRepository.findById(41L)).thenReturn(Optional.of(book(41L, BookStatus.LOST)));

            assertThatThrownBy(() -> loanService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

            verify(memberRepository, never()).findByIdAndDeletedFalse(any());
            verify(loanRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsMemberNotFoundWhenTheMemberIsMissingOrArchived() {
            // The re-check MemberService.delete's javadoc requires: archiving does not
            // lock the row, so the member may have been archived since the picker loaded.
            when(bookRepository.findById(41L)).thenReturn(Optional.of(book(41L, BookStatus.ACTIVE)));
            when(memberRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
                        assertThat(ex.getField()).isEqualTo("memberId");
                    });

            verify(loanRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsWhenTheCopyIsAlreadyOut() {
            stubLendableBookAndMember();
            when(loanRepository.existsByBookIdAndStateIn(41L, LoanRepository.OPEN_STATES))
                    .thenReturn(true);

            assertThatThrownBy(() -> loanService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOK_ALREADY_ON_LOAN);
                        // Both ids are fine; the copy's situation is not.
                        assertThat(ex.getField()).isNull();
                    });

            verify(loanRepository, never()).saveAndFlush(any());
        }

        @Test
        void mapsAConcurrentUniqueIndexViolationOntoTheSameCode() {
            // ux_loan_one_open_per_book is the backstop for two requests lending the
            // same copy at once, and it must not surface as a raw database error.
            stubLendableBookAndMember();
            stubBothSafeguardsPass();
            when(loanRepository.saveAndFlush(any(Loan.class)))
                    .thenThrow(new DataIntegrityViolationException("ux_loan_one_open_per_book"));

            assertThatThrownBy(() -> loanService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BOOK_ALREADY_ON_LOAN);
        }

        @Test
        void throwsWhenTheMemberAlreadyHoldsTheMaximum() {
            stubLendableBookAndMember();
            when(loanRepository.existsByBookIdAndStateIn(41L, LoanRepository.OPEN_STATES))
                    .thenReturn(false);
            when(loanRepository.countByMemberIdAndStateIn(12L, LoanRepository.OPEN_STATES))
                    .thenReturn(3L);
            when(settingsService.getMaxBooksPerMember()).thenReturn(3);

            assertThatThrownBy(() -> loanService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_TOO_MANY_LOANS);
                        assertThat(ex.getField()).isNull();
                    });

            verify(loanRepository, never()).saveAndFlush(any());
        }

        @Test
        void lendsToAMemberOneUnderTheMaximum() {
            stubLendableBookAndMember();
            when(loanRepository.existsByBookIdAndStateIn(41L, LoanRepository.OPEN_STATES))
                    .thenReturn(false);
            when(loanRepository.countByMemberIdAndStateIn(12L, LoanRepository.OPEN_STATES))
                    .thenReturn(2L);
            when(settingsService.getMaxBooksPerMember()).thenReturn(3);
            stubLendingPeriod();
            when(loanRepository.saveAndFlush(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));

            assertThat(loanService.create(REQUEST).state()).isEqualTo(LoanState.ACTIVE);
        }

        @Test
        void throwsWhenTheMemberIsSomehowOverTheMaximum() {
            // Defensive: the limit can be lowered while members already hold more.
            stubLendableBookAndMember();
            when(loanRepository.existsByBookIdAndStateIn(41L, LoanRepository.OPEN_STATES))
                    .thenReturn(false);
            when(loanRepository.countByMemberIdAndStateIn(12L, LoanRepository.OPEN_STATES))
                    .thenReturn(5L);
            when(settingsService.getMaxBooksPerMember()).thenReturn(3);

            assertThatThrownBy(() -> loanService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_TOO_MANY_LOANS);
        }
    }

    @Nested
    @DisplayName("returnLoan")
    class ReturnLoan {

        @Test
        void finishesTheLoanAndStampsTheReturnDate() {
            Loan existing = openLoan(Duration.ofDays(3));
            when(loanRepository.findWithBookAndMemberById(508L)).thenReturn(Optional.of(existing));
            when(loanRepository.save(existing)).thenReturn(existing);
            stubLendingPeriod();

            LoanResponse result = loanService.returnLoan(508L);

            assertThat(existing.getState()).isEqualTo(LoanState.FINISHED);
            assertThat(existing.getFinishedAt()).isEqualTo(NOW);
            assertThat(result.state()).isEqualTo(LoanState.FINISHED);
            assertThat(result.finishedAt()).isEqualTo(NOW);
            assertThat(result.daysOverdue()).isZero();
        }

        @Test
        void reportsAReturnedOverdueLoanAsFinishedWithNoDaysOverdue() {
            // How late it was is history once the book is back on the shelf.
            Loan existing = openLoan(Duration.ofDays(40));
            when(loanRepository.findWithBookAndMemberById(508L)).thenReturn(Optional.of(existing));
            when(loanRepository.save(existing)).thenReturn(existing);
            stubLendingPeriod();

            LoanResponse result = loanService.returnLoan(508L);

            assertThat(result.state()).isEqualTo(LoanState.FINISHED);
            assertThat(result.daysOverdue()).isZero();
        }

        @Test
        void throwsWhenTheLoanIsAlreadyFinished() {
            Loan finished = loan(508L, NOW.minus(Duration.ofDays(20)), NOW.minus(Duration.ofDays(6)));
            when(loanRepository.findWithBookAndMemberById(508L)).thenReturn(Optional.of(finished));

            assertThatThrownBy(() -> loanService.returnLoan(508L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOAN_ALREADY_FINISHED);
                        assertThat(ex.getField()).isNull();
                    });

            // The original return date must survive a second attempt.
            assertThat(finished.getFinishedAt()).isEqualTo(NOW.minus(Duration.ofDays(6)));
            verify(loanRepository, never()).save(any());
        }

        @Test
        void throwsNotFoundWhenTheLoanDoesNotExist() {
            when(loanRepository.findWithBookAndMemberById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.returnLoan(99L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LOAN_NOT_FOUND);

            verify(loanRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        private LoanResponse find(Loan stored) {
            when(loanRepository.findWithBookAndMemberById(508L)).thenReturn(Optional.of(stored));
            stubLendingPeriod();
            return loanService.findById(508L);
        }

        @Test
        void derivesTheDueDateFromTheCurrentLendingPeriod() {
            Instant borrowedAt = Instant.parse("2026-08-20T10:15:00Z");

            LoanResponse result = find(loan(508L, borrowedAt, null));

            assertThat(result.borrowedAt()).isEqualTo(borrowedAt);
            assertThat(result.dueAt()).isEqualTo(Instant.parse("2026-09-03T10:15:00Z"));
        }

        @Test
        void reportsALoanBorrowedExactlyOnTheCutOffAsActive() {
            // dueAt lands exactly on now: due, not yet overdue.
            LoanResponse result = find(loan(508L, CUT_OFF, null));

            assertThat(result.dueAt()).isEqualTo(NOW);
            assertThat(result.state()).isEqualTo(LoanState.ACTIVE);
            assertThat(result.daysOverdue()).isZero();
        }

        @Test
        void reportsALoanBorrowedAnInstantAfterTheCutOffAsActive() {
            LoanResponse result = find(loan(508L, CUT_OFF.plusNanos(1), null));

            assertThat(result.state()).isEqualTo(LoanState.ACTIVE);
            assertThat(result.daysOverdue()).isZero();
        }

        @Test
        void reportsALoanBorrowedAnInstantBeforeTheCutOffAsLate() {
            LoanResponse result = find(loan(508L, CUT_OFF.minusNanos(1), null));

            assertThat(result.state()).isEqualTo(LoanState.LATE);
            // Clamped up: a loan flagged late can never read as 0 days overdue.
            assertThat(result.daysOverdue()).isEqualTo(1);
        }

        @Test
        void clampsAJustBarelyLateLoanToASingleDay() {
            LoanResponse result = find(openLoan(Duration.ofDays(DAYS_TO_KEEP).plusMinutes(30)));

            assertThat(result.state()).isEqualTo(LoanState.LATE);
            assertThat(result.daysOverdue()).isEqualTo(1);
        }

        @Test
        void countsWholeDaysOverdueExactly() {
            LoanResponse result = find(openLoan(Duration.ofDays(DAYS_TO_KEEP + 6)));

            assertThat(result.state()).isEqualTo(LoanState.LATE);
            assertThat(result.daysOverdue()).isEqualTo(6);
        }

        @Test
        void roundsAPartDayUp() {
            LoanResponse result = find(openLoan(Duration.ofDays(DAYS_TO_KEEP + 6).plusSeconds(1)));

            assertThat(result.daysOverdue()).isEqualTo(7);
        }

        @Test
        void clampsAnAncientLoanTo365Days() {
            LoanResponse result = find(openLoan(Duration.ofDays(1000)));

            assertThat(result.state()).isEqualTo(LoanState.LATE);
            assertThat(result.daysOverdue()).isEqualTo(365);
        }

        @Test
        void ignoresTheStoredStateInFavourOfTheDerivedOne() {
            // The stored column is only as fresh as the last re-evaluation run: a
            // loan still marked ACTIVE is reported LATE if the dates say so.
            Loan stale = openLoan(Duration.ofDays(30));
            stale.setState(LoanState.ACTIVE);

            assertThat(find(stale).state()).isEqualTo(LoanState.LATE);
        }

        @Test
        void reportsAFinishedLoanAsFinishedWhateverTheDatesSay() {
            Loan finished = loan(508L, NOW.minus(Duration.ofDays(90)), NOW.minus(Duration.ofDays(80)));

            LoanResponse result = find(finished);

            assertThat(result.state()).isEqualTo(LoanState.FINISHED);
            assertThat(result.daysOverdue()).isZero();
        }

        @Test
        void throwsNotFoundWhenTheLoanDoesNotExist() {
            when(loanRepository.findWithBookAndMemberById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> loanService.findById(99L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LOAN_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("list")
    class ListLoans {

        private static final Pageable FIRST_PAGE =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        private void stubSearch(Page<Loan> page) {
            when(loanRepository.search(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);
        }

        private void stubEmptySearch() {
            stubLendingPeriod();
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));
        }

        @SuppressWarnings("unchecked")
        private Collection<LoanState> capturedStates() {
            ArgumentCaptor<Collection<LoanState>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(loanRepository).search(
                    captor.capture(), any(), any(), any(), any(), any(), any(), any());
            return captor.getValue();
        }

        private Instant capturedLateBefore() {
            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(loanRepository).search(
                    any(), captor.capture(), any(), any(), any(), any(), any(), any());
            return captor.getValue();
        }

        private Instant capturedNotLateFrom() {
            ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
            verify(loanRepository).search(
                    any(), any(), captor.capture(), any(), any(), any(), any(), any());
            return captor.getValue();
        }

        private Pageable capturedPageable() {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(loanRepository).search(
                    any(), any(), any(), any(), any(), any(), any(), captor.capture());
            return captor.getValue();
        }

        @Test
        void mapsEveryRowThroughTheSameDerivation() {
            stubLendingPeriod();
            stubSearch(new PageImpl<>(
                    List.of(openLoan(Duration.ofDays(3)), openLoan(Duration.ofDays(30))),
                    FIRST_PAGE, 2));

            PagedResponse<LoanResponse> result = loanService.list(null, null, null, null, FIRST_PAGE);

            assertThat(result.content()).extracting(LoanResponse::state)
                    .containsExactly(LoanState.ACTIVE, LoanState.LATE);
            assertThat(result.content()).extracting(LoanResponse::daysOverdue)
                    .containsExactly(0, 16);
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @Test
        void readsTheLendingPeriodOncePerPageNotOncePerRow() {
            stubLendingPeriod();
            stubSearch(new PageImpl<>(
                    List.of(openLoan(Duration.ofDays(1)),
                            openLoan(Duration.ofDays(2)),
                            openLoan(Duration.ofDays(3))),
                    FIRST_PAGE, 3));

            loanService.list(null, null, null, null, FIRST_PAGE);

            verify(settingsService).getDaysToKeepABook();
        }

        @Test
        void asksForEveryStateWhenNoFilterIsGiven() {
            stubEmptySearch();

            loanService.list(null, null, null, null, FIRST_PAGE);

            assertThat(capturedStates())
                    .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(LoanState.class));
            assertThat(capturedLateBefore()).isNull();
            assertThat(capturedNotLateFrom()).isNull();
        }

        @Test
        void translatesActiveIntoOpenStatesBorrowedOnOrAfterTheCutOff() {
            // Not a state = ACTIVE filter: nothing in this phase ever writes LATE, so
            // a column filter would answer from a value that is never maintained.
            stubEmptySearch();

            loanService.list(LoanStateFilter.ACTIVE, null, null, null, FIRST_PAGE);

            assertThat(capturedStates())
                    .containsExactlyInAnyOrderElementsOf(LoanRepository.OPEN_STATES);
            assertThat(capturedLateBefore()).isNull();
            assertThat(capturedNotLateFrom()).isEqualTo(CUT_OFF);
        }

        @Test
        void translatesLateIntoOpenStatesBorrowedBeforeTheCutOff() {
            stubEmptySearch();

            loanService.list(LoanStateFilter.LATE, null, null, null, FIRST_PAGE);

            assertThat(capturedStates())
                    .containsExactlyInAnyOrderElementsOf(LoanRepository.OPEN_STATES);
            assertThat(capturedLateBefore()).isEqualTo(CUT_OFF);
            assertThat(capturedNotLateFrom()).isNull();
        }

        @Test
        void translatesOpenIntoBothOpenStatesWithNoCutOff() {
            stubEmptySearch();

            loanService.list(LoanStateFilter.OPEN, null, null, null, FIRST_PAGE);

            assertThat(capturedStates())
                    .containsExactlyInAnyOrderElementsOf(LoanRepository.OPEN_STATES);
            assertThat(capturedLateBefore()).isNull();
            assertThat(capturedNotLateFrom()).isNull();
        }

        @Test
        void translatesFinishedIntoAPlainStateFilter() {
            // The one case that really is a column filter: a returned loan is a fact,
            // not a calculation.
            stubEmptySearch();

            loanService.list(LoanStateFilter.FINISHED, null, null, null, FIRST_PAGE);

            assertThat(capturedStates()).containsExactly(LoanState.FINISHED);
            assertThat(capturedLateBefore()).isNull();
            assertThat(capturedNotLateFrom()).isNull();
        }

        @Test
        void neverAsksForAnEmptyStateCollection() {
            stubEmptySearch();

            loanService.list(LoanStateFilter.OPEN, null, null, null, FIRST_PAGE);

            // An empty IN list is not portable, so the translation must always
            // produce something.
            assertThat(capturedStates()).isNotEmpty();
        }

        @Test
        void movesTheCutOffWithTheConfiguredLendingPeriod() {
            when(settingsService.getDaysToKeepABook()).thenReturn(21);
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            loanService.list(LoanStateFilter.LATE, null, null, null, FIRST_PAGE);

            assertThat(capturedLateBefore()).isEqualTo(NOW.minus(21, ChronoUnit.DAYS));
        }

        @Test
        void passesTheMemberAndBookFiltersThrough() {
            stubEmptySearch();

            loanService.list(null, 12L, 41L, null, FIRST_PAGE);

            verify(loanRepository).search(
                    any(), any(), any(), eq(12L), eq(41L), eq(null), eq(null), any(Pageable.class));
        }

        @Test
        void passesNoSearchFiltersForABlankTerm() {
            stubEmptySearch();

            loanService.list(null, null, null, "   ", FIRST_PAGE);

            verify(loanRepository).search(
                    any(), any(), any(), eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
        }

        @Test
        void searchesTextAndTheExactNumberForANumericTerm() {
            stubEmptySearch();

            loanService.list(null, null, null, " 1201 ", FIRST_PAGE);

            verify(loanRepository).search(
                    any(), any(), any(), eq(null), eq(null), eq("%1201%"), eq(1201),
                    any(Pageable.class));
        }

        @Test
        void searchesTextOnlyForANonNumericTerm() {
            stubEmptySearch();

            loanService.list(null, null, null, "Popescu", FIRST_PAGE);

            verify(loanRepository).search(
                    any(), any(), any(), eq(null), eq(null), eq("%popescu%"), eq(null),
                    any(Pageable.class));
        }

        @Test
        void keepsAWhitelistedSort() {
            stubEmptySearch();

            loanService.list(null, null, null, null, PageRequest.of(1, 5, Sort.by("finishedAt")));

            Pageable used = capturedPageable();
            assertThat(used.getPageNumber()).isEqualTo(1);
            assertThat(used.getPageSize()).isEqualTo(5);
            assertThat(used.getSort()).isEqualTo(Sort.by("finishedAt"));
        }

        @Test
        void fallsBackToNewestFirstForAnUnknownSortProperty() {
            stubEmptySearch();

            // bookTitle is deliberately not sortable, staying consistent with the
            // catalogue's refusal to sort by its joined categoryName -- and an
            // unfiltered property would blow up at query-build time.
            loanService.list(null, null, null, null, PageRequest.of(0, 20, Sort.by("bookTitle")));

            assertThat(capturedPageable().getSort())
                    .isEqualTo(Sort.by("createdAt").descending());
        }

        @Test
        void refusesToSortByADerivedField() {
            stubEmptySearch();

            // dueAt and daysOverdue are not columns at all.
            loanService.list(null, null, null, null, PageRequest.of(0, 20, Sort.by("dueAt")));

            assertThat(capturedPageable().getSort())
                    .isEqualTo(Sort.by("createdAt").descending());
        }

        @Test
        void refusesToSortByTheStoredStateColumn() {
            stubEmptySearch();

            // The persisted column is never trusted for display (ACTIVE/LATE is always
            // recomputed), so ordering by it could visibly disagree with the badge shown
            // next to it.
            loanService.list(null, null, null, null, PageRequest.of(0, 20, Sort.by("state")));

            assertThat(capturedPageable().getSort())
                    .isEqualTo(Sort.by("createdAt").descending());
        }
    }
}
