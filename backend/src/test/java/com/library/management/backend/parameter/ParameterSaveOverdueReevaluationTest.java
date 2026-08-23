package com.library.management.backend.parameter;

import static org.assertj.core.api.Assertions.assertThat;

import com.library.management.backend.book.Book;
import com.library.management.backend.book.BookRepository;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.category.Category;
import com.library.management.backend.category.CategoryRepository;
import com.library.management.backend.loan.Loan;
import com.library.management.backend.loan.LoanRepository;
import com.library.management.backend.loan.LoanService;
import com.library.management.backend.loan.LoanState;
import com.library.management.backend.member.Member;
import com.library.management.backend.member.MemberRepository;
import com.library.management.backend.parameter.dto.ParameterRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Proves Phase 6's "done when" end to end: raising {@code DAYS_TO_KEEP_A_BOOK} flips
 * affected loans out of {@code LATE} immediately, inside the save request itself.
 *
 * <p>Every link in that chain already has a test, and none of them prove the chain.
 * {@code SettingsServiceTest} publishes the event to a mocked publisher,
 * {@code OverdueReevaluationSchedulerTest} calls the listener directly with a mocked
 * {@code LoanService}, and {@code LoanRepositoryReevaluationTest} calls the two bulk
 * updates directly. Nothing checks that a real {@code PUT /api/parameters} reaches a
 * real listener through a real {@code ApplicationEventPublisher} and changes a real
 * row -- which is the only thing the phase is actually judged on. Hence the full
 * context, a real port, and a real Postgres: every mock removed from the path is a
 * mock that can no longer hide a broken wire.
 *
 * <p>{@code RANDOM_PORT} rather than a slice. {@code @DataJpaTest} has no web server
 * and {@code @WebMvcTest} has no database, so neither can run this path at all, and a
 * {@code MOCK} environment would skip the HTTP round trip that is half the point.
 * {@code RestTestClient} is Boot 4's client for this; {@code TestRestTemplate} is on
 * the classpath but its auto-configuration needs {@code spring-boot-restclient},
 * which this project does not depend on.
 *
 * <p>Deliberately slower than every other test here. It is the one that would catch a
 * missing {@code @EventListener} or a publisher that was never injected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ParameterSaveOverdueReevaluationTest {

    /** Old enough to be overdue under the seeded 14 days, young enough to be in time under 30. */
    private static final int LOAN_AGE_IN_DAYS = 20;

    private static final String DAYS_TO_KEEP_A_BOOK = "DAYS_TO_KEEP_A_BOOK";
    private static final String MAX_BOOKS_PER_MEMBER = "MAX_BOOKS_PER_MEMBER";

    /** The values {@code V2__seed_parameters.sql} leaves behind, restored after each test. */
    private static final String SEEDED_DAYS_TO_KEEP_A_BOOK = "14";
    private static final String SEEDED_MAX_BOOKS_PER_MEMBER = "3";

    /** A raised period: comfortably longer than {@link #LOAN_AGE_IN_DAYS}. */
    private static final String RAISED_DAYS_TO_KEEP_A_BOOK = "30";

    /**
     * A genuinely shorter period than the seeded default, used only to establish the
     * "overdue" precondition. {@code SettingsService.save} now publishes its
     * re-evaluation event only when {@code DAYS_TO_KEEP_A_BOOK} actually changes (a
     * same-value save is a deliberate no-op, so it can no longer be used to trigger a
     * sweep the way the first version of this test did).
     */
    private static final String SHORTENED_DAYS_TO_KEEP_A_BOOK = "7";

    /** Far outside anything the dev database is likely to hold. */
    private static final int FIXTURE_BOOK_NUMBER = 990_601;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private LoanRepository loanRepository;

    /** Teardown only -- see {@link #removeFixturesAndRestoreSeededSettings()}. */
    @Autowired
    private LoanService loanService;

    /** For the two things JPA cannot do here: backdating an audited column, and hard deletes. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private Long categoryId;
    private Long bookId;
    private Long memberId;
    private Long loanId;

    @BeforeEach
    void seedAnOpenLoanBorrowedTwentyDaysAgo() {
        Category category = new Category();
        category.setName("Parameter save reevaluation IT — fiction");
        categoryId = categoryRepository.save(category).getId();

        Book book = new Book();
        book.setTitle("Amintiri din copilărie");
        book.setAuthor("Ion Creangă");
        book.setBookNumber(FIXTURE_BOOK_NUMBER);
        book.setCategory(category);
        book.setStatus(BookStatus.ACTIVE);
        bookId = bookRepository.save(book).getId();

        Member member = new Member();
        member.setName("Parameter save reevaluation IT — Popescu Ion");
        memberId = memberRepository.save(member).getId();

        Loan loan = new Loan();
        loan.setBook(book);
        loan.setMember(member);
        loan.setState(LoanState.ACTIVE);
        loanId = loanRepository.save(loan).getId();

        // Auditing stamps created_at on insert whatever the entity carried, so the
        // borrow date can only be moved into the past after the row exists -- the same
        // native update LoanRepositoryReevaluationTest uses, for the same reason.
        Instant borrowedAt = Instant.now(clock).minus(LOAN_AGE_IN_DAYS, ChronoUnit.DAYS);
        jdbcTemplate.update("update loan set created_at = ? where id = ?",
                OffsetDateTime.ofInstant(borrowedAt, ZoneOffset.UTC), loanId);
    }

    /**
     * Nothing here is a transaction rollback, because there is no transaction to roll
     * back: the {@code PUT} runs on a Tomcat thread in its own transaction, so
     * {@code @DataJpaTest}'s tidy-up-for-free does not apply. Both the fixture rows and
     * the settings are shared dev-database state and have to be put back by hand.
     */
    @AfterEach
    void removeFixturesAndRestoreSeededSettings() {
        deleteById("loan", loanId);
        deleteById("book", bookId);
        deleteById("member", memberId);
        deleteById("category", categoryId);

        // Back to exactly what the seed migration left: the value, and the updated_at
        // that stays NULL until a setting is genuinely changed.
        jdbcTemplate.update("update parameter set value = ?, updated_at = null where key = ?",
                SEEDED_DAYS_TO_KEEP_A_BOOK, DAYS_TO_KEEP_A_BOOK);
        jdbcTemplate.update("update parameter set value = ?, updated_at = null where key = ?",
                SEEDED_MAX_BOOKS_PER_MEMBER, MAX_BOOKS_PER_MEMBER);

        // The saves above swept every open loan against a 30-day period, including any
        // this test did not create. One more sweep, now under the restored 14 days,
        // leaves them as they were found.
        loanService.reevaluateOverdue();
    }

    /**
     * The phase's acceptance criterion, in one request.
     *
     * <p>The precondition is established through the same production path rather than
     * by writing {@code LATE} into the column by hand: saving a genuinely shorter
     * period than the twenty-day-old fixture loan re-evaluates it into {@code LATE}.
     * That first call therefore proves the {@code ACTIVE -> LATE} direction of the
     * chain before the second call proves the direction the phase is judged on. It has
     * to be a real change -- {@code SettingsService.save} only publishes its
     * re-evaluation event when {@code DAYS_TO_KEEP_A_BOOK} actually moves, so a
     * same-value save would leave the fixture untouched and this precondition false.
     */
    @Test
    void raisingTheLendingPeriodFlipsAnOverdueLoanBackToActiveWithinTheSaveRequest() {
        saveSettings(SHORTENED_DAYS_TO_KEEP_A_BOOK, SEEDED_MAX_BOOKS_PER_MEMBER);
        assertThat(storedState()).isEqualTo(LoanState.LATE);
        assertDerivedStateIs("LATE");

        saveSettings(RAISED_DAYS_TO_KEEP_A_BOOK, SEEDED_MAX_BOOKS_PER_MEMBER)
                .expectBody()
                .jsonPath("$[?(@.key == '" + DAYS_TO_KEEP_A_BOOK + "')].value")
                .isEqualTo(List.of(RAISED_DAYS_TO_KEEP_A_BOOK));

        // Immediately: no polling and no sleep. If the listener were @Async or
        // @TransactionalEventListener(AFTER_COMMIT), this line is what would fail.
        assertThat(storedState()).isEqualTo(LoanState.ACTIVE);
        assertDerivedStateIs("ACTIVE");
    }

    /**
     * Reads the persisted {@code state} column, which is the point <em>here and
     * nowhere else</em>.
     *
     * <p>Everywhere in production the answer to "is this loan late?" is
     * {@code LoanService.toResponse}'s date arithmetic, never this column -- see its
     * Javadoc, which forbids exactly this read. This test is the one place that has to
     * look at the cache itself, because the thing under test is whether the bulk update
     * ran at all. Not a precedent: a test that wants to know a loan's state should ask
     * the API, as {@link #assertDerivedStateIs} does.
     *
     * <p>No transaction wraps the test method, so this is a fresh read of the row the
     * Tomcat thread committed, not a stale first-level-cache copy.
     */
    private LoanState storedState() {
        return loanRepository.findById(loanId).orElseThrow().getState();
    }

    /** What a librarian would see: the state the API derives, independent of the column. */
    private void assertDerivedStateIs(String expected) {
        restTestClient.get()
                .uri("/api/loans/{id}", loanId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.state").isEqualTo(expected);
    }

    /** A real HTTP {@code PUT} on the random port -- both keys, as the contract requires. */
    private RestTestClient.ResponseSpec saveSettings(String daysToKeepABook, String maxBooksPerMember) {
        return restTestClient.put()
                .uri("/api/parameters")
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(
                        new ParameterRequest(DAYS_TO_KEEP_A_BOOK, daysToKeepABook),
                        new ParameterRequest(MAX_BOOKS_PER_MEMBER, maxBooksPerMember)))
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * A hard delete, which no service offers: books, members and categories are only
     * ever soft-deleted, and a soft-deleted fixture would still be a leftover row.
     * Tolerates a null id so a failure part-way through the seeding still cleans up
     * whatever did get created.
     */
    private void deleteById(String table, Long id) {
        if (id != null) {
            jdbcTemplate.update("delete from " + table + " where id = ?", id);
        }
    }
}
