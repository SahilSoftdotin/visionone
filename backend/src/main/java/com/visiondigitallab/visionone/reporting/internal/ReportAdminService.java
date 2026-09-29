package com.visiondigitallab.visionone.reporting.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.reporting.api.ReportPayload;
import com.visiondigitallab.visionone.reporting.api.ReportWriteRequests;
import com.visiondigitallab.visionone.reporting.domain.MonthlyReport;
import com.visiondigitallab.visionone.reporting.repository.MonthlyReportRepository;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vision Digital Lab generating, writing and sharing a monthly report. */
@Service
public class ReportAdminService {

    private final MonthlyReportRepository reports;
    private final ReportComposer composer;
    private final ObjectMapper objectMapper;

    public ReportAdminService(
            MonthlyReportRepository reports, ReportComposer composer, ObjectMapper objectMapper) {
        this.reports = reports;
        this.composer = composer;
        this.objectMapper = objectMapper;
    }

    /**
     * Freezes the month's figures into the report, creating it if this is the first time.
     *
     * <p>Idempotent in the way that matters: generating twice for the same month updates the one
     * row rather than creating a second report, which the unique constraint on
     * {@code (organization_id, period_month)} would refuse anyway. Generating a month that has
     * already been shared is refused by the entity, not by a check here that could be forgotten.
     */
    @Transactional
    public UUID generate(OrganizationContext context, YearMonth month) {
        MonthlyReport report = reports
                .findByOrganizationIdAndPeriodMonth(context.organizationId(), month.atDay(1))
                .orElseGet(() -> new MonthlyReport(
                        UUID.randomUUID(), context.organizationId(), month.atDay(1)));

        report.freeze(serialise(composer.compose(context, month)), Instant.now());
        reports.save(report);
        return report.getId();
    }

    @Transactional
    public void writeNarrative(
            OrganizationContext context, UUID reportId, ReportWriteRequests.Narrative request) {
        MonthlyReport report = require(context, reportId);
        report.writeNarrative(request.keyLearning(), request.nextActions(), request.decisionsRequired());
        reports.save(report);
    }

    /**
     * Makes the report visible to the practice.
     *
     * <p>Nothing about a shared report changes afterwards. That is the whole reason the figures are
     * frozen, so sharing an ungenerated report is refused rather than quietly generating one.
     */
    @Transactional
    public void share(OrganizationContext context, UUID reportId) {
        MonthlyReport report = require(context, reportId);
        report.share();
        reports.save(report);
    }

    private MonthlyReport require(OrganizationContext context, UUID reportId) {
        return reports
                .findByIdAndOrganizationId(reportId, context.organizationId())
                .orElseThrow(() -> new NotFoundException("MonthlyReport", reportId));
    }

    private String serialise(ReportPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            // Unreachable for a record of records, and not worth a checked exception up the stack.
            throw new IllegalStateException("Could not serialise the report payload", ex);
        }
    }
}
