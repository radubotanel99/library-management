package com.library.management.backend.book.dto;

import jakarta.validation.constraints.Positive;

/**
 * Optional payload for {@code POST /api/books/{id}/restore}
 * ({@code API_CONTRACT.md} §5).
 *
 * <p>Absent -- or present with {@code bookNumber} absent -- restores the copy under
 * its existing number. Supplying {@code bookNumber} replaces it in the same call:
 * this is the retry path after a {@code BOOK_NUMBER_TAKEN_ON_RESTORE} collision, so
 * the frontend never needs a separate {@code PUT} to change the number first.
 *
 * @param bookNumber the number to restore the copy under; omit to keep its own
 */
public record BookRestoreRequest(@Positive Integer bookNumber) {
}
