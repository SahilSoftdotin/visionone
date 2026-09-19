package com.visiondigitallab.visionone.auth;

/**
 * Phase-1 roles.
 *
 * <p>Vision operates, the client decides. VISION_ADMIN performs the work and enters the data;
 * CLIENT_OWNER's only writes are approvals and decisions. Further roles arrive with a real need,
 * not in anticipation of one.
 */
public enum Role {
    VISION_ADMIN,
    CLIENT_OWNER;

    public String authority() {
        return "ROLE_" + name();
    }
}
