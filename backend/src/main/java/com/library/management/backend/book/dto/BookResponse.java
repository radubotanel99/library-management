package com.library.management.backend.book.dto;

import com.library.management.backend.book.BookStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the API returns for a book ({@code API_CONTRACT.md} §5).
 *
 * <p>{@code categoryName} is denormalised so the catalogue table needs no second
 * request, and {@code onLoan} is derived (does this copy have an {@code ACTIVE} or
 * {@code LATE} loan?) so the UI can disable the Lend action without extra calls.
 * Neither is a column on {@code book}.
 *
 * <p><strong>{@code bookNumber} is unique only among active copies</strong>, so any
 * screen that shows it must also show {@code title} and {@code author}
 * ({@code DATA_MODEL.md} §4) -- which is why both are always in this response.
 *
 * @param removalNote why the copy left the collection; null while it is active
 * @param updatedAt   null until the row is actually edited
 */
public record BookResponse(
        Long id,
        String title,
        String author,
        Integer bookNumber,
        Long categoryId,
        String categoryName,
        String publisher,
        BigDecimal price,
        BookStatus status,
        String removalNote,
        boolean onLoan,
        Instant createdAt,
        Instant updatedAt) {
}
