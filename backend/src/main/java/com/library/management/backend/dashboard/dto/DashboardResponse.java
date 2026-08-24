package com.library.management.backend.dashboard.dto;

import java.util.List;

/**
 * Everything the home screen needs, in one response
 * ({@code API_CONTRACT.md} §10).
 *
 * <p>One endpoint rather than four, because the dashboard renders as a unit: four
 * requests would let the tiles disagree with each other while they arrive.
 *
 * @param totalCopies  copies actually held -- {@code ACTIVE} books only, since a
 *                     lost or withdrawn one is not on the shelf
 * @param totalMembers members not archived
 * @param loansActive  open loans still in time, derived from
 *                     {@code borrowedAt + DAYS_TO_KEEP_A_BOOK} rather than read off
 *                     {@code loan.state}, which is only as fresh as the last
 *                     re-evaluation run ({@code API_CONTRACT.md} §11)
 * @param loansOverdue open loans past due, derived the same way -- so raising the
 *                     lending period moves this number immediately instead of at
 *                     the next sweep
 * @param mostBorrowed top 5 by all-time loan count, active books only; empty for a
 *                     library that has not lent anything yet, never null
 */
public record DashboardResponse(
        long totalCopies,
        long totalMembers,
        long loansActive,
        long loansOverdue,
        List<MostBorrowedBookResponse> mostBorrowed) {
}
