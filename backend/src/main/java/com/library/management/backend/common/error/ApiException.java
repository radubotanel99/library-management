package com.library.management.backend.common.error;

import lombok.Getter;

/**
 * The exception services throw to reject a request for a business reason.
 *
 * <p>It carries only an {@link ErrorCode} (plus an optional offending field): the
 * HTTP status and the message come from the code, so services never mention HTTP
 * and {@link GlobalExceptionHandler} stays the only place that builds a response.
 *
 * <p>Unchecked on purpose -- a rule violation is not something a caller can
 * meaningfully recover from, and a checked exception would litter every signature.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /** Offending field for validation-like errors; {@code null} otherwise. */
    private final String field;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ApiException(ErrorCode errorCode, String field) {
        this(errorCode, field, null);
    }

    public ApiException(ErrorCode errorCode, String field, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.field = field;
    }
}
