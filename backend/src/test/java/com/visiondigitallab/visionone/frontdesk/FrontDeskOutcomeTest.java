package com.visiondigitallab.visionone.frontdesk;

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
 * The Front Desk outcome filter.
 *
 * <p>The rule these tests exist to hold: filtering narrows the call list and nothing else. The
 * summary tiles and the filter's own counts are always the whole month, because the pills sit
 * directly beneath the tiles and the same word carrying two numbers on one screen is how a
 * dashboard loses its reader.
 */
@AutoConfigureMockMvc
class FrontDeskOutcomeTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedCalls() {
        jdbc.sql("delete from call where organization_id = :org").param("org", THRIVE).update();

        jdbc.sql("""
                insert into membership (id, organization_id, keycloak_user_id, role, display_name)
                values (:id, :org, :user, 'CLIENT_OWNER', 'Dr. Gary Adams')
                on conflict (organization_id, keycloak_user_id) do nothing
                """)
                .param("id", UUID.fromString("0199a1d0-0002-7000-8000-0000000000c1"))
                .param("org", THRIVE)
                .param("user", TestTokens.CLIENT_OWNER_SUBJECT)
                .update();

        // Five calls in May 2026: three missed, one booked, one answered enquiry.
        insertCall("0199a1d0-a000-7000-8000-000000000001", "MISSED", false, "2026-05-04 10:00:00+00");
        insertCall("0199a1d0-a000-7000-8000-000000000002", "MISSED", false, "2026-05-05 10:00:00+00");
        insertCall("0199a1d0-a000-7000-8000-000000000003", "MISSED", false, "2026-05-06 10:00:00+00");
        insertCall("0199a1d0-a000-7000-8000-000000000004", "BOOKED", true, "2026-05-07 10:00:00+00");
        insertCall(
                "0199a1d0-a000-7000-8000-000000000005", "ENQUIRY_ANSWERED", true, "2026-05-08 10:00:00+00");
    }

    private void insertCall(String id, String outcome, boolean answered, String startedAt) {
        jdbc.sql("""
                insert into call (id, organization_id, direction, started_at, duration_seconds,
                                  outcome, answered, caller_label)
                values (:id, :org, 'INBOUND', timestamptz '%s', 120, :outcome, :answered, 'Caller A.')
                on conflict (id) do nothing
                """.formatted(startedAt))
                .param("id", UUID.fromString(id))
                .param("org", THRIVE)
                .param("outcome", outcome)
                .param("answered", answered)
                .update();
    }

    @Test
    @DisplayName("outcome counts cover the whole month, not the capped list")
    void countsAreForTheMonth() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/frontdesk", THRIVE)
                        .param("month", "2026-05")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalCalls").value(5))
                .andExpect(jsonPath("$.summary.missed").value(3))
                .andExpect(jsonPath("$.outcomeCounts.length()").value(3))
                // Declaration order, so the pill row does not reshuffle between refreshes.
                .andExpect(jsonPath("$.outcomeCounts[0].outcome").value("BOOKED"))
                .andExpect(jsonPath("$.outcomeCounts[0].count").value(1))
                .andExpect(jsonPath("$.outcomeCounts[1].outcome").value("ENQUIRY_ANSWERED"))
                .andExpect(jsonPath("$.outcomeCounts[2].outcome").value("MISSED"))
                .andExpect(jsonPath("$.outcomeCounts[2].count").value(3));
    }

    @Test
    @DisplayName("filtering narrows the call list and leaves every figure above it alone")
    void filterTouchesTheListOnly() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/frontdesk", THRIVE)
                        .param("month", "2026-05")
                        .param("outcome", "MISSED")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent.length()").value(3))
                .andExpect(jsonPath("$.recent[0].outcome").value("MISSED"))
                .andExpect(jsonPath("$.recent[2].outcome").value("MISSED"))
                // The whole point: the tiles and the pills do not move when the table does.
                .andExpect(jsonPath("$.summary.totalCalls").value(5))
                .andExpect(jsonPath("$.summary.missed").value(3))
                .andExpect(jsonPath("$.outcomeCounts.length()").value(3))
                .andExpect(jsonPath("$.outcomeCounts[0].outcome").value("BOOKED"));
    }

    @Test
    @DisplayName("an outcome with no calls this month returns an empty list, not an error")
    void filteringToNothingIsNotAFailure() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/frontdesk", THRIVE)
                        .param("month", "2026-05")
                        .param("outcome", "VOICEMAIL")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent.length()").value(0))
                .andExpect(jsonPath("$.summary.totalCalls").value(5));
    }

    @Test
    @DisplayName("an unrecognised outcome reads as no filter, because it arrives from a URL")
    void junkOutcomeIsIgnored() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/frontdesk", THRIVE)
                        .param("month", "2026-05")
                        .param("outcome", "NOT_AN_OUTCOME")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent.length()").value(5));
    }

    private static RequestPostProcessor clientOwner() {
        var token = TestTokens.tokenFor(TestTokens.CLIENT_OWNER_SUBJECT, Role.CLIENT_OWNER);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }
}
