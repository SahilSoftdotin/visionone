package com.visiondigitallab.visionone.integration.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ICS adapter, against a stubbed export.
 *
 * <p>Every feed here is invented. What these prove is the adapter's own behaviour - the date window
 * it has to apply itself, the refusal to report an outage or a wrong token as an empty calendar, and
 * the reduction of a SUMMARY to something non-identifying. What they cannot prove is that Healthie's
 * real export looks like any of this, because nothing here has read one. That gap closes against a
 * live feed, not before, and the adapter is written to degrade rather than guess precisely because
 * it is still open.
 */
class HealthieIcsSchedulingProviderTest {

    private static final UUID ORG = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private WireMockServer healthie;

    @BeforeEach
    void start() {
        healthie = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        healthie.start();
    }

    @AfterEach
    void stop() {
        healthie.stop();
    }

    private HealthieIcsSchedulingProvider providerFor(String url) {
        return new HealthieIcsSchedulingProvider(url, "America/New_York", 45, 2000, 5000);
    }

    private HealthieIcsSchedulingProvider stub(String body) {
        healthie.stubFor(get(urlPathEqualTo("/appointments/ics_export.ics"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/calendar")
                        .withBody(body)));
        return providerFor(healthie.baseUrl() + "/appointments/ics_export.ics?access_token=test-token");
    }

    private HealthieIcsSchedulingProvider stubStatus(int status, String body) {
        healthie.stubFor(get(urlPathEqualTo("/appointments/ics_export.ics"))
                .willReturn(aResponse().withStatus(status).withBody(body)));
        return providerFor(healthie.baseUrl() + "/appointments/ics_export.ics?access_token=test-token");
    }

    private static String calendar(String... events) {
        StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");
        for (String event : events) {
            out.append(event);
        }
        out.append("END:VCALENDAR\r\n");
        return out.toString();
    }

    private static String event(String uid, String start, String end, String summary, String status) {
        return "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTART:" + start + "\r\n"
                + (end == null ? "" : "DTEND:" + end + "\r\n")
                + (summary == null ? "" : "SUMMARY:" + summary + "\r\n")
                + (status == null ? "" : "STATUS:" + status + "\r\n")
                + "END:VEVENT\r\n";
    }

    @Test
    @DisplayName("maps an appointment onto the port")
    void mapsAnAppointment() {
        var provider = stub(calendar(event(
                "appt-1", "20261015T143000Z", "20261015T151500Z",
                "Hormone Therapy with Dana Whitfield", "CONFIRMED")));

        var appointments = provider.appointments(ORG, FROM, TO);

        assertThat(appointments).hasSize(1);
        var appointment = appointments.get(0);
        assertThat(appointment.externalRef()).isEqualTo("appt-1");
        assertThat(appointment.durationMinutes()).isEqualTo(45);
        assertThat(appointment.displayLabel()).isEqualTo("Dana W.");
        assertThat(appointment.serviceCategory()).isEqualTo("HORMONE_OPTIMIZATION");
        assertThat(appointment.status()).isEqualTo(SchedulingProvider.AppointmentState.BOOKED);
        // Healthie cannot know which advertisement produced a patient, and null says so. "Direct"
        // would be a guess that looks like a fact on a dashboard.
        assertThat(appointment.channelCode()).isNull();
    }

    @Test
    @DisplayName("applies the date window the export cannot apply itself")
    void appliesTheWindow() {
        var provider = stub(calendar(
                event("before", "20260915T143000Z", null, "Consult with A B", null),
                event("inside", "20261015T143000Z", null, "Consult with C D", null),
                event("after", "20270115T143000Z", null, "Consult with E F", null)));

        assertThat(provider.appointments(ORG, FROM, TO))
                .extracting(SchedulingProvider.AppointmentReference::externalRef)
                .containsExactly("inside");
    }

    @Test
    @DisplayName("substitutes the configured default when the feed gives no length")
    void defaultsDuration() {
        var provider = stub(calendar(event("a", "20261015T143000Z", null, "Consult with Dana W", null)));

        assertThat(provider.appointments(ORG, FROM, TO).get(0).durationMinutes()).isEqualTo(45);
    }

    @Test
    @DisplayName("maps a cancelled event to CANCELLED")
    void mapsCancelled() {
        var provider = stub(calendar(
                event("a", "20261015T143000Z", null, "Consult with Dana W", "CANCELLED"),
                event("b", "20261016T143000Z", null, "Consult with Dana W", "TENTATIVE")));

        assertThat(provider.appointments(ORG, FROM, TO))
                .extracting(SchedulingProvider.AppointmentReference::status)
                .containsExactly(
                        SchedulingProvider.AppointmentState.CANCELLED,
                        // Tentative is still a booking as far as a marketing dashboard goes.
                        SchedulingProvider.AppointmentState.BOOKED);
    }

    @Test
    @DisplayName("never claims to know about attendance")
    void doesNotReportAttendance() {
        // This is what stops an hourly sync erasing an ATTENDED that Vision recorded by hand. The
        // flag is the whole protection, so it is worth a test of its own.
        assertThat(providerFor("https://example.test/x.ics").reportsAttendance()).isFalse();
    }

    @Test
    @DisplayName("skips a recurring event rather than taking its first occurrence as the only one")
    void skipsRecurring() {
        String recurring = "BEGIN:VEVENT\r\nUID:series\r\nDTSTART:20261015T143000Z\r\n"
                + "RRULE:FREQ=WEEKLY;COUNT=8\r\nSUMMARY:Follow-up with Dana W\r\nEND:VEVENT\r\n";
        var provider = stub(calendar(
                recurring, event("single", "20261016T143000Z", null, "Consult with Dana W", null)));

        // Showing one of eight occurrences would be a wrong calendar presented as a right one. The
        // adapter logs loudly and leaves it out until there is real RRULE support.
        assertThat(provider.appointments(ORG, FROM, TO))
                .extracting(SchedulingProvider.AppointmentReference::externalRef)
                .containsExactly("single");
    }

    @Test
    @DisplayName("skips an event with no UID, because the upsert has nothing to key on")
    void skipsMissingUid() {
        String noUid = "BEGIN:VEVENT\r\nDTSTART:20261015T143000Z\r\nSUMMARY:Consult with A B\r\nEND:VEVENT\r\n";
        var provider = stub(calendar(noUid, event("good", "20261016T143000Z", null, "Consult with C D", null)));

        // Without a stable id, every sync would insert another copy.
        assertThat(provider.appointments(ORG, FROM, TO))
                .extracting(SchedulingProvider.AppointmentReference::externalRef)
                .containsExactly("good");
    }

    @Test
    @DisplayName("reports an unreachable export as unavailable, not as an empty calendar")
    void unreachableIsNotEmpty() {
        var provider = providerFor("http://127.0.0.1:1/appointments/ics_export.ics?access_token=x");

        assertThatThrownBy(() -> provider.appointments(ORG, FROM, TO))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class);
    }

