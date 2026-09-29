package com.visiondigitallab.visionone.work.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** What Vision Admin sends to create, edit or move a work item. */
public final class WorkWriteRequests {

    private WorkWriteRequests() {}

    public record UpsertWork(
            @NotBlank @Size(max = 240) String title,
            @NotBlank String category,
            @NotBlank @Size(max = 4000) String businessReason,
            @NotBlank @Size(max = 160) String owner,
            LocalDate targetDate,
            @Size(max = 4000) String clientUpdate) {}

    public record TransitionWork(@NotBlank String toStatus) {}
}
