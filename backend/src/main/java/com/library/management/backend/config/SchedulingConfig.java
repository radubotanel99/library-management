package com.library.management.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler, which drives the daily overdue re-evaluation
 * ({@code API_CONTRACT.md} §11).
 *
 * <p>A class of its own rather than an annotation on {@code BackendApplication}:
 * the test slices ({@code @WebMvcTest}, {@code @DataJpaTest}) build a context from a
 * filtered set of components and do not pick up an arbitrary {@code @Configuration}
 * unless it is explicitly {@code @Import}ed, whereas an annotation on the application
 * class is part of every slice's configuration. Keeping it here means no slice test
 * ever starts a scheduler thread or fires a cron job mid-assertion.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
