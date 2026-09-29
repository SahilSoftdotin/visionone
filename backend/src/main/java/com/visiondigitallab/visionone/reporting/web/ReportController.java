package com.visiondigitallab.visionone.reporting.web;

import com.visiondigitallab.visionone.reporting.api.MonthlyReportResponse;
import com.visiondigitallab.visionone.reporting.api.ReportListResponse;
import com.visiondigitallab.visionone.reporting.internal.ReportQueryService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Monthly reports. Both roles read; only Vision Digital Lab generates and shares. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/reports")
public class ReportController {

    private final ReportQueryService queryService;

    public ReportController(ReportQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public ReportListResponse list(
            @PathVariable UUID orgId, @RequestParam(required = false) Integer year) {
        return queryService.list(OrganizationContextHolder.require(), year);
    }

    /** What the screen opens on: the most recent report, not necessarily this month's. */
    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public MonthlyReportResponse latest(@PathVariable UUID orgId) {
        return queryService.latest(OrganizationContextHolder.require());
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public MonthlyReportResponse read(@PathVariable UUID orgId, @PathVariable UUID reportId) {
        return queryService.read(OrganizationContextHolder.require(), reportId);
    }
}
