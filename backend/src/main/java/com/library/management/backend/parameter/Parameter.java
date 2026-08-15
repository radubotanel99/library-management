package com.library.management.backend.parameter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A single key/value setting row.
 *
 * <p>Adding a setting is an INSERT, not a migration. This stays a plain data
 * holder with no behaviour: a typed settings service is the only place allowed to
 * parse {@link #value}, and it arrives in a later phase.
 *
 * <p>Does not extend {@code AuditableEntity}: the key is a natural {@code String}
 * id and the table has no {@code created_at} column, so the auditing listener is
 * registered directly here for {@link #updatedAt}.
 */
@Entity
@Table(name = "parameter")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Parameter {

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    /** Null until the setting is changed from its seeded value. */
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
