package com.library.management.backend.parameter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One entry of the {@code PUT /api/parameters} payload ({@code API_CONTRACT.md} §9).
 *
 * <p>The contract sends the full set as an array, so a request is a
 * {@code List<ParameterRequest>} rather than a single object.
 *
 * <p>The annotations here are structural only -- they mirror the column widths from
 * {@code DATA_MODEL.md} §8 and reject a malformed request before any business rule
 * runs. The rule that a value must be a <em>positive integer</em> is deliberately
 * <strong>not</strong> a Bean Validation annotation: the contract requires it to be
 * reported as {@code PARAMETER_INVALID_VALUE} with the offending {@code key} in
 * {@code field}, not as the generic {@code VALIDATION_ERROR} that Bean Validation
 * produces ({@code API_CONTRACT.md} §4). {@code SettingsService} enforces it, which
 * also keeps parsing of settings text in the one class allowed to do it.
 *
 * @param key   parameter name; must be one of the known keys, checked by the service
 * @param value the new value as text, exactly as it is stored
 */
public record ParameterRequest(
        @NotBlank @Size(max = 100) String key,
        @NotBlank @Size(max = 255) String value) {
}
