package com.visiondigitallab.visionone.tenant.api;

import com.visiondigitallab.visionone.auth.Role;
import java.time.ZoneId;
import java.util.UUID;

/**
 * The organization this request is operating on, resolved once and verified once.
 *
 * <p>Request-scoped. An event handler has no request and therefore no context: handlers read
 * the organization id from the event envelope and pass it explicitly.
 */
public record OrganizationContext(
        UUID organizationId, String name, ZoneId zoneId, String currency, Role role) {

    public boolean isVisionAdmin() {
        return role == Role.VISION_ADMIN;
    }
}
