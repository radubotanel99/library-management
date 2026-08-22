package com.library.management.backend.parameter;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Parameter}.
 *
 * <p>No custom methods: the key <em>is</em> the primary key, so {@code findById} is
 * the only lookup a settings row ever needs.
 *
 * <p>{@code SettingsService} is the only class allowed to touch this repository
 * ({@code DATA_MODEL.md} §8) -- values are raw text, and letting callers read them
 * directly would spread parsing across the codebase.
 */
public interface ParameterRepository extends JpaRepository<Parameter, String> {
}
