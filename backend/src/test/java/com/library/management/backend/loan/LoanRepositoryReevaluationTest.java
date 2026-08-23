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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * Executes the two overdue-sweep bulk updates against a real Postgres
 * ({@code API_CONTRACT.md} §11).
 *
 * <p>A mocked repository cannot answer the only questions worth asking here: whether
 * a {@code @Modifying} JPQL {@code update} carrying fully-qualified enum literals
 * compiles and runs at all, whether it writes the enum as the string the
 * {@code @Enumerated(STRING)} column expects, whether the boundary lands on the right
 * side of the cut-off, and whether the reported row count matches what actually
 * changed. All four are database facts.
 *
 * <p>{@code Replace.NONE} for the same reason as {@link LoanRepositorySearchTest}:
 * there is no embedded database on the classpath and the schema is Flyway's.
 *
 * <p>Each test runs in its own transaction, rolled back afterwards
 * ({@code @DataJpaTest}'s default), so the fixture rows never persist against the
 * shared dev database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Without JpaConfig, @EnableJpaAuditing never runs, created_at stays null, and every
// insert fails its NOT NULL constraint before the update under test executes.
// TimeConfig supplies the Clock that JpaConfig's auditing provider needs.
@Import({JpaConfig.class, TimeConfig.class})
class LoanRepositoryReevaluationTest {

    /** Any fixed instant; the tests only care which side of it a loan falls on. */
    private static final Instant CUT_OFF = Instant.parse("2026-06-01T10:00:00Z");

    private static final Instant BEFORE_CUT_OFF = CUT_OFF.minus(3, ChronoUnit.DAYS);
    private static final Instant AFTER_CUT_OFF = CUT_OFF.plus(3, ChronoUnit.DAYS);

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private LoanRepository loanRepository;

    private Category category;
    private Member popescu;
    private int nextBookNumber = 910_101;

    @BeforeEach
    void seedCatalogueAndMember() {
        category = new Category();
        category.setName("Loan reevaluation IT — fiction");
        testEntityManager.persist(category);

        popescu = new Member();
        popescu.setName("Loan reevaluation IT — Popescu Ion");
        testEntityManager.persist(popescu);

        testEntityManager.flush();
    }

    /**
     * A fresh copy per loan: {@code ux_loan_one_open_per_book} allows a book only one
     * unfinished loan, and these tests seed several open ones at a time.
     */
    private Book newCopy() {
        Book book = new Book();
        book.setTitle("Amintiri din copilărie");
        book.setAuthor("Ion Creangă");
        book.setBookNumber(nextBookNumber++);
        book.setCategory(category);
        book.setStatus(BookStatus.ACTIVE);
        testEntityManager.persist(book);
        testEntityManager.flush();
        return book;
    }

    /**
     * Persists a loan, then overwrites {@code created_at} with a native update.
     *
     * <p>Auditing stamps {@code createdAt} unconditionally on insert (see
     * {@code JpaConfig}), so a value set on the entity before {@code persist} is
     * discarded — the only way to fix a loan's borrow date in the past is to change it
     * after the row exists, bypassing the entity and its listener entirely. Same
     * approach as {@link LoanRepositorySearchTest}.
     */
    private Long persistLoanBorrowedAt(LoanState state, Instant createdAt) {
        Loan loan = new Loan();
        loan.setBook(newCopy());
        loan.setMember(popescu);
        loan.setState(state);
        if (state == LoanState.FINISHED) {
            loan.setFinishedAt(createdAt.plus(1, ChronoUnit.DAYS));
        }
        testEntityManager.persist(loan);
        testEntityManager.flush();

        testEntityManager.getEntityManager()
                .createNativeQuery("update loan set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, loan.getId())
                .executeUpdate();

        // The first-level cache still holds the auditing-stamped value; the bulk update
        // must see the backdated row, and the assertion must re-read it from Postgres.
        testEntityManager.clear();
        return loan.getId();
    }

    /** Reads the stored column back, never the possibly-stale in-memory value. */
    private LoanState storedStateOf(Long loanId) {
        testEntityManager.clear();
        return testEntityManager.find(Loan.class, loanId).getState();
    }

    @Test
    void marksAnActiveLoanBorrowedBeforeTheCutOffAsLate() {
        Long overdue = persistLoanBorrowedAt(LoanState.ACTIVE, BEFORE_CUT_OFF);

        int flipped = loanRepository.markLateBorrowedBefore(CUT_OFF);

        assertThat(flipped).isEqualTo(1);
        assertThat(storedStateOf(overdue)).isEqualTo(LoanState.LATE);
    }

    @Test
    void leavesAnActiveLoanBorrowedOnOrAfterTheCutOffAlone() {
        Long onTheCutOff = persistLoanBorrowedAt(LoanState.ACTIVE, CUT_OFF);
        Long stillInTime = persistLoanBorrowedAt(LoanState.ACTIVE, AFTER_CUT_OFF);

        int flipped = loanRepository.markLateBorrowedBefore(CUT_OFF);

        // Strict <: borrowed exactly on the cut-off means due now, not yet overdue --
        // the same boundary LoanService.toResponse draws.
        assertThat(flipped).isZero();
        assertThat(storedStateOf(onTheCutOff)).isEqualTo(LoanState.ACTIVE);
        assertThat(storedStateOf(stillInTime)).isEqualTo(LoanState.ACTIVE);
    }

    @Test
    void marksALateLoanBorrowedOnOrAfterTheCutOffAsActiveAgain() {
        // The lending period was raised, so the cut-off moved backwards and this loan
        // is no longer late (DATA_MODEL.md §6).
        Long noLongerLate = persistLoanBorrowedAt(LoanState.LATE, AFTER_CUT_OFF);
        Long exactlyOnTheCutOff = persistLoanBorrowedAt(LoanState.LATE, CUT_OFF);

        int flipped = loanRepository.markActiveBorrowedFrom(CUT_OFF);

        assertThat(flipped).isEqualTo(2);
        assertThat(storedStateOf(noLongerLate)).isEqualTo(LoanState.ACTIVE);
        assertThat(storedStateOf(exactlyOnTheCutOff)).isEqualTo(LoanState.ACTIVE);
    }

    @Test
    void leavesALateLoanBorrowedBeforeTheCutOffAlone() {
        Long genuinelyLate = persistLoanBorrowedAt(LoanState.LATE, BEFORE_CUT_OFF);

        int flipped = loanRepository.markActiveBorrowedFrom(CUT_OFF);

        assertThat(flipped).isZero();
        assertThat(storedStateOf(genuinelyLate)).isEqualTo(LoanState.LATE);
    }

    @Test
    void neverTouchesAFinishedLoanWhicheverSideOfTheCutOffItFallsOn() {
        // A returned loan is history: no borrow date may reopen it.
        Long returnedLongOverdue = persistLoanBorrowedAt(LoanState.FINISHED, BEFORE_CUT_OFF);
        Long returnedPromptly = persistLoanBorrowedAt(LoanState.FINISHED, AFTER_CUT_OFF);

        assertThat(loanRepository.markLateBorrowedBefore(CUT_OFF)).isZero();
        assertThat(loanRepository.markActiveBorrowedFrom(CUT_OFF)).isZero();

        assertThat(storedStateOf(returnedLongOverdue)).isEqualTo(LoanState.FINISHED);
        assertThat(storedStateOf(returnedPromptly)).isEqualTo(LoanState.FINISHED);
    }

    @Test
    void reportsExactlyTheRowsItFlippedInAMixedPopulation() {
        Long activeOverdue = persistLoanBorrowedAt(LoanState.ACTIVE, BEFORE_CUT_OFF);
        Long activeSecondOverdue = persistLoanBorrowedAt(LoanState.ACTIVE, BEFORE_CUT_OFF);
        Long activeInTime = persistLoanBorrowedAt(LoanState.ACTIVE, AFTER_CUT_OFF);
        Long lateNoLonger = persistLoanBorrowedAt(LoanState.LATE, AFTER_CUT_OFF);
        Long lateStill = persistLoanBorrowedAt(LoanState.LATE, BEFORE_CUT_OFF);
        Long finished = persistLoanBorrowedAt(LoanState.FINISHED, BEFORE_CUT_OFF);

        // Order matters for the counts: the LATE sweep must run against the states the
        // ACTIVE sweep left behind, which is the order LoanService.reevaluateOverdue
        // uses.
        int markedLate = loanRepository.markLateBorrowedBefore(CUT_OFF);
        int markedActive = loanRepository.markActiveBorrowedFrom(CUT_OFF);

        assertThat(markedLate).isEqualTo(2);
        assertThat(markedActive).isEqualTo(1);

        assertThat(storedStateOf(activeOverdue)).isEqualTo(LoanState.LATE);
        assertThat(storedStateOf(activeSecondOverdue)).isEqualTo(LoanState.LATE);
        assertThat(storedStateOf(activeInTime)).isEqualTo(LoanState.ACTIVE);
        assertThat(storedStateOf(lateNoLonger)).isEqualTo(LoanState.ACTIVE);
        assertThat(storedStateOf(lateStill)).isEqualTo(LoanState.LATE);
        assertThat(storedStateOf(finished)).isEqualTo(LoanState.FINISHED);
    }

    @Test
    void aFlippedLoanStaysCoveredByThePartialUniqueIndexWithoutColliding() {
        // ux_loan_one_open_per_book covers state in ('ACTIVE', 'LATE'), so a row moving
        // between the two never leaves the index -- there is nothing for the sweep to
        // work around, and this pins that.
        Long overdue = persistLoanBorrowedAt(LoanState.ACTIVE, BEFORE_CUT_OFF);

        assertThat(loanRepository.markLateBorrowedBefore(CUT_OFF)).isEqualTo(1);
        assertThat(loanRepository.markActiveBorrowedFrom(BEFORE_CUT_OFF)).isEqualTo(1);

        assertThat(storedStateOf(overdue)).isEqualTo(LoanState.ACTIVE);
    }
}
