package com.visiondigitallab.visionone.tenant.web;

import com.visiondigitallab.visionone.auth.CurrentUser;
import com.visiondigitallab.visionone.auth.Role;
import com.visiondigitallab.visionone.tenant.api.OrganizationDirectory;
import com.visiondigitallab.visionone.tenant.domain.Membership;
import com.visiondigitallab.visionone.tenant.domain.MembershipStatus;
import com.visiondigitallab.visionone.tenant.repository.MembershipRepository;
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
    private final MembershipRepository memberships;

    public SessionController(
            CurrentUser currentUser, OrganizationDirectory directory, MembershipRepository memberships) {
        this.currentUser = currentUser;
        this.directory = directory;
        this.memberships = memberships;
    }

    @GetMapping("/me")
    public SessionResponse me() {
        String subject = currentUser.subject();
        List<OrganizationMembership> orgs = memberships
                .findByKeycloakUserIdAndStatus(subject, MembershipStatus.ACTIVE)
                .stream()
                .map(this::toOrganizationMembership)
                .toList();
        return new SessionResponse(subject, currentUser.displayName(), orgs);
    }

    /** Unauthenticated: lets the SPA discover the identity provider before it has a token. */
    @GetMapping("/meta")
    public MetaResponse meta() {
        return new MetaResponse("VisionOne", "v1", "phase-1");
    }

    private OrganizationMembership toOrganizationMembership(Membership membership) {
        OrganizationDirectory.OrganizationSummary summary = directory.require(membership.getOrganizationId());
        return new OrganizationMembership(
                summary.id(), summary.name(), summary.slug(), summary.timezone(),
                summary.currency(), membership.getRole());
    }

    public record SessionResponse(
            String subject, String displayName, List<OrganizationMembership> organizations) {}

    public record OrganizationMembership(
            UUID id, String name, String slug, String timezone, String currency, Role role) {}

    public record MetaResponse(String product, String apiVersion, String phase) {}
}
