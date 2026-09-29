package com.visiondigitallab.visionone.reporting;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visiondigitallab.visionone.auth.JwtRoleConverter;
import com.visiondigitallab.visionone.auth.Role;
import com.visiondigitallab.visionone.support.PostgresIntegrationTest;
import com.visiondigitallab.visionone.support.TestTokens;
import java.sql.Timestamp;
import java.time.Instant;
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
 * The monthly report's two promises: figures are frozen once generated, and the practice sees
 * only what Vision has chosen to share.
 */
@AutoConfigureMockMvc
class MonthlyReportTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final String AUGUST = "2026-08";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        jdbc.sql("delete from monthly_report where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from outbox_event where organization_id = :org").param("org", THRIVE).update();
        jdbc.sql("delete from content_item where organization_id = :org").param("org", THRIVE).update();

        membership("0199a1d0-0002-7000-8000-0000000000f1", TestTokens.VISION_ADMIN_SUBJECT, "VISION_ADMIN");
        membership("0199a1d0-0002-7000-8000-0000000000f2", TestTokens.CLIENT_OWNER_SUBJECT, "CLIENT_OWNER");

        published("Hormone health after forty", Instant.parse("2026-08-12T15:00:00Z"));
        published("What a longevity panel measures", Instant.parse("2026-08-20T15:00:00Z"));
        // Published in September: must not appear in August's report.
        published("Sleep and recovery", Instant.parse("2026-09-03T15:00:00Z"));
    }

    @Test
    @DisplayName("generating freezes the month's figures into the report")
    void generateFreezesFigures() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/reports/generate", THRIVE)
                        .param("month", AUGUST)
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.frozen").value(true))
                .andExpect(jsonPath("$.periodMonth").value("2026-08-01"))
                .andExpect(jsonPath("$.figures.publishedContent.length()").value(2))
                .andExpect(jsonPath("$.figures.organizationName").value("THRIVE Longevity Center"));

        Assertions.assertThat(payloadOf(AUGUST).path("publishedContent").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("a frozen report still says what it said after the underlying data changes")
    void frozenFiguresDoNotMove() throws Exception {
        UUID reportId = generate(AUGUST);

        // A late backfill lands in August after the report was generated.
        published("Backfilled August article", Instant.parse("2026-08-25T15:00:00Z"));

        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/{id}", THRIVE, reportId).with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(true))
                .andExpect(jsonPath("$.figures.publishedContent.length()").value(2));
    }

    @Test
    @DisplayName("regenerating an unshared report refreshes it in place rather than adding a second")
    void regenerateUpdatesOneRow() throws Exception {
        UUID first = generate(AUGUST);
        published("Backfilled August article", Instant.parse("2026-08-25T15:00:00Z"));
        UUID second = generate(AUGUST);

        Assertions.assertThat(second).isEqualTo(first);
        Assertions.assertThat(reportCount()).isEqualTo(1);
        Assertions.assertThat(payloadOf(AUGUST).path("publishedContent").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("the practice cannot see a report until Vision shares it")
    void unsharedReportIsInvisibleToTheClient() throws Exception {
        UUID reportId = generate(AUGUST);

        // Not found rather than forbidden: forbidden would confirm the report exists.
        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/{id}", THRIVE, reportId).with(clientOwner()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports", THRIVE).with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports.length()").value(0));
        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/latest", THRIVE).with(clientOwner()))
                .andExpect(status().isNotFound());

        share(reportId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHARED"));

        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/latest", THRIVE).with(clientOwner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId.toString()));
    }

    @Test
    @DisplayName("the practice cannot generate, write or share a report")
    void clientCannotWrite() throws Exception {
        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/reports/generate", THRIVE)
                        .param("month", AUGUST)
                        .with(clientOwner()))
                .andExpect(status().isForbidden());

        Assertions.assertThat(reportCount()).isZero();
    }

    @Test
    @DisplayName("a shared report cannot be regenerated or rewritten")
    void sharedReportIsFinal() throws Exception {
        UUID reportId = generate(AUGUST);
        share(reportId).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/reports/generate", THRIVE)
                        .param("month", AUGUST)
                        .with(visionAdmin()))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/reports/{id}/narrative", THRIVE, reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyLearning\":\"Rewritten after the fact\"}")
                        .with(visionAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a report must be generated before it can be shared")
    void cannotShareAnUngeneratedReport() throws Exception {
        UUID draft = UUID.randomUUID();
        jdbc.sql("""
                insert into monthly_report (id, organization_id, period_month, status)
                values (:id, :org, date '2026-08-01', 'DRAFT')
                """)
                .param("id", draft)
                .param("org", THRIVE)
                .update();

        share(draft).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a report cannot be shared until its key learning is written")
    void cannotShareWithoutKeyLearning() throws Exception {
        UUID reportId = generate(AUGUST);

        shareAsIs(reportId).andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/{id}", THRIVE, reportId).with(visionAdmin()))
                .andExpect(jsonPath("$.status").value("GENERATED"));
    }

    @Test
    @DisplayName("the narrative is one item per line, and key learning is required")
    void narrativeIsSplitIntoLines() throws Exception {
        UUID reportId = generate(AUGUST);

        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/reports/{id}/narrative", THRIVE, reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyLearning":"Local search is the efficient channel.",
                                 "nextActions":"Shift budget to local search\\n\\n  Publish two articles  ",
                                 "decisionsRequired":"Approve the budget shift"}
                                """)
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextActions.length()").value(2))
                .andExpect(jsonPath("$.nextActions[1]").value("Publish two articles"))
                .andExpect(jsonPath("$.decisionsRequired[0]").value("Approve the budget shift"));

        mockMvc.perform(put("/api/v1/orgs/{orgId}/admin/reports/{id}/narrative", THRIVE, reportId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyLearning\":\"   \"}")
                        .with(visionAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a draft that was never generated shows live figures and says so")
    void draftComposesLive() throws Exception {
        UUID draft = UUID.randomUUID();
        jdbc.sql("""
                insert into monthly_report (id, organization_id, period_month, status)
                values (:id, :org, date '2026-08-01', 'DRAFT')
                """)
                .param("id", draft)
                .param("org", THRIVE)
                .update();

        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/{id}", THRIVE, draft).with(visionAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozen").value(false))
                .andExpect(jsonPath("$.generatedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.figures.publishedContent.length()").value(2));
    }

    @Test
    @DisplayName("the API says who may edit: Vision until shared, the practice never")
    void editabilityIsStatedPerRoleAndStatus() throws Exception {
        UUID reportId = generate(AUGUST);

        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/{id}", THRIVE, reportId).with(visionAdmin()))
                .andExpect(jsonPath("$.editable").value(true));
        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports", THRIVE).with(visionAdmin()))
                .andExpect(jsonPath("$.canGenerate").value(true));
        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports", THRIVE).with(clientOwner()))
                .andExpect(jsonPath("$.canGenerate").value(false));

        share(reportId).andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false));

        mockMvc.perform(get("/api/v1/orgs/{orgId}/reports/{id}", THRIVE, reportId).with(clientOwner()))
                .andExpect(jsonPath("$.editable").value(false));
    }

    private UUID generate(String month) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/reports/generate", THRIVE)
                        .param("month", month)
                        .with(visionAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("id").asText());
    }

    /** Writes a minimal narrative, then shares. Sharing requires the key learning. */
    private org.springframework.test.web.servlet.ResultActions share(UUID reportId) throws Exception {
        jdbc.sql("update monthly_report set key_learning = 'Local search is the efficient channel.' "
                        + "where id = :id and key_learning is null")
                .param("id", reportId)
                .update();
        return shareAsIs(reportId);
    }

    private org.springframework.test.web.servlet.ResultActions shareAsIs(UUID reportId) throws Exception {
        return mockMvc.perform(post("/api/v1/orgs/{orgId}/admin/reports/{id}/share", THRIVE, reportId)
                .with(visionAdmin()));
    }

    private JsonNode payloadOf(String month) throws Exception {
        String json = jdbc.sql("""
                select payload_json::text from monthly_report
                where organization_id = :org and period_month = cast(:month as date)
                """)
                .param("org", THRIVE)
                .param("month", month + "-01")
                .query(String.class)
                .single();
        return objectMapper.readTree(json);
    }

    private long reportCount() {
        return jdbc.sql("select count(*) from monthly_report where organization_id = :org")
                .param("org", THRIVE)
                .query(Long.class)
                .single();
    }

    private void published(String title, Instant at) {
        jdbc.sql("""
                insert into content_item (id, organization_id, title, content_type, status,
                                          author_name, published_url, published_at, summary)
                values (:id, :org, :title, 'BLOG', 'PUBLISHED', 'Vision Digital Lab',
                        'https://example.com/post', :at, 'A one-line summary.')
                """)
                .param("id", UUID.randomUUID())
                .param("org", THRIVE)
                .param("title", title)
                .param("at", Timestamp.from(at))
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
