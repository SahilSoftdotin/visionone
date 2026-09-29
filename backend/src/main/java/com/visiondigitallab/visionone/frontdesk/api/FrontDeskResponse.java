package com.visiondigitallab.visionone.frontdesk.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The Front Desk screen.
 *
 * <p>{@code dataSource} is stated rather than implied: synthetic call data and live call data look
 * identical on a dashboard and mean opposite things. The screen renders its badge from this field,
 * so the label cannot drift out of step with what is actually connected.
 */
public record FrontDeskResponse(
        LocalDate periodMonth,
        String dataSource,
        FrontDeskMetrics.CallSummary summary,
        List<Integer> hourly,
        /**
         * How many calls each outcome holds this month, always over the whole month and never over
         * the filtered list. The pills these feed sit directly beneath the KPI tiles, so if they
         * counted only what {@code recent} happens to show, the same word would carry two numbers
         * on one screen.
         */
        List<OutcomeCount> outcomeCounts,
        List<CallRow> recent) {

    /** One pill on the Recent calls filter. Only outcomes with at least one call are sent. */
    public record OutcomeCount(String outcome, long count) {}

    public record CallRow(
            UUID id,
            String callerLabel,
            String handledBy,
            String outcome,
            boolean afterHours,
            int durationSeconds,
            String startedAt) {}
}
