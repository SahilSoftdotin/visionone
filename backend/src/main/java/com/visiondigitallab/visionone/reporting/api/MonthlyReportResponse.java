package com.visiondigitallab.visionone.reporting.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One monthly report as the screen renders it.
 *
 * <p>{@code frozen} is not decoration. A DRAFT report shows figures composed live from today's
 * data, which will move; a GENERATED or SHARED one shows figures as they stood when it was
 * generated. Those are different claims and the screen says which it is making.
 *
 * <p>{@code editable} is true only for Vision Digital Lab, and only until the report is shared. The
 * screen hides the controls when it is false; the admin endpoints refuse regardless - the hiding is
 * a courtesy, the refusal is the control.
 */
public record MonthlyReportResponse(
        UUID id,
        LocalDate periodMonth,
        String status,
        String generatedAt,
        boolean frozen,
        String keyLearning,
        List<String> nextActions,
        List<String> decisionsRequired,
        ReportPayload figures,
        boolean editable) {}
