package com.library.management.backend.loan;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Loan}.
 *
 * <p>Only the "is this copy out?" questions so far -- lending and returning arrive
 * with the loans phase. The catalogue needs them to derive {@code onLoan} on a
 * {@code BookResponse} ({@code API_CONTRACT.md} §5).
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
}
