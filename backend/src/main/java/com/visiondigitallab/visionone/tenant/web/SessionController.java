package com.visiondigitallab.visionone.tenant.web;

import com.visiondigitallab.visionone.auth.CurrentUser;
import com.visiondigitallab.visionone.auth.Role;
import com.visiondigitallab.visionone.tenant.api.OrganizationDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the frontend needs immediately after login: who the caller is and which organizations
 * they can open. Not tenant-scoped, so it sits outside the {@code /orgs/{orgId}} tree.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final CurrentUser currentUser;
    private final OrganizationDirectory directory;

    public SessionController(CurrentUser currentUser, OrganizationDirectory directory) {
        this.currentUser = currentUser;
        this.directory = directory;
    }

    @GetMapping("/me")
    public SessionResponse me() {
        String subject = currentUser.subject();
        List<OrganizationMembership> orgs = directory.forSubject(subject).stream()
                .map(SessionController::toOrganizationMembership)
                .toList();
        return new SessionResponse(subject, currentUser.displayName(), orgs);
    }

    /** Unauthenticated: lets the SPA discover the identity provider before it has a token. */
    @GetMapping("/meta")
    public MetaResponse meta() {
        return new MetaResponse("VisionOne", "v1", "phase-1");
    }

    private static OrganizationMembership toOrganizationMembership(
            OrganizationDirectory.OrganizationMembershipSummary membership) {
        OrganizationDirectory.OrganizationSummary summary = membership.organization();
        return new OrganizationMembership(
                summary.id(), summary.name(), summary.slug(), summary.timezone(),
                summary.currency(), membership.role());
    }

    public record SessionResponse(
            String subject, String displayName, List<OrganizationMembership> organizations) {}

    public record OrganizationMembership(
            UUID id, String name, String slug, String timezone, String currency, Role role) {}

    public record MetaResponse(String product, String apiVersion, String phase) {}
}
