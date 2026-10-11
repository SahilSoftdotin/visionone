package com.visiondigitallab.visionone.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.visiondigitallab.visionone.appointment.internal.AppointmentSyncService;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider.AppointmentReference;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider.AppointmentState;
import com.visiondigitallab.visionone.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * What a scheduling sync is allowed to overwrite.
 *
 * <p>This had no coverage at all, which is a gap worth naming: it is the only thing that writes a
 * provider's view over the practice's calendar, it runs unattended every hour, and the two rules
 * that matter most are both about what it must <em>not</em> do. A sync that silently destroys a
 * human's entry looks exactly like a sync that works.
 *
 * <p>Against real Postgres rather than a mock, because both rules live in the {@code on conflict}
 * clause. A mocked JdbcClient would assert that a string was passed to a method and prove nothing
 * about what the database did with it.
 */
class AppointmentSyncServiceTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID GOOGLE_ADS = UUID.fromString("0199a1d0-0001-7000-8000-000000000001");
    private static final String REF = "ics-test-appointment-1";

    @Autowired
    private AppointmentSyncService sync;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from appointment_reference where external_ref like 'ics-test-%'").update();
    }

    private AppointmentReference reference(AppointmentState status, String channelCode) {
        return new AppointmentReference(
                REF,
                Instant.parse("2026-10-15T14:30:00Z"),
                45,
                "Dana W.",
                "HORMONE_OPTIMIZATION",
                channelCode,
                status);
    }

    private String status() {
        return jdbc.sql("select status from appointment_reference where external_ref = :ref")
                .param("ref", REF)
                .query(String.class)
                .single();
    }

    private Optional<UUID> channelSourceId() {
        return jdbc.sql("select channel_source_id from appointment_reference where external_ref = :ref")
                .param("ref", REF)
                .query(UUID.class)
                .optional();
    }

    private int rowCount() {
        return jdbc.sql("select count(*) from appointment_reference where external_ref = :ref")
                .param("ref", REF)
                .query(Integer.class)
                .single();
    }

    @Test
    @DisplayName("syncing the same appointment twice updates one row rather than adding a second")
    void upsertIsIdempotent() {
        // Everything that writes here can run twice - a webhook retry, a sync overlapping a
        // webhook, two syncs after a restart.
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)));
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)));

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("attribution set by hand is never replaced by a later sync")
    void attributionIsNeverOverwritten() {
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)));
        // Vision credits the channel on the Calendar. It is the one column on this table VisionOne
        // owns rather than mirrors, and no provider could know it.
        jdbc.sql("update appointment_reference set channel_source_id = :channel where external_ref = :ref")
                .param("channel", GOOGLE_ADS)
                .param("ref", REF)
                .update();

        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)));

        assertThat(channelSourceId()).contains(GOOGLE_ADS);
    }

    @Test
    @DisplayName("a provider that reports attendance may correct it back to booked")
    void providerThatKnowsOwnsTheOutcome() {
        sync.apply(THRIVE, "HEALTHIE", List.of(reference(AppointmentState.ATTENDED, null)), true);

        // Healthie's API reports pm_status, so a correction there is a correction here. This is the
        // behaviour that existed before the flag, and it has to keep working.
        sync.apply(THRIVE, "HEALTHIE", List.of(reference(AppointmentState.BOOKED, null)), true);

        assertThat(status()).isEqualTo("BOOKED");
    }

    @Test
    @DisplayName("a provider that cannot report attendance never flattens an attended appointment")
    void providerThatCannotKnowLeavesAttendanceAlone() {
        jdbc.sql("""
                insert into appointment_reference
                    (id, organization_id, external_ref, requested_at, scheduled_for, status,
                     provider_code, display_label, duration_minutes)
                values (:id, :org, :ref, :at, :at, 'ATTENDED', 'HEALTHIE_ICS', 'Dana W.', 45)
                """)
                .param("id", UUID.randomUUID())
                .param("org", THRIVE)
                .param("ref", REF)
                .param("at", java.sql.Timestamp.from(Instant.parse("2026-10-15T14:30:00Z")))
                .update();

        // The .ics feed reports every past appointment as BOOKED forever - it has no way to say
        // otherwise. Mirroring that would erase this hourly, with no trace of what happened.
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)), false);

        assertThat(status()).isEqualTo("ATTENDED");
    }

    @Test
    @DisplayName("and leaves a no-show alone for the same reason")
    void noShowIsAlsoPreserved() {
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)), false);
        jdbc.sql("update appointment_reference set status = 'NO_SHOW' where external_ref = :ref")
                .param("ref", REF)
                .update();

        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)), false);

        assertThat(status()).isEqualTo("NO_SHOW");
    }

    @Test
    @DisplayName("a cancellation in the feed does not override an attendance recorded here")
    void cancellationDoesNotOverrideAttendance() {
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)), false);
        jdbc.sql("update appointment_reference set status = 'ATTENDED' where external_ref = :ref")
                .param("ref", REF)
                .update();

        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.CANCELLED, null)), false);

        // Someone watched this appointment happen. A cancellation appearing afterwards is an admin
        // tidying the practice's own book, not evidence that it did not happen, so the human's
        // entry stands. Stated as a decision rather than left to be discovered.
        assertThat(status()).isEqualTo("ATTENDED");
    }

    @Test
    @DisplayName("preserving attendance still lets everything else update")
    void preservingAttendanceDoesNotFreezeTheRow() {
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)), false);
        jdbc.sql("update appointment_reference set status = 'ATTENDED' where external_ref = :ref")
                .param("ref", REF)
                .update();

        sync.apply(
                THRIVE,
                "HEALTHIE_ICS",
                List.of(new AppointmentReference(
                        REF,
                        Instant.parse("2026-10-16T09:00:00Z"),
                        60,
                        "Dana W.",
                        "WEIGHT_MANAGEMENT",
                        null,
                        AppointmentState.BOOKED)),
                false);

        // The status is held back; the rest of the row is still a mirror.
        var row = jdbc.sql("""
                select duration_minutes, service_category, status
                  from appointment_reference where external_ref = :ref
                """)
                .param("ref", REF)
                .query((rs, n) -> rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3))
                .single();
        assertThat(row).isEqualTo("60|WEIGHT_MANAGEMENT|ATTENDED");
    }

    @Test
    @DisplayName("an unknown channel code leaves attribution unset rather than inventing a channel")
    void unknownChannelIsUnattributed() {
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, "NOT_A_CHANNEL")));

        assertThat(channelSourceId()).isEmpty();
    }

    @Test
    @DisplayName("forgetting an appointment cancels it rather than deleting the only record of it")
    void forgetCancels() {
        sync.apply(THRIVE, "HEALTHIE_ICS", List.of(reference(AppointmentState.BOOKED, null)));

        sync.forget(THRIVE, REF);

        // The month's booked count is built from history, so losing the row loses the fact that the
        // booking ever existed.
        assertThat(rowCount()).isEqualTo(1);
        assertThat(status()).isEqualTo("CANCELLED");
    }
}
