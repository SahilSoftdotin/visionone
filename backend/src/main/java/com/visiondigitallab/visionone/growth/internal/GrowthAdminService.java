package com.visiondigitallab.visionone.growth.internal;

import com.visiondigitallab.visionone.auth.CurrentUser;
import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.eventing.api.DomainEventPublisher;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.growth.api.AllocationUpdateRequest;
import com.visiondigitallab.visionone.growth.api.GrowthPlanUpdateRequest;
import com.visiondigitallab.visionone.growth.domain.BudgetAllocation;
import com.visiondigitallab.visionone.growth.domain.ChannelSource;
import com.visiondigitallab.visionone.growth.domain.GrowthPlan;
import com.visiondigitallab.visionone.growth.repository.BudgetAllocationRepository;
import com.visiondigitallab.visionone.growth.repository.ChannelSourceRepository;
import com.visiondigitallab.visionone.growth.repository.GrowthPlanRepository;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vision Admin's write path for the monthly budget.
 *
 * <p>This is the service behind the Week-2 promise: Vision changes a number and the client's
 * Overview and Growth screens show it. Every change publishes {@code BudgetUpdated} through the
 * outbox in the same transaction, so reporting and audit cannot miss it.
 */
@Service
public class GrowthAdminService {

    private static final String AGGREGATE = "GrowthPlan";

    private final GrowthPlanRepository plans;
    private final BudgetAllocationRepository allocations;
    private final ChannelSourceRepository channels;
    private final DomainEventPublisher events;
    private final CurrentUser currentUser;

    public GrowthAdminService(
            GrowthPlanRepository plans,
            BudgetAllocationRepository allocations,
            ChannelSourceRepository channels,
            DomainEventPublisher events,
            CurrentUser currentUser) {
        this.plans = plans;
        this.allocations = allocations;
        this.channels = channels;
        this.events = events;
        this.currentUser = currentUser;
    }

    /** Creates the month's plan if it does not exist yet, so an admin never has to seed one first. */
    @Transactional
    public UUID upsertPlan(OrganizationContext context, YearMonth month, GrowthPlanUpdateRequest request) {
        LocalDate periodMonth = month.atDay(1);
        GrowthPlan plan = plans
                .findByOrganizationIdAndPeriodMonth(context.organizationId(), periodMonth)
                .orElseGet(() -> plans.save(new GrowthPlan(
                        UUID.randomUUID(),
                        context.organizationId(),
                        periodMonth,
                        0L,
                        context.currency())));

        plan.changePlannedTotal(request.plannedTotalMinor());
        plan.changeNotes(request.notes());
        plans.save(plan);

        publishBudgetUpdated(context, plan, "PLAN_TOTAL_CHANGED");
        return plan.getId();
    }

    @Transactional
    public void replaceAllocations(
            OrganizationContext context, YearMonth month, AllocationUpdateRequest request) {
        LocalDate periodMonth = month.atDay(1);
        GrowthPlan plan = plans
                .findByOrganizationIdAndPeriodMonth(context.organizationId(), periodMonth)
                .orElseThrow(() -> new NotFoundException(
                        "No growth plan exists for " + month + "; set the monthly budget first"));

        Map<UUID, ChannelSource> known = channels
                .findByOrganizationIdAndActiveOrderBySortOrder(context.organizationId(), true)
                .stream()
                .collect(Collectors.toMap(ChannelSource::getId, Function.identity()));

        for (AllocationUpdateRequest.Allocation incoming : request.allocations()) {
            // A channel id from another organization must not be writable here.
            if (!known.containsKey(incoming.channelSourceId())) {
                throw new NotFoundException("ChannelSource", incoming.channelSourceId());
            }
            BudgetAllocation allocation = allocations
                    .findByOrganizationIdAndGrowthPlanIdAndChannelSourceId(
                            context.organizationId(), plan.getId(), incoming.channelSourceId())
                    .orElseGet(() -> new BudgetAllocation(
                            UUID.randomUUID(),
                            context.organizationId(),
                            plan.getId(),
                            incoming.channelSourceId()));
            allocation.change(incoming.plannedMinor(), incoming.actualMinor());
            allocations.save(allocation);
        }

        publishBudgetUpdated(context, plan, "ALLOCATIONS_CHANGED");
    }

    private void publishBudgetUpdated(OrganizationContext context, GrowthPlan plan, String change) {
        events.publish(
                EventType.BUDGET_UPDATED,
                context.organizationId(),
                AGGREGATE,
                plan.getId(),
                new BudgetUpdatedPayload(
                        plan.getId(),
                        plan.getPeriodMonth().toString(),
                        plan.getPlannedTotalMinor(),
                        plan.getCurrency(),
                        change,
                        currentUser.displayName()));
    }

    /** Versioned by the envelope; add fields, never repurpose them. */
    public record BudgetUpdatedPayload(
            UUID growthPlanId,
            String periodMonth,
            long plannedTotalMinor,
            String currency,
            String change,
            String changedBy) {}
}
