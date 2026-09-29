package com.visiondigitallab.visionone.work.repository;

import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import com.visiondigitallab.visionone.work.domain.WorkItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID>, TenantScopedRepository {

    Optional<WorkItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<WorkItem> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
