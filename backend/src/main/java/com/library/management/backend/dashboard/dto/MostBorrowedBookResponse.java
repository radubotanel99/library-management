package com.library.management.backend.dashboard.dto;

/**
 * One row of the dashboard's most-borrowed list ({@code API_CONTRACT.md} §10).
 *
 * <p>Title and author rather than a book number: the number is unique only among
 * active copies ({@code DATA_MODEL.md} §4), so it never names a book on its own.
 * {@code bookId} is here so the screen can link through to the catalogue.
 *
 * @param loanCount loans ever recorded against this copy, in every state --
 *                  returned loans count towards popularity too
 */
public record MostBorrowedBookResponse(Long bookId, String title, String author, long loanCount) {
}
