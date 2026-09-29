package com.visiondigitallab.visionone.appointment.web;

import com.visiondigitallab.visionone.appointment.api.CalendarResponse;
import com.visiondigitallab.visionone.appointment.internal.CalendarQueryService;
import com.visiondigitallab.visionone.common.RequestedMonth;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Calendar. Read-only: appointments are booked in the practice's own system, not here. */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/calendar")
public class CalendarController {

    private final CalendarQueryService calendarQueryService;

    public CalendarController(CalendarQueryService calendarQueryService) {
        this.calendarQueryService = calendarQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public CalendarResponse read(
            @PathVariable UUID orgId, @RequestParam(required = false) String month) {
        return calendarQueryService.read(OrganizationContextHolder.require(), RequestedMonth.parse(month, OrganizationContextHolder.require().zoneId()));
    }
}
