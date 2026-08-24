package com.library.management.backend.dashboard;

import com.library.management.backend.book.BookRepository;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.dashboard.dto.DashboardResponse;
import com.library.management.backend.dashboard.dto.MostBorrowedBookResponse;
import com.library.management.backend.loan.LoanRepository;
import com.library.management.backend.member.MemberRepository;
import com.library.management.backend.parameter.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the home screen's figures ({@code API_CONTRACT.md} §10).
 *
 * <p>No business rules of its own -- it owns no data and decides nothing. What it
 * does own is the choice of <em>which</em> question to ask each repository, and one
 * of those choices matters: the active/overdue split is a date comparison, not a
 * read of {@code loan.state}. The stored column is only as fresh as the last
 * re-evaluation run ({@code API_CONTRACT.md} §11), so counting it would make the
 * dashboard disagree with the loans screen and would leave {@code loansOverdue}
 * stale until the next sweep after a {@code DAYS_TO_KEEP_A_BOOK} change. The same
 * derivation {@code LoanService} uses is used here, for the same reason.
 *
 * <p>It reads across four features, which is exactly what a dashboard is; it does so
 * through their repositories rather than their services because every figure here is
 * a count, and routing counts through {@code BookService} and friends would drag
 * their DTO mapping along for nothing.
 *
 * <p>Read-only and single-shot: one clock reading and one settings read serve the
 * whole response, so the two loan counts can never straddle a change.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /**
     * How many books the most-borrowed list carries.
     *
     * <p>Five is a contract figure ({@code API_CONTRACT.md} §10), not a tuning knob:
     * changing it changes what the API returns.
     */
    private static final int MOST_BORROWED_LIMIT = 5;

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public DashboardService(BookRepository bookRepository,
                            MemberRepository memberRepository,
                            LoanRepository loanRepository,
                            SettingsService settingsService,
                            Clock clock) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    /**
     * Every figure the dashboard shows.
     *
     * <p>The cut-off is {@code now - DAYS_TO_KEEP_A_BOOK}: a loan borrowed on or
     * after it is still in time, one borrowed before it is overdue. Computed here
     * rather than shared with {@code LoanService.list}, which deliberately reads the
     * lending period exactly once per page -- one line of arithmetic is cheaper than
     * a helper that would make either side read it twice.
     */
    public DashboardResponse load() {
        Instant now = Instant.now(clock);
        int daysToKeep = settingsService.getDaysToKeepABook();
        Instant cutOff = now.minus(daysToKeep, ChronoUnit.DAYS);

        long totalCopies = bookRepository.countByStatus(BookStatus.ACTIVE);
        long totalMembers = memberRepository.countByDeletedFalse();
        long loansActive = loanRepository.countByStateInAndCreatedAtGreaterThanEqual(
                LoanRepository.OPEN_STATES, cutOff);
        long loansOverdue = loanRepository.countByStateInAndCreatedAtBefore(
                LoanRepository.OPEN_STATES, cutOff);

        List<MostBorrowedBookResponse> mostBorrowed =
                loanRepository.findMostBorrowed(PageRequest.of(0, MOST_BORROWED_LIMIT)).stream()
                        .map(book -> new MostBorrowedBookResponse(
                                book.bookId(), book.title(), book.author(), book.loanCount()))
                        .toList();

        return new DashboardResponse(totalCopies, totalMembers, loansActive, loansOverdue, mostBorrowed);
    }
}
