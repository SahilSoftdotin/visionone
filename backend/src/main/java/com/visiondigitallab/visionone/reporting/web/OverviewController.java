package com.visiondigitallab.visionone.reporting.web;

import com.visiondigitallab.visionone.common.RequestedMonth;
import com.visiondigitallab.visionone.reporting.api.OverviewResponse;
import com.visiondigitallab.visionone.reporting.internal.OverviewService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    /**
     * The Overview. Both roles read it; neither writes it.
     *
     * <p>The {@code orgId} path variable is what the organization-context interceptor verified
     * before this method ran, so there is no membership check here to forget.
     */
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public OverviewResponse overview(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String month) {
        return overviewService.build(OrganizationContextHolder.require(), RequestedMonth.parse(month, OrganizationContextHolder.require().zoneId()));
    }
}
