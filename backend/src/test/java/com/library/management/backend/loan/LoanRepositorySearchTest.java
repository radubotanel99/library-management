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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Executes {@link LoanRepository#search} against a real Postgres, rather than
 * asserting what gets passed to a mock ({@link LoanServiceTest} already covers
 * that translation exhaustively).
 *
 * <p>This is the query the design review flagged as the phase's biggest risk: the
 * {@code cast(:x as Instant)} workaround for Postgres's bind-type inference, the
 * hand-written {@code countQuery}, and the {@code or}-group across four search
 * predicates are all things a mocked repository cannot verify. {@code Replace.NONE}
 * is required -- there is no embedded database on the classpath, and the JPQL here
 * is Postgres-specific by design (the cast would not even be needed against H2).
 *
 * <p>Each test runs in its own transaction, rolled back afterwards
 * ({@code @DataJpaTest}'s default), so the fixture rows never persist against the
 * shared dev database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// The slice does not pick up the application's own @Configuration classes: without
// JpaConfig, @EnableJpaAuditing never runs, created_at/updated_at stay null, and
// every insert fails its NOT NULL constraint before the query under test ever
// executes. TimeConfig supplies the Clock that JpaConfig's auditing provider needs.
@Import({JpaConfig.class, TimeConfig.class})
class LoanRepositorySearchTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private LoanRepository loanRepository;

    private Book copyOfCopilarie;
    private Book copyOfEnigma;
    private Member popescu;
    private Member ionescu;

    @BeforeEach
    void seedCatalogueAndMembers() {
        Category category = new Category();
        category.setName("Loan repository IT — fiction");
        testEntityManager.persist(category);

        copyOfCopilarie = new Book();
        copyOfCopilarie.setTitle("Amintiri din copilărie");
        copyOfCopilarie.setAuthor("Ion Creangă");
        copyOfCopilarie.setBookNumber(900_101);
        copyOfCopilarie.setCategory(category);
        copyOfCopilarie.setStatus(BookStatus.ACTIVE);
        testEntityManager.persist(copyOfCopilarie);

        copyOfEnigma = new Book();
        copyOfEnigma.setTitle("Enigma Otiliei");
        copyOfEnigma.setAuthor("George Călinescu");
        copyOfEnigma.setBookNumber(900_102);
        copyOfEnigma.setCategory(category);
        copyOfEnigma.setStatus(BookStatus.ACTIVE);
        testEntityManager.persist(copyOfEnigma);

        popescu = new Member();
        popescu.setName("Loan repository IT — Popescu Ion");
        testEntityManager.persist(popescu);

        ionescu = new Member();
        ionescu.setName("Loan repository IT — Ionescu Maria");
        testEntityManager.persist(ionescu);

        testEntityManager.flush();
    }

    /**
     * Persists a loan, then overwrites {@code created_at} with a native update.
     *
     * <p>Auditing stamps {@code createdAt} unconditionally on insert (see
     * {@code JpaConfig}), so a value set on the entity before {@code persist} is
     * discarded — the only way to fix a loan's borrow date in the past is to change
     * it after the row exists, bypassing the entity and its listener entirely.
     */
    private Loan persistLoanBorrowedAt(Book book, Member member, LoanState state, Instant createdAt) {
        Loan loan = new Loan();
        loan.setBook(book);
        loan.setMember(member);
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

        // The first-level cache still holds the auditing-stamped value; the next
        // repository call must hit the database to see the backdated row.
        testEntityManager.clear();
        return loan;
    }

    @Test
    void aLoanBorrowedExactlyAtTheCutOffIsNotLate() {
        Instant cutOff = Instant.parse("2026-06-01T10:00:00Z");
        persistLoanBorrowedAt(copyOfCopilarie, popescu, LoanState.ACTIVE, cutOff);

        // notLateFrom uses >=: a loan borrowed exactly on the cut-off is still in time.
        Page<Loan> active = loanRepository.search(
                LoanRepository.OPEN_STATES, null, cutOff, null, null, null, null,
                PageRequest.of(0, 10));
        assertThat(active.getContent()).hasSize(1);

        // lateBefore uses strict <: the same instant must not also count as late.
        Page<Loan> late = loanRepository.search(
                LoanRepository.OPEN_STATES, cutOff, null, null, null, null, null,
                PageRequest.of(0, 10));
        assertThat(late.getContent()).isEmpty();
    }

    @Test
    void aLoanBorrowedOneSecondBeforeTheCutOffIsLate() {
        Instant cutOff = Instant.parse("2026-06-01T10:00:00Z");
        persistLoanBorrowedAt(copyOfCopilarie, popescu, LoanState.ACTIVE, cutOff.minusSeconds(1));

        Page<Loan> late = loanRepository.search(
                LoanRepository.OPEN_STATES, cutOff, null, null, null, null, null,
                PageRequest.of(0, 10));
        assertThat(late.getContent()).hasSize(1);

        Page<Loan> active = loanRepository.search(
                LoanRepository.OPEN_STATES, null, cutOff, null, null, null, null,
                PageRequest.of(0, 10));
        assertThat(active.getContent()).isEmpty();
    }

    @Test
    void bothNullDateGuardsMatchEveryOpenLoanRegardlessOfAge() {
        Instant longAgo = Instant.parse("2020-01-01T00:00:00Z");
        Instant justNow = Instant.now();
        persistLoanBorrowedAt(copyOfCopilarie, popescu, LoanState.ACTIVE, longAgo);
        persistLoanBorrowedAt(copyOfEnigma, ionescu, LoanState.ACTIVE, justNow);

        // This is the exact shape that failed at runtime before the `cast(:x as
        // Instant)` fix — both timestamp guards null, nothing to infer a type from.
        Page<Loan> everyOpenLoan = loanRepository.search(
                LoanRepository.OPEN_STATES, null, null, null, null, null, null,
                PageRequest.of(0, 10));
        assertThat(everyOpenLoan.getContent()).hasSize(2);
    }

    @Test
    void theHandWrittenCountQueryMatchesTheContentAcrossPages() {
        Instant now = Instant.now();
        persistLoanBorrowedAt(copyOfCopilarie, popescu, LoanState.ACTIVE, now);
        persistLoanBorrowedAt(copyOfEnigma, ionescu, LoanState.ACTIVE, now);
        Book thirdCopy = new Book();
        thirdCopy.setTitle("A treia carte");
        thirdCopy.setAuthor("Autor Necunoscut");
        thirdCopy.setBookNumber(900_103);
        thirdCopy.setCategory(copyOfCopilarie.getCategory());
        thirdCopy.setStatus(BookStatus.ACTIVE);
        testEntityManager.persist(thirdCopy);
        testEntityManager.flush();
        persistLoanBorrowedAt(thirdCopy, popescu, LoanState.ACTIVE, now);

        // A deterministic sort is required for stable paging: LoanService always
        // supplies one (defaulting to createdAt descending), so an unsorted
        // PageRequest here would test something the production code never does —
        // Postgres makes no ordering guarantee across separate LIMIT/OFFSET calls
        // without one.
        Sort byId = Sort.by("id");
        Page<Loan> firstPage = loanRepository.search(
                LoanRepository.OPEN_STATES, null, null, null, null, null, null,
                PageRequest.of(0, 2, byId));
        Page<Loan> secondPage = loanRepository.search(
                LoanRepository.OPEN_STATES, null, null, null, null, null, null,
                PageRequest.of(1, 2, byId));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(1);
        List<Long> allIds = List.of(
                firstPage.getContent().get(0).getId(), firstPage.getContent().get(1).getId(),
                secondPage.getContent().get(0).getId());
        assertThat(allIds).doesNotHaveDuplicates();
    }

    @Test
    void matchesTitleAuthorMemberNameAndExactBookNumberInTheSameOrGroup() {
        Instant now = Instant.now();
        persistLoanBorrowedAt(copyOfCopilarie, popescu, LoanState.ACTIVE, now);
        persistLoanBorrowedAt(copyOfEnigma, ionescu, LoanState.ACTIVE, now);

        assertMatchesOnlyCopilarie("%copilărie%", null);
        assertMatchesOnlyCopilarie("%creangă%", null);
        // pattern and searchNumber are never independently null in production: both
        // are derived from the same search term (LoanService.list). A null pattern
        // alone would short-circuit the query's "(:pattern is null or ...)" group to
        // true and match everything, so the fixture must mirror the real derivation.
        assertMatchesOnlyCopilarie("%900101%", 900_101);
        assertMatchesOnlyIonescu("%ionescu%");
    }

    private void assertMatchesOnlyCopilarie(String pattern, Integer searchNumber) {
        Page<Loan> result = loanRepository.search(
                LoanRepository.OPEN_STATES, null, null, null, null, pattern, searchNumber,
                PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBook().getId()).isEqualTo(copyOfCopilarie.getId());
    }

    private void assertMatchesOnlyIonescu(String pattern) {
        Page<Loan> result = loanRepository.search(
                LoanRepository.OPEN_STATES, null, null, null, null, pattern, null,
                PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMember().getId()).isEqualTo(ionescu.getId());
    }

    @Test
    void aFinishedLoanIsExcludedFromTheOpenStatesFilter() {
        Instant now = Instant.now();
        persistLoanBorrowedAt(copyOfCopilarie, popescu, LoanState.FINISHED, now);

        Page<Loan> open = loanRepository.search(
                LoanRepository.OPEN_STATES, null, null, null, null, null, null,
                PageRequest.of(0, 10));
        Page<Loan> finished = loanRepository.search(
                java.util.EnumSet.of(LoanState.FINISHED), null, null, null, null, null, null,
                PageRequest.of(0, 10));

        assertThat(open.getContent()).isEmpty();
        assertThat(finished.getContent()).hasSize(1);
    }

    @Test
    void fetchJoinsBookAndMemberSoTheResultIsUsableOutsideTheTransaction() {
        Instant now = Instant.now();
        persistLoanBorrowedAt(copyOfCopilarie, popescu, LoanState.ACTIVE, now);

        Page<Loan> result = loanRepository.search(
                LoanRepository.OPEN_STATES, null, null, null, null, null, null,
                PageRequest.of(0, 10));

        Loan loan = result.getContent().get(0);
        // Accessing these must not lazily hit the database — they were fetch-joined.
        assertThat(loan.getBook().getTitle()).isEqualTo("Amintiri din copilărie");
        assertThat(loan.getMember().getName()).isEqualTo("Loan repository IT — Popescu Ion");
    }
}
