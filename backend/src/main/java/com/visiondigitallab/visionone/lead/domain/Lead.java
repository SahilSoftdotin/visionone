package com.visiondigitallab.visionone.lead.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An enquiry.
 *
 * <p>No contact details are stored in clear: {@code contactHash} is a one-way hash used for
 * deduplication and, from Phase 2, for matching a booked appointment back to the lead that
 * produced it without VisionOne ever holding an email address.
 */
@Entity
@Table(name = "lead")
public class Lead {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Human-readable handle, assigned by the database sequence. */
    @Column(nullable = false, insertable = false, updatable = false)
    private String reference;

    @Column(name = "channel_source_id", nullable = false)
    private UUID channelSourceId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "contact_hash")
    private String contactHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_interest", nullable = false)
    private ServiceInterest serviceInterest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "owner_membership_id")
    private UUID ownerMembershipId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "booked_at")
    private Instant bookedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Lead() {}

    public Lead(UUID id, UUID organizationId, UUID channelSourceId, String displayName,
            ServiceInterest serviceInterest) {
        this.id = id;
        this.organizationId = organizationId;
        this.channelSourceId = channelSourceId;
        this.displayName = displayName;
        this.serviceInterest = serviceInterest;
    }

    /**
     * Moves the lead, recording the timestamps the response-time and booking metrics depend on.
     *
     * <p>First response is stamped once and never overwritten: it answers "how fast did we reply",
     * not "when did we last touch this".
     */
    public LeadStatus transitionTo(LeadStatus next, Instant when) {
        LeadStatus previous = this.status;
        if (previous == next) {
            return previous;
        }
        this.status = next;
        this.updatedAt = when;

        if (this.firstResponseAt == null && next != LeadStatus.DUPLICATE && next.rank() >= LeadStatus.CONTACTED.rank()) {
            this.firstResponseAt = when;
        }
        if (next.isBooked() && this.bookedAt == null) {
            this.bookedAt = when;
        }
        return previous;
    }

    public void reassign(UUID ownerMembershipId) {
        this.ownerMembershipId = ownerMembershipId;
        this.updatedAt = Instant.now();
    }

    public void correctDetails(String displayName, ServiceInterest serviceInterest, UUID campaignId) {
        this.displayName = displayName;
        this.serviceInterest = serviceInterest;
        this.campaignId = campaignId;
        this.updatedAt = Instant.now();
    }

    /** Minutes from creation to first response, or null when nobody has replied yet. */
    public Long responseMinutes() {
        if (firstResponseAt == null) {
            return null;
        }
        return Duration.between(createdAt, firstResponseAt).toMinutes();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getReference() {
        return reference;
    }

    public UUID getChannelSourceId() {
        return channelSourceId;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getContactHash() {
        return contactHash;
    }

    public ServiceInterest getServiceInterest() {
        return serviceInterest;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public UUID getOwnerMembershipId() {
        return ownerMembershipId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFirstResponseAt() {
        return firstResponseAt;
    }

    public Instant getBookedAt() {
        return bookedAt;
    }
}
