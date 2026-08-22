package com.library.management.backend.book;

/**
 * Why a copy is being taken out of the collection.
 *
 * <p>Deliberately a separate enum from {@link BookStatus} rather than a reuse of it,
 * even though the three constants have the same names. {@code BookStatus} has a
 * fourth value, {@code ACTIVE}, which is never a removal reason
 * ({@code API_CONTRACT.md} §5, {@code DATA_MODEL.md} §4.1). Typing the request field
 * as this enum means {@code {"status":"ACTIVE"}} fails in Jackson before the payload
 * is ever built, and the existing {@code HttpMessageNotReadableException} handler
 * turns that into the contract's {@code 400 VALIDATION_ERROR} -- so the rule is
 * enforced by the type system instead of by a custom validator. Do not "simplify"
 * this away by reusing {@code BookStatus}.
 */
public enum RemovalReason {
    LOST,
    DAMAGED,
    WITHDRAWN;

    /**
     * The status a removed copy takes on.
     *
     * <p>A {@code switch} expression rather than {@code BookStatus.valueOf(name())}:
     * adding a fourth reason without a matching status then fails to compile, where
     * {@code valueOf} would only fail at runtime, on the one request that used it.
     */
    public BookStatus toBookStatus() {
        return switch (this) {
            case LOST -> BookStatus.LOST;
            case DAMAGED -> BookStatus.DAMAGED;
            case WITHDRAWN -> BookStatus.WITHDRAWN;
        };
    }
}
