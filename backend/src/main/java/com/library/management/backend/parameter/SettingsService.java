package com.library.management.backend.parameter;

import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.parameter.dto.ParameterRequest;
import com.library.management.backend.parameter.dto.ParameterResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The typed way to read and the only way to write library-wide settings
 * ({@code DATA_MODEL.md} §8).
 *
 * <p>Values are stored as text, so exactly one class is allowed to parse them --
 * this one. Everything else asks for an {@code int} and never sees a raw string.
 *
 * <p>A missing row or an unparsable value falls back to a hardcoded default and
 * logs a warning rather than failing: a lending screen that stops working because
 * somebody deleted a settings row would be a far worse outcome than lending with
 * the shipped default. The warning is what makes the fallback visible.
 *
 * <p>{@link #save} takes the full set at once ({@code API_CONTRACT.md} §9). It
 * validates everything before writing anything -- the request must carry exactly the
 * known keys, and every value must be a positive integer -- so a rejected request
 * never leaves the library half-configured. Reads are forgiving and writes are
 * strict on purpose: a bad row already in the database must not stop lending, but
 * there is no reason to accept a new one.
 *
 * <p>A successful save publishes {@link ParametersUpdatedEvent} -- but only when
 * {@code DAYS_TO_KEEP_A_BOOK} actually changed -- so open loans get re-evaluated
 * against the new lending period in the same transaction. It is an event rather
 * than a call into the loan package because that package already depends on this
 * class. The change check matters: re-evaluation is a bulk update across every
 * open loan, and publishing it unconditionally would mean a save that changes
 * nothing still takes a table-wide write lock, momentarily blocking lending and
 * returns for no reason.
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

    /** The keys a save must supply, all of them and nothing else. */
    private static final Set<String> KNOWN_KEYS = Set.of(DAYS_TO_KEEP_A_BOOK, MAX_BOOKS_PER_MEMBER);

    /** Matches the value seeded by {@code V2__seed_parameters.sql}. */
    private static final int DEFAULT_DAYS_TO_KEEP_A_BOOK = 14;

    /** Matches the value seeded by {@code V2__seed_parameters.sql}. */
    private static final int DEFAULT_MAX_BOOKS_PER_MEMBER = 3;

    private final ParameterRepository parameterRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SettingsService(ParameterRepository parameterRepository, ApplicationEventPublisher eventPublisher) {
        this.parameterRepository = parameterRepository;
        this.eventPublisher = eventPublisher;
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
     * Every setting, ordered by key so the settings screen has a stable layout.
     *
     * <p>Returns whatever the table holds rather than only the known keys: a row
     * this version does not understand is still somebody's setting, and hiding it
     * would make it impossible to see or correct.
     */
    public List<ParameterResponse> findAll() {
        return parameterRepository.findAll(Sort.by("key")).stream()
                .map(ParameterMapper::toResponse)
                .toList();
    }

    /**
     * Replaces the full set of settings in one transaction
     * ({@code API_CONTRACT.md} §9).
     *
     * <p>Validation runs to completion before the first row is touched, so a bad
     * second value can never leave the first one persisted. Every failure is
     * {@code PARAMETER_INVALID_VALUE} carrying the offending {@code key} as the
     * field -- the body is an array, so a JSON path would tell the frontend nothing
     * useful about which input to highlight.
     *
     * @throws ApiException if a key is duplicated, unrecognised or missing, or if
     *                      any value is not a positive integer
     */
    @Transactional
    public List<ParameterResponse> save(List<ParameterRequest> requests) {
        Map<String, Integer> values = validate(requests);

        // Read before writing: this is the only way to know whether the lending
        // period is actually moving, which decides whether re-evaluation runs at all.
        int previousDaysToKeep = getDaysToKeepABook();

        List<Parameter> entities = new ArrayList<>(values.size());
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            Parameter parameter = new Parameter();
            parameter.setKey(entry.getKey());
            // The parsed integer, not the raw text: stray whitespace and leading
            // zeros never reach the database, so what is read back is what was meant.
            parameter.setValue(String.valueOf(entry.getValue()));
            entities.add(parameter);
        }

        // The key is a natural, client-assigned id, so save() resolves to an INSERT
        // or an UPDATE on its own -- no lookup first. The flush is what makes the
        // audited updated_at available to the response below.
        parameterRepository.saveAll(entities);
        parameterRepository.flush();

        // Synchronous, and therefore still inside this transaction: if loan
        // re-evaluation fails, the settings save rolls back with it. Gated on an
        // actual change to the lending period -- see the class Javadoc for why an
        // unconditional publish here would be a problem, not just a waste.
        if (!values.get(DAYS_TO_KEEP_A_BOOK).equals(previousDaysToKeep)) {
            eventPublisher.publishEvent(new ParametersUpdatedEvent());
        }

        return findAll();
    }

    /**
     * Checks the whole request and returns the parsed values, keyed by parameter.
     *
     * <p>Order matters only in that structure is checked before content: an
     * unrecognised key is a more useful complaint than the value it carries.
     */
    private Map<String, Integer> validate(List<ParameterRequest> requests) {
        Set<String> seen = new HashSet<>();
        for (ParameterRequest request : requests) {
            if (!seen.add(request.key())) {
                throw new ApiException(ErrorCode.PARAMETER_INVALID_VALUE, request.key());
            }
            if (!KNOWN_KEYS.contains(request.key())) {
                throw new ApiException(ErrorCode.PARAMETER_INVALID_VALUE, request.key());
            }
        }

        for (String known : KNOWN_KEYS) {
            if (!seen.contains(known)) {
                throw new ApiException(ErrorCode.PARAMETER_INVALID_VALUE, known);
            }
        }

        Map<String, Integer> values = new LinkedHashMap<>();
        for (ParameterRequest request : requests) {
            values.put(request.key(), parsePositive(request.key(), request.value()));
        }
        return values;
    }

    /**
     * A setting is a count of days or of books, so zero and negatives are
     * meaningless. There is no upper bound: an unusually long lending period is a
     * policy decision, not an error.
     */
    private int parsePositive(String key, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.PARAMETER_INVALID_VALUE, key, ex);
        }
        if (parsed <= 0) {
            throw new ApiException(ErrorCode.PARAMETER_INVALID_VALUE, key);
        }
        return parsed;
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
