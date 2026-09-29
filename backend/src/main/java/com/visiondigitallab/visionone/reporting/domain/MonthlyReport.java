package com.visiondigitallab.visionone.reporting.domain;

import com.visiondigitallab.visionone.common.ConflictException;
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

/**
 * One month, one report.
 *
 * <p>{@code payloadJson} is the point of this table. Everything in it can be recomputed from the
 * other modules right up until the report is generated - and must not be recomputed afterwards. A
 * report Gary read in August should still say in December what it said in August, even though the
 * underlying rows have since been corrected, backfilled or re-attributed. Freezing is what makes
 * "here is what we told you" a checkable claim rather than a memory.
 */
@Entity
@Table(name = "monthly_report")
public class MonthlyReport {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Always the first of the month; the database enforces it too. */
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.DRAFT;

    @Column(name = "key_learning")
    private String keyLearning;

    /** One action per line. Newlines rather than a JSON array: Vision writes these in a textarea. */
    @Column(name = "next_actions")
    private String nextActions;

    @Column(name = "decisions_required")
    private String decisionsRequired;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    protected MonthlyReport() {}

    public MonthlyReport(UUID id, UUID organizationId, LocalDate periodMonth) {
        this.id = id;
        this.organizationId = organizationId;
        this.periodMonth = periodMonth.withDayOfMonth(1);
    }

    /** Vision's narrative. Editable while the report is not yet shared. */
    public void writeNarrative(String keyLearning, String nextActions, String decisionsRequired) {
        requireNotShared();
        this.keyLearning = keyLearning;
        this.nextActions = nextActions;
        this.decisionsRequired = decisionsRequired;
    }

    /**
     * Freezes the figures.
     *
     * <p>Re-generating a GENERATED report is allowed and overwrites the payload: a report that has
     * not been shared has not been relied on. Re-generating a SHARED one is refused.
     */
    public void freeze(String payloadJson, Instant at) {
        requireNotShared();
        this.payloadJson = payloadJson;
        this.generatedAt = at;
        this.status = ReportStatus.GENERATED;
    }

    /**
     * Makes the report visible to the practice, permanently.
     *
     * <p>Two preconditions, both enforced here rather than by the screen: the figures must be
     * frozen, and the key learning must be written. A report of figures with no reading of them is
     * a data dump, and the reading is what the practice is paying for.
     */
    public void share() {
        if (!status.canMoveTo(ReportStatus.SHARED)) {
            throw new ConflictException(
                    "A report must be generated before it can be shared; this one is " + status);
        }
        if (keyLearning == null || keyLearning.isBlank()) {
            throw new ConflictException("Write the key learning before sharing this report");
        }
        this.status = ReportStatus.SHARED;
    }

    private void requireNotShared() {
        if (status == ReportStatus.SHARED) {
            throw new ConflictException(
                    "This report has already been shared with the client and cannot be changed");
        }
    }

    /** True once the figures are frozen. A DRAFT still shows live numbers. */
    public boolean isFrozen() {
        return generatedAt != null && !"{}".equals(payloadJson);
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

    public ReportStatus getStatus() {
        return status;
    }

    public String getKeyLearning() {
        return keyLearning;
    }

    public String getNextActions() {
        return nextActions;
    }

    public String getDecisionsRequired() {
        return decisionsRequired;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }
}
