package com.visiondigitallab.visionone.appointment.internal;

import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulls the appointment window from the scheduling provider on a timer.
 *
 * <p>This is not an alternative to the webhooks - it is the thing that makes them survivable.
 * Webhooks get lost: a deploy restarts the container mid-delivery, Healthie disables a subscription
 * after three days of failures, a notification arrives for a resource we could not fetch at the
 * time. Every one of those leaves the Calendar quietly wrong, and quietly wrong is the worst state
 * for a dashboard a client makes decisions from. A periodic re-read of the whole window converges
 * regardless of what was missed, because the sync upserts current state rather than applying diffs.
 *
 * <p>The window reaches backwards as well as forwards. Past appointments change - a booking becomes
 * attended or a no-show days later - and those transitions are what the booked-appointment count and
 * the conversion rate are built on.
 *
 * <p>Off unless a real provider is configured. Under the {@code demo} profile the adapter is
 * synthetic and the seed already owns the table, so a sync would fight the fixtures.
 */
@Component
@ConditionalOnProperty(name = "visionone.appointment.sync.enabled", havingValue = "true")
public class AppointmentSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentSyncScheduler.class);

    private final SchedulingProvider provider;
    private final AppointmentSyncService sync;
    private final JdbcClient jdbc;
    private final int daysBack;
    private final int daysForward;

    public AppointmentSyncScheduler(
            SchedulingProvider provider,
            AppointmentSyncService sync,
            JdbcClient jdbc,
            @Value("${visionone.appointment.sync.days-back:45}") int daysBack,
            @Value("${visionone.appointment.sync.days-forward:90}") int daysForward) {
        this.provider = provider;
        this.sync = sync;
        this.jdbc = jdbc;
        this.daysBack = daysBack;
        this.daysForward = daysForward;
    }

    /**
     * Hourly by default, matching the eventing drains. A marketing dashboard does not need a booking
     * within seconds, and the webhook already covers the case where someone is watching.
     */
    @Scheduled(fixedDelayString = "${visionone.appointment.sync.interval-ms:3600000}")
    public void sync() {
        for (UUID organizationId : connectedOrganizations()) {
            try {
                ProviderStatus status = provider.status(organizationId);
                if (status == ProviderStatus.ERROR || status == ProviderStatus.NEEDS_AUTHORIZATION) {
                    // Do not call a provider that has just told us it cannot answer, and do not
                    // write an empty window on top of real data because of it.
                    log.warn("Skipping appointment sync for {}: provider reports {}", organizationId, status);
                    continue;
                }
                LocalDate today = LocalDate.now();
                List<SchedulingProvider.AppointmentReference> refs = provider.appointments(
                        organizationId, today.minusDays(daysBack), today.plusDays(daysForward));
                sync.apply(organizationId, provider.providerCode(), refs, provider.reportsAttendance());
            } catch (SchedulingProvider.SchedulingUnavailableException e) {
                // One organization's upstream being down must not stop the others, and must not
                // look like a quiet week. Logged and left for the next pass.
                log.warn("Appointment sync failed for {}: {}", organizationId, e.getMessage());
            }
        }
    }

    private List<UUID> connectedOrganizations() {
        return jdbc.sql("""
                select organization_id from integration_connection
                 where provider_kind = 'SCHEDULING' and provider_code = :providerCode
                 order by organization_id
                """)
                .param("providerCode", provider.providerCode())
                .query(UUID.class)
                .list();
    }
}
