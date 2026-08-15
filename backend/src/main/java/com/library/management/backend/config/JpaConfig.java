package com.library.management.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing, which fills {@code created_at} / {@code updated_at}.
 *
 * <p>{@code modifyOnCreate = false} is required: the data model mandates that
 * {@code updated_at} stays NULL until the row is genuinely edited. Spring Data's
 * default would stamp both fields on insert, making them identical and destroying
 * the "has this ever been edited?" signal.
 */
@Configuration
@EnableJpaAuditing(modifyOnCreate = false)
public class JpaConfig {
}
