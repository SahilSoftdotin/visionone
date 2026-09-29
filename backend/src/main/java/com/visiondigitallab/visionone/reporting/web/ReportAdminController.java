package com.visiondigitallab.visionone.reporting.web;

import com.visiondigitallab.visionone.common.RequestedMonth;
import com.visiondigitallab.visionone.reporting.api.MonthlyReportResponse;
import com.visiondigitallab.visionone.reporting.api.ReportWriteRequests;
import com.visiondigitallab.visionone.reporting.internal.ReportAdminService;
import com.visiondigitallab.visionone.reporting.internal.ReportQueryService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/admin/reports")
@PreAuthorize("hasRole('VISION_ADMIN')")
public class ReportAdminController {

    private final ReportAdminService adminService;
    private final ReportQueryService queryService;

    public ReportAdminController(ReportAdminService adminService, ReportQueryService queryService) {
        this.adminService = adminService;
        this.queryService = queryService;
    }

    /** Freezes the month's figures. Safe to call twice; the second call refreshes the payload. */
    @PostMapping("/generate")
    public MonthlyReportResponse generate(
            @PathVariable UUID orgId, @RequestParam(required = false) String month) {
        var context = OrganizationContextHolder.require();
        UUID reportId = adminService.generate(context, RequestedMonth.parse(month, OrganizationContextHolder.require().zoneId()));
        return queryService.read(context, reportId);
    }

    @PutMapping("/{reportId}/narrative")
    public MonthlyReportResponse writeNarrative(
            @PathVariable UUID orgId,
            @PathVariable UUID reportId,
            @Valid @RequestBody ReportWriteRequests.Narrative request) {
        var context = OrganizationContextHolder.require();
        adminService.writeNarrative(context, reportId, request);
        return queryService.read(context, reportId);
    }

    @PostMapping("/{reportId}/share")
    public MonthlyReportResponse share(@PathVariable UUID orgId, @PathVariable UUID reportId) {
        var context = OrganizationContextHolder.require();
        adminService.share(context, reportId);
        return queryService.read(context, reportId);
    }
}
