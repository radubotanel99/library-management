package com.library.management.backend.loan;

/**
 * The {@code state} query parameter of {@code GET /api/loans}
 * ({@code API_CONTRACT.md} §8).
 *
 * <p>Deliberately a separate enum from {@link LoanState} rather than a reuse of it,
 * for the opposite reason to {@code RemovalReason}: this one has a value the stored
 * enum does not. {@code OPEN} is an alias for "{@code ACTIVE} or {@code LATE}" and is
 * never persisted -- adding it to {@link LoanState} would put a value in the column
 * that {@code ck_loan_state} rejects.
 *
 * <p>Typing the parameter as this enum also means an unknown value fails during
 * binding, which the existing {@code MethodArgumentTypeMismatchException} handler
 * turns into the contract's {@code 400 VALIDATION_ERROR}.
 *
 * <p>Note that {@code ACTIVE} and {@code LATE} here are <em>not</em> column filters:
 * the service translates them into a date comparison against the current lending
 * period, because the stored state can be stale. See {@code LoanService.list}.
 */
public enum LoanStateFilter {
    ACTIVE,
    LATE,
    FINISHED,
    OPEN
}
