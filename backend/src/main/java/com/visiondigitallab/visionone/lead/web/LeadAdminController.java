package com.visiondigitallab.visionone.lead.web;

import com.visiondigitallab.visionone.lead.api.LeadDetailResponse;
import com.visiondigitallab.visionone.lead.api.LeadWriteRequests;
import com.visiondigitallab.visionone.lead.internal.LeadAdminService;
import com.visiondigitallab.visionone.lead.internal.LeadQueryService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vision Digital Lab's operating surface for the pipeline.
 *
 * <p>The client reads the pipeline and never edits it: the status of a lead is a record of what
 * Vision did, not something the practice maintains.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/admin/leads")
@PreAuthorize("hasRole('VISION_ADMIN')")
public class LeadAdminController {

    private final LeadAdminService adminService;
    private final LeadQueryService queryService;

    public LeadAdminController(LeadAdminService adminService, LeadQueryService queryService) {
        this.adminService = adminService;
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadDetailResponse create(
            @PathVariable UUID orgId, @Valid @RequestBody LeadWriteRequests.CreateLead request) {
        var context = OrganizationContextHolder.require();
        UUID id = adminService.create(context, request);
        return queryService.detail(context, id);
    }

    @PutMapping("/{leadId}")
    public LeadDetailResponse update(
            @PathVariable UUID orgId,
            @PathVariable UUID leadId,
            @Valid @RequestBody LeadWriteRequests.UpdateLead request) {
        var context = OrganizationContextHolder.require();
        adminService.update(context, leadId, request);
        return queryService.detail(context, leadId);
    }

    /** Returns the lead with its new history, so the screen needs no follow-up call. */
    @PostMapping("/{leadId}/status")
    public LeadDetailResponse transition(
            @PathVariable UUID orgId,
            @PathVariable UUID leadId,
            @Valid @RequestBody LeadWriteRequests.TransitionLead request) {
        var context = OrganizationContextHolder.require();
        adminService.transition(context, leadId, request);
        return queryService.detail(context, leadId);
    }
}
