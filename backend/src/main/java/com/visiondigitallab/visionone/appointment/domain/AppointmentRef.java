package com.visiondigitallab.visionone.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A pointer to an appointment that lives in the practice's scheduling system.
 *
 * <p>This is the PHI boundary made concrete. It holds an external id, a time, a duration, a
 * non-identifying label and a broad service category. There is no appointment reason, no note, no
 * diagnosis and no patient record id, because the point of a reference is that the clinical record
 * stays where it belongs. The class is named {@code AppointmentRef} to keep that in view.
 */
@Entity
@Table(name = "appointment_reference")
public class AppointmentRef {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** The scheduling system's own id, which is what makes a repeated sync idempotent. */
    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "call_id")
    private UUID callId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentState status;

    @Column(name = "provider_code", nullable = false)
    private String providerCode = "DEMO";

    /** A first name and an initial. Recognisable to a front desk, not identifying on a screen. */
    @Column(name = "display_label")
    private String displayLabel;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 45;

    /** One of the practice's broad commercial categories. Never a diagnosis. */
    @Column(name = "service_category")
    private String serviceCategory;

    @Column(name = "channel_source_id")
    private UUID channelSourceId;

    protected AppointmentRef() {}

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public UUID getCallId() {
        return callId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public AppointmentState getStatus() {
        return status;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getServiceCategory() {
        return serviceCategory;
    }

    public UUID getChannelSourceId() {
        return channelSourceId;
    }

    /**
     * Credit this booking to a marketing channel, or to none.
     *
     * <p>The only field on this entity VisionOne owns. Everything else mirrors the scheduling
     * system: when the appointment is, how long for, what it is called, whether it happened. Those
     * have no setter on purpose, so that "VisionOne cannot reschedule an appointment" is a fact
     * about this class rather than an omission somewhere in a service.
     *
     * <p>Null is a legitimate value and means the channel is unknown. Guessing here would credit a
     * channel that earned nothing, and the cost-per-booking figure is built on these.
     */
    public void attributeTo(UUID channelSourceId) {
        this.channelSourceId = channelSourceId;
    }
}
