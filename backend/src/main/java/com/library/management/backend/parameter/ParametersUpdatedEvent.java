package com.library.management.backend.parameter;

/**
 * Published by {@code SettingsService} once a settings save has been written.
 *
 * <p>Saving parameters must immediately re-evaluate open loans in the
 * <strong>same transaction</strong> ({@code API_CONTRACT.md} §9): changing
 * {@code DAYS_TO_KEEP_A_BOOK} moves every due date, so settings and loan state can
 * never be allowed to disagree, and a failed re-evaluation must roll the save back.
 *
 * <p>An event rather than a direct call because {@code LoanService} already injects
 * {@code SettingsService}; calling back the other way would be a circular bean
 * dependency. A plain {@code publishEvent} is synchronous and runs its listeners on
 * the same thread, inside the caller's transaction, which is exactly the required
 * semantics -- a listener must therefore <em>not</em> be {@code @Async} or a
 * {@code @TransactionalEventListener}.
 *
 * <p>Carries no data: a listener that needs the new values asks
 * {@code SettingsService} for them, and by then the rows are written.
 */
public record ParametersUpdatedEvent() {
}
