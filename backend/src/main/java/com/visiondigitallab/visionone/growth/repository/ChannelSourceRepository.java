package com.visiondigitallab.visionone.growth.repository;

import com.visiondigitallab.visionone.growth.domain.ChannelSource;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelSourceRepository extends JpaRepository<ChannelSource, UUID>, TenantScopedRepository {

    List<ChannelSource> findByOrganizationIdAndActiveOrderBySortOrder(UUID organizationId, boolean active);

    Optional<ChannelSource> findByOrganizationIdAndCode(UUID organizationId, String code);
}
