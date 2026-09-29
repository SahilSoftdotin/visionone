package com.visiondigitallab.visionone.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.visiondigitallab.visionone.auth.JwtRoleConverter;
import com.visiondigitallab.visionone.auth.Role;
import com.visiondigitallab.visionone.support.PostgresIntegrationTest;
import com.visiondigitallab.visionone.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Who may change what on the Calendar.
 *
 * <p>Two rules, and the second is the unusual one. Nobody may reschedule, cancel or re-time an
 * appointment from VisionOne, because the practice's scheduling system owns those and VisionOne
 * showing "cancelled" while a patient is still booked is a fact about someone's week rather than a
 * stale read. And attribution inverts the app's usual pattern: Vision writes it, the practice only
 * reads it, because which advertisement produced a patient is Vision's data and Vision's job.
 */
@AutoConfigureMockMvc
class CalendarRightsTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID ACME = UUID.fromString("0199a1d0-0000-7000-8000-0000000000fe");
    private static final UUID GOOGLE_ADS = UUID.fromString("0199a1d0-0001-7000-8000-000000000001");
    private static final UUID GOOGLE_MAPS = UUID.fromString("0199a1d0-0001-7000-8000-000000000003");
    private static final UUID ACME_CHANNEL = UUID.fromString("0199a1d0-0001-7000-8000-0000000000fe");
    private static final UUID APPOINTMENT = UUID.fromString("0199a1d0-b000-7000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from appointment_reference where id = :id").param("id", APPOINTMENT).update();

        jdbc.sql("""
                insert into membership (id, organization_id, keycloak_user_id, role, display_name)
                values (:id, :org, :user, 'CLIENT_OWNER', 'Dr. Gary Adams')
                on conflict (organization_id, keycloak_user_id) do nothing
                """)
                .param("id", UUID.fromString("0199a1d0-0002-7000-8000-0000000000d1"))
                .param("org", THRIVE)
                .param("user", TestTokens.CLIENT_OWNER_SUBJECT)
                .update();

        jdbc.sql("""
                insert into membership (id, organization_id, keycloak_user_id, role, display_name)
                values (:id, :org, :user, 'VISION_ADMIN', 'Sahil Arora')
                on conflict (organization_id, keycloak_user_id) do nothing
                """)
                .param("id", UUID.fromString("0199a1d0-0002-7000-8000-0000000000d2"))
                .param("org", THRIVE)
                .param("user", TestTokens.VISION_ADMIN_SUBJECT)
                .update();

        // A second practice, so "credit another practice's channel" is a case we can actually make.
        jdbc.sql("""
                insert into organization (id, name, slug, timezone, currency)
                values (:id, 'ACME Dental', 'acme-cal', 'America/Chicago', 'USD')
                on conflict (id) do nothing
                """).param("id", ACME).update();

        jdbc.sql("""
                insert into channel_source (id, organization_id, code, display_name, category, sort_order)
                values (:id, :org, 'ACME_ADS', 'ACME Ads', 'PAID', 10)
                on conflict (id) do nothing
                """).param("id", ACME_CHANNEL).param("org", ACME).update();

        // One booking in June 2026, credited to Google Ads.
        jdbc.sql("""
                insert into appointment_reference
                    (id, organization_id, requested_at, scheduled_for, status, display_label,
                     duration_minutes, service_category, channel_source_id)
                values (:id, :org, timestamptz '2026-06-01 09:00:00+00',
                        timestamptz '2026-06-10 14:00:00+00', 'BOOKED', 'Dana W.',
                        45, 'LONGEVITY_CONSULT', :channel)
                """)
                .param("id", APPOINTMENT)
                .param("org", THRIVE)
                .param("channel", GOOGLE_ADS)
                .update();
    }

    @Test
    @DisplayName("the practice reads the calendar but is not offered the attribution control")
    void practiceSeesReadOnlyCalendar() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/calendar", THRIVE)
                        .param("month", "2026-06")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.days[0].appointments[0].source").value("Google Ads"));
    }

    @Test
    @DisplayName("the practice cannot change attribution, and the row is untouched after trying")
    void practiceCannotAttribute() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/calendar/{id}/attribution", THRIVE, APPOINTMENT)
                        .param("month", "2026-06")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelSourceId\":\"" + GOOGLE_MAPS + "\"}")
                        .with(clientOwner()))
                .andExpect(status().isForbidden());

        assertThat(currentChannel()).isEqualTo(GOOGLE_ADS);
    }

    @Test
    @DisplayName("Vision corrects attribution and the calendar comes back already showing it")
    void visionAttributes() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/calendar/{id}/attribution", THRIVE, APPOINTMENT)
                        .param("month", "2026-06")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelSourceId\":\"" + GOOGLE_MAPS + "\"}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.days[0].appointments[0].source").value("Google Maps"));

        assertThat(currentChannel()).isEqualTo(GOOGLE_MAPS);
    }

    @Test
    @DisplayName("clearing attribution is allowed, because 'we do not know' has to stay sayable")
    void attributionCanBeCleared() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/calendar/{id}/attribution", THRIVE, APPOINTMENT)
                        .param("month", "2026-06")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelSourceId\":null}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                // Null, not "Direct": a guess here would credit a channel that earned nothing.
                .andExpect(jsonPath("$.days[0].appointments[0].source").doesNotExist());

        assertThat(currentChannel()).isNull();
    }

    @Test
    @DisplayName("another practice's channel cannot be credited, even by Vision")
    void channelMustBelongToThisOrganization() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/calendar/{id}/attribution", THRIVE, APPOINTMENT)
                        .param("month", "2026-06")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelSourceId\":\"" + ACME_CHANNEL + "\"}")
                        .with(visionAdmin()))
                .andExpect(status().isNotFound());

        assertThat(currentChannel()).isEqualTo(GOOGLE_ADS);
    }

    @Test
    @DisplayName("there is no endpoint that reschedules, cancels or re-times an appointment")
    void schedulingItselfIsNotWritable() throws Exception {
        // Healthie owns these. If any of these ever answer, the promise in SchedulingProvider's
        // javadoc has been broken and this test is the place that says so.
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/calendar/{id}", THRIVE, APPOINTMENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}")
                        .with(visionAdmin()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/calendar/{id}/status", THRIVE, APPOINTMENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}")
                        .with(visionAdmin()))
                .andExpect(status().isNotFound());

        assertThat(currentStatus()).isEqualTo("BOOKED");
    }

    private UUID currentChannel() {
        return jdbc.sql("select channel_source_id from appointment_reference where id = :id")
                .param("id", APPOINTMENT)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private String currentStatus() {
        return jdbc.sql("select status from appointment_reference where id = :id")
                .param("id", APPOINTMENT)
                .query(String.class)
                .single();
    }

    private static RequestPostProcessor clientOwner() {
        var token = TestTokens.tokenFor(TestTokens.CLIENT_OWNER_SUBJECT, Role.CLIENT_OWNER);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }

    private static RequestPostProcessor visionAdmin() {
        var token = TestTokens.tokenFor(TestTokens.VISION_ADMIN_SUBJECT, Role.VISION_ADMIN);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }
}