    @Test
    @DisplayName("treats a 401 as unavailable and names the likely cause")
    void revokedTokenIsAnError() {
        var provider = stubStatus(401, "Unauthorized");

        assertThatThrownBy(() -> provider.appointments(ORG, FROM, TO))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class)
                .hasMessageContaining("regenerated");
        assertThat(provider.status(ORG)).isEqualTo(ProviderStatus.ERROR);
    }

    @Test
    @DisplayName("refuses a 200 that is a sign-in page rather than a calendar")
    void htmlIsNotACalendar() {
        // The classic shape of an expired token: 200 with a login form. Parsing it yields zero
        // events, which would otherwise render as a quiet month.
        var provider = stub("<html><body>Please sign in</body></html>");

        assertThatThrownBy(() -> provider.appointments(ORG, FROM, TO))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class)
                .hasMessageContaining("did not return a calendar");
    }

    @Test
    @DisplayName("refuses a calendar with no events at all")
    void emptyCalendarIsAnError() {
        // Far likelier to be a feed scoped to the wrong user than a clinic with nothing booked in
        // three months.
        var provider = stub(calendar());

        assertThatThrownBy(() -> provider.appointments(ORG, FROM, TO))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class)
                .hasMessageContaining("no events");
    }

    @Test
    @DisplayName("reports NOT_CONNECTED when no URL is configured")
    void unconfiguredIsNotConnected() {
        var provider = providerFor("");

        assertThat(provider.status(ORG)).isEqualTo(ProviderStatus.NOT_CONNECTED);
        assertThatThrownBy(() -> provider.appointments(ORG, FROM, TO))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class);
    }

    @Test
    @DisplayName("reports CONNECTED against a working export")
    void workingExportIsConnected() {
        var provider = stub(calendar(event("a", "20261015T143000Z", null, "Consult with Dana W", null)));

        assertThat(provider.status(ORG)).isEqualTo(ProviderStatus.CONNECTED);
    }

    @Test
    @DisplayName("finds one appointment by its external ref")
    void findsOne() {
        var provider = stub(calendar(
                event("a", "20261015T143000Z", null, "Consult with Dana W", null),
                event("b", "20261016T143000Z", null, "Weight Management with Ray Q", null)));

        assertThat(provider.appointment(ORG, "b").serviceCategory()).isEqualTo("WEIGHT_MANAGEMENT");
        assertThat(provider.appointment(ORG, "missing")).isNull();
    }

    @Test
    @DisplayName("never logs the access token")
    void redactsTheToken() {
        // The whole URL is a bearer credential. A log line lands in tickets and screenshots, and a
        // token in one of those is a token that has to be rotated.
        assertThat(HealthieIcsSchedulingProvider.redact(
                        "https://app.gethealthie.com/appointments/ics_export.ics?access_token=secret"))
                .isEqualTo("https://app.gethealthie.com/appointments/ics_export.ics?...")
                .doesNotContain("secret");
    }

    @Test
    @DisplayName("reduces a SUMMARY to a first name and a last initial")
    void reducesTheLabel() {
        // The export has one free-text SUMMARY and no structured patient field, and its wording is
        // the practice's own. These are the shapes seen so far; the point of the test is that every
        // one of them comes out non-identifying rather than that the list is complete.
        assertThat(HealthieIcsSchedulingProvider.patientLabel("Hormone Therapy with Dana Whitfield"))
                .isEqualTo("Dana W.");
        assertThat(HealthieIcsSchedulingProvider.patientLabel("Dana Whitfield - Hormone Therapy"))
                .isEqualTo("Dana W.");
        assertThat(HealthieIcsSchedulingProvider.patientLabel("Dana Whitfield")).isEqualTo("Dana W.");
        assertThat(HealthieIcsSchedulingProvider.patientLabel("Dana")).isEqualTo("Dana");
        assertThat(HealthieIcsSchedulingProvider.patientLabel("  ")).isNull();
        assertThat(HealthieIcsSchedulingProvider.patientLabel(null)).isNull();
    }

    @Test
    @DisplayName("hashes a UID too long for the column instead of truncating it")
    void hashesOverlongUid() {
        String shortUid = "appt-1@gethealthie.com";
        assertThat(HealthieIcsSchedulingProvider.stableRef(shortUid)).isEqualTo(shortUid);

        String longUid = "u".repeat(200);
        String hashed = HealthieIcsSchedulingProvider.stableRef(longUid);
        // Must fit varchar(120), and must be the same on every sync or the upsert duplicates.
        assertThat(hashed).hasSizeLessThanOrEqualTo(120).startsWith("ics-sha256-");
        assertThat(HealthieIcsSchedulingProvider.stableRef(longUid)).isEqualTo(hashed);
        // Truncating would have collided these two onto one row, merging two people's bookings.
        assertThat(HealthieIcsSchedulingProvider.stableRef(longUid + "x")).isNotEqualTo(hashed);
    }
}
