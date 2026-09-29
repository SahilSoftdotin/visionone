package com.visiondigitallab.visionone.reporting.api;

import com.visiondigitallab.visionone.common.Money;
import com.visiondigitallab.visionone.content.api.ContentPublication;
import com.visiondigitallab.visionone.frontdesk.api.FrontDeskMetrics;
import com.visiondigitallab.visionone.work.api.WorkActivity;
import java.time.LocalDate;
import java.util.List;

/**
 * Every figure in a monthly report, in the shape it is frozen in.
 *
 * <p>This is what gets written to {@code monthly_report.payload_json}, and what is read back out of
 * it months later. Two consequences worth stating: it holds no database ids, because ids are not
 * figures and a frozen report should not invite a reader to click through to a row that has since
 * changed; and it reuses each module's own record types rather than restating their fields, so a
 * report cannot describe a call summary differently from the Front Desk screen.
 *
 * <p>Fields may only ever be added to this record, never renamed or removed: an old row in the
 * database is deserialized by this class, and Jackson is configured to ignore what it does not
 * recognise so that a payload written by an older build still reads.
 */
public record ReportPayload(
        String organizationName,
        LocalDate periodMonth,
        String currency,
        Investment investment,
        Headline headline,
        Funnel funnel,
        List<SourceRow> sources,
        FrontDeskMetrics.CallSummary frontDesk,
        List<WorkActivity.CompletedWork> completedWork,
        List<ContentPublication.PublishedContent> publishedContent) {

    public record Investment(Money planned, Money actual, Money remaining, double utilizationPercent) {}

    /**
     * @param costPerLead null when there were no leads - the UI renders an em dash rather than a
     *     zero that would read as "free".
     */
    public record Headline(
            long newLeads,
            long qualified,
            long appointments,
            Money costPerLead,
            Money costPerBooked,
            Double bookingRatePercent) {}

    public record Funnel(long leads, long qualified, long appointmentRequested, long booked) {}

    public record SourceRow(
            String channelCode,
            String displayName,
            long leads,
            long qualified,
            long booked,
            Money spend,
            Money costPerBooked) {}
}
