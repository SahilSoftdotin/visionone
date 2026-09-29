package com.visiondigitallab.visionone.work.web;

import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import com.visiondigitallab.visionone.work.api.WorkListResponse;
import com.visiondigitallab.visionone.work.internal.WorkQueryService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The work half of Work & Content. Both roles read it. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/work")
public class WorkController {

    private final WorkQueryService workQueryService;

    public WorkController(WorkQueryService workQueryService) {
        this.workQueryService = workQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public WorkListResponse list(@PathVariable UUID orgId) {
        return workQueryService.list(OrganizationContextHolder.require());
    }
}
