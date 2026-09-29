package com.visiondigitallab.visionone.reporting.internal;

import com.visiondigitallab.visionone.appointment.api.AppointmentMetrics;
import com.visiondigitallab.visionone.common.Money;
import com.visiondigitallab.visionone.content.api.ContentPublication;
import com.visiondigitallab.visionone.frontdesk.api.FrontDeskMetrics;
import com.visiondigitallab.visionone.growth.api.GrowthFinance;
import com.visiondigitallab.visionone.lead.api.LeadMetrics;
import com.visiondigitallab.visionone.reporting.api.ReportPayload;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import com.visiondigitallab.visionone.work.api.WorkActivity;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a month's figures out of the other modules' read surfaces.
 *
 * <p>Reporting owns no operational tables, so this class runs entirely on {@code api} interfaces:
 * growth, lead, appointment, front desk, work and content. That is deliberate and slightly
 * inconvenient - it would be quicker to write six joins - but it is why the report can never
 * disagree with the screens, and why adding a seventh module to the report is an interface method
 * rather than a rewrite.
 */
@Service
@Transactional(readOnly = true)
public class ReportComposer {

    private final GrowthFinance growthFinance;
    private final LeadMetrics leadMetrics;
    private final AppointmentMetrics appointmentMetrics;
    private final FrontDeskMetrics frontDeskMetrics;
    private final WorkActivity workActivity;
    private final ContentPublication contentPublication;

    public ReportComposer(
            GrowthFinance growthFinance,
            LeadMetrics leadMetrics,
            AppointmentMetrics appointmentMetrics,
            FrontDeskMetrics frontDeskMetrics,
            WorkActivity workActivity,
            ContentPublication contentPublication) {
        this.growthFinance = growthFinance;
        this.leadMetrics = leadMetrics;
        this.appointmentMetrics = appointmentMetrics;
        this.frontDeskMetrics = frontDeskMetrics;
        this.workActivity = workActivity;
        this.contentPublication = contentPublication;
    }

    public ReportPayload compose(OrganizationContext context, YearMonth month) {
        UUID orgId = context.organizationId();
        ZoneId zone = context.zoneId();
        String currency = context.currency();

        // The organization's timezone decides which month a row belongs to, never the server's.
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        GrowthFinance.Investment investment = growthFinance
                .investmentFor(orgId, month.atDay(1))
                .orElseGet(() -> new GrowthFinance.Investment(Money.zero(currency), Money.zero(currency)));

        LeadMetrics.Funnel funnel = leadMetrics.funnel(orgId, from, to);
        long newLeads = leadMetrics.newLeadCount(orgId, from, to);
        long booked = appointmentMetrics.bookedCount(orgId, from, to);

        return new ReportPayload(
                context.name(),
                month.atDay(1),
                currency,
                new ReportPayload.Investment(
                        investment.planned(),
                        investment.actual(),
                        investment.remaining(),
                        investment.utilizationPercent()),
                new ReportPayload.Headline(
                        newLeads,
                        funnel.qualified(),
                        booked,
                        investment.actual().dividedBy(newLeads),
                        investment.actual().dividedBy(booked),
                        percentage(booked, newLeads)),
                new ReportPayload.Funnel(
                        funnel.leads(), funnel.qualified(), funnel.appointmentRequested(), funnel.booked()),
                sources(orgId, month, from, to),
                frontDeskMetrics.activityFor(orgId, from, to, zone).summary(),
                workActivity.completedIn(orgId, from, to),
                contentPublication.publishedIn(orgId, from, to));
    }

    /**
     * Spend and outcome side by side, one row per funded channel.
     *
     * <p>Driven by the channels that have a budget rather than by the channels that produced leads:
     * a channel Vision spent on and got nothing from is the single most useful row in the table,
     * and a lead-driven join would silently drop it.
     */
    private List<ReportPayload.SourceRow> sources(UUID orgId, YearMonth month, Instant from, Instant to) {
        Map<String, LeadMetrics.SourceCounts> counts = new HashMap<>();
        leadMetrics.countsBySource(orgId, from, to).forEach(row -> counts.put(row.channelCode(), row));

        return growthFinance.spendByChannel(orgId, month.atDay(1)).stream()
                .map(spend -> {
                    LeadMetrics.SourceCounts row = counts.getOrDefault(
                            spend.channelCode(),
                            new LeadMetrics.SourceCounts(spend.channelCode(), 0L, 0L, 0L));
                    return new ReportPayload.SourceRow(
                            spend.channelCode(),
                            spend.displayName(),
                            row.leads(),
                            row.qualified(),
                            row.booked(),
                            spend.actual(),
                            spend.actual().dividedBy(row.booked()));
                })
                .toList();
    }

    /** Null rather than zero when there is no denominator: the UI renders an em dash. */
    private static Double percentage(long numerator, long denominator) {
        if (denominator == 0L) {
            return null;
        }
        return Math.round(numerator * 1000.0d / denominator) / 10.0d;
    }
}
