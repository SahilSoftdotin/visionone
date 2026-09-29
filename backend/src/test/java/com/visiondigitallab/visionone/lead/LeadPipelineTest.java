package com.visiondigitallab.visionone.lead;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.visiondigitallab.visionone.auth.JwtRoleConverter;
import com.visiondigitallab.visionone.auth.Role;
import com.visiondigitallab.visionone.support.PostgresIntegrationTest;
import com.visiondigitallab.visionone.support.TestTokens;
import java.util.UUID;
import org.assertj.core.api.Assertions;
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
 * The Leads screen and the Week-3 promise: a lead moves, and the record of how it moved is the
 * thing the funnel counts.
 */
@AutoConfigureMockMvc
class LeadPipelineTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID GOOGLE_ADS = UUID.fromString("0199a1d0-0001-7000-8000-000000000001");
    private static final UUID LEAD_FAST = UUID.fromString("0199a1d0-a000-7000-8000-000000000001");
    private static final UUID LEAD_SLOW = UUID.fromString("0199a1d0-a000-7000-8000-000000000002");
    private static final UUID LEAD_DUPLICATE = UUID.fromString("0199a1d0-a000-7000-8000-000000000003");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from outbox_event where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from lead_status_history where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from appointment_reference where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from lead where organization_id = :org").param("org", THRIVE).update();

        membership("0199a1d0-0002-7000-8000-0000000000d1", TestTokens.VISION_ADMIN_SUBJECT, "VISION_ADMIN");
        membership("0199a1d0-0002-7000-8000-0000000000d2", TestTokens.CLIENT_OWNER_SUBJECT, "CLIENT_OWNER");

        // Answered in 6 minutes, reached QUALIFIED.
        lead(LEAD_FAST, "QUALIFIED", "2026-04-03 14:00:00+00", "2026-04-03 14:06:00+00");
        history(LEAD_FAST, null, "NEW", "2026-04-03 14:00:00+00");
        history(LEAD_FAST, "NEW", "CONTACTED", "2026-04-03 14:06:00+00");
        history(LEAD_FAST, "CONTACTED", "QUALIFIED", "2026-04-04 09:00:00+00");

        // Answered after two hours, never progressed.
        lead(LEAD_SLOW, "CONTACTED", "2026-04-05 10:00:00+00", "2026-04-05 12:00:00+00");
        history(LEAD_SLOW, null, "NEW", "2026-04-05 10:00:00+00");
        history(LEAD_SLOW, "NEW", "CONTACTED", "2026-04-05 12:00:00+00");

        // Counts nowhere.
        lead(LEAD_DUPLICATE, "DUPLICATE", "2026-04-06 10:00:00+00", null);
        history(LEAD_DUPLICATE, null, "NEW", "2026-04-06 10:00:00+00");
    }

    @Test
    @DisplayName("the summary excludes duplicates and reports a median, not a mean")
    void summaryFollowsTheStatedRules() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/leads", THRIVE)
                        .param("month", "2026-04")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                // Three leads seeded, one is a duplicate.
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.summary.qualified").value(1))
                .andExpect(jsonPath("$.summary.booked").value(0))
                // Responses are 6 and 120 minutes; one of the two is inside 15.
                .andExpect(jsonPath("$.summary.underFifteenMinutesPercent").value(50.0))
                // Even-sized sample takes the upper of the two middles. Pinned deliberately: a
                // front desk should not get credit for a median that rounds in its favour.
                .andExpect(jsonPath("$.summary.medianResponseMinutes").value(120))
                .andExpect(jsonPath("$.leads.length()").value(3))
                // The client reads the pipeline and never edits it.
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    @DisplayName("every lead carries a readable reference and its source name")
    void rowsCarryReferenceAndSource() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/leads", THRIVE)
                        .param("month", "2026-04")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                // Newest first, so the duplicate seeded on the 6th leads.
                .andExpect(jsonPath("$.leads[0].reference").exists())
                .andExpect(jsonPath("$.leads[0].source").value("Google Ads"))
                .andExpect(jsonPath("$.leads[0].owner").value("Unassigned"));
    }

    @Test
    @DisplayName("a month with no responses reports no median rather than zero")
    void emptyMonthReportsNulls() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/leads", THRIVE)
                        .param("month", "2026-01")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.medianResponseMinutes").doesNotExist())
                .andExpect(jsonPath("$.summary.underFifteenMinutesPercent").doesNotExist());
    }

    @Test
    @DisplayName("lead detail returns the stages it passed through, oldest first")
    void detailReturnsHistoryInOrder() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/leads/{leadId}", THRIVE, LEAD_FAST)
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.status").value("QUALIFIED"))
                .andExpect(jsonPath("$.lead.responseMinutes").value(6))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[0].toStatus").value("NEW"))
                .andExpect(jsonPath("$.history[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.history[2].toStatus").value("QUALIFIED"));
    }

    @Test
    @DisplayName("Vision Admin moves a lead, and the history and the event both record it")
    void adminTransitionAppendsHistoryAndPublishes() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/leads/{leadId}/status", THRIVE, LEAD_SLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"BOOKED\",\"reason\":\"Booked on the follow-up call\"}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead.status").value("BOOKED"))
                .andExpect(jsonPath("$.lead.booked").value(true))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[2].toStatus").value("BOOKED"))
                .andExpect(jsonPath("$.history[2].reason").value("Booked on the follow-up call"));

        Assertions.assertThat(outboxCount("LeadStatusChanged")).isEqualTo(1);
    }

    @Test
    @DisplayName("moving a lead to the status it already has changes nothing")
    void noOpTransitionIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/leads/{leadId}/status", THRIVE, LEAD_SLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"CONTACTED\"}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                // Still the two stages it was seeded with; a double-clicked control is not an error.
                .andExpect(jsonPath("$.history.length()").value(2));

        Assertions.assertThat(outboxCount("LeadStatusChanged")).isZero();
    }

    @Test
    @DisplayName("the client owner cannot move a lead")
    void clientOwnerCannotTransition() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/leads/{leadId}/status", THRIVE, LEAD_SLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"BOOKED\"}")
                        .with(clientOwner()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unknown status is rejected rather than stored")
    void unknownStatusIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/leads/{leadId}/status", THRIVE, LEAD_SLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"WARM\"}")
                        .with(visionAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a lead id that is not in this organization reads as absent")
    void unknownLeadIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/leads/{leadId}", THRIVE, UUID.randomUUID())
                        .with(clientOwner()))
                .andExpect(status().isNotFound());
    }

    private long outboxCount(String eventType) {
        return jdbc.sql("select count(*) from outbox_event where organization_id = :org "
                        + "and event_type = :type")
                .param("org", THRIVE)
                .param("type", eventType)
                .query(Long.class)
                .single();
    }

    private void lead(UUID id, String status, String createdAt, String firstResponseAt) {
        jdbc.sql("""
                insert into lead (id, organization_id, channel_source_id, display_name,
                                  service_interest, status, created_at, first_response_at)
                values (:id, :org, :channel, 'Test Lead', 'GENERAL_ENQUIRY', :status,
                        :createdAt::timestamptz, :firstResponseAt::timestamptz)
                """)
                .param("id", id)
                .param("org", THRIVE)
                .param("channel", GOOGLE_ADS)
                .param("status", status)
                .param("createdAt", createdAt)
                .param("firstResponseAt", firstResponseAt)
                .update();
    }

    private void history(UUID leadId, String from, String to, String changedAt) {
        jdbc.sql("""
                insert into lead_status_history (id, organization_id, lead_id, from_status,
                                                 to_status, changed_at, changed_by)
                values (:id, :org, :lead, :from, :to, :changedAt::timestamptz, 'seed')
                """)
                .param("id", UUID.randomUUID())
                .param("org", THRIVE)
                .param("lead", leadId)
                .param("from", from)
                .param("to", to)
                .param("changedAt", changedAt)
                .update();
    }

    private void membership(String id, String subject, String role) {
        jdbc.sql("""
                insert into membership (id, organization_id, keycloak_user_id, role, display_name)
                values (:id, :org, :user, :role, 'Test User')
                on conflict (organization_id, keycloak_user_id) do nothing
                """)
                .param("id", UUID.fromString(id))
                .param("org", THRIVE)
                .param("user", subject)
                .param("role", role)
                .update();
    }

    private static RequestPostProcessor visionAdmin() {
        var token = TestTokens.tokenFor(TestTokens.VISION_ADMIN_SUBJECT, Role.VISION_ADMIN);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }

    private static RequestPostProcessor clientOwner() {
        var token = TestTokens.tokenFor(TestTokens.CLIENT_OWNER_SUBJECT, Role.CLIENT_OWNER);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }
}
