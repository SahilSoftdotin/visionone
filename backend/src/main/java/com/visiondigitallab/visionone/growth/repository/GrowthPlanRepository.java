package com.visiondigitallab.visionone.growth.repository;

import com.visiondigitallab.visionone.growth.domain.GrowthPlan;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrowthPlanRepository extends JpaRepository<GrowthPlan, UUID>, TenantScopedRepository {

    Optional<GrowthPlan> findByOrganizationIdAndPeriodMonth(UUID organizationId, LocalDate periodMonth);
}
