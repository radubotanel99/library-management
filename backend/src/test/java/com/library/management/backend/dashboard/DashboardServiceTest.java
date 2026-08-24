package com.library.management.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.management.backend.book.BookRepository;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.dashboard.dto.DashboardResponse;
import com.library.management.backend.loan.LoanRepository;
import com.library.management.backend.loan.MostBorrowedBook;
import com.library.management.backend.member.MemberRepository;
import com.library.management.backend.parameter.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for the dashboard assembly.
 *
 * <p>Plain JUnit + Mockito, no Spring context: this service only chooses which
 * question to ask each repository and how to lay the answers out, so a container
 * would only make the suite slower. The queries themselves run against a real
 * Postgres in {@code LoanRepositoryDashboardTest}.
 *
 * <p>Every mocked figure is a different number on purpose. Five counts assembled into
 * one record is exactly the shape where a transposed pair compiles, passes a
 * "returns 4 numbers" assertion, and ships the wrong dashboard.
 *
 * <p>The clock is fixed rather than mocked, matching {@code LoanServiceTest}: the
 * cut-off is arithmetic on "now", and pinning it is what makes it assertable.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    /** The seeded lending period; every test stubs this value explicitly. */
    private static final int DAYS_TO_KEEP = 14;

    /** A loan borrowed exactly here comes due exactly now. */
    private static final Instant CUT_OFF = NOW.minus(DAYS_TO_KEEP, ChronoUnit.DAYS);

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private SettingsService settingsService;

    @Spy
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks
    private DashboardService dashboardService;

    /**
     * Stubbed per test rather than in a {@code @BeforeEach}: one test needs a
     * different lending period, and re-stubbing an already-stubbed call would leave
     * the first stubbing unused under strict stubs.
     */
    private void stubLendingPeriod(int days) {
        when(settingsService.getDaysToKeepABook()).thenReturn(days);
    }

    private void stubCounts(long copies, long members, long active, long overdue) {
        when(bookRepository.countByStatus(BookStatus.ACTIVE)).thenReturn(copies);
        when(memberRepository.countByDeletedFalse()).thenReturn(members);
        when(loanRepository.countByStateInAndCreatedAtGreaterThanEqual(any(), any()))
                .thenReturn(active);
        when(loanRepository.countByStateInAndCreatedAtBefore(any(), any()))
                .thenReturn(overdue);
    }

    private void stubEmptyMostBorrowed() {
        when(loanRepository.findMostBorrowed(any(Pageable.class))).thenReturn(List.of());
    }

    @Test
    void putsEveryCountInItsOwnField() {
        stubLendingPeriod(DAYS_TO_KEEP);
        stubCounts(137L, 64L, 23L, 4L);
        stubEmptyMostBorrowed();

        DashboardResponse response = dashboardService.load();

        assertThat(response.totalCopies()).isEqualTo(137L);
        assertThat(response.totalMembers()).isEqualTo(64L);
        assertThat(response.loansActive()).isEqualTo(23L);
        assertThat(response.loansOverdue()).isEqualTo(4L);
    }

    @Test
    void mapsEachProjectionRowFieldForFieldAndKeepsTheQueryOrder() {
        stubLendingPeriod(DAYS_TO_KEEP);
        stubCounts(1L, 1L, 0L, 0L);
        when(loanRepository.findMostBorrowed(any(Pageable.class))).thenReturn(List.of(
                new MostBorrowedBook(41L, "Amintiri din copilărie", "Ion Creangă", 18L),
                new MostBorrowedBook(42L, "Baltagul", "Mihail Sadoveanu", 11L)));

        DashboardResponse response = dashboardService.load();

        assertThat(response.mostBorrowed()).hasSize(2);
        assertThat(response.mostBorrowed().get(0).bookId()).isEqualTo(41L);
        assertThat(response.mostBorrowed().get(0).title()).isEqualTo("Amintiri din copilărie");
        assertThat(response.mostBorrowed().get(0).author()).isEqualTo("Ion Creangă");
        assertThat(response.mostBorrowed().get(0).loanCount()).isEqualTo(18L);
        // The repository already ordered by count; the service must not re-sort.
        assertThat(response.mostBorrowed().get(1).title()).isEqualTo("Baltagul");
    }

    /**
     * The whole point of {@code API_CONTRACT.md} §10's second bullet: the split is
     * {@code now - DAYS_TO_KEEP_A_BOOK}, not a read of {@code loan.state}.
     */
    @Test
    void splitsOpenLoansOnNowMinusTheLendingPeriod() {
        stubLendingPeriod(DAYS_TO_KEEP);
        stubCounts(0L, 0L, 0L, 0L);
        stubEmptyMostBorrowed();

        dashboardService.load();

        ArgumentCaptor<Instant> activeCutOff = ArgumentCaptor.forClass(Instant.class);
        verify(loanRepository).countByStateInAndCreatedAtGreaterThanEqual(
                any(), activeCutOff.capture());
        assertThat(activeCutOff.getValue()).isEqualTo(CUT_OFF);

        ArgumentCaptor<Instant> overdueCutOff = ArgumentCaptor.forClass(Instant.class);
        verify(loanRepository).countByStateInAndCreatedAtBefore(any(), overdueCutOff.capture());
        // Both sides of the split share one cut-off, so they can never overlap or gap.
        assertThat(overdueCutOff.getValue()).isEqualTo(CUT_OFF);
    }

    @Test
    void movesTheCutOffWhenTheLendingPeriodChanges() {
        stubLendingPeriod(21);
        stubCounts(0L, 0L, 0L, 0L);
        stubEmptyMostBorrowed();

        dashboardService.load();

        ArgumentCaptor<Instant> cutOff = ArgumentCaptor.forClass(Instant.class);
        verify(loanRepository).countByStateInAndCreatedAtBefore(any(), cutOff.capture());
        assertThat(cutOff.getValue()).isEqualTo(NOW.minus(21, ChronoUnit.DAYS));
    }

    /**
     * Only the open/finished split may come off the column; which of the two open
     * states a row is in is decided by the date, never by {@code loan.state}.
     */
    @Test
    void narrowsBothCountsToOpenLoansOnly() {
        stubLendingPeriod(DAYS_TO_KEEP);
        stubCounts(0L, 0L, 0L, 0L);
        stubEmptyMostBorrowed();

        dashboardService.load();

        // ACTIVE and LATE both, so the column decides only "still out" -- never which
        // of the two, which is what the cut-off above is for.
        verify(loanRepository).countByStateInAndCreatedAtGreaterThanEqual(
                eq(LoanRepository.OPEN_STATES), any());
        verify(loanRepository).countByStateInAndCreatedAtBefore(
                eq(LoanRepository.OPEN_STATES), any());
    }

    /** "Top 5" is the contract's number ({@code API_CONTRACT.md} §10). */
    @Test
    void asksForTheFirstFiveMostBorrowedBooks() {
        stubLendingPeriod(DAYS_TO_KEEP);
        stubCounts(0L, 0L, 0L, 0L);
        stubEmptyMostBorrowed();

        dashboardService.load();

        verify(loanRepository).findMostBorrowed(PageRequest.of(0, 5));
    }

    /** Copies held are active books; a lost one is not on the shelf. */
    @Test
    void countsOnlyActiveCopies() {
        stubLendingPeriod(DAYS_TO_KEEP);
        stubCounts(137L, 0L, 0L, 0L);
        stubEmptyMostBorrowed();

        dashboardService.load();

        verify(bookRepository).countByStatus(eq(BookStatus.ACTIVE));
    }

    /** A brand-new library has lent nothing; that must be a list, never null. */
    @Test
    void returnsAnEmptyListWhenNothingHasBeenBorrowed() {
        stubLendingPeriod(DAYS_TO_KEEP);
        stubCounts(0L, 0L, 0L, 0L);
        stubEmptyMostBorrowed();

        DashboardResponse response = dashboardService.load();

        assertThat(response.mostBorrowed()).isNotNull().isEmpty();
    }
}
