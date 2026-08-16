package com.library.management.backend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a category ({@code API_CONTRACT.md} §6).
 *
 * <p>There is deliberately no {@code deleted} field: archiving is
 * {@code DELETE /api/categories/{id}} and nothing else, so an edit can never
 * silently un-archive a row (see {@code DATA_MODEL.md} §4.1).
 *
 * @param name        display name, unique among active categories, case-insensitively
 * @param description optional free text
 */
public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description) {
}
