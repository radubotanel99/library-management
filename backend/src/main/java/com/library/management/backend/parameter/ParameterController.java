package com.library.management.backend.parameter;

import com.library.management.backend.parameter.dto.ParameterRequest;
import com.library.management.backend.parameter.dto.ParameterResponse;
import jakarta.validation.Valid;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings endpoints ({@code API_CONTRACT.md} §9).
 *
 * <p>Pure HTTP plumbing: bind, delegate, return. No business rules and no
 * try/catch -- rule violations arrive as {@code ApiException} and are turned into
 * responses by the single {@code @RestControllerAdvice}.
 *
 * <p>Both ends of the write are arrays, not single resources: the contract saves
 * the whole set at once, because a partial save could leave the library
 * half-configured. There is no {@code POST} and no {@code DELETE} -- adding a
 * setting is an INSERT performed by a migration, never by a user.
 */
@RestController
@RequestMapping("/api/parameters")
public class ParameterController {

    /**
     * The request carries exactly two known keys today; ten is headroom, not a
     * real limit. Without it, this is the only endpoint in the system that binds
     * an unbounded array -- every other write is a single fixed-shape DTO -- so a
     * huge body would be fully deserialized and validated before
     * {@code SettingsService} ever gets a chance to reject it on content.
     */
    private static final int MAX_PARAMETERS_PER_REQUEST = 10;

    private final SettingsService settingsService;

    public ParameterController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public List<ParameterResponse> findAll() {
        return settingsService.findAll();
    }

    /**
     * Saves the full set of settings.
     *
     * <p>{@code @Valid} sits on the type argument, not on the parameter: applying it
     * to a container is deprecated in Bean Validation and only the type-argument
     * form actually cascades into each element.
     */
    @PutMapping
    public List<ParameterResponse> save(
            @RequestBody @Size(max = MAX_PARAMETERS_PER_REQUEST) List<@NotNull @Valid ParameterRequest> requests) {
        return settingsService.save(requests);
    }
}
