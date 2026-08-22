package com.library.management.backend.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing, which fills {@code created_at} / {@code updated_at}.
 *
 * <p>{@code modifyOnCreate = false} is required: the data model mandates that
 * {@code updated_at} stays NULL until the row is genuinely edited. Spring Data's
 * default would stamp both fields on insert, making them identical and destroying
 * the "has this ever been edited?" signal.
 *
 * <p>{@code dateTimeProviderRef} points auditing at the same {@link Clock} services
 * inject for their own time arithmetic (loan due dates, in particular). Without it,
 * auditing falls back to its default {@code CurrentDateTimeProvider}, which reads
 * the system clock directly -- so an entity's {@code createdAt} and a service's "now"
 * would be two different clocks that merely agree in production. A fixed test clock
 * would then diverge from the timestamp auditing actually persists.
 */
@Configuration
@EnableJpaAuditing(modifyOnCreate = false, dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
