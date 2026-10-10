package com.visiondigitallab.visionone.tenant;

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

/**
 * The tenant-isolation matrix, as executable tests.
 *
 * <p>Two organizations exist here: THRIVE from the migrations, and ACME inserted by this fixture.
 * Every row asserts that a caller cannot reach data outside their own membership.
 */
@AutoConfigureMockMvc
class TenantIsolationTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID ACME = UUID.fromString("0199a1d0-0000-7000-8000-0000000000ff");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seedSecondOrganization() {
        jdbc.sql("""
                insert into organization (id, name, slug, timezone, currency)
                values (:id, 'ACME Dental', 'acme', 'America/Chicago', 'USD')
                on conflict (id) do nothing
                """).param("id", ACME).update();

        // Gary is a CLIENT_OWNER at THRIVE and nowhere else.
        jdbc.sql("""
                insert into membership (id, organization_id, keycloak_user_id, role, display_name)
                values (:id, :org, :user, 'CLIENT_OWNER', 'Dr. Gary Adams')
                on conflict (organization_id, keycloak_user_id) do nothing
                """)
                .param("id", UUID.fromString("0199a1d0-0002-7000-8000-0000000000a1"))
                .param("org", THRIVE)
                .param("user", TestTokens.CLIENT_OWNER_SUBJECT)
                .update();

        // Sahil is a VISION_ADMIN at THRIVE only - the role does not grant reach into ACME.
        jdbc.sql("""
                insert into membership (id, organization_id, keycloak_user_id, role, display_name)
                values (:id, :org, :user, 'VISION_ADMIN', 'Sahil Arora')
                on conflict (organization_id, keycloak_user_id) do nothing
                """)
                .param("id", UUID.fromString("0199a1d0-0002-7000-8000-0000000000a2"))
                .param("org", THRIVE)
                .param("user", TestTokens.VISION_ADMIN_SUBJECT)
                .update();
    }

    @Test
    @DisplayName("/me carries the inactivity policy the browser enforces")
    void sessionCarriesTheIdlePolicy() throws Exception {
        // The browser reads these two off /me and starts its idle timer from them. Asserted
        // through the wire rather than against the record, because the risk is the field name and
        // the nesting - a renamed or unserialized sessionPolicy leaves the SPA silently falling
        // back to its own defaults, which looks exactly like it working.
        mockMvc.perform(get("/api/v1/me").with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionPolicy.idleTimeoutMinutes").value(15))
                .andExpect(jsonPath("$.sessionPolicy.warningSeconds").value(60));
    }

    @Test
    @DisplayName("Client Owner of THRIVE reads THRIVE overview")
    void clientOwnerReadsOwnOrganization() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", THRIVE)
                        .param("month", "2026-09")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("THRIVE Longevity Center"));
    }

    @Test
    @DisplayName("Client Owner of THRIVE reading ACME is forbidden, not given an empty dashboard")
    void clientOwnerCannotReadAnotherOrganization() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", ACME)
                        .param("month", "2026-09")
                        .with(clientOwner()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vision Admin without membership in ACME is forbidden")
    void visionAdminCannotReachOrganizationTheyAreNotAMemberOf() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", ACME)
                        .param("month", "2026-09")
                        .with(visionAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A caller with no membership anywhere is forbidden")
    void outsiderIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", THRIVE)
                        .param("month", "2026-09")
                        .with(jwt().jwt(TestTokens.tokenFor(TestTokens.OUTSIDER_SUBJECT, Role.CLIENT_OWNER))
                                .authorities(new JwtRoleConverter()
                                        .convert(TestTokens.tokenFor(
                                                TestTokens.OUTSIDER_SUBJECT, Role.CLIENT_OWNER))
                                        .getAuthorities())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No token is unauthorized")
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", THRIVE).param("month", "2026-09"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token with its role stripped is forbidden")
    void tokenWithoutRolesIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", THRIVE)
                        .param("month", "2026-09")
                        .with(jwt().jwt(TestTokens.tokenWithoutRoles(TestTokens.CLIENT_OWNER_SUBJECT))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An unparseable organization id is not found")
    void malformedOrganizationIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/overview", "not-a-uuid").with(clientOwner()))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor clientOwner() {
        var token = TestTokens.tokenFor(TestTokens.CLIENT_OWNER_SUBJECT, Role.CLIENT_OWNER);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor visionAdmin() {
        var token = TestTokens.tokenFor(TestTokens.VISION_ADMIN_SUBJECT, Role.VISION_ADMIN);
        return jwt().jwt(token).authorities(new JwtRoleConverter().convert(token).getAuthorities());
    }
}
