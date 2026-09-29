package com.visiondigitallab.visionone.growth.api;

import com.visiondigitallab.visionone.common.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The Growth screen's payload: the month's plan, per-channel plan versus actual, and what each
 * channel produced.
 *
 * <p>One call for the whole screen. Cost figures are null rather than zero when there is no
 * denominator, exactly as on the Overview, and the UI renders an em dash.
 */
public record GrowthPlanResponse(
        LocalDate periodMonth,
        String currency,
        String status,
        Money plannedTotal,
        Money actualTotal,
        Money remaining,
        double utilizationPercent,
        String notes,
        boolean editable,
        List<ChannelAllocation> allocations,
        List<CampaignRow> campaigns) {

    public record ChannelAllocation(
            UUID channelSourceId,
            String channelCode,
            String displayName,
            String category,
            Money planned,
            Money actual,
            Money variance,
            long leads,
            long qualified,
            long booked,
            Money costPerLead,
            Money costPerBookedAppointment) {}

    /**
     * A campaign and what it produced.
     *
     * <p>{@code spend} is null in Phase 1: the schema carries budget at channel level, not per
     * campaign. The Google Ads adapter fills it in Phase 2. Leads and booked are real now.
     */
    public record CampaignRow(
            UUID id, String name, String channelCode, String status, Money spend, long leads, long booked) {}
}
