package com.library.management.backend.loan;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Loan}.
 *
 * <p>Two kinds of question. The other features ask "what is still out?": the
 * catalogue per copy, to derive {@code onLoan} on a {@code BookResponse}
 * ({@code API_CONTRACT.md} §5); members per member, to derive {@code openLoanCount}
 * on a {@code MemberResponse} ({@code API_CONTRACT.md} §7) and to refuse archiving a
 * member who still holds books. The loans feature itself asks for pages, single
 * loans, and the per-member open count that caps lending.
 */
public interface LoanRepository extends JpaRepository<Loan, Long> {

    /**
     * The states that mean "the copy is still out".
     *
     * <p>{@code OPEN} is an API-level filter alias, never a stored value, so the
     * pair is spelled out here rather than added to {@link LoanState}.
     */
    Set<LoanState> OPEN_STATES = EnumSet.of(LoanState.ACTIVE, LoanState.LATE);

    boolean existsByBookIdAndStateIn(Long bookId, Collection<LoanState> states);

    /**
     * One query for a whole page of books instead of an exists-check per row.
     *
     * <p>Returns only the ids that <em>do</em> have an open loan; the caller treats
     * every other id as free. Callers must skip this query when {@code bookIds} is
     * empty -- an empty {@code IN} list is not valid on every database.
     */
    @Query("select l.book.id from Loan l where l.state in :states and l.book.id in :bookIds")
    Set<Long> findBookIdsWithLoanIn(@Param("bookIds") Collection<Long> bookIds,
                                    @Param("states") Collection<LoanState> states);

    boolean existsByMemberIdAndStateIn(Long memberId, Collection<LoanState> states);

    /**
     * One grouped query for a whole page of members instead of a count per row.
     *
     * <p>Members with no matching loan are simply absent from the result -- the
     * caller defaults them to zero. As with {@link #findBookIdsWithLoanIn}, callers
     * must skip this query when {@code memberIds} is empty: an empty {@code IN} list
     * is not valid on every database.
     */
    @Query("""
            select new com.library.management.backend.loan.MemberOpenLoanCount(l.member.id, count(l))
            from Loan l
            where l.state in :states and l.member.id in :memberIds
            group by l.member.id
            """)
    List<MemberOpenLoanCount> countLoansByMemberIn(@Param("memberIds") Collection<Long> memberIds,
                                                   @Param("states") Collection<LoanState> states);

    /** How many books this member is holding, for the per-member lending cap. */
    long countByMemberIdAndStateIn(Long memberId, Collection<LoanState> states);

    /**
     * One loan with its book and member already loaded.
     *
     * <p>{@code LoanResponse} denormalises title, author, number and member name, so
     * the plain {@code findById} would always be followed by two lazy loads. Fetching
     * them up front makes it one query.
     */
    @Query("""
            select l from Loan l
            join fetch l.book b
            join fetch l.member m
            where l.id = :id
            """)
    Optional<Loan> findWithBookAndMemberById(@Param("id") Long id);

    /**
     * One page of loans, filtered and searched ({@code API_CONTRACT.md} §8).
     *
     * <p>Book and member are fetch-joined because every row's response carries their
     * details; without it each lazy association would fire a query per row. The count
     * query is written out by hand rather than derived: Spring Data cannot derive a
     * correct count from a query with {@code join fetch}.
     *
     * <p><strong>Lateness is a date comparison, not a state filter.</strong>
     * {@code loan.state} is only as fresh as the last re-evaluation run, so asking
     * for {@code state = 'LATE'} would answer from a column that may be stale -- and
     * during the loans phase, one that is never written at all. Instead the caller
     * passes {@code lateBefore} (borrowed before the cut-off, so overdue) or
     * {@code notLateFrom} (borrowed on or after it, so still in time), computed from
     * the current {@code DAYS_TO_KEEP_A_BOOK}. Both {@code null} means "either".
     *
     * <p><strong>The {@code cast(:x as Instant)} around the two null-guards is
     * required, not decorative.</strong> Postgres has to infer a type for every bind
     * parameter, and a bare {@code ? is null} gives it nothing to infer from, so it
     * rejects the statement outright with "could not determine data type of
     * parameter". The other guards below need no cast because their parameters carry
     * a known JDBC type -- this only bites on the timestamps. Removing the cast to
     * match the shape of {@code BookRepository.search} breaks every call to this
     * query, including the ones that pass a non-null value.
     *
     * <p>{@code states} narrows to open or finished loans and is never null or empty:
     * an empty {@code IN} list is not portable.
     *
     * <p>{@code pattern} arrives lowercased and wrapped in {@code %}, and
     * {@code searchNumber} arrives already parsed -- both {@code null} when they do
     * not apply. The number sits inside the same {@code or} group as the text
     * predicates, exactly as in {@code BookRepository.search}: a term is one search
     * box, matching any of the four fields, not four filters combined. Matching on
     * the number is exact: a book number is an identifier, not text.
     */
    @Query(value = """
            select l from Loan l
            join fetch l.book b
            join fetch l.member m
            where l.state in :states
              and (cast(:lateBefore as Instant) is null or l.createdAt < :lateBefore)
              and (cast(:notLateFrom as Instant) is null or l.createdAt >= :notLateFrom)
              and (:memberId is null or m.id = :memberId)
              and (:bookId is null or b.id = :bookId)
              and (:pattern is null
                   or lower(b.title) like :pattern
                   or lower(b.author) like :pattern
                   or lower(m.name) like :pattern
                   or b.bookNumber = :searchNumber)
            """,
            countQuery = """
            select count(l) from Loan l
            join l.book b
            join l.member m
            where l.state in :states
              and (cast(:lateBefore as Instant) is null or l.createdAt < :lateBefore)
              and (cast(:notLateFrom as Instant) is null or l.createdAt >= :notLateFrom)
              and (:memberId is null or m.id = :memberId)
              and (:bookId is null or b.id = :bookId)
              and (:pattern is null
                   or lower(b.title) like :pattern
                   or lower(b.author) like :pattern
                   or lower(m.name) like :pattern
                   or b.bookNumber = :searchNumber)
            """)
    Page<Loan> search(@Param("states") Collection<LoanState> states,
                      @Param("lateBefore") Instant lateBefore,
                      @Param("notLateFrom") Instant notLateFrom,
                      @Param("memberId") Long memberId,
                      @Param("bookId") Long bookId,
                      @Param("pattern") String pattern,
                      @Param("searchNumber") Integer searchNumber,
                      Pageable pageable);
}
