package com.visiondigitallab.visionone.work.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The work panel: what Vision has done, is doing, and is waiting on the client for. */
public record WorkListResponse(Summary summary, List<WorkItemRow> items, boolean editable) {

    public record Summary(long completed, long inProgress, long blocked, long waitingForClient) {}

    public record WorkItemRow(
            UUID id,
            String title,
            String category,
            String status,
            String businessReason,
            String owner,
            LocalDate targetDate,
            boolean clientDependency,
            String clientUpdate,
            String completedAt) {}
}
