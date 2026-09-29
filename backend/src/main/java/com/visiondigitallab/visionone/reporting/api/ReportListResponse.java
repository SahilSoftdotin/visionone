package com.visiondigitallab.visionone.reporting.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The reports the caller can see, newest first. Narrative-free: the list is for choosing one.
 *
 * @param canGenerate whether the caller may start or refresh a report - Vision Digital Lab only.
 */
public record ReportListResponse(List<ReportSummary> reports, boolean canGenerate) {

    public record ReportSummary(
            UUID id, LocalDate periodMonth, String status, String generatedAt, boolean frozen) {}
}
