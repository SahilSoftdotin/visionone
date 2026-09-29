package com.visiondigitallab.visionone.content.api;

import java.util.List;
import java.util.UUID;

/** The content panel, and what the caller is allowed to do with it. */
public record ContentListResponse(
        Summary summary, List<ContentItemRow> items, boolean canDecide, boolean editable) {

    public record Summary(long awaitingClient, long changesRequested, long approved, long published) {}

    public record ContentItemRow(
            UUID id,
            String title,
            String summary,
            String contentType,
            String status,
            String author,
            String draftUrl,
            String publishedUrl,
            String publishedAt,
            String clientFeedback,
            /** True only while this item is waiting on the practice. */
            boolean awaitingClient,
            /**
             * The statuses Vision may move this item to next, from the transition table on
             * ContentStatus. Empty for the practice. Sent rather than restated in the browser so
             * the approval rule lives in exactly one place.
             */
            List<String> nextStatuses) {}
}
