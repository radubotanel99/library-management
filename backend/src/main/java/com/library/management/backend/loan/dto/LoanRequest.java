package com.library.management.backend.loan.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Lend payload ({@code API_CONTRACT.md} §8).
 *
 * <p>Two ids and nothing else. There is deliberately no borrow date: it is stamped
 * server-side, so a client cannot back-date a loan and quietly move its due date.
 * Returning has no payload at all -- it is {@code POST /api/loans/{id}/return}.
 *
 * @param bookId   an {@code ACTIVE} copy that is not already out
 * @param memberId an active member under the open-loan limit; boxed rather than
 *                 {@code long} so a missing field fails validation instead of
 *                 silently becoming {@code 0}
 */
public record LoanRequest(
        @NotNull Long bookId,
        @NotNull Long memberId) {
}
