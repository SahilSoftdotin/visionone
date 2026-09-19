package com.visiondigitallab.visionone.tenant.api;

import java.util.List;
import java.util.UUID;

/** The tenant module's public surface. Other modules use this, never the repositories. */
public interface OrganizationDirectory {

    OrganizationSummary require(UUID organizationId);

    /** Organizations the given Keycloak subject can reach, for the post-login landing decision. */
    List<OrganizationSummary> forSubject(String keycloakUserId);

    record OrganizationSummary(UUID id, String name, String slug, String timezone, String currency) {}
}
