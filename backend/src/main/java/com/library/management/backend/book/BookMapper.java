package com.library.management.backend.book;

import com.library.management.backend.book.dto.BookResponse;

/**
 * Entity to DTO conversion for books.
 *
 * <p>Package-private and static, like {@code CategoryMapper}: the mapping is a pure
 * function with no state and no dependencies, so making it a Spring bean would buy
 * nothing. Keeping it out of the {@code dto} package and out of public view means
 * only the service can call it -- the entity never gets a chance to leave the
 * service layer.
 *
 * <p>{@code onLoan} is passed in rather than read off the entity: {@code Book} has
 * no {@code @OneToMany} to loans, and the caller resolves it either per book or in
 * one batch query for a whole page.
 */
final class BookMapper {

    private BookMapper() {
    }

    /**
     * <p>{@code getCategory().getName()} initialises the lazy association if it is
     * still a proxy. Every path that reaches here either fetch-joins the category
     * (the catalogue query) or runs inside the service transaction, so it never
     * fails -- but it is the reason the search query joins rather than relying on
     * lazy loading per row.
     */
    static BookResponse toResponse(Book book, boolean onLoan) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getBookNumber(),
                book.getCategory().getId(),
                book.getCategory().getName(),
                book.getPublisher(),
                book.getPrice(),
                book.getStatus(),
                book.getRemovalNote(),
                onLoan,
                book.getCreatedAt(),
                book.getUpdatedAt());
    }
}
