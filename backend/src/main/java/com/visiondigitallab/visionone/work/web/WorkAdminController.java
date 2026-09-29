package com.visiondigitallab.visionone.work.web;

import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import com.visiondigitallab.visionone.work.api.WorkListResponse;
import com.visiondigitallab.visionone.work.api.WorkWriteRequests;
import com.visiondigitallab.visionone.work.internal.WorkAdminService;
import com.visiondigitallab.visionone.work.internal.WorkQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/admin/work")
@PreAuthorize("hasRole('VISION_ADMIN')")
public class WorkAdminController {

    private final WorkAdminService adminService;
    private final WorkQueryService queryService;

    public WorkAdminController(WorkAdminService adminService, WorkQueryService queryService) {
        this.adminService = adminService;
        this.queryService = queryService;
    }

    @PostMapping
    public WorkListResponse create(
            @PathVariable UUID orgId, @Valid @RequestBody WorkWriteRequests.UpsertWork request) {
        var context = OrganizationContextHolder.require();
        adminService.create(context, request);
        return queryService.list(context);
    }

    @PutMapping("/{workItemId}")
    public WorkListResponse update(
            @PathVariable UUID orgId,
            @PathVariable UUID workItemId,
            @Valid @RequestBody WorkWriteRequests.UpsertWork request) {
        var context = OrganizationContextHolder.require();
        adminService.update(context, workItemId, request);
        return queryService.list(context);
    }

    @PostMapping("/{workItemId}/status")
    public WorkListResponse transition(
            @PathVariable UUID orgId,
            @PathVariable UUID workItemId,
            @Valid @RequestBody WorkWriteRequests.TransitionWork request) {
        var context = OrganizationContextHolder.require();
        adminService.transition(context, workItemId, request);
        return queryService.list(context);
    }
}
