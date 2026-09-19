package com.visiondigitallab.visionone.reporting.api;

import com.visiondigitallab.visionone.common.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the Overview screen needs, in one payload.
 *
 * <p>One call, not five: a dashboard that renders in pieces reads as broken. A null money or
 * ratio means "not a number for this period" and the UI shows an em dash, never a zero.
 */
public record OverviewResponse(
        String organizationName,
        LocalDate periodMonth,
        String currency,
        Kpis kpis,
        Funnel funnel,
        List<SourcePerformance> sourcePerformance,
        Investment investment,
        VisionActivity visionActivity,
        Recommendation recommendation) {

    /** Each figure carries the prior month so a tile can show direction without a second call. */
    public record Kpis(
            Money monthlyGrowthBudget,
            Money actualSpend,
            long newLeads,
            long qualifiedLeads,
            long bookedAppointments,
            Money costPerLead,
            Money costPerBookedAppointment,
            Double leadToBookConversionPercent,
            PriorMonth priorMonth) {}

    public record PriorMonth(
            Money actualSpend,
            long newLeads,
            long qualifiedLeads,
            long bookedAppointments,
            Double leadToBookConversionPercent) {}

    /** Four stages, each measurable from lead status history. No invented stages. */
    public record Funnel(long leads, long qualified, long appointmentRequested, long booked) {}

    public record SourcePerformance(
            String channelCode,
            String displayName,
            long leads,
            long qualified,
            long booked,
            Money spend,
            Money costPerLead,
            Money costPerBookedAppointment) {}

    public record Investment(Money planned, Money actual, Money remaining, double utilizationPercent) {}

    public record VisionActivity(long completed, long inProgress, long waitingForClient) {}

    /**
     * The month's recommendation. {@code expectedEffect} is a hypothesis and the UI labels it as
     * one in the markup itself. VisionOne never displays a guaranteed result.
     */
    public record Recommendation(
            java.util.UUID id,
            String observation,
            String proposedAction,
            String rationale,
            String expectedEffect,
            String decisionRequired,
            String status) {}
}
