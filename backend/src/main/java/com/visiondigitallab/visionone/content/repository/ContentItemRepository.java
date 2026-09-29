package com.visiondigitallab.visionone.content.repository;

import com.visiondigitallab.visionone.content.domain.ContentItem;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentItemRepository
        extends JpaRepository<ContentItem, UUID>, TenantScopedRepository {

    Optional<ContentItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<ContentItem> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
