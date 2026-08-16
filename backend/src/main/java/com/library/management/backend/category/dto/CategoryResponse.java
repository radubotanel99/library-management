package com.library.management.backend.category.dto;

/**
 * What the API returns for a category ({@code API_CONTRACT.md} §6).
 *
 * <p>No timestamps: the category screens never show them, and the contract lists
 * these four fields exactly.
 *
 * @param id          surrogate key
 * @param name        display name
 * @param description optional free text
 * @param bookCount   number of <strong>active</strong> books in this category, so the
 *                    UI can warn before archiving
 */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        long bookCount) {
}
