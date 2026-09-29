package com.visiondigitallab.visionone.frontdesk.internal;

import com.visiondigitallab.visionone.frontdesk.api.FrontDeskMetrics;
import com.visiondigitallab.visionone.frontdesk.api.FrontDeskResponse;
import com.visiondigitallab.visionone.frontdesk.domain.Call;
import com.visiondigitallab.visionone.frontdesk.domain.CallHandler;
import com.visiondigitallab.visionone.frontdesk.domain.CallOutcome;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * How a set of calls becomes a summary.
 *
 * <p>One implementation, two callers: the Front Desk screen, which already holds the month's calls
 * in memory, and the reporting read surface, which loads its own. Counting the same thing twice in
 * two places is how a dashboard and a report end up disagreeing.
 */
final class CallSummaries {

    private static final int HOURS_IN_DAY = 24;

    private CallSummaries() {}

    /**
     * How many calls each outcome holds, for the filter pills on the Recent calls table.
     *
     * <p>Declaration order rather than count order, so the row does not reshuffle itself between
     * refreshes as the month fills up. Outcomes with no calls are left out: a pill that always
     * filters to nothing is noise, and the component that renders these already drops them.
     *
     * <p>Always called with the whole month. Counting the filtered list instead would put a
     * different number under the same word as the KPI tile above it.
     */
    static List<FrontDeskResponse.OutcomeCount> outcomeCounts(List<Call> calls) {
        List<FrontDeskResponse.OutcomeCount> out = new ArrayList<>();
        for (CallOutcome outcome : CallOutcome.values()) {
            long count = calls.stream().filter(call -> call.getOutcome() == outcome).count();
            if (count > 0) {
                out.add(new FrontDeskResponse.OutcomeCount(outcome.name(), count));
            }
        }
        return List.copyOf(out);
    }

    /** Volume by hour of the practice's own day, which is what makes the after-hours gap legible. */
    static List<Integer> hourly(List<Call> calls, ZoneId zone) {
        int[] buckets = new int[HOURS_IN_DAY];
        for (Call call : calls) {
            buckets[call.getStartedAt().atZone(zone).getHour()] += 1;
        }
        List<Integer> out = new ArrayList<>(HOURS_IN_DAY);
        for (int count : buckets) {
            out.add(count);
        }
        return out;
    }

    static FrontDeskMetrics.CallSummary summarise(List<Call> calls) {
        long total = calls.size();
        long answered = calls.stream().filter(Call::isAnswered).count();
        long booked = calls.stream().filter(c -> c.getOutcome() == CallOutcome.BOOKED).count();

        // Averaged over answered calls only: a missed call lasts zero seconds and would drag the
        // figure toward something that describes no real conversation.
        Integer averageDuration = answered == 0
                ? null
                : (int) Math.round(calls.stream()
                        .filter(Call::isAnswered)
                        .mapToInt(Call::getDurationSeconds)
                        .average()
                        .orElse(0.0d));

        Double conversion = total == 0 ? null : Math.round(booked * 1000.0d / total) / 10.0d;

        return new FrontDeskMetrics.CallSummary(
                total,
                answered,
                total - answered,
                calls.stream().filter(Call::isAfterHours).count(),
                booked,
                calls.stream().filter(c -> c.getOutcome() == CallOutcome.CANCELLED).count(),
                calls.stream().filter(c -> c.getOutcome() == CallOutcome.RESCHEDULED).count(),
                calls.stream().filter(Call::isTransferred).count(),
                calls.stream().filter(c -> c.getHandledBy() == CallHandler.AI_FRONT_DESK).count(),
                calls.stream().filter(c -> c.getHandledBy() == CallHandler.PRACTICE_TEAM).count(),
                averageDuration,
                conversion);
    }
}
