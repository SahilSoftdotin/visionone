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
import java.time.Instant;
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
        // Not the UID: Healthie regenerates that on every fetch. See contentRef.
        assertThat(appointment.externalRef()).startsWith("ics-").hasSize(44);
        assertThat(appointment.durationMinutes()).isEqualTo(45);
        assertThat(appointment.displayLabel()).isEqualTo("Hormone Therapy with Dana Whitfield");
        // Null on this route: the export names a contact type, never a service.
        assertThat(appointment.serviceCategory()).isNull();
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
                .extracting(SchedulingProvider.AppointmentReference::displayLabel)
                .containsExactly("Consult with C D");
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
                .extracting(SchedulingProvider.AppointmentReference::displayLabel)
                .containsExactly("Consult with Dana W");
    }

    @Test
    @DisplayName("skips an event with no summary, because identity is derived from it")
    void skipsMissingSummary() {
        String noSummary = "BEGIN:VEVENT\r\nUID:x\r\nDTSTART:20261015T143000Z\r\nEND:VEVENT\r\n";
        var provider = stub(calendar(
                noSummary, event("good", "20261016T143000Z", null, "Consult with C D", null)));

        // Two summary-less appointments in the same slot would key identically and collapse onto
        // one row. The UID cannot save them - Healthie regenerates it on every fetch.
        assertThat(provider.appointments(ORG, FROM, TO))
                .extracting(SchedulingProvider.AppointmentReference::displayLabel)
                .containsExactly("Consult with C D");
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
    @DisplayName("refuses the real empty export Healthie serves for a user with no calendar")
    void realEmptyHealthieExportIsAnError() {
        // Captured verbatim from app.gethealthie.com. It contains no events, so there is nothing
        // in it belonging to a patient - which is the whole point of it being here.
        //
        // This is the failure the guard exists for, and it is not hypothetical: a token generated
        // by an agency user returns HTTP 200 and a structurally valid calendar carrying none of the
        // practice's appointments. Parsed naively it reads as "no bookings this quarter" on a
        // screen a client makes decisions from. Worth a test against the real bytes rather than an
        // invented approximation, because the producer line and the trailing CRLF are the parts an
        // invented one would get wrong.
        var provider = stub("BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:icalendar-ruby\r\n"
                + "CALSCALE:GREGORIAN\r\n"
                + "METHOD:PUBLISH\r\n"
                + "END:VCALENDAR\r\n");

        assertThatThrownBy(() -> provider.appointments(ORG, FROM, TO))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class)
                .hasMessageContaining("no events");
        // And the connection is reported as faulty afterwards, not healthy. The HTTP exchange
        // succeeded, so an earlier version answered CONNECTED here - but a feed that returns none
        // of the practice's appointments is a broken integration, and saying otherwise is how a
        // misconfigured token stays misconfigured.
        assertThat(provider.status(ORG)).isEqualTo(ProviderStatus.ERROR);
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

        String ref = provider.appointments(ORG, FROM, TO).stream()
                .filter(a -> a.displayLabel().contains("Ray Q")).findFirst().orElseThrow()
                .externalRef();
        assertThat(provider.appointment(ORG, ref).displayLabel()).isEqualTo("Weight Management with Ray Q");
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
    @DisplayName("accepts a webcal:// subscription link as well as an https one")
    void acceptsWebcal() {
        // Healthie hands this feed out as a calendar subscription link, and those carry the webcal
        // scheme by convention. Apple Calendar rewrites it to https before fetching; Java's HTTP
        // client does not, so the adapter does it instead of failing on the scheme.
        assertThat(HealthieIcsSchedulingProvider.normalizeUrl(
                        "webcal://app.gethealthie.com/appointments/ics_export.ics?access_token=t"))
                .isEqualTo("https://app.gethealthie.com/appointments/ics_export.ics?access_token=t");
        assertThat(HealthieIcsSchedulingProvider.normalizeUrl(
                        "WEBCAL://app.gethealthie.com/x.ics"))
                .isEqualTo("https://app.gethealthie.com/x.ics");
        assertThat(HealthieIcsSchedulingProvider.normalizeUrl("  https://example.test/x.ics  "))
                .isEqualTo("https://example.test/x.ics");
        assertThat(HealthieIcsSchedulingProvider.normalizeUrl(null)).isEmpty();
    }

    @Test
    @DisplayName("passes the contact type through, because that is all SUMMARY ever holds")
    void contactTypeIsTheSummary() {
        // The three values a real export actually uses, measured across 5,257 events. There is no
        // patient name anywhere in this feed - an earlier version assumed one and rendered
        // "In Person" as "In P." on the Calendar.
        assertThat(HealthieIcsSchedulingProvider.contactType("In Person")).isEqualTo("In Person");
        assertThat(HealthieIcsSchedulingProvider.contactType("Phone Call")).isEqualTo("Phone Call");
        assertThat(HealthieIcsSchedulingProvider.contactType("Healthie Video Call"))
                .isEqualTo("Healthie Video Call");
        assertThat(HealthieIcsSchedulingProvider.contactType("  In Person  ")).isEqualTo("In Person");
        assertThat(HealthieIcsSchedulingProvider.contactType("  ")).isNull();
        assertThat(HealthieIcsSchedulingProvider.contactType(null)).isNull();
    }

    @Test
    @DisplayName("identity survives Healthie regenerating every UID on every fetch")
    void identityIsDerivedFromContent() {
        // The bug this exists for, reproduced: the same calendar fetched twice returns the same
        // appointments under entirely different UIDs. Keyed on the UID, a sync re-inserted the
        // whole calendar every pass - 10,152 rows for 1,128 appointments before it was stopped.
        String first = calendar(event("uid-aaa", "20261015T143000Z", "20261015T151500Z", "Megan Carr", null));
        String second = calendar(event("uid-zzz", "20261015T143000Z", "20261015T151500Z", "Megan Carr", null));

        var one = stub(first).appointments(ORG, FROM, TO).get(0).externalRef();
        healthie.resetAll();
        var two = stub(second).appointments(ORG, FROM, TO).get(0).externalRef();

        assertThat(one).isEqualTo(two).startsWith("ics-").hasSize(44);
    }

    @Test
    @DisplayName("a rescheduled appointment gets a new identity, so the old row can be reconciled")
    void reschedulingChangesTheKey() {
        Instant at = Instant.parse("2026-10-15T14:30:00Z");
        String original = HealthieIcsSchedulingProvider.contentRef(at, at.plusSeconds(2700), "Megan Carr");
        String moved = HealthieIcsSchedulingProvider.contentRef(
                at.plusSeconds(3600), at.plusSeconds(6300), "Megan Carr");
        String otherPatient = HealthieIcsSchedulingProvider.contentRef(at, at.plusSeconds(2700), "Megan Carr Jr");

        // A move orphans the old row. That is precisely why reconcilesByAbsence is true.
        assertThat(moved).isNotEqualTo(original);
        assertThat(otherPatient).isNotEqualTo(original);
        assertThat(HealthieIcsSchedulingProvider.contentRef(at, at.plusSeconds(2700), "Megan Carr"))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("treats a feed that ends mid-document as unavailable")
    void refusesTruncatedFeed() {
        // Critical because absence drives cancellation: half a feed would cancel the other half of
        // the practice's calendar. iCalendar is a flat sequence, so a truncated body parses as a
        // shorter but perfectly valid list.
        String full = calendar(event("a", "20261015T143000Z", null, "Megan Carr", null));
        var provider = stub(full.substring(0, full.length() - 20));

        assertThatThrownBy(() -> provider.appointments(ORG, FROM, TO))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class)
                .hasMessageContaining("ended mid-document");
    }

    @Test
    @DisplayName("declares that a missing appointment means a cancelled one")
    void reconcilesByAbsence() {
        assertThat(providerFor("https://example.test/x.ics").reconcilesByAbsence()).isTrue();
    }
}
