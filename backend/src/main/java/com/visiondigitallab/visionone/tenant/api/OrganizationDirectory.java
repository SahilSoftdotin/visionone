package com.visiondigitallab.visionone.tenant.api;

import com.visiondigitallab.visionone.auth.Role;
import java.util.List;
import java.util.UUID;

/** The tenant module's public surface. Other modules use this, never the repositories. */
public interface OrganizationDirectory {

    OrganizationSummary require(UUID organizationId);

    /**
     * Organizations the given Keycloak subject can reach, each with the role held there, for the
     * post-login landing decision. Returning the role here is what keeps controllers off the
     * membership entity.
     */
    List<OrganizationMembershipSummary> forSubject(String keycloakUserId);

    record OrganizationSummary(UUID id, String name, String slug, String timezone, String currency) {}

    record OrganizationMembershipSummary(OrganizationSummary organization, Role role) {}
}
