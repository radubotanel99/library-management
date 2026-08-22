package com.library.management.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the application clock.
 *
 * <p>Services that reason about time inject this {@link Clock} instead of calling
 * {@code Instant.now()} directly. Loans need it: whether a loan is active or late,
 * and by how many days, is arithmetic on "now", and a hardcoded call to the system
 * clock would leave those rules testable only by waiting.
 *
 * <p>UTC, not the system zone: every timestamp in this application is stored and
 * compared in UTC ({@code DATA_MODEL.md} §1).
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
