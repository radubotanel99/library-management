package com.library.management.backend.loan;

/**
 * How many loans in a given set of states one member holds.
 *
 * <p>A projection, not an entity: it exists so a page of members can be built with
 * a single grouped query instead of one count per member.
 *
 * <p>It lives in the {@code loan} package because the loan repository owns the
 * query, the same way {@code CategoryBookCount} lives with the books it counts.
 *
 * @param memberId the member the loans belong to
 * @param count    number of matching loans
 */
public record MemberOpenLoanCount(Long memberId, long count) {
}
