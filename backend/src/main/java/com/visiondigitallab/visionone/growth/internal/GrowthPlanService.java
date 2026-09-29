package com.visiondigitallab.visionone.growth.internal;

import com.visiondigitallab.visionone.common.Money;
import com.visiondigitallab.visionone.growth.api.GrowthPlanResponse;
import com.visiondigitallab.visionone.growth.domain.BudgetAllocation;
import com.visiondigitallab.visionone.growth.domain.ChannelSource;
import com.visiondigitallab.visionone.growth.domain.GrowthPlan;
import com.visiondigitallab.visionone.growth.domain.GrowthPlanStatus;
import com.visiondigitallab.visionone.growth.repository.BudgetAllocationRepository;
import com.visiondigitallab.visionone.growth.repository.ChannelSourceRepository;
import com.visiondigitallab.visionone.growth.repository.GrowthPlanRepository;
import com.visiondigitallab.visionone.lead.api.LeadMetrics;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GrowthPlanService {

    private final GrowthPlanRepository plans;
    private final BudgetAllocationRepository allocations;
    private final ChannelSourceRepository channels;
    private final LeadMetrics leadMetrics;
    private final JdbcClient jdbc;

    public GrowthPlanService(
            GrowthPlanRepository plans,
            BudgetAllocationRepository allocations,
            ChannelSourceRepository channels,
            LeadMetrics leadMetrics,
            JdbcClient jdbc) {
        this.plans = plans;
        this.allocations = allocations;
        this.channels = channels;
        this.leadMetrics = leadMetrics;
        this.jdbc = jdbc;
    }

    public GrowthPlanResponse read(OrganizationContext context, YearMonth month) {
        UUID orgId = context.organizationId();
        String currency = context.currency();
        LocalDate periodMonth = month.atDay(1);

        Optional<GrowthPlan> plan = plans.findByOrganizationIdAndPeriodMonth(orgId, periodMonth);

        Map<UUID, BudgetAllocation> byChannel = new HashMap<>();
        plan.ifPresent(p -> allocations
                .findByOrganizationIdAndGrowthPlanId(orgId, p.getId())
                .forEach(a -> byChannel.put(a.getChannelSourceId(), a)));

        // Month boundaries in the organization's timezone, never the server's.
        Instant from = periodMonth.atStartOfDay(context.zoneId()).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(context.zoneId()).toInstant();

        Map<String, LeadMetrics.SourceCounts> counts = new HashMap<>();
        leadMetrics.countsBySource(orgId, from, to).forEach(row -> counts.put(row.channelCode(), row));

        List<GrowthPlanResponse.ChannelAllocation> rows = channels
                .findByOrganizationIdAndActiveOrderBySortOrder(orgId, true)
                .stream()
                .map(channel -> toRow(channel, byChannel.get(channel.getId()), counts, currency))
                .toList();

        long plannedTotal = plan.map(GrowthPlan::getPlannedTotalMinor).orElse(0L);
        long actualTotal = rows.stream().mapToLong(row -> row.actual().amountMinor()).sum();
        Money planned = Money.of(plannedTotal, currency);
        Money actual = Money.of(actualTotal, currency);

        return new GrowthPlanResponse(
                periodMonth,
                currency,
                plan.map(GrowthPlan::getStatus).orElse(GrowthPlanStatus.DRAFT).name(),
                planned,
                actual,
                planned.minus(actual),
                utilization(plannedTotal, actualTotal),
                plan.map(GrowthPlan::getNotes).orElse(null),
                context.isVisionAdmin(),
                rows,
                campaigns(orgId, from, to));
    }

    private GrowthPlanResponse.ChannelAllocation toRow(
            ChannelSource channel,
            BudgetAllocation allocation,
            Map<String, LeadMetrics.SourceCounts> counts,
            String currency) {
        Money planned = allocation == null ? Money.zero(currency) : allocation.planned(currency);
        Money actual = allocation == null ? Money.zero(currency) : allocation.actual(currency);
        LeadMetrics.SourceCounts row = counts.getOrDefault(
                channel.getCode(), new LeadMetrics.SourceCounts(channel.getCode(), 0L, 0L, 0L));

        return new GrowthPlanResponse.ChannelAllocation(
                channel.getId(),
                channel.getCode(),
                channel.getDisplayName(),
                channel.getCategory(),
                planned,
                actual,
                planned.minus(actual),
                row.leads(),
                row.qualified(),
                row.booked(),
                actual.dividedBy(row.leads()),
                actual.dividedBy(row.booked()));
    }

    /**
     * Campaigns with the leads and bookings they produced.
     *
     * <p>Spend stays null: Phase 1 tracks budget per channel, not per campaign. Inventing a split
     * would be a fabricated number on a screen whose whole job is to be trusted.
     */
    private List<GrowthPlanResponse.CampaignRow> campaigns(UUID orgId, Instant from, Instant to) {
        return jdbc.sql("""
                select c.id, c.name, c.status, cs.code as channel_code,
                       count(l.id) as leads,
                       count(l.id) filter (where exists (
                           select 1 from lead_status_history h
                           where h.lead_id = l.id and h.to_status in ('BOOKED', 'ATTENDED'))) as booked
                from campaign c
                join channel_source cs on cs.id = c.channel_source_id
                left join lead l
                       on l.campaign_id = c.id
                      and l.organization_id = c.organization_id
                      and l.created_at >= :from and l.created_at < :to
                      and l.status <> 'DUPLICATE'
                where c.organization_id = :orgId
                group by c.id, c.name, c.status, cs.code
                order by c.name
                """)
                .param("orgId", orgId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, rowNum) -> new GrowthPlanResponse.CampaignRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("channel_code"),
                        rs.getString("status"),
                        null,
                        rs.getLong("leads"),
                        rs.getLong("booked")))
                .list();
    }

    /** Percentage of plan spent, one decimal place. A zero plan is zero, not a divide by zero. */
    private static double utilization(long plannedMinor, long actualMinor) {
        if (plannedMinor == 0L) {
            return 0.0d;
        }
        return Math.round(actualMinor * 1000.0d / plannedMinor) / 10.0d;
    }
}
