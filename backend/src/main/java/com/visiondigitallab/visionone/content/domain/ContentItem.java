package com.visiondigitallab.visionone.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A blog post, social post, video or page, on its way to being published.
 *
 * <p>The client's two decisions live here and nowhere else: approve, or request changes. Both are
 * refused unless the item is actually waiting on them, so an approval always means someone chose
 * to give one.
 */
@Entity
@Table(name = "content_item")
public class ContentItem {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String title;

    /** One line, so the client knows what they are approving without opening the draft. */
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status = ContentStatus.IDEA;

    @Column(name = "channel_source_id")
    private UUID channelSourceId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "draft_url")
    private String draftUrl;

    @Column(name = "published_url")
    private String publishedUrl;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "client_feedback")
    private String clientFeedback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ContentItem() {}

    public ContentItem(UUID id, UUID organizationId, String title, ContentType contentType,
            String authorName) {
        this.id = id;
        this.organizationId = organizationId;
        this.title = title;
        this.contentType = contentType;
        this.authorName = authorName;
    }

    /** True only while the item is genuinely waiting on the practice. */
    public boolean awaitingClient() {
        return status == ContentStatus.CLIENT_REVIEW;
    }

    public void approve() {
        requireAwaitingClient("approve");
        this.status = ContentStatus.APPROVED;
        this.clientFeedback = null;
        this.updatedAt = Instant.now();
    }

    public void requestChanges(String feedback) {
        requireAwaitingClient("request changes on");
        this.status = ContentStatus.CHANGES_REQUESTED;
        this.clientFeedback = feedback;
        this.updatedAt = Instant.now();
    }

    private void requireAwaitingClient(String action) {
        if (!awaitingClient()) {
            throw new IllegalArgumentException(
                    "Cannot " + action + " content that is " + status
                            + "; only items in CLIENT_REVIEW are waiting on the practice");
        }
    }

    /**
     * Vision moving the item through its own stages.
     *
     * <p>The allowed set lives on {@link ContentStatus}, so the rule holds however the call
     * arrives. Notably PUBLISHED is reachable only from APPROVED: blocking Vision from setting
     * APPROVED is worthless if Vision can publish without one.
     */
    public void moveTo(ContentStatus next) {
        if (next == this.status) {
            return;
        }
        if (!this.status.visionCanMoveTo().contains(next)) {
            throw new IllegalArgumentException(
                    "Vision cannot move content from " + this.status + " to " + next
                            + (next == ContentStatus.APPROVED || next == ContentStatus.CHANGES_REQUESTED
                                    ? "; that is the client's decision"
                                    : "; allowed from here: " + this.status.visionCanMoveTo()));
        }
        this.status = next;
        this.updatedAt = Instant.now();
        if (next == ContentStatus.PUBLISHED && this.publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    public void edit(String title, String summary, ContentType contentType, String authorName,
            String draftUrl, String publishedUrl) {
        this.title = title;
        this.summary = summary;
        this.contentType = contentType;
        this.authorName = authorName;
        this.draftUrl = draftUrl;
        this.publishedUrl = publishedUrl;
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

    public String getSummary() {
        return summary;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public UUID getChannelSourceId() {
        return channelSourceId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getDraftUrl() {
        return draftUrl;
    }

    public String getPublishedUrl() {
        return publishedUrl;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getClientFeedback() {
        return clientFeedback;
    }
}
