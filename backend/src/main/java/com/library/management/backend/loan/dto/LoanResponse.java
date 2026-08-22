package com.library.management.backend.loan.dto;

import com.library.management.backend.loan.LoanState;
import java.time.Instant;

/**
 * What the API returns for a loan ({@code API_CONTRACT.md} §8).
 *
 * <p>Book and member details are denormalised so the loans table renders in one
 * request. {@code bookNumber} is unique only among active copies, which is why
 * {@code bookTitle} and {@code bookAuthor} always travel with it
 * ({@code DATA_MODEL.md} §4).
 *
 * <p>Three of these fields are computed, not stored: {@code state} is re-derived at
 * read time rather than read off the row, and {@code dueAt} and {@code daysOverdue}
 * have no columns at all.
 *
 * @param state       the loan's state <em>as of this response</em>, derived from the
 *                    current lending period -- it can differ from the stored
 *                    {@code loan.state} between a settings change and the next
 *                    re-evaluation run
 * @param borrowedAt  when the book went out; the loan's {@code created_at}
 * @param dueAt       {@code borrowedAt + DAYS_TO_KEEP_A_BOOK}, computed rather than
 *                    stored so that changing the lending period re-dates every open
 *                    loan ({@code DATA_MODEL.md} §6). Display only -- never sent back
 * @param daysOverdue {@code 0} unless the loan is late, otherwise clamped to
 *                    {@code [1, 365]}: a loan minutes past due still counts as a full
 *                    day, and an ancient one does not produce an absurd number
 * @param finishedAt  when the book came back; null while the loan is open
 */
public record LoanResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String bookAuthor,
        Integer bookNumber,
        Long memberId,
        String memberName,
        LoanState state,
        Instant borrowedAt,
        Instant dueAt,
        int daysOverdue,
        Instant finishedAt) {
}
