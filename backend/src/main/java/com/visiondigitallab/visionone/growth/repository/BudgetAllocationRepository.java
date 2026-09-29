package com.visiondigitallab.visionone.growth.repository;

import com.visiondigitallab.visionone.growth.domain.BudgetAllocation;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetAllocationRepository
        extends JpaRepository<BudgetAllocation, UUID>, TenantScopedRepository {

    List<BudgetAllocation> findByOrganizationIdAndGrowthPlanId(UUID organizationId, UUID growthPlanId);

    Optional<BudgetAllocation> findByOrganizationIdAndGrowthPlanIdAndChannelSourceId(
            UUID organizationId, UUID growthPlanId, UUID channelSourceId);
}
