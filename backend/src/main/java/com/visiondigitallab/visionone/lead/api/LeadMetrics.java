package com.visiondigitallab.visionone.lead.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The lead module's read surface for other modules.
 *
 * <p>Reporting composes these rather than querying {@code lead} directly, which is what keeps the
 * module boundary real. The full lead pipeline lands in Week 3; this interface is what the
 * Overview needs in Week 1.
 */
public interface LeadMetrics {

    /** Leads created in the window, excluding duplicates. */
    long newLeadCount(UUID organizationId, Instant from, Instant to);

    /**
     * Funnel counts for leads created in the window, each stage counted as
     * <em>ever reached</em>, so the funnel is monotonically decreasing.
     */
    Funnel funnel(UUID organizationId, Instant from, Instant to);

    List<SourceCounts> countsBySource(UUID organizationId, Instant from, Instant to);

    record Funnel(long leads, long qualified, long appointmentRequested, long booked) {}

    record SourceCounts(String channelCode, long leads, long qualified, long booked) {}
}
