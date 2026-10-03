package com.visiondigitallab.visionone.appointment.internal;

import com.visiondigitallab.visionone.integration.api.SchedulingProvider.AppointmentReference;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes what a scheduling provider reports into {@code appointment_reference}.
 *
 * <p><b>Upsert, not insert.</b> Keyed on {@code (organization_id, external_ref)}, which V13 made
 * unique for exactly this reason. Everything that writes here can run twice: a webhook retry, a
 * scheduled sync overlapping a webhook, or two syncs after a restart. Inserting would quietly
 * double every appointment on the Calendar.
 *
 * <p><b>Attribution is never overwritten.</b> {@code channel_source_id} is the one column on this
 * table VisionOne owns rather than mirrors - Vision sets it by hand on the Calendar, and Healthie
 * neither knows nor could know which advertisement produced a patient. So a sync writes it only
 * where it is currently null. The tradeoff, stated rather than discovered: a value set by an earlier
 * sync is then never corrected by a later one either. A human correction outranking a feed is worth
 * more than a feed being able to refresh its own guess.
 *
 * <p>Everything else - time, duration, status, label, category - belongs to the practice's system
 * and is replaced on every pass, because that system is the truth and this table is a mirror.
 */
@Service
public class AppointmentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentSyncService.class);

    private final JdbcClient jdbc;

    public AppointmentSyncService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return how many rows were written or updated */
    @Transactional
    public int apply(UUID organizationId, String providerCode, List<AppointmentReference> references) {
        int applied = 0;
        for (AppointmentReference ref : references) {
            applied += apply(organizationId, providerCode, ref);
        }
        if (applied > 0) {
            log.info("Synced {} appointment(s) for organization {}", applied, organizationId);
        }
        return applied;
    }

    @Transactional
    public int apply(UUID organizationId, String providerCode, AppointmentReference ref) {
        if (ref == null || ref.externalRef() == null) {
            return 0;
        }
        return jdbc.sql("""
                insert into appointment_reference
                    (id, organization_id, external_ref, requested_at, scheduled_for, status,
                     provider_code, display_label, duration_minutes, service_category,
                     channel_source_id)
                values
                    (:id, :orgId, :externalRef, :scheduledFor, :scheduledFor, :status,
                     :providerCode, :displayLabel, :durationMinutes, :serviceCategory,
                     (select cs.id from channel_source cs
                       where cs.organization_id = :orgId and cs.code = :channelCode))
                on conflict (organization_id, external_ref) where external_ref is not null
                do update set
                    scheduled_for    = excluded.scheduled_for,
                    status           = excluded.status,
                    display_label    = excluded.display_label,
                    duration_minutes = excluded.duration_minutes,
                    service_category = excluded.service_category,
                    provider_code    = excluded.provider_code,
                    -- Only fill attribution in, never replace it. See the class comment.
                    channel_source_id = coalesce(appointment_reference.channel_source_id,
                                                 excluded.channel_source_id)
                """)
                .param("id", UUID.randomUUID())
                .param("orgId", organizationId)
                .param("externalRef", ref.externalRef())
                .param("scheduledFor", ref.startsAt() == null ? null
                        : java.sql.Timestamp.from(ref.startsAt()))
                .param("status", ref.status() == null ? "BOOKED" : ref.status().name())
                .param("providerCode", providerCode == null ? "UNKNOWN" : providerCode)
                .param("displayLabel", ref.displayLabel())
                .param("durationMinutes", ref.durationMinutes() > 0 ? ref.durationMinutes() : 45)
                .param("serviceCategory", ref.serviceCategory())
                // Null is a legitimate answer and matches nothing, leaving the column null. A
                // channel code the provider invented that we do not have also matches nothing,
                // which is the right outcome: an unknown channel is unattributed, not a new one.
                .param("channelCode", ref.channelCode())
                .update();
    }

    /** Deletes nothing. See {@link #forget}. */
    @Transactional
    public int forget(UUID organizationId, String externalRef) {
        // A provider saying an appointment is gone is not a reason to lose the row: it is the only
        // record that the booking existed, and the month's booked count is built from history. So it
        // is marked cancelled, not deleted.
        return jdbc.sql("""
                update appointment_reference set status = 'CANCELLED'
                 where organization_id = :orgId and external_ref = :externalRef
                """)
                .param("orgId", organizationId)
                .param("externalRef", externalRef)
                .update();
    }
}
