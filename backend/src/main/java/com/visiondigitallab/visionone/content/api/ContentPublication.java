package com.visiondigitallab.visionone.content.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The content module's read surface for other modules.
 *
 * <p>Reporting asks for what was published rather than querying {@code content_item} itself, which
 * is what keeps the module boundary real.
 */
public interface ContentPublication {

    List<PublishedContent> publishedIn(UUID organizationId, Instant from, Instant to);

    record PublishedContent(String title, String contentType, String publishedAt, String publishedUrl) {}
}
