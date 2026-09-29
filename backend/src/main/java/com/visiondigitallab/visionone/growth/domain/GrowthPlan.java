package com.visiondigitallab.visionone.growth.domain;

import com.visiondigitallab.visionone.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One month's growth budget for one organization. */
@Entity
@Table(name = "growth_plan")
public class GrowthPlan {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Always the first of the month, in the organization's timezone. */
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "planned_total_minor", nullable = false)
    private long plannedTotalMinor;

    /** ISO 4217, stored as char(3) by the migration - same mapping as Organization.currency. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrowthPlanStatus status = GrowthPlanStatus.ACTIVE;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected GrowthPlan() {}

    public GrowthPlan(UUID id, UUID organizationId, LocalDate periodMonth, long plannedTotalMinor,
            String currency) {
        this.id = id;
        this.organizationId = organizationId;
        this.periodMonth = periodMonth.withDayOfMonth(1);
        this.plannedTotalMinor = plannedTotalMinor;
        this.currency = currency;
    }

    public void changePlannedTotal(long plannedTotalMinor) {
        if (plannedTotalMinor < 0) {
            throw new IllegalArgumentException("Planned budget cannot be negative");
        }
        this.plannedTotalMinor = plannedTotalMinor;
        this.updatedAt = Instant.now();
    }

    public void changeNotes(String notes) {
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public Money plannedTotal() {
        return Money.of(plannedTotalMinor, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public LocalDate getPeriodMonth() {
        return periodMonth;
    }

    public long getPlannedTotalMinor() {
        return plannedTotalMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public GrowthPlanStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
