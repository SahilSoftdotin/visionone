package com.visiondigitallab.visionone.content.web;

import com.visiondigitallab.visionone.content.api.ContentListResponse;
import com.visiondigitallab.visionone.content.api.ContentWriteRequests;
import com.visiondigitallab.visionone.content.internal.ContentDecisionService;
import com.visiondigitallab.visionone.content.internal.ContentQueryService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content, and the client's two decisions.
 *
 * <p>Approve and request-changes are CLIENT_OWNER only, and deliberately not under {@code /admin}:
 * they are not administration, they are the practice exercising the one authority Phase 1 gives it.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/content")
public class ContentController {

    private final ContentQueryService queryService;
    private final ContentDecisionService decisionService;

    public ContentController(ContentQueryService queryService, ContentDecisionService decisionService) {
        this.queryService = queryService;
        this.decisionService = decisionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VISION_ADMIN', 'CLIENT_OWNER')")
    public ContentListResponse list(@PathVariable UUID orgId) {
        return queryService.list(OrganizationContextHolder.require());
    }

    @PostMapping("/{contentItemId}/approve")
    @PreAuthorize("hasRole('CLIENT_OWNER')")
    public ContentListResponse approve(@PathVariable UUID orgId, @PathVariable UUID contentItemId) {
        var context = OrganizationContextHolder.require();
        decisionService.approve(context, contentItemId);
        return queryService.list(context);
    }

    @PostMapping("/{contentItemId}/request-changes")
    @PreAuthorize("hasRole('CLIENT_OWNER')")
    public ContentListResponse requestChanges(
            @PathVariable UUID orgId,
            @PathVariable UUID contentItemId,
            @Valid @RequestBody ContentWriteRequests.RequestChanges request) {
        var context = OrganizationContextHolder.require();
        decisionService.requestChanges(context, contentItemId, request.feedback());
        return queryService.list(context);
    }
}
