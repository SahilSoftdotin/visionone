package com.visiondigitallab.visionone.appointment.internal;

import com.visiondigitallab.visionone.common.AlertMailer;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code integration_connection} honest, and tells someone when it stops being green.
 *
 * <p><b>The failure this exists for is a silence.</b> If the export token is regenerated, every
 * sync fails, the calendar stops updating, and absolutely nothing changes on screen. A practice
 * carries on reading a dashboard that quietly stopped being true. There is no exception for anyone
 * to see, no empty state, no error page - the last good data simply stays there looking current.
 *
 * <p>So two things happen on every pass. The connection row records what actually happened, with a
 * timestamp, because "connected, last checked four days ago" and "connected, last checked a minute
 * ago" are different situations that otherwise render identically. And if the failure persists,
 * somebody gets an email.
 *
 * <p><b>Alerting is time-based, not count-based.</b> Three consecutive failures means three hours
 * at the hourly default and fifteen minutes at the five-minute one, so a count threshold would
 * silently change meaning the moment the interval is tuned. A duration means the same thing at any
 * interval.
 *
 * <p><b>One alert per incident, plus one on recovery.</b> A scheduler failing every five minutes
 * for a day would otherwise send 288 identical emails, which is a filter rule waiting to happen
 * and therefore the same as sending none. The recovery notice matters as much as the alert: it is
 * what distinguishes "fixed" from "nobody has looked yet".
 *
 * <p>Nothing here throws. It is called from inside the sync's own error handling, and a failure to
 * record or report a problem must never turn a recoverable upstream outage into a broken sync.
 */
@Component
public class SyncHealthRecorder {

    private static final Logger log = LoggerFactory.getLogger(SyncHealthRecorder.class);

    /** The column is varchar(500). Adapter messages are already stripped of credentials. */
    private static final int MAX_DETAIL = 500;

    private final JdbcClient jdbc;
    private final AlertMailer mailer;
    private final Duration alertAfter;

    public SyncHealthRecorder(
            JdbcClient jdbc,
            AlertMailer mailer,
            @Value("${visionone.appointment.sync.alert-after-minutes:30}") long alertAfterMinutes) {
        this.jdbc = jdbc;
        this.mailer = mailer;
        // Zero or less would alert on the very first failure, including the transient ones that
        // resolve by themselves on the next pass - which is how an alert channel becomes noise
        // and then becomes ignored.
        this.alertAfter = Duration.ofMinutes(alertAfterMinutes > 0 ? alertAfterMinutes : 30);
    }

    /**
     * A pass that worked. Clears any open incident and, if one was reported, says so.
     */
    public void recordSuccess(UUID organizationId, String providerCode, String detail) {
        try {
            Instant failingSince = currentFailingSince(organizationId, providerCode);
            boolean wasAlerted = currentAlertedAt(organizationId, providerCode) != null;

            jdbc.sql("""
                    update integration_connection
                       set status = 'CONNECTED', last_checked_at = now(), detail = :detail,
                           failing_since = null, alerted_at = null
                     where organization_id = :orgId and provider_code = :providerCode
                    """)
                    .param("detail", truncate(detail))
                    .param("orgId", organizationId)
                    .param("providerCode", providerCode)
                    .update();

            if (wasAlerted) {
                String outage = failingSince == null
                        ? "an unknown period"
                        : humanDuration(Duration.between(failingSince, Instant.now()));
                mailer.send(
                        "VisionOne: " + providerCode + " sync has recovered",
                        "The " + providerCode + " appointment sync is working again.\n\n"
                                + "It had been failing for " + outage + ".\n"
                                + "Latest pass: " + detail + "\n\n"
                                + "No action needed. This is the all-clear for the earlier alert.");
            }
        } catch (RuntimeException e) {
            log.warn("Could not record sync success for organization {}: {}", organizationId, e.getMessage());
        }
    }

    /**
     * A pass that did not work. Opens an incident if there is not one, and reports it once it has
     * lasted long enough to be worth waking someone for.
     */
    public void recordFailure(
            UUID organizationId, String providerCode, ProviderStatus status, String detail) {
        try {
            // coalesce keeps the original start time across repeated failures, which is what makes
            // the duration threshold mean anything.
            jdbc.sql("""
                    update integration_connection
                       set status = :status, last_checked_at = now(), detail = :detail,
                           failing_since = coalesce(failing_since, now())
                     where organization_id = :orgId and provider_code = :providerCode
                    """)
                    .param("status", status.name())
                    .param("detail", truncate(detail))
                    .param("orgId", organizationId)
                    .param("providerCode", providerCode)
                    .update();

            Instant failingSince = currentFailingSince(organizationId, providerCode);
            if (failingSince == null || currentAlertedAt(organizationId, providerCode) != null) {
                // Either nothing to measure, or this incident has already been reported. Repeating
                // it every pass is how an alert channel gets filtered into silence.
                return;
            }
            Duration failingFor = Duration.between(failingSince, Instant.now());
            if (failingFor.compareTo(alertAfter) < 0) {
                return;
            }

            mailer.send(
                    "VisionOne: " + providerCode + " sync has been failing for "
                            + humanDuration(failingFor),
                    "The " + providerCode + " appointment sync has not succeeded since "
                            + failingSince + ".\n\n"
                            + "Status: " + status + "\n"
                            + "Last error: " + detail + "\n\n"
                            + "The calendar is not being updated. Existing appointments are\n"
                            + "untouched - a failed sync never changes or cancels anything - but\n"
                            + "new bookings, changes and cancellations made since then are not\n"
                            + "reflected in VisionOne.\n\n"
                            + "The usual cause is a regenerated export token. Check\n"
                            + "VISIONONE_HEALTHIE_ICS_URL against the link in Healthie's calendar\n"
                            + "settings.\n\n"
                            + "You will get one more email when it recovers.");

            // Marked whether or not the mail actually left, so an SMTP outage cannot turn one
            // alert into one per pass.
            jdbc.sql("""
                    update integration_connection set alerted_at = now()
                     where organization_id = :orgId and provider_code = :providerCode
                    """)
                    .param("orgId", organizationId)
                    .param("providerCode", providerCode)
                    .update();
        } catch (RuntimeException e) {
            log.warn("Could not record sync failure for organization {}: {}", organizationId, e.getMessage());
        }
    }

    private Instant currentFailingSince(UUID organizationId, String providerCode) {
        return timestamp(organizationId, providerCode, "failing_since");
    }

    private Instant currentAlertedAt(UUID organizationId, String providerCode) {
        return timestamp(organizationId, providerCode, "alerted_at");
    }

    private Instant timestamp(UUID organizationId, String providerCode, String column) {
        // The column name is one of two literals chosen here, never anything from outside.
        return jdbc.sql("select %s from integration_connection where organization_id = :orgId and provider_code = :providerCode"
                        .formatted(column))
                .param("orgId", organizationId)
                .param("providerCode", providerCode)
                .query(Instant.class)
                .optional()
                .orElse(null);
    }

    private static String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }

    /** "2 hours 5 minutes" reads better in a subject line than PT2H5M. */
    static String humanDuration(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        String text = hours + (hours == 1 ? " hour" : " hours");
        return rest == 0 ? text : text + " " + rest + (rest == 1 ? " minute" : " minutes");
    }
}
