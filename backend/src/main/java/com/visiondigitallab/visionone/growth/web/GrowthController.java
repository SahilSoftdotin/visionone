package com.visiondigitallab.visionone.growth.web;

import com.visiondigitallab.visionone.common.RequestedMonth;
import com.visiondigitallab.visionone.growth.api.GrowthPlanResponse;
import com.visiondigitallab.visionone.growth.internal.GrowthPlanService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Growth screen. Both roles read; only Vision Admin writes, through the admin controller. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/growth")
public class GrowthController {

    private final GrowthPlanService growthPlanService;

    public GrowthController(GrowthPlanService growthPlanService) {
        this.growthPlanService = growthPlanService;
    }

    @GetMapping("/plan")
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public GrowthPlanResponse plan(
            @PathVariable UUID orgId, @RequestParam(required = false) String month) {
        return growthPlanService.read(OrganizationContextHolder.require(), RequestedMonth.parse(month, OrganizationContextHolder.require().zoneId()));
    }
}
