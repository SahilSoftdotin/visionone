package com.visiondigitallab.visionone.content.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ContentWriteRequests {

    private ContentWriteRequests() {}

    public record UpsertContent(
            @NotBlank @Size(max = 240) String title,
            @Size(max = 500) String summary,
            @NotBlank String contentType,
            @NotBlank @Size(max = 160) String author,
            @Size(max = 500) String draftUrl,
            @Size(max = 500) String publishedUrl) {}

    public record MoveContent(@NotBlank String toStatus) {}

    /** The client asking for changes. Feedback is required: "no" without a reason is not useful. */
    public record RequestChanges(@NotBlank @Size(max = 4000) String feedback) {}
}
