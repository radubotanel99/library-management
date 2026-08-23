package com.library.management.backend.parameter;

import com.library.management.backend.parameter.dto.ParameterResponse;

/**
 * Entity to DTO conversion for settings.
 *
 * <p>Package-private and static: the mapping is a pure function with no state and
 * no dependencies, so making it a Spring bean would buy nothing. Keeping it out of
 * the {@code dto} package and out of public view means only {@code SettingsService}
 * can call it -- the entity never gets a chance to leave the service layer.
 */
final class ParameterMapper {

    private ParameterMapper() {
    }

    static ParameterResponse toResponse(Parameter parameter) {
        return new ParameterResponse(
                parameter.getKey(),
                parameter.getValue(),
                parameter.getUpdatedAt());
    }
}
