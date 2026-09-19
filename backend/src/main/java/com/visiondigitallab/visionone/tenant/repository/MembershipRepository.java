package com.visiondigitallab.visionone.tenant.repository;

import com.visiondigitallab.visionone.tenant.domain.Membership;
import com.visiondigitallab.visionone.tenant.domain.MembershipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByOrganizationIdAndKeycloakUserIdAndStatus(
            UUID organizationId, String keycloakUserId, MembershipStatus status);

    List<Membership> findByKeycloakUserIdAndStatus(String keycloakUserId, MembershipStatus status);

    List<Membership> findByOrganizationIdAndStatus(UUID organizationId, MembershipStatus status);
}
