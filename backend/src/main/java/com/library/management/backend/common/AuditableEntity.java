package com.library.management.backend.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Shared identity and audit columns for entities that have them.
 *
 * <p>The {@code common} package is a deliberate, narrow exception to the
 * package-by-feature rule: this is a single cross-cutting persistence concern,
 * not a feature.
 *
 * <p>{@code createdAt} / {@code updatedAt} are populated exclusively here, by
 * Spring Data JPA auditing. The database has no defaults and no triggers for
 * them, so the application is the only supported writer.
 *
 * <p>{@code Parameter} deliberately does not extend this class: it has a
 * natural {@code String} key and no {@code created_at} column.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class AuditableEntity {

    /** Matches the {@code BIGSERIAL} primary keys created by Flyway. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Stays {@code null} until the row is actually updated. */
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
