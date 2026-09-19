package com.visiondigitallab.visionone.reporting.internal;

import com.visiondigitallab.visionone.appointment.api.AppointmentMetrics;
import com.visiondigitallab.visionone.common.Money;
import com.visiondigitallab.visionone.growth.api.GrowthFinance;
import com.visiondigitallab.visionone.lead.api.LeadMetrics;
import com.visiondigitallab.visionone.reporting.api.OverviewResponse;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import com.visiondigitallab.visionone.work.api.WorkActivity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the Overview from each module's read surface.
 *
 * <p>Reporting owns no operational tables. It asks the lead, appointment, growth and work modules
 * for their numbers and assembles them, which is what keeps the module boundaries honest.
 */
@Service
@Transactional(readOnly = true)
public class OverviewService {

    private final LeadMetrics leadMetrics;
    private final AppointmentMetrics appointmentMetrics;
    private final GrowthFinance growthFinance;
    private final WorkActivity workActivity;
    private final JdbcClient jdbc;

    public OverviewService(
            LeadMetrics leadMetrics,
            AppointmentMetrics appointmentMetrics,
            GrowthFinance growthFinance,
            WorkActivity workActivity,
            JdbcClient jdbc) {
        this.leadMetrics = leadMetrics;
        this.appointmentMetrics = appointmentMetrics;
        this.growthFinance = growthFinance;
        this.workActivity = workActivity;
        this.jdbc = jdbc;
    }

    public OverviewResponse build(OrganizationContext context, YearMonth month) {
        UUID orgId = context.organizationId();
        ZoneId zone = context.zoneId();
        String currency = context.currency();

        // Month boundaries are evaluated in the organization's timezone, not the server's:
        // a lead created at 11pm on the 31st belongs to that month in the client's reckoning.
        Instant from = startOf(month, zone);
        Instant to = startOf(month.plusMonths(1), zone);
        Instant priorFrom = startOf(month.minusMonths(1), zone);

        LeadMetrics.Funnel funnel = leadMetrics.funnel(orgId, from, to);
        long newLeads = leadMetrics.newLeadCount(orgId, from, to);
        long booked = appointmentMetrics.bookedCount(orgId, from, to);

        GrowthFinance.Investment investment = growthFinance
                .investmentFor(orgId, month.atDay(1))
                .orElseGet(() -> new GrowthFinance.Investment(Money.zero(currency), Money.zero(currency)));

        OverviewResponse.Kpis kpis = new OverviewResponse.Kpis(
                investment.planned(),
                investment.actual(),
                newLeads,
                funnel.qualified(),
                booked,
                investment.actual().dividedBy(newLeads),
                investment.actual().dividedBy(booked),
                percentage(booked, newLeads),
                priorMonth(orgId, zone, currency, priorFrom, from, month.minusMonths(1)));

        return new OverviewResponse(
                context.name(),
                month.atDay(1),
                currency,
                kpis,
                new OverviewResponse.Funnel(
                        funnel.leads(), funnel.qualified(), funnel.appointmentRequested(), funnel.booked()),
                sourcePerformance(orgId, month, from, to),
                new OverviewResponse.Investment(
                        investment.planned(),
                        investment.actual(),
                        investment.remaining(),
                        investment.utilizationPercent()),
                activity(orgId, from, to),
                currentRecommendation(orgId, month.atDay(1)).orElse(null));
    }

    private OverviewResponse.PriorMonth priorMonth(
            UUID orgId, ZoneId zone, String currency, Instant from, Instant to, YearMonth month) {
        LeadMetrics.Funnel funnel = leadMetrics.funnel(orgId, from, to);
        long newLeads = leadMetrics.newLeadCount(orgId, from, to);
        long booked = appointmentMetrics.bookedCount(orgId, from, to);
        Money actual = growthFinance
                .investmentFor(orgId, month.atDay(1))
                .map(GrowthFinance.Investment::actual)
                .orElseGet(() -> Money.zero(currency));
        return new OverviewResponse.PriorMonth(
                actual, newLeads, funnel.qualified(), booked, percentage(booked, newLeads));
    }

    private List<OverviewResponse.SourcePerformance> sourcePerformance(
            UUID orgId, YearMonth month, Instant from, Instant to) {
        Map<String, LeadMetrics.SourceCounts> counts = new HashMap<>();
        leadMetrics.countsBySource(orgId, from, to).forEach(row -> counts.put(row.channelCode(), row));

        return growthFinance.spendByChannel(orgId, month.atDay(1)).stream()
                .map(spend -> {
                    LeadMetrics.SourceCounts row = counts.getOrDefault(
                            spend.channelCode(),
                            new LeadMetrics.SourceCounts(spend.channelCode(), 0L, 0L, 0L));
                    return new OverviewResponse.SourcePerformance(
                            spend.channelCode(),
                            spend.displayName(),
                            row.leads(),
                            row.qualified(),
                            row.booked(),
                            spend.actual(),
                            spend.actual().dividedBy(row.leads()),
                            spend.actual().dividedBy(row.booked()));
                })
                .toList();
    }

    private OverviewResponse.VisionActivity activity(UUID orgId, Instant from, Instant to) {
        WorkActivity.ActivityCounts counts = workActivity.countsFor(orgId, from, to);
        return new OverviewResponse.VisionActivity(
                counts.completed(), counts.inProgress(), counts.waitingForClient());
    }

    private Optional<OverviewResponse.Recommendation> currentRecommendation(UUID orgId, LocalDate month) {
        return jdbc.sql("""
                select id, observation, proposed_action, rationale, expected_effect,
                       decision_required, status
                from recommendation
                where organization_id = :orgId and period_month = :month
                order by created_at desc
                limit 1
                """)
                .param("orgId", orgId)
                .param("month", java.sql.Date.valueOf(month))
                .query((rs, rowNum) -> new OverviewResponse.Recommendation(
                        rs.getObject("id", UUID.class),
                        rs.getString("observation"),
                        rs.getString("proposed_action"),
                        rs.getString("rationale"),
                        rs.getString("expected_effect"),
                        rs.getString("decision_required"),
                        rs.getString("status")))
                .optional();
    }

    /** Null rather than zero when there is no denominator: the UI renders an em dash. */
    private static Double percentage(long numerator, long denominator) {
        if (denominator == 0L) {
            return null;
        }
        return Math.round(numerator * 1000.0d / denominator) / 10.0d;
    }

    private static Instant startOf(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }
}
