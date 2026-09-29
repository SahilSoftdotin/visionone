package com.visiondigitallab.visionone.content;

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
 * The client's two decisions, and the asymmetry the product depends on.
 *
 * <p>Vision operates and the client decides. If Vision could record an approval, every approval in
 * the audit trail would be worthless, so that is tested as carefully as the happy path.
 */
@AutoConfigureMockMvc
class ContentApprovalTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final UUID AWAITING = UUID.fromString("0199a1d0-b000-7000-8000-000000000001");
    private static final UUID DRAFTING = UUID.fromString("0199a1d0-b000-7000-8000-000000000002");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from outbox_event where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from content_item where organization_id = :org").param("org", THRIVE).update();

        membership("0199a1d0-0002-7000-8000-0000000000e1", TestTokens.VISION_ADMIN_SUBJECT, "VISION_ADMIN");
        membership("0199a1d0-0002-7000-8000-0000000000e2", TestTokens.CLIENT_OWNER_SUBJECT, "CLIENT_OWNER");

        content(AWAITING, "Inside a THRIVE first visit", "CLIENT_REVIEW", "SHORT_VIDEO");
        content(DRAFTING, "Metabolic health after forty", "DRAFTING", "BLOG");
    }

    @Test
    @DisplayName("the client is told they can decide; Vision is told they cannot")
    void capabilityIsStatedPerRole() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/content", THRIVE).with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDecide").value(true))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.summary.awaitingClient").value(1));

        mockMvc.perform(get("/api/v1/orgs/{orgId}/content", THRIVE).with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDecide").value(false))
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    @DisplayName("the client approves an item waiting on them, and an event is recorded")
    void clientApproves() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/content/{id}/approve", THRIVE, AWAITING)
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.approved").value(1))
                .andExpect(jsonPath("$.summary.awaitingClient").value(0));

        Assertions.assertThat(outboxCount("ContentApproved")).isEqualTo(1);
        Assertions.assertThat(statusOf(AWAITING)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("requesting changes keeps the feedback and publishes nothing")
    void clientRequestsChanges() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/content/{id}/request-changes", THRIVE, AWAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"Please cut the intro and lead with the panel\"}")
                        .with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.changesRequested").value(1));

        Assertions.assertThat(statusOf(AWAITING)).isEqualTo("CHANGES_REQUESTED");
        Assertions.assertThat(feedbackOf(AWAITING))
                .isEqualTo("Please cut the intro and lead with the panel");
        // Nothing downstream needs to know, so no event.
        Assertions.assertThat(outboxCount("ContentApproved")).isZero();
    }

    @Test
    @DisplayName("requesting changes without saying what is rejected")
    void feedbackIsRequired() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/content/{id}/request-changes", THRIVE, AWAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback\":\"  \"}")
                        .with(clientOwner()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Vision cannot approve on the client's behalf")
    void visionCannotApprove() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/content/{id}/approve", THRIVE, AWAITING)
                        .with(visionAdmin()))
                .andExpect(status().isForbidden());

        Assertions.assertThat(statusOf(AWAITING)).isEqualTo("CLIENT_REVIEW");
    }

    @Test
    @DisplayName("Vision cannot reach APPROVED through its own status endpoint either")
    void visionCannotBackDoorAnApproval() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/content/{id}/status", THRIVE, DRAFTING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"APPROVED\"}")
                        .with(visionAdmin()))
                .andExpect(status().isBadRequest());

        Assertions.assertThat(statusOf(DRAFTING)).isEqualTo("DRAFTING");
    }

    @Test
    @DisplayName("Vision can send an item to the client for review")
    void visionCanSendForReview() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/content/{id}/status", THRIVE, DRAFTING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"CLIENT_REVIEW\"}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.awaitingClient").value(2));
    }

    @Test
    @DisplayName("Vision cannot publish without the client's approval")
    void visionCannotPublishUnapprovedContent() throws Exception {
        // The real bypass: blocking APPROVED is pointless if PUBLISHED is reachable without it.
        for (String target : new String[] {"PUBLISHED", "SCHEDULED"}) {
            mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/content/{id}/status", THRIVE, DRAFTING)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toStatus\":\"" + target + "\"}")
                            .with(visionAdmin()))
                    .andExpect(status().isBadRequest());
        }
        Assertions.assertThat(statusOf(DRAFTING)).isEqualTo("DRAFTING");
    }

    @Test
    @DisplayName("publishing works once the client has approved")
    void publishingFollowsApproval() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/content/{id}/approve", THRIVE, AWAITING)
                        .with(clientOwner()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/content/{id}/status", THRIVE, AWAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"PUBLISHED\"}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.published").value(1));
    }

    @Test
    @DisplayName("Vision can withdraw an item from the client's queue")
    void visionCanWithdrawFromReview() throws Exception {
        // Retracting is not deciding, so it stays available.
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/content/{id}/status", THRIVE, AWAITING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"DRAFTING\"}")
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.awaitingClient").value(0));
    }

    @Test
    @DisplayName("an item not waiting on the client cannot be approved")
    void cannotApproveWhatIsNotWaiting() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/content/{id}/approve", THRIVE, DRAFTING)
                        .with(clientOwner()))
                .andExpect(status().isBadRequest());

        Assertions.assertThat(statusOf(DRAFTING)).isEqualTo("DRAFTING");
    }

    @Test
    @DisplayName("Vision is told which moves it may make; the practice is told none")
    void nextStatusesComeFromTheTransitionTable() throws Exception {
        mockMvc.perform(get("/api/v1/orgs/{orgId}/content", THRIVE).with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + DRAFTING + "')].nextStatuses[*]")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "IDEA", "INTERNAL_REVIEW", "CLIENT_REVIEW")));

        mockMvc.perform(get("/api/v1/orgs/{orgId}/content", THRIVE).with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nextStatuses").isEmpty());
    }

    private long outboxCount(String eventType) {
        return jdbc.sql("select count(*) from outbox_event where organization_id = :org and event_type = :t")
                .param("org", THRIVE)
                .param("t", eventType)
                .query(Long.class)
                .single();
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from content_item where id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private String feedbackOf(UUID id) {
        return jdbc.sql("select client_feedback from content_item where id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private void content(UUID id, String title, String status, String type) {
        jdbc.sql("""
                insert into content_item (id, organization_id, title, content_type, status,
                                          author_name, draft_url, summary)
                values (:id, :org, :title, :type, :status, 'Vision Digital Lab',
                        'https://drafts.example.com/1', 'A one-line summary for the client.')
                """)
                .param("id", id)
                .param("org", THRIVE)
                .param("title", title)
                .param("type", type)
                .param("status", status)
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
