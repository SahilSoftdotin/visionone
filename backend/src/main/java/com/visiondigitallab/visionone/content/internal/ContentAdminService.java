package com.visiondigitallab.visionone.content.internal;

import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.content.api.ContentWriteRequests;
import com.visiondigitallab.visionone.content.domain.ContentItem;
import com.visiondigitallab.visionone.content.domain.ContentStatus;
import com.visiondigitallab.visionone.content.domain.ContentType;
import com.visiondigitallab.visionone.content.repository.ContentItemRepository;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vision Digital Lab authoring and moving content through its own stages. */
@Service
public class ContentAdminService {

    private final ContentItemRepository contentItems;

    public ContentAdminService(ContentItemRepository contentItems) {
        this.contentItems = contentItems;
    }

    @Transactional
    public UUID create(OrganizationContext context, ContentWriteRequests.UpsertContent request) {
        ContentItem item = new ContentItem(
                UUID.randomUUID(),
                context.organizationId(),
                request.title(),
                parseType(request.contentType()),
                request.author());
        item.edit(request.title(), request.summary(), parseType(request.contentType()),
                request.author(), request.draftUrl(), request.publishedUrl());
        contentItems.save(item);
        return item.getId();
    }

    @Transactional
    public void update(OrganizationContext context, UUID id, ContentWriteRequests.UpsertContent request) {
        ContentItem item = require(context, id);
        item.edit(request.title(), request.summary(), parseType(request.contentType()),
                request.author(), request.draftUrl(), request.publishedUrl());
        contentItems.save(item);
    }

    /**
     * Moves content between Vision's own stages.
     *
     * <p>Sending an item to CLIENT_REVIEW is how it lands in front of the practice. Vision cannot
     * set APPROVED or CHANGES_REQUESTED here: those are the client's to give, and letting Vision
     * record them would make every approval in the audit trail meaningless.
     */
    @Transactional
    public void move(OrganizationContext context, UUID id, ContentWriteRequests.MoveContent request) {
        ContentItem item = require(context, id);
        // The transition table on ContentStatus is the single place this rule lives.
        item.moveTo(parseStatus(request.toStatus()));
        contentItems.save(item);
    }

    private ContentItem require(OrganizationContext context, UUID id) {
        return contentItems
                .findByIdAndOrganizationId(id, context.organizationId())
                .orElseThrow(() -> new NotFoundException("ContentItem", id));
    }

    private static ContentType parseType(String raw) {
        try {
            return ContentType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown content type: " + raw);
        }
    }

    private static ContentStatus parseStatus(String raw) {
        try {
            return ContentStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown content status: " + raw);
        }
    }
}
