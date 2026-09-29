package com.visiondigitallab.visionone.frontdesk.repository;

import com.visiondigitallab.visionone.frontdesk.domain.Call;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallRepository extends JpaRepository<Call, UUID>, TenantScopedRepository {

    List<Call> findByOrganizationIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
            UUID organizationId, Instant from, Instant to);
}
