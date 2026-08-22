package com.library.management.backend.parameter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The typed way to read library-wide settings ({@code DATA_MODEL.md} §8).
 *
 * <p>Values are stored as text, so exactly one class is allowed to parse them --
 * this one. Everything else asks for an {@code int} and never sees a raw string.
 *
 * <p>A missing row or an unparsable value falls back to a hardcoded default and
 * logs a warning rather than failing: a lending screen that stops working because
 * somebody deleted a settings row would be a far worse outcome than lending with
 * the shipped default. The warning is what makes the fallback visible.
 *
 * <p>Read-only on purpose. The write path ({@code PUT /api/parameters}, validation,
 * and the loan re-evaluation it triggers) belongs to the settings phase; this class
 * exists now only because lending cannot compute a due date or enforce a limit
 * without these two numbers.
 *
 * <p>Not cached: two single-row primary-key lookups are sub-millisecond and the
 * persistence context already de-duplicates them inside one transaction.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class SettingsService {

    private static final String DAYS_TO_KEEP_A_BOOK = "DAYS_TO_KEEP_A_BOOK";
    private static final String MAX_BOOKS_PER_MEMBER = "MAX_BOOKS_PER_MEMBER";

    /** Matches the value seeded by {@code V2__seed_parameters.sql}. */
    private static final int DEFAULT_DAYS_TO_KEEP_A_BOOK = 14;

    /** Matches the value seeded by {@code V2__seed_parameters.sql}. */
    private static final int DEFAULT_MAX_BOOKS_PER_MEMBER = 3;

    private final ParameterRepository parameterRepository;

    public SettingsService(ParameterRepository parameterRepository) {
        this.parameterRepository = parameterRepository;
    }

    /** The lending period in days -- the basis of every due date and overdue check. */
    public int getDaysToKeepABook() {
        return readInt(DAYS_TO_KEEP_A_BOOK, DEFAULT_DAYS_TO_KEEP_A_BOOK);
    }

    /** How many open loans one member may hold at a time. */
    public int getMaxBooksPerMember() {
        return readInt(MAX_BOOKS_PER_MEMBER, DEFAULT_MAX_BOOKS_PER_MEMBER);
    }

    /**
     * Reads one setting as an {@code int}, degrading to {@code fallback} rather than
     * throwing.
     *
     * <p>Both failure modes are the same kind of problem -- the database says
     * something this application cannot use -- so both take the same branch: warn
     * loudly, carry on with the default.
     */
    private int readInt(String key, int fallback) {
        String value = parameterRepository.findById(key)
                .map(Parameter::getValue)
                .orElse(null);

        if (value == null) {
            log.warn("Setting {} is missing; falling back to {}", key, fallback);
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Setting {} holds a non-numeric value '{}'; falling back to {}", key, value, fallback);
            return fallback;
        }
    }
}
