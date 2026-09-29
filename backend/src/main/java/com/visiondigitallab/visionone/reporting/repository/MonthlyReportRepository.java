package com.visiondigitallab.visionone.reporting.repository;

import com.visiondigitallab.visionone.reporting.domain.MonthlyReport;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReportRepository
        extends JpaRepository<MonthlyReport, UUID>, TenantScopedRepository {

    Optional<MonthlyReport> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<MonthlyReport> findByOrganizationIdAndPeriodMonth(UUID organizationId, LocalDate periodMonth);

    List<MonthlyReport> findByOrganizationIdOrderByPeriodMonthDesc(UUID organizationId);

    List<MonthlyReport> findByOrganizationIdAndPeriodMonthBetweenOrderByPeriodMonthDesc(
            UUID organizationId, LocalDate from, LocalDate to);
}
