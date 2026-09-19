package com.visiondigitallab.visionone.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The Overview's counting rules, against a real database.
 *
 * <p>A duplicate counts nowhere. A lead that jumped straight to booked still counts at every
 * earlier funnel stage. A KPI with no denominator is null rather than zero.
 */
@AutoConfigureMockMvc
class OverviewKpiTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID GOOGLE_ADS = UUID.fromString("0199a1d0-0001-7000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedFixture() {
        jdbc.sql("delete from lead_status_history where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from appointment_reference where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from lead where organization_id = :org").param("org", THRIVE).update();

        jdbc.sql("""
                insert into membership (id, organization_id, keycloak_user_id, role, display_name)
                values (:id, :org, :user, 'CLIENT_OWNER', 'Dr. Gary Adams')
                on conflict (organization_id, keycloak_user_id) do nothing
                """)
                .param("id", UUID.fromString("0199a1d0-0002-7000-8000-0000000000b1"))
                .param("org", THRIVE)
                .param("user", TestTokens.CLIENT_OWNER_SUBJECT)
                .update();

        // Three leads in March 2026: one that jumped straight to BOOKED, one NEW, one DUPLICATE.
        insertLead("0199a1d0-9000-7000-8000-000000000001", "BOOKED", "2026-03-04 10:00:00+00");
        insertHistory("0199a1d0-9100-7000-8000-000000000001", "0199a1d0-9000-7000-8000-000000000001", "NEW");
        insertHistory("0199a1d0-9100-7000-8000-000000000002", "0199a1d0-9000-7000-8000-000000000001", "QUALIFIED");
        insertHistory("0199a1d0-9100-7000-8000-000000000003", "0199a1d0-9000-7000-8000-000000000001", "APPOINTMENT_REQUESTED");
        insertHistory("0199a1d0-9100-7000-8000-000000000004", "0199a1d0-9000-7000-8000-000000000001", "BOOKED");

        insertLead("0199a1d0-9000-7000-8000-000000000002", "NEW", "2026-03-06 10:00:00+00");
        insertHistory("0199a1d0-9100-7000-8000-000000000005", "0199a1d0-9000-7000-8000-000000000002", "NEW");

        insertLead("0199a1d0-9000-7000-8000-000000000003", "DUPLICATE", "2026-03-08 10:00:00+00");
        insertHistory("0199a1d0-9100-7000-8000-000000000006", "0199a1d0-9000-7000-8000-000000000003", "NEW");

        jdbc.sql("""
                insert into appointment_reference (id, organization_id, lead_id, requested_at, scheduled_for, status)
                values (:id, :org, :lead, timestamptz '2026-03-05 10:00:00+00',
                        timestamptz '2026-03-10 10:00:00+00', 'BOOKED')
                on conflict (id) do nothing
                """)
                .param("id", UUID.fromString("0199a1d0-9200-7000-8000-000000000001"))
                .param("org", THRIVE)
                .param("lead", UUID.fromString("0199a1d0-9000-7000-8000-000000000001"))
                .update();
    }

    @Test
    @DisplayName("duplicates count nowhere and the funnel counts every stage ever reached")
    void funnelAndCountsFollowTheStatedRules() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", THRIVE)
                        .param("month", "2026-03")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                // Two leads, not three: the duplicate is excluded.
                .andExpect(jsonPath("$.kpis.newLeads").value(2))
                .andExpect(jsonPath("$.funnel.leads").value(2))
                // The booked lead counts at qualified and appointment-requested too.
                .andExpect(jsonPath("$.funnel.qualified").value(1))
                .andExpect(jsonPath("$.funnel.appointmentRequested").value(1))
                .andExpect(jsonPath("$.funnel.booked").value(1))
                .andExpect(jsonPath("$.kpis.bookedAppointments").value(1))
                .andExpect(jsonPath("$.kpis.leadToBookConversionPercent").value(50.0));
    }

    @Test
    @DisplayName("a month with no leads reports null cost per lead, never zero")
    void emptyMonthReportsNullRatios() throws Exception {
        String body = mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", THRIVE)
                        .param("month", "2026-01")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.newLeads").value(0))
                .andExpect(jsonPath("$.kpis.costPerLead").doesNotExist())
                .andExpect(jsonPath("$.kpis.leadToBookConversionPercent").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("\"costPerLead\":{\"amountMinor\":0");
    }

    private void insertLead(String id, String status, String createdAt) {
        jdbc.sql("""
                insert into lead (id, organization_id, channel_source_id, display_name,
                                  service_interest, status, created_at)
                values (:id, :org, :channel, 'Test Lead', 'GENERAL_ENQUIRY', :status, :createdAt::timestamptz)
                """)
                .param("id", UUID.fromString(id))
                .param("org", THRIVE)
                .param("channel", GOOGLE_ADS)
                .param("status", status)
                .param("createdAt", createdAt)
                .update();
    }

    private void insertHistory(String id, String leadId, String toStatus) {
        jdbc.sql("""
                insert into lead_status_history (id, organization_id, lead_id, to_status, changed_by)
                values (:id, :org, :lead, :toStatus, 'test')
                """)
                .param("id", UUID.fromString(id))
                .param("org", THRIVE)
                .param("lead", UUID.fromString(leadId))
                .param("toStatus", toStatus)
                .update();
    }

    private static RequestPostProcessor clientOwner() {
        var token = TestTokens.tokenFor(TestTokens.CLIENT_OWNER_SUBJECT, Role.CLIENT_OWNER);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }
}
