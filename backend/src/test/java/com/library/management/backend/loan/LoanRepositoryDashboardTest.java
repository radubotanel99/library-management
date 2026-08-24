package com.library.management.backend.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.management.backend.book.Book;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.category.Category;
import com.library.management.backend.config.JpaConfig;
import com.library.management.backend.config.TimeConfig;
import com.library.management.backend.member.Member;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * Executes the two dashboard queries against a real Postgres
 * ({@code API_CONTRACT.md} §10).
 *
 * <p>{@link DashboardServiceTest} already covers what gets passed to a mock. What a
 * mock cannot answer is whether the grouped {@code select new} projection with a
 * fully-qualified enum literal in its {@code where} clause compiles and runs, whether
 * {@code order by count(l) desc, b.id asc} really breaks ties the way the contract
 * promises, and which side of the cut-off a boundary row lands on. All three are
 * database facts.
 *
 * <p>{@code Replace.NONE} for the same reason as {@link LoanRepositorySearchTest}:
 * there is no embedded database on the classpath and the schema is Flyway's.
 *
 * <p>Each test runs in its own transaction, rolled back afterwards
 * ({@code @DataJpaTest}'s default), so the fixture rows never persist against the
 * shared dev database.
 *
 * <p><strong>Every assertion here is relative, never absolute.</strong> The dev
 * database is shared with the other integration tests and with whatever a developer
 * has entered by hand, so "there are 3 books" is not a fact this test can know. The
 * most-borrowed assertions filter to this test's own fixture titles, and the loan
 * counts are compared as before/after deltas. The two trivial derived counts
 * ({@code countByStatus}, {@code countByDeletedFalse}) are deliberately not tested
 * here at all: there is no way to assert them without an absolute number, and
 * {@link DashboardServiceTest} already covers that they are called with the right
 * argument.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Without JpaConfig, @EnableJpaAuditing never runs, created_at stays null, and every
// insert fails its NOT NULL constraint before the query under test executes.
// TimeConfig supplies the Clock that JpaConfig's auditing provider needs.
@Import({JpaConfig.class, TimeConfig.class})
class LoanRepositoryDashboardTest {

    /** Any fixed instant; the tests only care which side of it a loan falls on. */
    private static final Instant CUT_OFF = Instant.parse("2026-06-01T10:00:00Z");

    /** Marks this test's own books, so the shared database's rows can be filtered out. */
    private static final String FIXTURE_AUTHOR = "Dashboard IT — Autor";

    /** Enough rows to prove the cap; the contract's limit is 5. */
    private static final int TOP_FIVE = 5;

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private LoanRepository loanRepository;

    private Category category;
    private Member popescu;
    private int nextBookNumber = 900_201;

    @BeforeEach
    void seedCategoryAndMember() {
        category = new Category();
        category.setName("Dashboard IT — fiction");
        testEntityManager.persist(category);

        popescu = new Member();
        popescu.setName("Dashboard IT — Popescu Ion");
        testEntityManager.persist(popescu);

        testEntityManager.flush();
    }

    private Book newCopy(String title, BookStatus status) {
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(FIXTURE_AUTHOR);
        book.setBookNumber(nextBookNumber++);
        book.setCategory(category);
        book.setStatus(status);
        testEntityManager.persist(book);
        testEntityManager.flush();
        return book;
    }

    /**
     * Records {@code count} loans on one copy, all of them already returned.
     *
     * <p>{@code ux_loan_one_open_per_book} allows a copy only one unfinished loan, so
     * repeat borrowings have to be {@code FINISHED} — which is also the case that
     * matters: a returned loan still counts towards popularity.
     */
    private void borrowAndReturn(Book book, int count) {
        for (int i = 0; i < count; i++) {
            Loan loan = new Loan();
            loan.setBook(book);
            loan.setMember(popescu);
            loan.setState(LoanState.FINISHED);
            loan.setFinishedAt(Instant.now().plus(i + 1L, ChronoUnit.DAYS));
            testEntityManager.persist(loan);
        }
        testEntityManager.flush();
    }

    /**
     * Persists an open loan, then overwrites {@code created_at} with a native update.
     *
     * <p>Auditing stamps {@code createdAt} unconditionally on insert (see
     * {@code JpaConfig}), so a value set on the entity before {@code persist} is
     * discarded — the only way to fix a loan's borrow date in the past is to change it
     * after the row exists, bypassing the entity and its listener entirely. Same
     * approach as {@link LoanRepositorySearchTest}.
     */
    private void persistOpenLoanBorrowedAt(Book book, Instant createdAt) {
        Loan loan = new Loan();
        loan.setBook(book);
        loan.setMember(popescu);
        loan.setState(LoanState.ACTIVE);
        testEntityManager.persist(loan);
        testEntityManager.flush();

        testEntityManager.getEntityManager()
                .createNativeQuery("update loan set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, loan.getId())
                .executeUpdate();

        // The first-level cache still holds the auditing-stamped value; the next
        // repository call must hit the database to see the backdated row.
        testEntityManager.clear();
    }

    /** This test's own rows only — the dev database holds other people's books too. */
    private List<MostBorrowedBook> fixtureRows(List<MostBorrowedBook> rows) {
        return rows.stream().filter(row -> FIXTURE_AUTHOR.equals(row.author())).toList();
    }

    @Test
    void ordersByLoanCountDescending() {
        Book popular = newCopy("Dashboard IT — Cea mai citită", BookStatus.ACTIVE);
        Book middling = newCopy("Dashboard IT — Citită uneori", BookStatus.ACTIVE);
        Book unpopular = newCopy("Dashboard IT — Rar citită", BookStatus.ACTIVE);
        borrowAndReturn(popular, 5);
        borrowAndReturn(middling, 3);
        borrowAndReturn(unpopular, 1);

        // A generous page: the shared database's own books may outrank these.
        List<MostBorrowedBook> rows =
                fixtureRows(loanRepository.findMostBorrowed(PageRequest.of(0, 100)));

        assertThat(rows).extracting(MostBorrowedBook::bookId)
                .containsExactly(popular.getId(), middling.getId(), unpopular.getId());
        assertThat(rows).extracting(MostBorrowedBook::loanCount)
                .containsExactly(5L, 3L, 1L);
        assertThat(rows.get(0).title()).isEqualTo("Dashboard IT — Cea mai citită");
        assertThat(rows.get(0).author()).isEqualTo(FIXTURE_AUTHOR);
    }

    /**
     * Without the {@code b.id asc} tie-break, Postgres is free to return equal-count
     * rows in either order, and the fifth slot would shuffle between two refreshes of
     * an unchanged database.
     */
    @Test
    void breaksTiesByBookIdAscending() {
        Book first = newCopy("Dashboard IT — Prima", BookStatus.ACTIVE);
        Book second = newCopy("Dashboard IT — A doua", BookStatus.ACTIVE);
        Book third = newCopy("Dashboard IT — A treia", BookStatus.ACTIVE);
        borrowAndReturn(first, 2);
        borrowAndReturn(second, 2);
        borrowAndReturn(third, 2);

        List<MostBorrowedBook> rows =
                fixtureRows(loanRepository.findMostBorrowed(PageRequest.of(0, 100)));

        assertThat(rows).extracting(MostBorrowedBook::bookId)
                .containsExactly(first.getId(), second.getId(), third.getId())
                .isSorted();
    }

    /** The contract's top 5 is enforced by the {@code Pageable}, not by the query. */
    @Test
    void capsTheResultAtThePageSize() {
        for (int i = 0; i < 7; i++) {
            borrowAndReturn(newCopy("Dashboard IT — Carte " + i, BookStatus.ACTIVE), 7 - i);
        }

        List<MostBorrowedBook> rows = loanRepository.findMostBorrowed(PageRequest.of(0, TOP_FIVE));

        assertThat(rows).hasSize(TOP_FIVE);
    }

    @Test
    void returnsFewerRowsThanTheLimitWhenFewerBooksHaveBeenBorrowed() {
        borrowAndReturn(newCopy("Dashboard IT — Singura", BookStatus.ACTIVE), 4);
        // Never borrowed: a book with no loans is absent, not a zero-count row.
        newCopy("Dashboard IT — Neîmprumutată", BookStatus.ACTIVE);

        List<MostBorrowedBook> rows =
                fixtureRows(loanRepository.findMostBorrowed(PageRequest.of(0, TOP_FIVE)));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).title()).isEqualTo("Dashboard IT — Singura");
    }

    /**
     * All loan states count towards popularity — this query is the one place in
     * {@link LoanRepository} with no {@code state} filter, on purpose.
     */
    @Test
    void countsReturnedLoansTowardsPopularity() {
        Book book = newCopy("Dashboard IT — Doar returnate", BookStatus.ACTIVE);
        borrowAndReturn(book, 6);

        List<MostBorrowedBook> rows =
                fixtureRows(loanRepository.findMostBorrowed(PageRequest.of(0, 100)));

        assertThat(rows).singleElement()
                .extracting(MostBorrowedBook::loanCount)
                .isEqualTo(6L);
    }

    /**
     * A removed copy does not occupy a slot on an operational screen, however good
     * its history was: a librarian cannot lend it.
     */
    @Test
    void excludesBooksThatAreNoLongerActive() {
        Book withdrawn = newCopy("Dashboard IT — Retrasă", BookStatus.WITHDRAWN);
        Book active = newCopy("Dashboard IT — În colecție", BookStatus.ACTIVE);
        borrowAndReturn(withdrawn, 20);
        borrowAndReturn(active, 1);

        List<MostBorrowedBook> rows =
                fixtureRows(loanRepository.findMostBorrowed(PageRequest.of(0, 100)));

        // Twenty loans and still absent — status, not popularity, decides membership.
        assertThat(rows).extracting(MostBorrowedBook::bookId).containsExactly(active.getId());
    }

    /**
     * The same boundary {@code LoanService} and {@link LoanRepository#search} already
     * apply: a loan borrowed exactly on the cut-off comes due this instant but is not
     * yet overdue, so it counts as active.
     */
    @Test
    void aLoanBorrowedExactlyAtTheCutOffCountsAsActiveNotOverdue() {
        long activeBefore = countActive();
        long overdueBefore = countOverdue();

        persistOpenLoanBorrowedAt(newCopy("Dashboard IT — Exact la limită", BookStatus.ACTIVE), CUT_OFF);

        assertThat(countActive() - activeBefore).isOne();
        assertThat(countOverdue() - overdueBefore).isZero();
    }

    @Test
    void aLoanBorrowedOneSecondBeforeTheCutOffCountsAsOverdue() {
        long activeBefore = countActive();
        long overdueBefore = countOverdue();

        persistOpenLoanBorrowedAt(
                newCopy("Dashboard IT — O secundă peste", BookStatus.ACTIVE), CUT_OFF.minusSeconds(1));

        assertThat(countActive() - activeBefore).isZero();
        assertThat(countOverdue() - overdueBefore).isOne();
    }

    /**
     * A returned loan is not still out, so it belongs to neither count — this is the
     * one split the queries are allowed to take off the stored column.
     */
    @Test
    void ignoresFinishedLoansOnBothSidesOfTheCutOff() {
        long activeBefore = countActive();
        long overdueBefore = countOverdue();

        Book book = newCopy("Dashboard IT — Deja returnată", BookStatus.ACTIVE);
        borrowAndReturn(book, 3);

        assertThat(countActive() - activeBefore).isZero();
        assertThat(countOverdue() - overdueBefore).isZero();
    }

    private long countActive() {
        return loanRepository.countByStateInAndCreatedAtGreaterThanEqual(
                LoanRepository.OPEN_STATES, CUT_OFF);
    }

    private long countOverdue() {
        return loanRepository.countByStateInAndCreatedAtBefore(
                LoanRepository.OPEN_STATES, CUT_OFF);
    }
}
