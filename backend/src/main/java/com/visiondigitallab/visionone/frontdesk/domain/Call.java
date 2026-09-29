package com.visiondigitallab.visionone.frontdesk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A phone call, reduced to what a growth platform is allowed to know.
 *
 * <p>There is no recording, no transcript and no caller identity here. {@code callerLabel} arrives
 * already masked from the adapter: a full number that reaches this table has already escaped. The
 * screen answers how the phone performed, never what was said on it.
 */
@Entity
@Table(name = "call")
public class Call {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The provider's own id, which is what makes a repeated sync idempotent. */
    @Column(name = "external_ref")
    private String externalRef;

    @Column(nullable = false)
    private String direction;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallOutcome outcome;

    @Column(nullable = false)
    private boolean answered;

    /** Decided against the organization's timezone, never the server's. */
    @Column(name = "after_hours", nullable = false)
    private boolean afterHours;

    @Column(nullable = false)
    private boolean transferred;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "provider_code", nullable = false)
    private String providerCode = "DEMO";

    /** Masked by the adapter before it crossed the provider boundary. */
    @Column(name = "caller_label", nullable = false)
    private String callerLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "handled_by")
    private CallHandler handledBy;

    @Column(name = "channel_source_id")
    private UUID channelSourceId;

    protected Call() {}

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public String getDirection() {
        return direction;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public CallOutcome getOutcome() {
        return outcome;
    }

    public boolean isAnswered() {
        return answered;
    }

    public boolean isAfterHours() {
        return afterHours;
    }

    public boolean isTransferred() {
        return transferred;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getCallerLabel() {
        return callerLabel;
    }

    public CallHandler getHandledBy() {
        return handledBy;
    }

    public UUID getChannelSourceId() {
        return channelSourceId;
    }
}
