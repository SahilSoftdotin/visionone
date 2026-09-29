package com.visiondigitallab.visionone.appointment.repository;

import com.visiondigitallab.visionone.appointment.domain.AppointmentRef;
import com.visiondigitallab.visionone.tenant.api.TenantScopedRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRefRepository
        extends JpaRepository<AppointmentRef, UUID>, TenantScopedRepository {

    List<AppointmentRef>
            findByOrganizationIdAndScheduledForGreaterThanEqualAndScheduledForLessThanOrderByScheduledForAsc(
                    UUID organizationId, Instant from, Instant to);

    /** Organization-scoped by construction: an id alone must never reach another practice's row. */
    Optional<AppointmentRef> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
