package com.visiondigitallab.visionone.eventing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A webhook that arrived, recorded before anything is done about it.
 *
 * <p>The mirror of {@link OutboxEvent}. Healthie retries a slow delivery for three days and then
 * disables the webhook, so the endpoint writes this row and returns 200 without fetching or
 * joining anything; the poller does the real work afterwards.
 */
@Entity
@Table(name = "inbox_event")
public class InboxEvent {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboxSource source;

    /** The provider's own event id. Unique per source, which is what rejects a redelivery. */
    @Column(name = "external_event_id", nullable = false)
    private String externalEventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "resource_type")
    private String resourceType;

    /** Null until the poller works out which tenant the resource belongs to. */
    @Column(name = "organization_id")
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson = "{}";

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    protected InboxEvent() {}

    public InboxEvent(UUID id, InboxSource source, String externalEventId, String eventType,
            String resourceId, String resourceType, String payloadJson, boolean signatureVerified) {
        this.id = id;
        this.source = source;
        this.externalEventId = externalEventId;
        this.eventType = eventType;
        this.resourceId = resourceId;
        this.resourceType = resourceType;
        this.payloadJson = payloadJson == null ? "{}" : payloadJson;
        this.signatureVerified = signatureVerified;
    }

    public void markProcessed(UUID organizationId) {
        this.organizationId = organizationId;
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts += 1;
        this.lastError = error != null && error.length() > 2000 ? error.substring(0, 2000) : error;
    }

    public UUID getId() {
        return id;
    }

    public InboxSource getSource() {
        return source;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public boolean isSignatureVerified() {
        return signatureVerified;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
