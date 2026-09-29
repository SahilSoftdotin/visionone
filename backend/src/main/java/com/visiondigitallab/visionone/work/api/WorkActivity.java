package com.visiondigitallab.visionone.work.api;

import java.time.Instant;
import java.util.UUID;

/** What Vision Digital Lab has done, is doing, and is waiting on the client for. */
public interface WorkActivity {

    ActivityCounts countsFor(UUID organizationId, Instant from, Instant to);

    /** What Vision finished in the period, for the monthly report. */
    java.util.List<CompletedWork> completedIn(UUID organizationId, Instant from, Instant to);

    record ActivityCounts(long completed, long inProgress, long waitingForClient) {}

    record CompletedWork(String title, String category, String businessReason, String completedAt) {}
}
