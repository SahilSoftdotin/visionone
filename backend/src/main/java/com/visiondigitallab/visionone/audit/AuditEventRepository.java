package com.visiondigitallab.visionone.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    java.util.List<AuditEvent> findByOrganizationIdOrderByOccurredAtDesc(
            UUID organizationId, org.springframework.data.domain.Pageable pageable);
}
