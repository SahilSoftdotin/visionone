package com.visiondigitallab.visionone.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.visiondigitallab.visionone.integration.internal.HealthieSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Webhook signature verification.
 *
 * <p>The case that matters most is {@link #aSwappedBodyIsRejected()}. Healthie signs the
 * {@code Content-Digest} header rather than the bytes, so a signature alone says nothing about the
 * payload that arrived with it - only that someone holding the secret committed to some digest. If
 * the digest is not re-checked against the body, a captured request can be replayed with any payload
 * at all, and every later check in the pipeline is working on attacker-chosen data.
 */
class HealthieSignatureVerifierTest {

    private static final String SECRET = "whsec_a-shared-secret-value";
    private static final String BODY =
            "{\"resource_id\":\"4821\",\"resource_id_type\":\"Appointment\",\"event_type\":\"appointment.updated\"}";
    private static final String PATH = "/api/v1/webhooks/healthie";
    private static final String TYPE = "application/json";

    private final HealthieSignatureVerifier verifier = new HealthieSignatureVerifier(SECRET);

    @Test
    @DisplayName("a correctly signed request is accepted")
    void validSignaturePasses() {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);
        assertThat(verifier.verify(
                        "POST", PATH, "", digest, TYPE, String.valueOf(body.length),
                        "sig1=" + signBase64(base(PATH, "", digest, TYPE, body.length)), body))
                .isTrue();
    }

    @Test
    @DisplayName("a body swapped after signing is rejected, even though the signature is genuine")
    void aSwappedBodyIsRejected() {
        byte[] original = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(original);
        String signature = "sig1=" + signBase64(base(PATH, "", digest, TYPE, original.length));

        // Everything the signature covers is unchanged. Only the payload is different.
        byte[] swapped = BODY.replace("4821", "9999").getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.verify(
                        "POST", PATH, "", digest, TYPE, String.valueOf(original.length),
                        signature, swapped))
                .isFalse();
    }

    @Test
    @DisplayName("a signature made with a different secret is rejected")
    void wrongSecretIsRejected() {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);
        String forged = "sig1=" + Base64.getEncoder()
                .encodeToString(hmac("whsec_not-the-real-secret",
                        base(PATH, "", digest, TYPE, body.length)));

        assertThat(verifier.verify(
                        "POST", PATH, "", digest, TYPE, String.valueOf(body.length), forged, body))
                .isFalse();
    }

    @Test
    @DisplayName("changing the path invalidates the signature, because the path is signed")
    void pathIsCovered() {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);
        String signature = "sig1=" + signBase64(base(PATH, "", digest, TYPE, body.length));

        assertThat(verifier.verify(
                        "POST", "/api/v1/webhooks/elsewhere", "", digest, TYPE,
                        String.valueOf(body.length), signature, body))
                .isFalse();
    }

    @Test
    @DisplayName("a hex-encoded signature is accepted too, since Healthie does not document which")
    void hexEncodingAlsoWorks() {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);
        String hex = "sig1=" + HexFormat.of()
                .formatHex(hmac(SECRET, base(PATH, "", digest, TYPE, body.length)));

        assertThat(verifier.verify(
                        "POST", PATH, "", digest, TYPE, String.valueOf(body.length), hex, body))
                .isTrue();
    }

    @Test
    @DisplayName("a missing signing header is rejected rather than skipped")
    void missingHeadersAreRejected() {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);
        String signature = "sig1=" + signBase64(base(PATH, "", digest, TYPE, body.length));

        assertThat(verifier.verify("POST", PATH, "", null, TYPE, "1", signature, body)).isFalse();
        assertThat(verifier.verify("POST", PATH, "", digest, null, "1", signature, body)).isFalse();
        assertThat(verifier.verify("POST", PATH, "", digest, TYPE, null, signature, body)).isFalse();
        assertThat(verifier.verify("POST", PATH, "", digest, TYPE, "1", null, body)).isFalse();
    }

    @Test
    @DisplayName("with no secret configured nothing verifies, rather than everything")
    void noSecretMeansNothingIsTrusted() {
        var unconfigured = new HealthieSignatureVerifier("");
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String digest = digestOf(body);

        assertThat(unconfigured.configured()).isFalse();
        assertThat(unconfigured.verify(
                        "POST", PATH, "", digest, TYPE, String.valueOf(body.length),
                        "sig1=" + signBase64(base(PATH, "", digest, TYPE, body.length)), body))
                .isFalse();
    }

    // --- helpers that build what Healthie would send ---

    private static String base(String path, String query, String digest, String type, int length) {
        return "post " + path + " " + query + " " + digest + " " + type + " " + length;
    }

    private static String digestOf(byte[] body) {
        try {
            return "SHA-256=" + Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signBase64(String base) {
        return Base64.getEncoder().encodeToString(hmac(SECRET, base));
    }

    private static byte[] hmac(String secret, String base) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
