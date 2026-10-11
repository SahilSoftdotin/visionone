package com.visiondigitallab.visionone.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import com.visiondigitallab.visionone.integration.internal.HealthieGraphQlClient;
import com.visiondigitallab.visionone.integration.internal.HealthieSchedulingProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Healthie adapter, against a stubbed Healthie.
 *
 * <p>This proves the adapter is internally correct - the mapping, the date window Healthie cannot
 * apply itself, and the refusal to report an outage as an empty calendar. It cannot prove the field
 * names are right, because those come from Healthie's published schema and nothing here has spoken
 * to a real account. That gap closes when there is an API key, not before, and it is worth being
 * explicit about rather than letting green tests imply more than they check.
 */
class HealthieSchedulingProviderTest {

    private static final UUID ORG = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");

    private WireMockServer healthie;
    private HealthieSchedulingProvider provider;

    @BeforeEach
    void start() {
        healthie = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        healthie.start();
        var client = new HealthieGraphQlClient(
                healthie.baseUrl() + "/graphql", "test-key", "2024-06-01", 2000, 5000);
        provider = new HealthieSchedulingProvider(client, 100, 20, 45);
    }

    @AfterEach
    void stop() {
        healthie.stop();
    }

    private void stub(String body) {
        healthie.stubFor(post(urlPathEqualTo("/graphql"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    @DisplayName("maps Healthie's fields onto the port, reducing the patient to a first name and initial")
    void mapsAnAppointment() {
        stub("""
                {"data":{"appointments":[
                  {"id":"4821","date":"2026-09-14 14:30:00 UTC","length":60,
                   "pm_status":"Occurred",
                   "appointment_type":{"name":"Hormone Optimization Review"},
                   "user":{"full_name":"Dana Whitfield"}}
                ]}}
                """);

        List<SchedulingProvider.AppointmentReference> out =
                provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(out).hasSize(1);
        var a = out.get(0);
        assertThat(a.externalRef()).isEqualTo("4821");
        assertThat(a.startsAt()).isEqualTo(java.time.Instant.parse("2026-09-14T14:30:00Z"));
        assertThat(a.durationMinutes()).isEqualTo(60);
        // Not "Dana Whitfield". Enough to recognise the booking, not enough to identify someone.
        assertThat(a.displayLabel()).isEqualTo("Dana W.");
        // Our category, not Healthie's wording.
        assertThat(a.serviceCategory()).isEqualTo("HORMONE_OPTIMIZATION");
        assertThat(a.status()).isEqualTo(SchedulingProvider.AppointmentState.ATTENDED);
        // Healthie cannot know which advertisement produced a patient. Null, not a guess.
        assertThat(a.channelCode()).isNull();
    }

    @Test
    @DisplayName("the query never asks for anything the port forbids us to keep")
    void theQueryIsThePhiBoundary() {
        stub("{\"data\":{\"appointments\":[]}}");
        provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        var sent = healthie.findAll(postRequestedFor(urlPathEqualTo("/graphql")));
        assertThat(sent).hasSize(1);
        String body = sent.get(0).getBodyAsString();

        // Healthie's Appointment exposes all of these. Asking for none of them is the boundary:
        // data never fetched cannot be logged, surfaced in an error, or persisted by a later change.
        assertThat(body)
                .doesNotContain("phone_number")
                .doesNotContain("avatar_url")
                .doesNotContain("form_answer_group")
                .doesNotContain("filled_embed_form")
                .doesNotContain("contact_type")
                .doesNotContain("location")
                .doesNotContain("provider");
        // And it does ask for exactly what it maps.
        assertThat(body).contains("pm_status").contains("full_name").contains("length");
    }

    @Test
    @DisplayName("applies the date window itself, because Healthie's query cannot")
    void filtersByDateRange() {
        stub("""
                {"data":{"appointments":[
                  {"id":"before","date":"2026-08-31 23:00:00 UTC","length":30,"pm_status":null,
                   "appointment_type":{"name":"Consult"},"user":{"full_name":"Early Bird"}},
                  {"id":"inside","date":"2026-09-15 09:00:00 UTC","length":30,"pm_status":null,
                   "appointment_type":{"name":"Consult"},"user":{"full_name":"Right Time"}},
                  {"id":"after","date":"2026-10-01 00:00:00 UTC","length":30,"pm_status":null,
                   "appointment_type":{"name":"Consult"},"user":{"full_name":"Too Late"}}
                ]}}
                """);

        var out = provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(out).extracting(SchedulingProvider.AppointmentReference::externalRef)
                .containsExactly("inside");
    }

    @Test
    @DisplayName("every pm_status maps, and an empty one is a live booking rather than a gap")
    void mapsEveryStatus() {
        record Case(String healthie, SchedulingProvider.AppointmentState expected) {}
        List<Case> cases = List.of(
                new Case("Occurred", SchedulingProvider.AppointmentState.ATTENDED),
                new Case("No-Show", SchedulingProvider.AppointmentState.NO_SHOW),
                new Case("Re-Scheduled", SchedulingProvider.AppointmentState.RESCHEDULED),
                new Case("Cancelled", SchedulingProvider.AppointmentState.CANCELLED),
                new Case("Late Cancellation", SchedulingProvider.AppointmentState.CANCELLED),
                new Case("Checked-In", SchedulingProvider.AppointmentState.BOOKED),
                new Case(null, SchedulingProvider.AppointmentState.BOOKED));

        for (Case c : cases) {
            healthie.resetAll();
            stub("""
                    {"data":{"appointments":[
                      {"id":"1","date":"2026-09-10 10:00:00 UTC","length":45,"pm_status":%s,
                       "appointment_type":{"name":"Consult"},"user":{"full_name":"A B"}}
                    ]}}
                    """.formatted(c.healthie() == null ? "null" : "\"" + c.healthie() + "\""));

            var out = provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
            assertThat(out).singleElement()
                    .extracting(SchedulingProvider.AppointmentReference::status)
                    .as("pm_status %s", c.healthie())
                    .isEqualTo(c.expected());
        }
    }

    @Test
    @DisplayName("a GraphQL errors array is an outage, not an empty calendar")
    void graphQlErrorsBecomeAnOutage() {
        // GraphQL answers 200 with an errors array, so a successful status proves nothing. Returning
        // an empty list here would read on screen as "a quiet month" - the opposite of the truth.
        stub("{\"errors\":[{\"message\":\"Unauthorized\"}],\"data\":null}");

        assertThatThrownBy(() ->
                provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class);
    }

    @Test
    @DisplayName("an HTTP failure is an outage too")
    void httpFailureBecomesAnOutage() {
        healthie.stubFor(post(urlPathEqualTo("/graphql"))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"bad key\"}")));

        assertThatThrownBy(() ->
                provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isInstanceOf(SchedulingProvider.SchedulingUnavailableException.class);
    }

    @Test
    @DisplayName("status reports CONNECTED only when the key actually works")
    void statusReflectsReality() {
        stub("{\"data\":{\"currentUser\":{\"id\":\"1\"}}}");
        assertThat(provider.status(ORG)).isEqualTo(ProviderStatus.CONNECTED);

        healthie.resetAll();
        healthie.stubFor(post(urlPathEqualTo("/graphql"))
                .willReturn(aResponse().withStatus(500)));
        assertThat(provider.status(ORG)).isEqualTo(ProviderStatus.ERROR);
    }

    @Test
    @DisplayName("one appointment by id, and null when Healthie no longer has it")
    void fetchesOneAppointment() {
        stub("""
                {"data":{"appointment":
                  {"id":"4821","date":"2026-09-14 14:30:00 UTC","length":30,"pm_status":"Cancelled",
                   "appointment_type":{"name":"IV Therapy"},"user":{"full_name":"Sam Okonkwo"}}}}
                """);
        var one = provider.appointment(ORG, "4821");
        assertThat(one).isNotNull();
        assertThat(one.displayLabel()).isEqualTo("Sam O.");
        assertThat(one.serviceCategory()).isEqualTo("IV_THERAPY");
        assertThat(one.status()).isEqualTo(SchedulingProvider.AppointmentState.CANCELLED);

        healthie.resetAll();
        stub("{\"data\":{\"appointment\":null}}");
        assertThat(provider.appointment(ORG, "gone")).isNull();
    }

    @Test
    @DisplayName("an unmapped appointment type says nothing rather than leaking Healthie's wording")
    void unmappedTypeIsNull() {
        stub("""
                {"data":{"appointments":[
                  {"id":"9","date":"2026-09-10 10:00:00 UTC","length":45,"pm_status":null,
                   "appointment_type":{"name":"Something We Have Never Heard Of"},
                   "user":{"full_name":"A B"}}
                ]}}
                """);
        var out = provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(out).singleElement()
                .extracting(SchedulingProvider.AppointmentReference::serviceCategory)
                .isNull();
    }

    @Test
    @DisplayName("a row with no usable date is skipped rather than placed wrongly on the calendar")
    void unparseableDateIsSkipped() {
        stub("""
                {"data":{"appointments":[
                  {"id":"bad","date":"not a date","length":45,"pm_status":null,
                   "appointment_type":{"name":"Consult"},"user":{"full_name":"A B"}},
                  {"id":"good","date":"2026-09-10 10:00:00 UTC","length":45,"pm_status":null,
                   "appointment_type":{"name":"Consult"},"user":{"full_name":"C D"}}
                ]}}
                """);
        var out = provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(out).extracting(SchedulingProvider.AppointmentReference::externalRef)
                .containsExactly("good");
    }

    @Test
    @DisplayName("sends the API key, the source header and a pinned API version")
    void sendsTheRequiredHeaders() {
        stub("{\"data\":{\"appointments\":[]}}");
        provider.appointments(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        var sent = healthie.findAll(postRequestedFor(urlPathEqualTo("/graphql"))).get(0);
        assertThat(sent.getHeader("Authorization")).isEqualTo("Basic test-key");
        assertThat(sent.getHeader("AuthorizationSource")).isEqualTo("API");
        // Pinned, so a field cannot change shape under us without a deploy.
        assertThat(sent.getHeader("Healthie-GraphQL-API-Version")).isEqualTo("2024-06-01");
    }
}
