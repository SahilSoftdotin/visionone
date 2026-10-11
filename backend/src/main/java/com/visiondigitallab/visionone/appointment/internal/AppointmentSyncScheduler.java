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
    private final SyncHealthRecorder health;
    private final JdbcClient jdbc;
    private final int daysBack;
    private final int daysForward;

    public AppointmentSyncScheduler(
            SchedulingProvider provider,
            AppointmentSyncService sync,
            SyncHealthRecorder health,
            JdbcClient jdbc,
            @Value("${visionone.appointment.sync.days-back:45}") int daysBack,
            @Value("${visionone.appointment.sync.days-forward:90}") int daysForward) {
        this.provider = provider;
        this.sync = sync;
        this.health = health;
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
                    health.recordFailure(organizationId, provider.providerCode(), status,
                            "Provider reported " + status + "; sync skipped");
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

                // Nothing changed since the last pass: no upserts, no reconciliation, no writes
                // at all. Worth doing because a sync re-states the entire window every time -
                // 1,128 rows for one clinic - and almost every pass writes values identical to
                // the ones already there. The digest covers today's date too, so an unchanged
                // calendar still gets one full pass a day; that is what re-establishes the truth
                // as the window slides and bounds how long an out-of-band change can persist.
                String digest = digestOf(today, refs);
                if (digest.equals(storedDigest(organizationId))) {
                    // Debug rather than info: at five-minute polling this is 288 lines a day
                    // saying nothing happened. It exists so that "is the scheduler alive" is
                    // answerable from the logs and not only from last_checked_at.
                    log.debug("No calendar change for organization {}; skipping writes", organizationId);
                    health.recordSuccess(
                            organizationId,
                            provider.providerCode(),
                            "No change since last sync (" + refs.size() + " appointment(s) in window)");
                    continue;
                }

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
                storeDigest(organizationId, digest);
                health.recordSuccess(
                        organizationId,
                        provider.providerCode(),
                        "Synced " + applied + " appointment(s)"
                                + (cancelled > 0 ? ", cancelled " + cancelled + " no longer listed" : ""));
            } catch (SchedulingProvider.SchedulingUnavailableException e) {
                // One organization's upstream being down must not stop the others, and must not
                // look like a quiet week. Logged and left for the next pass.
                log.warn("Appointment sync failed for {}: {}", organizationId, e.getMessage());
                health.recordFailure(
                        organizationId, provider.providerCode(), ProviderStatus.ERROR, e.getMessage());
            } catch (RuntimeException e) {
                // Anything the adapter did not anticipate - a shape in the feed nobody expected, a
                // constraint violation on one row. Without this the exception escapes the loop,
                // every remaining organization is skipped, and no health is recorded for any of
                // them, so the failure is silent on screen as well as unhandled. Logged at error
                // rather than warn because, unlike an upstream outage, this one is a defect.
                log.error("Unexpected failure syncing appointments for {}", organizationId, e);
                health.recordFailure(organizationId, provider.providerCode(), ProviderStatus.ERROR,
                        "Unexpected " + e.getClass().getSimpleName() + " during sync");
            }
        }
    }

    /**
     * A fingerprint of everything this pass would write, plus the day it was taken.
     *
     * <p>Covers the mutable fields rather than just the identifiers, because an appointment can
     * change without changing its key - a provider that reports attendance moves a booking to
     * ATTENDED under the same reference, and skipping that would freeze the calendar.
     *
     * <p>The date is in the basis on purpose. Without it, a calendar that genuinely never changes
     * would never be re-written, and the sync window silently slides out from under it: rows age
     * past the trailing edge, and anything altered in the table by hand stays altered forever.
     * With it, the worst case is a day.
     */
    private String digestOf(LocalDate day, List<SchedulingProvider.AppointmentReference> refs) {
        StringBuilder basis = new StringBuilder(day.toString());
        for (SchedulingProvider.AppointmentReference ref : refs) {
            basis.append(' ').append(ref.externalRef())
                    .append('|').append(ref.startsAt())
                    .append('|').append(ref.durationMinutes())
                    .append('|').append(ref.status())
                    .append('|').append(ref.displayLabel())
                    .append('|').append(ref.serviceCategory());
        }
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(basis.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Null when nothing has been synced yet, which never equals a digest and so never skips. */
    private String storedDigest(UUID organizationId) {
        return jdbc.sql("""
                select content_digest from integration_connection
                 where organization_id = :orgId and provider_code = :providerCode
                """)
                .param("orgId", organizationId)
                .param("providerCode", provider.providerCode())
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /**
     * Written only after the rows are in. If the process dies mid-sync the digest is still the
     * old one, so the next pass redoes the work rather than assuming it finished.
     */
    private void storeDigest(UUID organizationId, String digest) {
        jdbc.sql("""
                update integration_connection set content_digest = :digest
                 where organization_id = :orgId and provider_code = :providerCode
                """)
                .param("digest", digest)
                .param("orgId", organizationId)
                .param("providerCode", provider.providerCode())
                .update();
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
