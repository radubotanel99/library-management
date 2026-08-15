package com.library.management.backend.book;

import com.library.management.backend.category.Category;
import com.library.management.backend.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * A single copy of a book in the collection.
 *
 * <p>{@link #bookNumber} is unique only among {@link BookStatus#ACTIVE} copies
 * (partial unique index {@code ux_book_number_active}), so removing a copy frees
 * its number for reuse. Loans reference the book id, so history stays
 * unambiguous -- but any screen showing a number must also show title and author.
 *
 * <p>Indexes and constraints are declared in Flyway only, never in
 * {@code @Table} annotations: Flyway owns the schema, and JPA cannot express the
 * partial unique indexes used here, so duplicating them would be misleading.
 */
@Entity
@Table(name = "book")
@Getter
@Setter
public class Book extends AuditableEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 255)
    private String author;

    @Column(name = "book_number", nullable = false)
    private Integer bookNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(length = 255)
    private String publisher;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status = BookStatus.ACTIVE;

    /** Why the copy left the collection; null while the copy is active. */
    @Column(name = "removal_note", length = 500)
    private String removalNote;
}
