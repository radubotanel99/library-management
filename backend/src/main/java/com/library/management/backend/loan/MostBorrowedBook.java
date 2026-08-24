package com.library.management.backend.loan;

/**
 * How many times one book has been borrowed, all-time.
 *
 * <p>A projection, not an entity: it exists so the dashboard's most-borrowed list
 * can be built with a single grouped query instead of a count per book
 * ({@code API_CONTRACT.md} §10).
 *
 * <p>It lives in the {@code loan} package because the loan repository owns the
 * query, the same way {@link MemberOpenLoanCount} does -- even though the rows it
 * describes are books.
 *
 * <p>Title and author travel with the id because a dashboard row has to name the
 * book, and {@code book_number} deliberately does not identify one on its own
 * ({@code DATA_MODEL.md} §4) -- which is why the number is absent here entirely.
 *
 * @param bookId    the book the loans were taken on
 * @param title     the book's title, denormalised so no second query is needed
 * @param author    the book's author, for the same reason
 * @param loanCount number of loans ever recorded against the book, in any state
 */
public record MostBorrowedBook(Long bookId, String title, String author, long loanCount) {
}
