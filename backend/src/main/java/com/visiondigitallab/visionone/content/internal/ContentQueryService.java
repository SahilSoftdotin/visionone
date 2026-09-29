package com.visiondigitallab.visionone.content.internal;

import com.visiondigitallab.visionone.content.api.ContentListResponse;
import com.visiondigitallab.visionone.content.domain.ContentItem;
import com.visiondigitallab.visionone.content.domain.ContentStatus;
import com.visiondigitallab.visionone.content.repository.ContentItemRepository;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContentQueryService {

    private final ContentItemRepository contentItems;

    public ContentQueryService(ContentItemRepository contentItems) {
        this.contentItems = contentItems;
    }

    public ContentListResponse list(OrganizationContext context) {
        List<ContentItem> all =
                contentItems.findByOrganizationIdOrderByCreatedAtDesc(context.organizationId());

        ContentListResponse.Summary summary = new ContentListResponse.Summary(
                count(all, ContentStatus.CLIENT_REVIEW),
                count(all, ContentStatus.CHANGES_REQUESTED),
                count(all, ContentStatus.APPROVED),
                count(all, ContentStatus.PUBLISHED));

        // Vision operates, the client decides: exactly one of these is true for any caller.
        return new ContentListResponse(
                summary,
                all.stream().map(item -> toRow(item, context.isVisionAdmin())).toList(),
                !context.isVisionAdmin(),
                context.isVisionAdmin());
    }

    private static long count(List<ContentItem> items, ContentStatus status) {
        return items.stream().filter(item -> item.getStatus() == status).count();
    }

    private static ContentListResponse.ContentItemRow toRow(ContentItem item, boolean visionAdmin) {
        return new ContentListResponse.ContentItemRow(
                item.getId(),
                item.getTitle(),
                item.getSummary(),
                item.getContentType().name(),
                item.getStatus().name(),
                item.getAuthorName(),
                item.getDraftUrl(),
                item.getPublishedUrl(),
                item.getPublishedAt() == null ? null : item.getPublishedAt().toString(),
                item.getClientFeedback(),
                item.awaitingClient(),
                visionAdmin
                        ? item.getStatus().visionCanMoveTo().stream()
                                .sorted() // declaration order, i.e. the pipeline's own order
                                .map(ContentStatus::name)
                                .toList()
                        : List.of());
    }
}
