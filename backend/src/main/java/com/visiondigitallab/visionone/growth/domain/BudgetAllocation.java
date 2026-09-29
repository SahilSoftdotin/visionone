package com.visiondigitallab.visionone.growth.domain;

import com.visiondigitallab.visionone.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Planned and actual spend for one channel in one month.
 *
 * <p>Actual spend is entered by Vision Digital Lab in Phase 1 and replaced by the
 * AdvertisingProvider in Phase 2. Both write the same column, so no screen changes when that
 * switch happens.
 */
@Entity
@Table(name = "budget_allocation")
public class BudgetAllocation {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "growth_plan_id", nullable = false)
    private UUID growthPlanId;

    @Column(name = "channel_source_id", nullable = false)
    private UUID channelSourceId;

    @Column(name = "planned_minor", nullable = false)
    private long plannedMinor;

    @Column(name = "actual_minor", nullable = false)
    private long actualMinor;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BudgetAllocation() {}

    public BudgetAllocation(UUID id, UUID organizationId, UUID growthPlanId, UUID channelSourceId) {
        this.id = id;
        this.organizationId = organizationId;
        this.growthPlanId = growthPlanId;
        this.channelSourceId = channelSourceId;
    }

    public void change(long plannedMinor, long actualMinor) {
        if (plannedMinor < 0 || actualMinor < 0) {
            throw new IllegalArgumentException("Spend figures cannot be negative");
        }
        this.plannedMinor = plannedMinor;
        this.actualMinor = actualMinor;
        this.updatedAt = Instant.now();
    }

    public Money planned(String currency) {
        return Money.of(plannedMinor, currency);
    }

    public Money actual(String currency) {
        return Money.of(actualMinor, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getGrowthPlanId() {
        return growthPlanId;
    }

    public UUID getChannelSourceId() {
        return channelSourceId;
    }

    public long getPlannedMinor() {
        return plannedMinor;
    }

    public long getActualMinor() {
        return actualMinor;
    }
}
