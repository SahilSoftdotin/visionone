package com.visiondigitallab.visionone.lead.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of every stage a lead passed through.
 *
 * <p>This, not {@code lead.status}, is what the funnel counts. Keeping it append-only is what lets
 * the Overview say a lead "ever reached" a stage, and it is never updated in place.
 */
@Entity
@Table(name = "lead_status_history")
public class LeadStatusHistory {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private LeadStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private LeadStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    private String reason;

    protected LeadStatusHistory() {}

    public LeadStatusHistory(UUID id, UUID organizationId, UUID leadId, LeadStatus fromStatus,
            LeadStatus toStatus, String changedBy, String reason) {
        this.id = id;
        this.organizationId = organizationId;
        this.leadId = leadId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public LeadStatus getFromStatus() {
        return fromStatus;
    }

    public LeadStatus getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }
}
