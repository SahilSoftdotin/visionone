package com.visiondigitallab.visionone.lead.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Leads screen's payload: the period's leads plus the summary above the table.
 *
 * <p>The table sorts and searches client-side within the period, which at this volume is a few
 * dozen rows. Server-side filters exist for status, source and date range so the screen does not
 * have to grow into pagination before it needs to.
 */
public record LeadListResponse(Summary summary, List<LeadRow> leads, boolean editable) {

    public record Summary(
            long total,
            long qualified,
            long booked,
            Long medianResponseMinutes,
            Double underFifteenMinutesPercent) {}

    public record LeadRow(
            UUID id,
            String reference,
            String name,
            String serviceInterest,
            String source,
            String campaign,
            Instant createdAt,
            String status,
            String owner,
            Long responseMinutes,
            boolean booked) {}
}
