package com.visiondigitallab.visionone.appointment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.visiondigitallab.visionone.appointment.internal.AppointmentSyncScheduler;
import com.visiondigitallab.visionone.appointment.internal.AppointmentSyncService;
import com.visiondigitallab.visionone.integration.internal.HealthieIcsSchedulingProvider;
import com.visiondigitallab.visionone.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Two consecutive syncs of a changing calendar, end to end.
 *
 * <p>This exists because the three things a practice actually does to a calendar - cancel
 * something, move something, add something - were the only part of the .ics route with no
 * coverage, and they are all served by one mechanism. The feed carries no status field, so a
 * cancellation can only ever appear as an absence; identity is derived from the appointment's own
 * details, so a reschedule arrives as a new row and orphans the old one. Both rely on
 * reconciliation being exactly right, and reconciliation is the one operation here that can
 * destroy real data if it is wrong.
 *
 * <p>Deliberately assembled from the real parts: the real {@link AppointmentSyncScheduler} driving
 * the real adapter against a stubbed export, writing through the real service into real Postgres.
 * The scheduler is constructed rather than injected because the application context binds the demo
 * provider - but it is the production class, so the window arithmetic and the connection lookup
 * are under test too, not just the pieces either side of them.
 *
 * <p>Dates are relative to today because the scheduler's window is. Pinning them to literals would
 * make this pass until the window moved past them and then fail for a reason that has nothing to
 * do with the code.
 */
class IcsSyncReplayTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final DateTimeFormatter ICS_LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    /** Healthie leaves timestamps floating - no Z, no TZID - so the fixtures do too. */
    private static String when(int daysFromNow, int hour, int minute) {
        return LocalDate.now().plusDays(daysFromNow).atTime(hour, minute).format(ICS_LOCAL);
    }

    private static String event(String start, String end, String summary) {
        // No STATUS and a throwaway UID, exactly as Healthie exports: the UID is regenerated on
        // every fetch, so each fixture uses a different one to prove nothing depends on it.
        return "BEGIN:VEVENT\r\nUID:" + UUID.randomUUID() + "\r\nDTSTART:" + start
                + "\r\nDTEND:" + end + "\r\nSUMMARY:" + summary + "\r\nEND:VEVENT\r\n";
    }

    private static String calendar(String... events) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:icalendar-ruby\r\nCALSCALE:GREGORIAN\r\n"
                + "METHOD:PUBLISH\r\n" + String.join("", events) + "END:VCALENDAR\r\n";
    }

    private WireMockServer healthie;

    @Autowired
    private AppointmentSyncService sync;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        healthie = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        healthie.start();
        jdbc.sql("delete from appointment_reference where provider_code = 'HEALTHIE_ICS'").update();
        jdbc.sql("""
                insert into integration_connection (id, organization_id, provider_kind, provider_code, status)
                values (:id, :org, 'SCHEDULING', 'HEALTHIE_ICS', 'CONNECTED')
                on conflict (organization_id, provider_code) do update set status = 'CONNECTED'
                """)
                .param("id", UUID.randomUUID())
                .param("org", THRIVE)
                .update();
    }

    @AfterEach
    void tearDown() {
        healthie.stop();
        jdbc.sql("delete from appointment_reference where provider_code = 'HEALTHIE_ICS'").update();
        jdbc.sql("delete from integration_connection where provider_code = 'HEALTHIE_ICS'").update();
    }

    /** Serves this body for every subsequent fetch, replacing whatever was there. */
    private void feed(String body) {
        healthie.resetAll();
        healthie.stubFor(get(urlPathEqualTo("/appointments/ics_export.ics"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/calendar")
                        .withBody(body)));
    }

    private void failWith(int status) {
        healthie.resetAll();
        healthie.stubFor(get(urlPathEqualTo("/appointments/ics_export.ics"))
                .willReturn(aResponse().withStatus(status).withBody("nope")));
    }

    /** One pass of the production scheduler against the stub. */
    private void runSync() {
        var provider = new HealthieIcsSchedulingProvider(
                healthie.baseUrl() + "/appointments/ics_export.ics?access_token=test",
                "America/New_York", 45, 2000, 5000);
        new AppointmentSyncScheduler(provider, sync, jdbc, 45, 90).sync();
    }

    /** label -> status, which is the view that matters and carries no identifier. */
    private Map<String, List<String>> byLabel() {
        return jdbc.sql("""
                select display_label, status from appointment_reference
                 where provider_code = 'HEALTHIE_ICS' order by display_label, status
                """)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private int rows() {
        return jdbc.sql("select count(*) from appointment_reference where provider_code = 'HEALTHIE_ICS'")
                .query(Integer.class)
                .single();
    }

    @Test
    @DisplayName("a cancelled appointment disappears from the feed and is cancelled here")
    void cancellation() {
        feed(calendar(
                event(when(10, 9, 0), when(10, 9, 45), "Ada Keeper"),
                event(when(10, 11, 0), when(10, 11, 45), "Cass Gone")));
        runSync();
        assertThat(byLabel()).containsOnlyKeys("Ada K.", "Cass G.");

        // Healthie has no way to say "cancelled" in an .ics feed. It simply stops listing it.
        feed(calendar(event(when(10, 9, 0), when(10, 9, 45), "Ada Keeper")));
        runSync();

        assertThat(byLabel().get("Cass G.")).containsExactly("CANCELLED");
        assertThat(byLabel().get("Ada K.")).containsExactly("BOOKED");
        // Cancelled, never deleted: the row is the only record the booking ever existed, and the
        // month's booked count is built from history.
        assertThat(rows()).isEqualTo(2);
    }

    @Test
    @DisplayName("a rescheduled appointment cancels the old slot and books the new one")
    void reschedule() {
        feed(calendar(event(when(12, 10, 0), when(12, 10, 45), "Mo Mover")));
        runSync();
        assertThat(byLabel().get("Mo M.")).containsExactly("BOOKED");

        // Identity is derived from start, end and summary, so a move is indistinguishable from a
        // cancellation plus a new booking. Reconciliation is what stops the old one lingering.
        feed(calendar(event(when(12, 15, 30), when(12, 16, 15), "Mo Mover")));
        runSync();

        assertThat(byLabel().get("Mo M.")).containsExactlyInAnyOrder("CANCELLED", "BOOKED");
        assertThat(rows()).isEqualTo(2);
    }

    @Test
    @DisplayName("a new booking appears without disturbing the others")
    void newBooking() {
        feed(calendar(event(when(8, 9, 0), when(8, 9, 15), "Ada Keeper")));
        runSync();

        feed(calendar(
                event(when(8, 9, 0), when(8, 9, 15), "Ada Keeper"),
                event(when(8, 13, 0), when(8, 13, 45), "New Arrival")));
        runSync();

        assertThat(byLabel().get("Ada K.")).containsExactly("BOOKED");
        assertThat(byLabel().get("New A.")).containsExactly("BOOKED");
        assertThat(rows()).isEqualTo(2);
    }

    @Test
    @DisplayName("all three at once, which is what a real day between syncs looks like")
    void cancelMoveAndAddTogether() {
        feed(calendar(
                event(when(14, 9, 0), when(14, 9, 15), "Ada Keeper"),
                event(when(14, 10, 0), when(14, 10, 5), "Cass Gone"),
                event(when(14, 11, 0), when(14, 11, 45), "Mo Mover")));
        runSync();
        assertThat(rows()).isEqualTo(3);

        feed(calendar(
                event(when(14, 9, 0), when(14, 9, 15), "Ada Keeper"),
                event(when(14, 16, 0), when(14, 16, 45), "Mo Mover"),
                event(when(14, 13, 0), when(14, 13, 45), "New Arrival")));
        runSync();

        assertThat(byLabel().get("Ada K.")).containsExactly("BOOKED");
        assertThat(byLabel().get("Cass G.")).containsExactly("CANCELLED");
        assertThat(byLabel().get("Mo M.")).containsExactlyInAnyOrder("CANCELLED", "BOOKED");
        assertThat(byLabel().get("New A.")).containsExactly("BOOKED");
        assertThat(rows()).isEqualTo(5);
    }

    @Test
    @DisplayName("an unchanged calendar synced repeatedly changes nothing")
    void repeatedIdenticalSyncs() {
        String stable = calendar(
                event(when(9, 9, 0), when(9, 9, 15), "Ada Keeper"),
                event(when(9, 10, 0), when(9, 10, 45), "Mo Mover"));
        feed(stable);
        runSync();
        runSync();
        runSync();

        // Every UID in those three fetches was different, which is exactly the condition that made
        // the first live run insert the whole calendar on every pass.
        assertThat(rows()).isEqualTo(2);
        assertThat(byLabel().get("Ada K.")).containsExactly("BOOKED");
        assertThat(byLabel().get("Mo M.")).containsExactly("BOOKED");
    }

    @Test
    @DisplayName("a failed fetch cancels nothing at all")
    void outageCancelsNothing() {
        feed(calendar(
                event(when(11, 9, 0), when(11, 9, 15), "Ada Keeper"),
                event(when(11, 10, 0), when(11, 10, 45), "Mo Mover")));
        runSync();

        // The single most dangerous failure available here: if "we could not ask Healthie" were
        // ever treated as "Healthie listed nothing", one 500 would cancel the practice's entire
        // calendar. The scheduler swallows the exception and leaves the data alone.
        failWith(500);
        runSync();

        assertThat(byLabel().get("Ada K.")).containsExactly("BOOKED");
        assertThat(byLabel().get("Mo M.")).containsExactly("BOOKED");
    }

    @Test
    @DisplayName("a truncated feed cancels nothing either")
    void truncatedFeedCancelsNothing() {
        String full = calendar(
                event(when(13, 9, 0), when(13, 9, 15), "Ada Keeper"),
                event(when(13, 10, 0), when(13, 10, 45), "Mo Mover"));
        feed(full);
        runSync();

        // iCalendar is a flat sequence, so half a download parses as a shorter, perfectly valid
        // calendar. Without the terminator check this would have cancelled whatever fell in the
        // half that did not arrive.
        feed(full.substring(0, full.length() / 2));
        runSync();

        assertThat(byLabel().get("Ada K.")).containsExactly("BOOKED");
        assertThat(byLabel().get("Mo M.")).containsExactly("BOOKED");
    }

    private Map<String, Object> health() {
        return jdbc.sql("""
                select status, detail, last_checked_at is not null as checked
                  from integration_connection
                 where organization_id = :org and provider_code = 'HEALTHIE_ICS'
                """)
                .param("org", THRIVE)
                .query((rs, n) -> Map.<String, Object>of(
                        "status", rs.getString(1),
                        "detail", rs.getString(2) == null ? "" : rs.getString(2),
                        "checked", rs.getBoolean(3)))
                .single();
    }

    @Test
    @DisplayName("a healthy sync records what it found and when")
    void recordsHealthOnSuccess() {
        feed(calendar(event(when(7, 9, 0), when(7, 9, 15), "Ada Keeper")));
        runSync();

        var health = health();
        assertThat(health.get("status")).isEqualTo("CONNECTED");
        assertThat(health.get("checked")).isEqualTo(true);
        assertThat(health.get("detail").toString()).contains("Synced 1 appointment");
    }

    @Test
    @DisplayName("a failing sync says so on the connection row instead of staying green")
    void recordsHealthOnFailure() {
        feed(calendar(event(when(7, 9, 0), when(7, 9, 15), "Ada Keeper")));
        runSync();
        assertThat(health().get("status")).isEqualTo("CONNECTED");

        // The failure that matters: a regenerated token. Before this, the row said CONNECTED
        // forever and the only symptom was a calendar that quietly stopped updating.
        failWith(401);
        runSync();

        assertThat(health().get("status")).isEqualTo("ERROR");
        assertThat(health().get("detail").toString()).contains("regenerated");
        // And the credential itself never reaches the column.
        assertThat(health().get("detail").toString()).doesNotContain("access_token=test");
    }

    @Test
    @DisplayName("the recorded detail names the cancellations it made")
    void healthMentionsReconciliation() {
        feed(calendar(
                event(when(7, 9, 0), when(7, 9, 15), "Ada Keeper"),
                event(when(7, 10, 0), when(7, 10, 15), "Cass Gone")));
        runSync();

        feed(calendar(event(when(7, 9, 0), when(7, 9, 15), "Ada Keeper")));
        runSync();

        assertThat(health().get("detail").toString()).contains("cancelled 1");
    }

    @Test
    @DisplayName("attendance recorded here survives a calendar that keeps saying booked")
    void attendanceSurvivesReplay() {
        feed(calendar(event(when(-5, 9, 0), when(-5, 9, 45), "Ada Keeper")));
        runSync();
        jdbc.sql("""
                update appointment_reference set status = 'ATTENDED'
                 where provider_code = 'HEALTHIE_ICS' and display_label = 'Ada K.'
                """).update();

        // The feed reports every past appointment as booked forever. Three more passes must not
        // walk that back.
        runSync();
        runSync();
        runSync();

        assertThat(byLabel().get("Ada K.")).containsExactly("ATTENDED");
    }
}
