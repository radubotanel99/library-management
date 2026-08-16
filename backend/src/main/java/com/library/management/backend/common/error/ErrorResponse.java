package com.library.management.backend.common.error;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * The single error body returned by every endpoint (see {@code API_CONTRACT.md} §3).
 *
 * @param code      stable machine-readable identifier the frontend translates
 * @param message   developer-facing English text, never shown to a user
 * @param field     offending field name for validation errors, otherwise {@code null}
 * @param timestamp when the error was produced, ISO-8601 UTC on the wire
 */
public record ErrorResponse(
        String code,
        String message,
        String field,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp) {

    /**
     * Builds a response from a code, defaulting the message and stamping the clock,
     * so no handler has to assemble the shape by hand.
     */
    public static ErrorResponse of(ErrorCode code, String message, String field) {
        return new ErrorResponse(
                code.name(),
                message != null ? message : code.getDefaultMessage(),
                field,
                Instant.now());
    }
}
