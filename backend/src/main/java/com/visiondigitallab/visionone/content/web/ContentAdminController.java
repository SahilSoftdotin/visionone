package com.visiondigitallab.visionone.content.web;

import com.visiondigitallab.visionone.content.api.ContentListResponse;
import com.visiondigitallab.visionone.content.api.ContentWriteRequests;
import com.visiondigitallab.visionone.content.internal.ContentAdminService;
import com.visiondigitallab.visionone.content.internal.ContentQueryService;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/admin/content")
@PreAuthorize("hasRole('VISION_ADMIN')")
public class ContentAdminController {

    private final ContentAdminService adminService;
    private final ContentQueryService queryService;

    public ContentAdminController(ContentAdminService adminService, ContentQueryService queryService) {
        this.adminService = adminService;
        this.queryService = queryService;
    }

    @PostMapping
    public ContentListResponse create(
            @PathVariable UUID orgId, @Valid @RequestBody ContentWriteRequests.UpsertContent request) {
        var context = OrganizationContextHolder.require();
        adminService.create(context, request);
        return queryService.list(context);
    }

    @PutMapping("/{contentItemId}")
    public ContentListResponse update(
            @PathVariable UUID orgId,
            @PathVariable UUID contentItemId,
            @Valid @RequestBody ContentWriteRequests.UpsertContent request) {
        var context = OrganizationContextHolder.require();
        adminService.update(context, contentItemId, request);
        return queryService.list(context);
    }

    @PostMapping("/{contentItemId}/status")
    public ContentListResponse move(
            @PathVariable UUID orgId,
            @PathVariable UUID contentItemId,
            @Valid @RequestBody ContentWriteRequests.MoveContent request) {
        var context = OrganizationContextHolder.require();
        adminService.move(context, contentItemId, request);
        return queryService.list(context);
    }
}
