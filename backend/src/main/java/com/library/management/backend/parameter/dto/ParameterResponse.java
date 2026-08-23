package com.library.management.backend.parameter.dto;

import java.time.Instant;

/**
 * What the API returns for one setting ({@code API_CONTRACT.md} §9).
 *
 * <p>{@code value} stays a string on the wire even though every current setting is
 * an integer: the column is text, and a key/value table exists precisely so a new
 * setting of some other type is an INSERT rather than a contract change.
 *
 * @param key       parameter name; the frontend keys its labels off it
 * @param updatedAt when the row was last written; null while it still holds the
 *                  value seeded by {@code V2__seed_parameters.sql}
 */
public record ParameterResponse(
        String key,
        String value,
        Instant updatedAt) {
}
