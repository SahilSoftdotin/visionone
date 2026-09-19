package com.visiondigitallab.visionone.work.api;

import java.time.Instant;
import java.util.UUID;

/** What Vision Digital Lab has done, is doing, and is waiting on the client for. */
public interface WorkActivity {

    ActivityCounts countsFor(UUID organizationId, Instant from, Instant to);

    record ActivityCounts(long completed, long inProgress, long waitingForClient) {}
}
