package com.library.management.backend.loan;

/**
 * The stored state of a loan.
 *
 * <p>These three values are the only ones ever persisted; the
 * {@code ck_loan_state} check constraint mirrors them in the database.
 *
 * <p>There is deliberately no {@code OPEN} value: {@code OPEN} is an API-level
 * filter alias meaning {@code ACTIVE} or {@code LATE}, introduced at the
 * DTO/service level in a later phase. It is never a stored value.
 */
public enum LoanState {
    ACTIVE,
    LATE,
    FINISHED
}
