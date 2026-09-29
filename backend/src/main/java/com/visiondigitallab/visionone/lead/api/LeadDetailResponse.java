package com.visiondigitallab.visionone.lead.api;

import java.time.Instant;
import java.util.List;

/** One lead and everything that happened to it. */
public record LeadDetailResponse(LeadListResponse.LeadRow lead, List<HistoryEntry> history) {

    public record HistoryEntry(
            String fromStatus, String toStatus, Instant changedAt, String changedBy, String reason) {}
}
