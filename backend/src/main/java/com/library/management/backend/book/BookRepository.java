package com.library.management.backend.book;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Book}.
 *
 * <p>Only what the category feature needs so far -- catalogue paging and search
 * arrive with the books phase. Category owns the business rules; this repository
 * just answers "how many active copies point at this category?".
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByCategoryIdAndStatus(Long categoryId, BookStatus status);

    long countByCategoryIdAndStatus(Long categoryId, BookStatus status);

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
