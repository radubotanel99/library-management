package com.library.management.backend.book.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create/update payload for a book ({@code API_CONTRACT.md} §5).
 *
 * <p>There is deliberately no {@code status} or {@code removalNote} field:
 * removing a copy is {@code POST /api/books/{id}/remove} and restoring it is
 * {@code POST /api/books/{id}/restore}, so an edit can never silently un-archive
 * a copy or erase the reason it left the collection (see {@code DATA_MODEL.md}
 * §4.1). Removal is a {@code POST} rather than a {@code DELETE} because a reason
 * is mandatory and {@code DELETE} has no body semantics for it.
 *
 * @param bookNumber the number written on the copy; boxed rather than {@code int}
 *                   so a missing field fails validation instead of silently
 *                   becoming {@code 0}
 * @param categoryId must reference an active category
 * @param price      bounded to what {@code NUMERIC(10,2)} can hold
 */
public record BookRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String author,
        @NotNull @Positive Integer bookNumber,
        @NotNull Long categoryId,
        @Size(max = 255) String publisher,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
