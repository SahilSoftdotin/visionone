package com.visiondigitallab.visionone.common;

import java.util.UUID;

/**
 * The caller is authenticated but has no active membership in the organization they addressed.
 *
 * <p>Deliberately a 403 and not an empty result: a client who mistypes an organization id should
 * be told they cannot see it, not shown a dashboard with nothing on it.
 */
public class AccessDeniedInOrganizationException extends RuntimeException {

    public AccessDeniedInOrganizationException(UUID organizationId) {
        super("No active membership in organization " + organizationId);
    }
}
