package com.library.management.backend.book;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Book}.
 *
 * <p>Every lookup takes a {@link BookStatus} rather than assuming one: book
 * numbers are unique only among {@code ACTIVE} copies, so "the book with number
 * 1201" is a question that only has a single answer once the status is fixed.
 *
 * <p>The category feature also reads from here -- it owns the rules, this
 * repository just answers "how many active copies point at this category?".
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByCategoryIdAndStatus(Long categoryId, BookStatus status);

    long countByCategoryIdAndStatus(Long categoryId, BookStatus status);

    Optional<Book> findByBookNumberAndStatus(Integer bookNumber, BookStatus status);

    boolean existsByBookNumberAndStatus(Integer bookNumber, BookStatus status);

    /** Duplicate check for updates: the book being edited is not its own duplicate. */
    boolean existsByBookNumberAndStatusAndIdNot(Integer bookNumber, BookStatus status, Long id);

    /**
     * One page of the catalogue, filtered and searched ({@code API_CONTRACT.md} §5).
     *
     * <p>The category is fetch-joined because every row's response carries
     * {@code categoryName}; without it the lazy association would fire one query
     * per row. The count query is written out by hand rather than derived: Spring
     * Data cannot derive a correct count from a query with {@code join fetch}.
     *
     * <p>{@code pattern} arrives lowercased and wrapped in {@code %}, and
     * {@code searchNumber} arrives already parsed -- both {@code null} when they do
     * not apply, which the {@code :x is null or ...} guards turn into "no filter".
     * Matching on the number is exact: a book number is an identifier, not text.
     */
    @Query(value = """
            select b from Book b
            join fetch b.category c
            where b.status = :status
              and (:categoryId is null or c.id = :categoryId)
              and (:pattern is null
                   or lower(b.title) like :pattern
                   or lower(b.author) like :pattern
                   or b.bookNumber = :searchNumber)
            """,
            countQuery = """
            select count(b) from Book b
            where b.status = :status
              and (:categoryId is null or b.category.id = :categoryId)
              and (:pattern is null
                   or lower(b.title) like :pattern
                   or lower(b.author) like :pattern
                   or b.bookNumber = :searchNumber)
            """)
    Page<Book> search(@Param("status") BookStatus status,
                      @Param("categoryId") Long categoryId,
                      @Param("pattern") String pattern,
                      @Param("searchNumber") Integer searchNumber,
                      Pageable pageable);

    /**
     * One page of the archive: everything not {@code ACTIVE}, or narrowed to a
     * single removal reason ({@code API_CONTRACT.md} §5).
     *
     * <p>A {@code status in :statuses} guard rather than {@code <>} so the service
     * can pass either all three archived statuses or just the one the caller asked
     * for, with no second query method. Otherwise identical to {@link #search}:
     * fetch-joined category, hand-written count query, same search-term guards.
     *
     * <p>{@code bookNumber} may repeat across the rows this returns -- the partial
     * unique index only covers {@code ACTIVE} copies -- so callers must not treat it
     * as a key here.
     */
    @Query(value = """
            select b from Book b
            join fetch b.category c
            where b.status in :statuses
              and (:categoryId is null or c.id = :categoryId)
              and (:pattern is null
                   or lower(b.title) like :pattern
                   or lower(b.author) like :pattern
                   or b.bookNumber = :searchNumber)
            """,
            countQuery = """
            select count(b) from Book b
            where b.status in :statuses
              and (:categoryId is null or b.category.id = :categoryId)
              and (:pattern is null
                   or lower(b.title) like :pattern
                   or lower(b.author) like :pattern
                   or b.bookNumber = :searchNumber)
            """)
    Page<Book> searchArchived(@Param("statuses") Set<BookStatus> statuses,
                              @Param("categoryId") Long categoryId,
                              @Param("pattern") String pattern,
                              @Param("searchNumber") Integer searchNumber,
                              Pageable pageable);

    /**
     * One grouped query for the whole category list, instead of a count per row.
     *
     * <p>Categories with no matching book are simply absent from the result -- the
     * caller defaults them to zero.
     */
    @Query("""
            select new com.library.management.backend.book.CategoryBookCount(b.category.id, count(b))
            from Book b
            where b.status = :status
            group by b.category.id
            """)
    List<CategoryBookCount> countBooksByCategory(@Param("status") BookStatus status);
}
