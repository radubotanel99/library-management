package com.library.management.backend.category;

import com.library.management.backend.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A category a book can belong to.
 *
 * <p>Categories are soft-deleted via {@link #deleted}; a partial unique index
 * (see {@code V1__init_schema.sql}) keeps names unique among live rows only, so
 * deleting a category frees its name for reuse.
 *
 * <p>There is intentionally no {@code @OneToMany} back-reference to books: it is
 * not needed and would add lazy-loading and cascade surface for nothing.
 */
@Entity
@Table(name = "category")
@Getter
@Setter
public class Category extends AuditableEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean deleted = false;
}
