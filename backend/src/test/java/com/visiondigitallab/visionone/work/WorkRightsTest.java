package com.visiondigitallab.visionone.work;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Who may change the work plan, and who may only read it.
 *
 * <p>Vision plans the work and the practice reads it. There is no client decision anywhere on this
 * screen - that asymmetry is the opposite of content, where the practice is the only party that
 * can approve. Content had tests for its side of that from the beginning and work had none, which
 * meant the only thing standing between a client and the work plan was an annotation nobody was
 * checking.
 *
 * <p>Both halves are tested: that the practice is <em>told</em> it cannot edit, through the
 * {@code editable} flag the screen renders from, and that the write endpoints refuse it anyway.
 * The flag is a convenience for the UI and is never the thing that enforces anything.
 */
@AutoConfigureMockMvc
class WorkRightsTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID EXISTING = UUID.fromString("0199a1d0-c000-7000-8000-000000000001");

    private static final String VALID_BODY =
            """
            {"title":"Search visibility for thrivelongevitycenter.com",
             "category":"SEO",
             "businessReason":"The site does not rank for the treatments the practice sells.",
             "owner":"Vision Digital Lab",
             "clientUpdate":"Audit complete, working through the fixes."}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from work_item where organization_id = :org").param("org", THRIVE).update();

        membership("0199a1d0-0002-7000-8000-0000000000f1", TestTokens.VISION_ADMIN_SUBJECT, "VISION_ADMIN");
        membership("0199a1d0-0002-7000-8000-0000000000f2", TestTokens.CLIENT_OWNER_SUBJECT, "CLIENT_OWNER");

        jdbc.sql("""
                insert into work_item (id, organization_id, title, category, status,
                                       business_reason, owner_name)
                values (:id, :org, 'Google Ads live and verified', 'PAID_ACQUISITION',
                        'WAITING_FOR_CLIENT', 'Reporting on a channel that is not running.',
                        'Vision Digital Lab')
                """)
                .param("id", EXISTING)
                .param("org", THRIVE)
                .update();
    }

    @Test
    @DisplayName("both roles can read the work plan, and only Vision is told it is editable")
    void capabilityIsStatedPerRole() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/work", THRIVE).with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/orgs/{orgId}/work", THRIVE).with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("the practice cannot create a work item")
    void clientCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/work", THRIVE)
                        .with(clientOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        // Refused before the body was applied, not after.
        assertWorkItemCount(1);
    }

    @Test
    @DisplayName("the practice cannot edit a work item")
    void clientCannotUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/work/{id}", THRIVE, EXISTING)
                        .with(clientOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());

        assertTitleUnchanged();
    }

    @Test
    @DisplayName("the practice cannot move a work item through its stages")
    void clientCannotTransition() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/work/{id}/status", THRIVE, EXISTING)
                        .with(clientOwner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());

        // The one that would matter most: a client marking Vision's work done.
        String status = jdbc.sql("select status from work_item where id = :id")
                .param("id", EXISTING)
                .query(String.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("WAITING_FOR_CLIENT");
    }

    @Test
    @DisplayName("Vision can create a work item, and the practice then sees it")
    void visionCreatesAndTheClientSeesIt() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/work", THRIVE)
                        .with(visionAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        // The whole point of the screen: what Vision adds is what the practice reads. Asserted
        // through the client's own request rather than the response to Vision's write, because
        // those are two different queries and only one of them is what Gary actually sees.
        mockMvc.perform(get("/api/v1/orgs/{orgId}/work", THRIVE).with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath(
                                "$.items[?(@.title == 'Search visibility for thrivelongevitycenter.com')]")
                        .exists());
    }

    @Test
    @DisplayName("Vision can move a work item through its stages")
    void visionCanTransition() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/work/{id}/status", THRIVE, EXISTING)
                        .with(visionAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        String status = jdbc.sql("select status from work_item where id = :id")
                .param("id", EXISTING)
                .query(String.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("IN_PROGRESS");
    }

    private void assertWorkItemCount(int expected) {
        Integer count = jdbc.sql("select count(*) from work_item where organization_id = :org")
                .param("org", THRIVE)
                .query(Integer.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }

    private void assertTitleUnchanged() {
        String title = jdbc.sql("select title from work_item where id = :id")
                .param("id", EXISTING)
                .query(String.class)
                .single();
        org.assertj.core.api.Assertions.assertThat(title).isEqualTo("Google Ads live and verified");
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
