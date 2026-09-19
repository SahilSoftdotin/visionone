package com.visiondigitallab.visionone.tenant.domain;

import com.visiondigitallab.visionone.auth.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A person's access to one organization.
 *
 * <p>Membership is per organization, including for VISION_ADMIN. A Vision Admin with no
 * membership in an organization cannot read it - the role grants capability, the membership
 * grants reach.
 */
@Entity
@Table(name = "membership")
public class Membership {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "keycloak_user_id", nullable = false)
    private String keycloakUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Membership() {}

    public Membership(UUID id, UUID organizationId, String keycloakUserId, Role role, String displayName) {
        this.id = id;
        this.organizationId = organizationId;
        this.keycloakUserId = keycloakUserId;
        this.role = role;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public Role getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
