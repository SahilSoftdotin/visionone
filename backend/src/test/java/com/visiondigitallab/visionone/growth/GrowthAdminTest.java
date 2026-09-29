package com.visiondigitallab.visionone.growth;

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
 * The Week-2 promise, as a test: Vision changes a budget and the client's screens show it.
 *
 * <p>Also pins the asymmetry the product depends on - Vision operates, the client decides - by
 * asserting a CLIENT_OWNER is refused at the endpoint, not merely hidden in the UI.
 */
@AutoConfigureMockMvc
class GrowthAdminTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID GOOGLE_ADS = UUID.fromString("0199a1d0-0001-7000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedMemberships() {
        jdbc.sql("delete from budget_allocation where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from growth_plan where organization_id = :org").param("org", THRIVE).update();

        membership("0199a1d0-0002-7000-8000-0000000000c1", TestTokens.VISION_ADMIN_SUBJECT, "VISION_ADMIN");
        membership("0199a1d0-0002-7000-8000-0000000000c2", TestTokens.CLIENT_OWNER_SUBJECT, "CLIENT_OWNER");
    }

    @Test
    @DisplayName("Vision Admin sets a budget and the client immediately reads the new figure")
    void adminSetsBudgetAndClientSeesIt() throws Exception {
        // No plan exists for this month: the admin write must create one rather than 404.
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/plan", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedTotalMinor\":650000,\"notes\":\"Shifted to local search\"}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedTotal.amountMinor").value(650000))
                .andExpect(jsonPath("$.editable").value(true));

        mockMvc.perform(get("/api/v1/orgs/{orgId}/growth/plan", THRIVE)
                        .param("month", "2026-11")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedTotal.amountMinor").value(650000))
                .andExpect(jsonPath("$.notes").value("Shifted to local search"))
                // Read-only for the client, and the UI is told so rather than guessing.
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    @DisplayName("actual spend flows into remaining budget and utilization")
    void allocationsDriveTotalsAndUtilization() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/plan", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedTotalMinor\":500000}")
                        .with(visionAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/allocations", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allocations\":[{\"channelSourceId\":\"" + GOOGLE_ADS
                                + "\",\"plannedMinor\":200000,\"actualMinor\":125000}]}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualTotal.amountMinor").value(125000))
                .andExpect(jsonPath("$.remaining.amountMinor").value(375000))
                .andExpect(jsonPath("$.utilizationPercent").value(25.0));
    }

    @Test
    @DisplayName("a month with no plan reads as zero rather than failing")
    void missingPlanReadsAsEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/growth/plan", THRIVE)
                        .param("month", "2027-05")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.plannedTotal.amountMinor").value(0))
                .andExpect(jsonPath("$.utilizationPercent").value(0.0));
    }

    @Test
    @DisplayName("the client owner cannot write a budget, whatever the UI shows")
    void clientOwnerCannotSetBudget() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/plan", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedTotalMinor\":1}")
                        .with(clientOwner()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a negative budget is rejected by validation, not stored")
    void negativeBudgetIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/plan", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedTotalMinor\":-500}")
                        .with(visionAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("allocating to another organization's channel is refused")
    void foreignChannelIsRefused() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/plan", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedTotalMinor\":500000}")
                        .with(visionAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/allocations", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allocations\":[{\"channelSourceId\":\"" + UUID.randomUUID()
                                + "\",\"plannedMinor\":1,\"actualMinor\":1}]}")
                        .with(visionAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a budget change writes an outbox event rather than publishing inline")
    void budgetChangeIsRecordedInTheOutbox() throws Exception {
        long before = outboxCount();

        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/growth/plan", THRIVE)
                        .param("month", "2026-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plannedTotalMinor\":700000}")
                        .with(visionAdmin()))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(outboxCount()).isEqualTo(before + 1);
    }

    private long outboxCount() {
        return jdbc.sql("select count(*) from outbox_event where organization_id = :org "
                        + "and event_type = 'BudgetUpdated'")
                .param("org", THRIVE)
                .query(Long.class)
                .single();
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
