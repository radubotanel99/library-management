package com.library.management.backend.dashboard;

import com.library.management.backend.dashboard.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoint ({@code API_CONTRACT.md} §10).
 *
 * <p>Pure HTTP plumbing: delegate and return. No parameters to bind, nothing to
 * validate, and no try/catch -- an unexpected failure is turned into the contract's
 * {@code INTERNAL_ERROR} body by the single {@code @RestControllerAdvice}, so this
 * endpoint needs no error code of its own.
 *
 * <p>One {@code GET} returning one object, rather than a figure per endpoint: the
 * dashboard renders as a unit and its tiles must agree with each other.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse load() {
        return dashboardService.load();
    }
}
