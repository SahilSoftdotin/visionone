package com.visiondigitallab.visionone.reporting.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.reporting.api.MonthlyReportResponse;
import com.visiondigitallab.visionone.reporting.api.ReportListResponse;
import com.visiondigitallab.visionone.reporting.api.ReportPayload;
import com.visiondigitallab.visionone.reporting.domain.MonthlyReport;
import com.visiondigitallab.visionone.reporting.domain.ReportStatus;
import com.visiondigitallab.visionone.reporting.repository.MonthlyReportRepository;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading monthly reports. Both roles read; only Vision writes. */
@Service
@Transactional(readOnly = true)
public class ReportQueryService {

    private static final Logger log = LoggerFactory.getLogger(ReportQueryService.class);

    private final MonthlyReportRepository reports;
    private final ReportComposer composer;
    private final ObjectMapper objectMapper;

    public ReportQueryService(
            MonthlyReportRepository reports, ReportComposer composer, ObjectMapper objectMapper) {
        this.reports = reports;
        this.composer = composer;
        // A payload written by an older build must still read. Fields are only ever added to
        // ReportPayload, so ignoring unknown ones is the forward half of that contract.
        this.objectMapper = objectMapper
                .copy()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ReportListResponse list(OrganizationContext context, Integer year) {
        List<MonthlyReport> rows = year == null
                ? reports.findByOrganizationIdOrderByPeriodMonthDesc(context.organizationId())
                : reports.findByOrganizationIdAndPeriodMonthBetweenOrderByPeriodMonthDesc(
                        context.organizationId(),
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 1));

        return new ReportListResponse(rows.stream()
                .filter(r -> isVisibleTo(context, r))
                .map(r -> new ReportListResponse.ReportSummary(
                        r.getId(),
                        r.getPeriodMonth(),
                        r.getStatus().name(),
                        r.getGeneratedAt() == null ? null : r.getGeneratedAt().toString(),
                        r.isFrozen()))
                .toList(),
                context.isVisionAdmin());
    }

    public MonthlyReportResponse read(OrganizationContext context, UUID reportId) {
        return reports.findByIdAndOrganizationId(reportId, context.organizationId())
                .filter(r -> isVisibleTo(context, r))
                .map(r -> toResponse(context, r))
                .orElseThrow(() -> new NotFoundException("MonthlyReport", reportId));
    }

    /**
     * The most recent report, which is what the screen opens on.
     *
     * <p>Not "this month's": the current month's report usually does not exist yet, and an empty
     * screen on the first of the month would be a worse answer than last month's report.
     */
    public MonthlyReportResponse latest(OrganizationContext context) {
        return reports.findByOrganizationIdOrderByPeriodMonthDesc(context.organizationId()).stream()
                .filter(r -> isVisibleTo(context, r))
                .findFirst()
                .map(r -> toResponse(context, r))
                .orElseThrow(() -> new NotFoundException("MonthlyReport", context.organizationId()));
    }

    /**
     * A report the practice has not been given is a report the practice cannot see.
     *
     * <p>Vision writes the narrative over several sittings and regenerates figures as the month
     * closes; a client reading a half-written draft would be reading something nobody meant to
     * say. An unshared report is reported as not found rather than as forbidden, because
     * "forbidden" would confirm that this month's report exists and is being worked on.
     */
    private static boolean isVisibleTo(OrganizationContext context, MonthlyReport report) {
        return context.isVisionAdmin() || report.getStatus() == ReportStatus.SHARED;
    }

    private MonthlyReportResponse toResponse(OrganizationContext context, MonthlyReport report) {
        return new MonthlyReportResponse(
                report.getId(),
                report.getPeriodMonth(),
                report.getStatus().name(),
                report.getGeneratedAt() == null ? null : report.getGeneratedAt().toString(),
                report.isFrozen(),
                report.getKeyLearning(),
                lines(report.getNextActions()),
                lines(report.getDecisionsRequired()),
                figures(context, report),
                context.isVisionAdmin() && report.getStatus() != ReportStatus.SHARED);
    }

    /**
     * Frozen figures if there are any, live ones otherwise.
     *
     * <p>A DRAFT report has never been generated, so there is nothing to read back and composing
     * live is the only honest answer; the response says {@code frozen: false} so the screen can
     * label it. A frozen payload that fails to deserialize is logged and recomposed rather than
     * failing the request - a report the client cannot open is worse than one whose figures have
     * moved, and the log is what gets it fixed.
     */
    private ReportPayload figures(OrganizationContext context, MonthlyReport report) {
        if (!report.isFrozen()) {
            return composer.compose(context, YearMonth.from(report.getPeriodMonth()));
        }
        try {
            return objectMapper.readValue(report.getPayloadJson(), ReportPayload.class);
        } catch (JsonProcessingException ex) {
            log.error(
                    "Frozen payload for report {} could not be read; recomposing from live data",
                    report.getId(),
                    ex);
            return composer.compose(context, YearMonth.from(report.getPeriodMonth()));
        }
    }

    /** One item per line, blanks dropped. Vision writes these in a textarea. */
    private static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
