package com.visiondigitallab.visionone.lead.repository;

import com.visiondigitallab.visionone.lead.domain.Lead;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID>, TenantScopedRepository {

    /** Scoped by organization as well as id: a lead id from another tenant must read as absent. */
    Optional<Lead> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Lead> findByOrganizationIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID organizationId, Instant from, Instant to);
}
