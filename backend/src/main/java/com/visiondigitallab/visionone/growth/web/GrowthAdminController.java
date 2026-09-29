package com.visiondigitallab.visionone.growth.web;

import com.visiondigitallab.visionone.common.RequestedMonth;
import com.visiondigitallab.visionone.growth.api.AllocationUpdateRequest;
import com.visiondigitallab.visionone.growth.api.GrowthPlanResponse;
import com.visiondigitallab.visionone.growth.api.GrowthPlanUpdateRequest;
import com.visiondigitallab.visionone.growth.internal.GrowthAdminService;
import com.visiondigitallab.visionone.growth.internal.GrowthPlanService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vision Digital Lab's operating surface for Growth.
 *
 * <p>CLIENT_OWNER is refused here at the method level, not hidden in the UI: Vision operates, the
 * client decides. Both writes return the recomputed plan so the admin screen needs no second call.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/admin/growth")
@PreAuthorize("hasRole('VISION_ADMIN')")
public class GrowthAdminController {

    private final GrowthAdminService adminService;
    private final GrowthPlanService readService;

    public GrowthAdminController(GrowthAdminService adminService, GrowthPlanService readService) {
        this.adminService = adminService;
        this.readService = readService;
    }

    @PutMapping("/plan")
    public GrowthPlanResponse setPlan(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String month,
            @Valid @RequestBody GrowthPlanUpdateRequest request) {
        var context = OrganizationContextHolder.require();
        var period = RequestedMonth.parse(month, context.zoneId());
        adminService.upsertPlan(context, period, request);
        return readService.read(context, period);
    }

    @PutMapping("/allocations")
    public GrowthPlanResponse setAllocations(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String month,
            @Valid @RequestBody AllocationUpdateRequest request) {
        var context = OrganizationContextHolder.require();
        var period = RequestedMonth.parse(month, context.zoneId());
        adminService.replaceAllocations(context, period, request);
        return readService.read(context, period);
    }
}
