package com.visiondigitallab.visionone.work.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A piece of work Vision Digital Lab is doing for the practice.
 *
 * <p>{@code businessReason} is required, not optional. A practice owner reads why before what, and
 * a work item that cannot say why it exists probably should not.
 */
@Entity
@Table(name = "work_item")
public class WorkItem {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkStatus status = WorkStatus.PLANNED;

    @Column(name = "business_reason", nullable = false)
    private String businessReason;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "client_dependency", nullable = false)
    private boolean clientDependency;

    /** What the client is told. Deliberately separate from any internal note. */
    @Column(name = "client_visible_update")
    private String clientVisibleUpdate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WorkItem() {}

    public WorkItem(UUID id, UUID organizationId, String title, WorkCategory category,
            String businessReason, String ownerName) {
        this.id = id;
        this.organizationId = organizationId;
        this.title = title;
        this.category = category;
        this.businessReason = businessReason;
        this.ownerName = ownerName;
    }

    /**
     * Moves the item.
     *
     * @return true when this transition completed the item, so the caller knows to publish
     */
    public boolean transitionTo(WorkStatus next) {
        if (this.status == next) {
            return false;
        }
        this.status = next;
        this.updatedAt = Instant.now();
        // WAITING_FOR_CLIENT is what the Overview counts as needing the client's attention.
        this.clientDependency = next == WorkStatus.WAITING_FOR_CLIENT;

        if (next == WorkStatus.COMPLETED) {
            this.completedAt = Instant.now();
            return true;
        }
        // Reopening clears the completion stamp, so "completed this month" stays truthful.
        this.completedAt = null;
        return false;
    }

    public void edit(String title, WorkCategory category, String businessReason, String ownerName,
            LocalDate targetDate, String clientVisibleUpdate) {
        this.title = title;
        this.category = category;
        this.businessReason = businessReason;
        this.ownerName = ownerName;
        this.targetDate = targetDate;
        this.clientVisibleUpdate = clientVisibleUpdate;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getTitle() {
        return title;
    }

    public WorkCategory getCategory() {
        return category;
    }

    public WorkStatus getStatus() {
        return status;
    }

    public String getBusinessReason() {
        return businessReason;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public boolean isClientDependency() {
        return clientDependency;
    }

    public String getClientVisibleUpdate() {
        return clientVisibleUpdate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
