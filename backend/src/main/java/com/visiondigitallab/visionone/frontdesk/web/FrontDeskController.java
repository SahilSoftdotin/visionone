package com.visiondigitallab.visionone.frontdesk.web;

import com.visiondigitallab.visionone.common.RequestedMonth;
import com.visiondigitallab.visionone.frontdesk.api.FrontDeskResponse;
import com.visiondigitallab.visionone.frontdesk.internal.FrontDeskQueryService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Front Desk screen. Read-only for both roles: nobody edits a call record. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/frontdesk")
public class FrontDeskController {

    private final FrontDeskQueryService frontDeskQueryService;

    public FrontDeskController(FrontDeskQueryService frontDeskQueryService) {
        this.frontDeskQueryService = frontDeskQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public FrontDeskResponse read(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String outcome) {
        return frontDeskQueryService.read(
                OrganizationContextHolder.require(),
                RequestedMonth.parse(month, OrganizationContextHolder.require().zoneId()),
                outcome);
    }
}
