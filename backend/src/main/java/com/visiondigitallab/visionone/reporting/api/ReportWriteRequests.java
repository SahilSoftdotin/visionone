package com.visiondigitallab.visionone.reporting.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What Vision Digital Lab sends when writing a report. */
public final class ReportWriteRequests {

    private ReportWriteRequests() {}

    /**
     * The three sections a practice owner actually reads. One item per line.
     *
     * <p>Key learning is required: a report with frozen figures and no reading of them is a data
     * dump, and the product's promise is the reading.
     */
    public record Narrative(
            @NotBlank @Size(max = 2000) String keyLearning,
            @Size(max = 2000) String nextActions,
            @Size(max = 2000) String decisionsRequired) {}
}
