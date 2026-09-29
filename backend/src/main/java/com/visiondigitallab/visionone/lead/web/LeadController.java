package com.visiondigitallab.visionone.lead.web;

import com.visiondigitallab.visionone.common.RequestedMonth;
import com.visiondigitallab.visionone.lead.api.LeadDetailResponse;
import com.visiondigitallab.visionone.lead.api.LeadListResponse;
import com.visiondigitallab.visionone.lead.internal.LeadQueryService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Leads screen. Both roles read; only Vision Admin writes. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/leads")
public class LeadController {

    private final LeadQueryService leadQueryService;

    public LeadController(LeadQueryService leadQueryService) {
        this.leadQueryService = leadQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public LeadListResponse list(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source) {
        return leadQueryService.list(
                OrganizationContextHolder.require(), RequestedMonth.parse(month, OrganizationContextHolder.require().zoneId()), status, source);
    }

    @GetMapping("/{leadId}")
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public LeadDetailResponse detail(@PathVariable UUID orgId, @PathVariable UUID leadId) {
        return leadQueryService.detail(OrganizationContextHolder.require(), leadId);
    }
}
