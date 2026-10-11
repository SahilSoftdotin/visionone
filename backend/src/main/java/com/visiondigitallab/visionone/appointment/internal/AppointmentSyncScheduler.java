package com.visiondigitallab.visionone.appointment.internal;

import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
                if (status == ProviderStatus.NOT_CONNECTED || status == ProviderStatus.NEEDS_AUTHORIZATION) {
                    // Nothing to try: no credential, or one the practice has not authorised.
                    // Retrying changes nothing until a human changes a setting.
                    log.warn("Skipping appointment sync for {}: provider reports {}", organizationId, status);
                    recordHealth(organizationId, status, "Provider reported " + status + "; sync skipped");
                    continue;
                }
                // ERROR deliberately does NOT skip. status() now reports the last exchange rather
                // than probing, so skipping on it would mean one bad response stops the sync
                // permanently - the provider could never report success again because nothing
                // would ever call it. Retrying IS the recovery, and attempting the real read is
                // also what produces a usable error message: a pre-flight probe that fails only
                // tells us "ERROR", while the call itself says the token may have been
                // regenerated. That message is what lands on the connection row below.
                LocalDate today = LocalDate.now();
                LocalDate from = today.minusDays(daysBack);
                LocalDate to = today.plusDays(daysForward);
                List<SchedulingProvider.AppointmentReference> refs =
                        provider.appointments(organizationId, from, to);
                int applied =
                        sync.apply(organizationId, provider.providerCode(), refs, provider.reportsAttendance());
                int cancelled = 0;

                if (provider.reconcilesByAbsence()) {
                    // Only after a successful read. An exception above skips this entirely, which
                    // is the point: "we could not ask" must never be allowed to mean "everything
                    // was cancelled".
                    cancelled = sync.cancelMissing(
                            organizationId,
                            provider.providerCode(),
                            from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                            to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                            refs.stream().map(SchedulingProvider.AppointmentReference::externalRef).toList());
                }
                recordHealth(
                        organizationId,
                        ProviderStatus.CONNECTED,
                        "Synced " + applied + " appointment(s)"
                                + (cancelled > 0 ? ", cancelled " + cancelled + " no longer listed" : ""));
            } catch (SchedulingProvider.SchedulingUnavailableException e) {
                // One organization's upstream being down must not stop the others, and must not
                // look like a quiet week. Logged and left for the next pass.
                log.warn("Appointment sync failed for {}: {}", organizationId, e.getMessage());
                recordHealth(organizationId, ProviderStatus.ERROR, e.getMessage());
            } catch (RuntimeException e) {
                // Anything the adapter did not anticipate - a shape in the feed nobody expected, a
                // constraint violation on one row. Without this the exception escapes the loop,
                // every remaining organization is skipped, and no health is recorded for any of
                // them, so the failure is silent on screen as well as unhandled. Logged at error
                // rather than warn because, unlike an upstream outage, this one is a defect.
                log.error("Unexpected failure syncing appointments for {}", organizationId, e);
                recordHealth(organizationId, ProviderStatus.ERROR,
                        "Unexpected " + e.getClass().getSimpleName() + " during sync");
            }
        }
    }

    /**
     * Records what this pass actually found, on the connection row the practice is shown.
     *
     * <p>Until this existed, {@code integration_connection} was written once by hand and never
     * again: the row said CONNECTED permanently, including after a month of failed syncs. The
     * columns for this - {@code status}, {@code last_checked_at}, {@code detail} - have been in
     * the schema since V7 and nothing ever filled them. That made the one genuinely dangerous
     * failure here invisible, because it is a silence rather than an error: a regenerated token
     * stops the calendar updating, and nothing on screen changes to say so.
     *
     * <p>{@code last_checked_at} matters as much as the status. "Connected, last checked four
     * days ago" is a different problem from "connected, last checked a minute ago", and without a
     * timestamp both render identically.
     *
     * <p>Never throws. A failure to write a health row must not be able to fail a sync that
     * worked, nor mask the original exception in the catch block that calls this.
     */
    private void recordHealth(UUID organizationId, ProviderStatus status, String detail) {
        try {
            jdbc.sql("""
                    update integration_connection
                       set status = :status, last_checked_at = now(), detail = :detail
                     where organization_id = :orgId and provider_code = :providerCode
                    """)
                    .param("status", status.name())
                    .param("orgId", organizationId)
                    .param("providerCode", provider.providerCode())
                    // The column is varchar(500). An adapter's message is already redacted of any
                    // credential before it gets here; this only guards the length.
                    .param("detail", detail == null ? null
                            : detail.substring(0, Math.min(detail.length(), 500)))
                    .update();
        } catch (RuntimeException e) {
            log.warn("Could not record sync health for organization {}: {}", organizationId, e.getMessage());
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
