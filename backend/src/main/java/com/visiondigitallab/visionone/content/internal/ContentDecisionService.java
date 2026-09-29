package com.visiondigitallab.visionone.content.internal;

import com.visiondigitallab.visionone.auth.CurrentUser;
import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.content.domain.ContentItem;
import com.visiondigitallab.visionone.content.repository.ContentItemRepository;
import com.visiondigitallab.visionone.eventing.api.DomainEventPublisher;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The client's two decisions, and the only writes CLIENT_OWNER has in Phase 1.
 *
 * <p>Both are refused unless the item is in CLIENT_REVIEW. That check lives in the entity, so it
 * holds however the call arrives, and it means an approval in the audit trail always corresponds
 * to something that was genuinely waiting for one.
 */
@Service
public class ContentDecisionService {

    private static final String AGGREGATE = "ContentItem";

    private final ContentItemRepository contentItems;
    private final DomainEventPublisher events;
    private final CurrentUser currentUser;

    public ContentDecisionService(
            ContentItemRepository contentItems, DomainEventPublisher events, CurrentUser currentUser) {
        this.contentItems = contentItems;
        this.events = events;
        this.currentUser = currentUser;
    }

    @Transactional
    public void approve(OrganizationContext context, UUID id) {
        ContentItem item = require(context, id);
        item.approve();
        contentItems.save(item);

        events.publish(
                EventType.CONTENT_APPROVED,
                context.organizationId(),
                AGGREGATE,
                item.getId(),
                new ContentApprovedPayload(
                        item.getId(),
                        item.getTitle(),
                        item.getContentType().name(),
                        currentUser.displayName()));
    }

    /**
     * Requests changes.
     *
     * <p>No event: nothing downstream needs to know, and the feedback belongs to Vision's own
     * queue rather than to the reporting layer. An event nobody consumes is noise.
     */
    @Transactional
    public void requestChanges(OrganizationContext context, UUID id, String feedback) {
        ContentItem item = require(context, id);
        item.requestChanges(feedback);
        contentItems.save(item);
    }

    private ContentItem require(OrganizationContext context, UUID id) {
        return contentItems
                .findByIdAndOrganizationId(id, context.organizationId())
                .orElseThrow(() -> new NotFoundException("ContentItem", id));
    }

    public record ContentApprovedPayload(
            UUID contentItemId, String title, String contentType, String approvedBy) {}
}
