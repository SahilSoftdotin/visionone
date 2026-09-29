package com.visiondigitallab.visionone.lead.repository;

import com.visiondigitallab.visionone.lead.domain.LeadStatusHistory;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadStatusHistoryRepository
        extends JpaRepository<LeadStatusHistory, UUID>, TenantScopedRepository {

    List<LeadStatusHistory> findByOrganizationIdAndLeadIdOrderByChangedAtAsc(
            UUID organizationId, UUID leadId);
}
