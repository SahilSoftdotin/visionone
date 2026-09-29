package com.visiondigitallab.visionone.appointment.web;

import com.visiondigitallab.visionone.appointment.api.CalendarResponse;
import com.visiondigitallab.visionone.appointment.internal.CalendarQueryService;
import com.visiondigitallab.visionone.common.RequestedMonth;
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
 * The one thing Vision may change on the Calendar: which marketing channel a booking is credited
 * to.
 *
 * <p>There is deliberately no endpoint here that reschedules, cancels or re-times an appointment.
 * Those belong to the practice's scheduling system, and {@code SchedulingProvider} has no method
 * that could write them. If VisionOne could cancel a booking locally, a doctor could read
 * "cancelled" on this screen while the patient is still booked for Tuesday in Healthie - a fact
 * about someone's week, not a cache to be invalidated.
 *
 * <p>Attribution is the opposite case: Healthie neither knows nor cares which advertisement
 * produced a patient, so this is VisionOne's own data and correcting it is Vision's own job. The
 * practice reads it; only Vision writes it.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/admin/calendar")
@PreAuthorize("hasRole('VISION_ADMIN')")
public class CalendarAdminController {

    private final CalendarQueryService calendarQueryService;

    public CalendarAdminController(CalendarQueryService calendarQueryService) {
        this.calendarQueryService = calendarQueryService;
    }

    /**
     * Credit one appointment to a channel, or to none.
     *
     * <p>Returns the refreshed month so the panel updates from the write itself rather than from a
     * second request that could disagree with it.
     */
    @PutMapping("/{appointmentId}/attribution")
    public CalendarResponse attribute(
            @PathVariable UUID orgId,
            @PathVariable UUID appointmentId,
            @RequestParam(required = false) String month,
            @Valid @RequestBody AttributionRequest request) {
        var context = OrganizationContextHolder.require();
        return calendarQueryService.attribute(
                context,
                RequestedMonth.parse(month, context.zoneId()),
                appointmentId,
                request.channelSourceId());
    }

    /**
     * @param channelSourceId the channel to credit, or null to clear it. Null is a legitimate
     *     answer and means the channel is unknown - the Calendar prints nothing rather than
     *     guessing, because a guess would credit a channel that earned nothing and cost per booked
     *     appointment is built on these.
     */
    public record AttributionRequest(UUID channelSourceId) {}
}
