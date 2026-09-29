package com.visiondigitallab.visionone.frontdesk.api;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The front desk module's read surface for other modules.
 *
 * <p>Reporting asks for this summary rather than querying {@code call} itself. The record it
 * returns is the same one the Front Desk screen renders, so the monthly report and the screen
 * cannot disagree about how many calls came in.
 */
public interface FrontDeskMetrics {

    /**
     * The window's calls, summarised and bucketed by hour.
     *
     * <p>One method rather than two because both answers come from the same set of rows, and the
     * zone is a parameter because "which hour of the day was this call" is a question about the
     * practice's own clock - a server in UTC would draw the after-hours gap in the wrong place.
     */
    CallActivity activityFor(UUID organizationId, Instant from, Instant to, ZoneId zone);

    /** @param hourly 24 buckets, index 0 to 23, in the organization's timezone. */
    record CallActivity(CallSummary summary, List<Integer> hourly) {}

    record CallSummary(
            long totalCalls,
            long answered,
            long missed,
            long afterHours,
            long appointmentsBooked,
            long cancelled,
            long rescheduled,
            long transferred,
            long aiHandled,
            long teamHandled,
            Integer averageDurationSeconds,
            Double bookingConversionPercent) {}
}
