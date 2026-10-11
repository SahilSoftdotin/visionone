package com.visiondigitallab.visionone.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.visiondigitallab.visionone.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Healthie webhook endpoint, in a real application context.
 *
 * <p>Two properties are being pinned here that the unit tests cannot reach.
 *
 * <p>First, the endpoint is <b>reachable without a token</b>. Healthie has no VisionOne credential
 * to present, so {@code SecurityConfig} has to permit the path - and if that permit is ever removed,
 * every webhook silently becomes a 401 and the Calendar quietly stops updating. A test that posts
 * with no {@code Authorization} header at all is what notices.
 *
 * <p>Second, an <b>unverified request writes nothing</b>. Permitting the path means anyone who finds
 * the URL can reach it, so the signature is the only thing standing between a stranger and work
 * queued onto the drain. The row count either moves or it does not.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "visionone.healthie.webhook-secret=whsec_test-secret")
class HealthieWebhookTest extends PostgresIntegrationTest {

    private static final String SECRET = "whsec_test-secret";
    private static final String PATH = "/api/v1/webhooks/healthie";
    // Exactly what the servlet reports, charset included. The signature base uses the
    // Content-Type verbatim as received, so signing "application/json" while the container
    // delivers "application/json;charset=UTF-8" produces a valid signature over the wrong
    // string - a real hazard in production too, if anything between Healthie and the app
    // rewrites that header.
    private static final String TYPE = "application/json;charset=UTF-8";
    private static final String BODY =
            "{\"resource_id\":\"4821\",\"resource_id_type\":\"Appointment\","
                    + "\"event_type\":\"appointment.updated\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clearInbox() {
        jdbc.sql("delete from inbox_event where source = 'HEALTHIE'").update();
    }

    private long inboxRows() {
        return jdbc.sql("select count(*) from inbox_event where source = 'HEALTHIE'")
                .query(Long.class).single();
    }

    @Test
    @DisplayName("a correctly signed webhook is accepted without any token, and recorded as verified")
    void signedWebhookIsRecorded() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);

        mockMvc.perform(post(PATH)
                        .content(body)
                        .header("Content-Type", TYPE)
                        .header("Content-Digest", digest)
                        .header("Content-Length", String.valueOf(body.length))
                        .header("Signature", "sig1=" + sign(base(digest, body.length))))
                .andExpect(status().isOk());

        assertThat(inboxRows()).isEqualTo(1);
        var row = jdbc.sql("""
                select event_type, resource_id, resource_type, signature_verified, processed_at
                  from inbox_event where source = 'HEALTHIE'
                """).query((rs, n) -> new Object[] {
                    rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getBoolean(4), rs.getTimestamp(5)
                }).single();

        assertThat(row[0]).isEqualTo("appointment.updated");
        assertThat(row[1]).isEqualTo("4821");
        assertThat(row[2]).isEqualTo("Appointment");
        assertThat(row[3]).as("signature_verified must be stored truthfully").isEqualTo(true);
        // Recorded, not yet applied. The endpoint's job ends here; the drain does the work.
        assertThat(row[4]).as("the endpoint must not process the event inline").isNull();
    }

    @Test
    @DisplayName("an unsigned webhook is refused and writes nothing")
    void unsignedWebhookIsRefused() throws Exception {
        mockMvc.perform(post(PATH)
                        .content(BODY)
                        .header("Content-Type", TYPE))
                .andExpect(status().isUnauthorized());

        assertThat(inboxRows()).isZero();
    }

    @Test
    @DisplayName("a genuine signature with a swapped body is refused and writes nothing")
    void tamperedBodyIsRefused() throws Exception {
        byte[] original = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(original);
        String signature = "sig1=" + sign(base(digest, original.length));

        byte[] swapped = BODY.replace("4821", "9999").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post(PATH)
                        .content(swapped)
                        .header("Content-Type", TYPE)
                        .header("Content-Digest", digest)
                        .header("Content-Length", String.valueOf(original.length))
                        .header("Signature", signature))
                .andExpect(status().isUnauthorized());

        assertThat(inboxRows()).isZero();
    }

    @Test
    @DisplayName("a verified but unparseable body is a 400, not a retry-provoking 500")
    void unparseableBodyIsRejectedCleanly() throws Exception {
        byte[] body = "not json at all".getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);

        mockMvc.perform(post(PATH)
                        .content(body)
                        .header("Content-Type", TYPE)
                        .header("Content-Digest", digest)
                        .header("Content-Length", String.valueOf(body.length))
                        .header("Signature", "sig1=" + sign(base(digest, body.length))))
                .andExpect(status().isBadRequest());

        assertThat(inboxRows()).isZero();
    }

    @Test
    @DisplayName("two deliveries of the same notification both record, because the handler is what dedupes")
    void redeliveryIsRecordedRatherThanCollapsed() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);
        String signature = "sig1=" + sign(base(digest, body.length));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(PATH)
                            .content(body)
                            .header("Content-Type", TYPE)
                            .header("Content-Digest", digest)
                            .header("Content-Length", String.valueOf(body.length))
                            .header("Signature", signature))
                    .andExpect(status().isOk());
        }

        // Healthie sends no delivery id, so there is nothing to deduplicate on here. Recording both
        // is the deliberate choice: the handler re-reads current state and upserts, so processing
        // twice converges, whereas collapsing two genuine notifications would lose one.
        assertThat(inboxRows()).isEqualTo(2);
    }

    // --- helpers producing what Healthie would send ---

    private static String base(String digest, int length) {
        return "post " + PATH + "  " + digest + " " + TYPE + " " + length;
    }

    private static String digestOf(byte[] body) {
        try {
            return "SHA-256=" + Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(String base) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
