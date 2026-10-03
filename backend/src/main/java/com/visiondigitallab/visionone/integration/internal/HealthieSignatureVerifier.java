package com.visiondigitallab.visionone.integration.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides whether a webhook really came from Healthie.
 *
 * <p>Healthie signs with HMAC-SHA256 over a string built from the request line and three headers:
 * {@code method path query content-digest content-type content-length}, space separated. The shared
 * secret is the {@code whsec_...} value from the webhook's configuration.
 *
 * <p><b>The digest has to be re-checked against the body.</b> The signature covers the
 * {@code Content-Digest} header, not the bytes - so a valid signature proves only that whoever sent
 * it knew the secret and committed to <i>some</i> digest. Without recomputing SHA-256 over the body
 * we have received, an attacker who captured one signed request could replay the headers with a
 * different payload and we would accept it. {@link #verify} therefore fails if the body does not
 * hash to the digest that was signed, and that check is not optional.
 *
 * <p>One thing Healthie's documentation does not pin down is how the signature is encoded in the
 * header - RFC 9421 uses base64, but the published example shows a bare {@code sig1=<signature>}.
 * Rather than guess and fail in production, both base64 and hex are accepted. That is not a weakness:
 * either way the value has to equal an HMAC only the holder of the secret could produce.
 *
 * <p>There is also no nonce, timestamp or delivery id anywhere in a Healthie webhook, so this cannot
 * detect a replay of a genuinely signed request. That is handled downstream instead: the payload is
 * only a notification, and the handler re-reads current state from Healthie, so applying the same
 * notification twice reaches the same answer.
 */
@Component
public class HealthieSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(HealthieSignatureVerifier.class);
    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;

    public HealthieSignatureVerifier(
            @Value("${visionone.healthie.webhook-secret:}") String webhookSecret) {
        this.secret = webhookSecret == null ? new byte[0] : webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    /** Whether a secret is configured at all. Without one, nothing can be verified and nothing is. */
    public boolean configured() {
        return secret.length > 0;
    }

    /**
     * @param method the HTTP method as received
     * @param path the request path, without the query
     * @param query the raw query string, or empty
     * @param contentDigest the {@code Content-Digest} header exactly as sent
     * @param contentType the {@code Content-Type} header exactly as sent
     * @param contentLength the {@code Content-Length} header exactly as sent
     * @param signature the {@code Signature} header exactly as sent
     * @param body the raw request body, as bytes, before any parsing
     */
    public boolean verify(
            String method,
            String path,
            String query,
            String contentDigest,
            String contentType,
            String contentLength,
            String signature,
            byte[] body) {

        if (!configured()) {
            log.error("A Healthie webhook arrived but visionone.healthie.webhook-secret is not set");
            return false;
        }
        if (contentDigest == null || contentType == null || contentLength == null || signature == null) {
            log.warn("Rejecting a Healthie webhook: a signing header is missing");
            return false;
        }
        if (!digestMatchesBody(contentDigest, body)) {
            log.warn("Rejecting a Healthie webhook: Content-Digest does not match the body received");
            return false;
        }

        String base = method.toLowerCase(java.util.Locale.ROOT)
                + " " + path
                + " " + (query == null ? "" : query)
                + " " + contentDigest
                + " " + contentType
                + " " + contentLength;

        byte[] expected;
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            expected = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            log.error("Cannot compute an HMAC to verify a Healthie webhook", e);
            return false;
        }

        String presented = stripLabel(signature);
        // MessageDigest.isEqual is the constant-time comparison; String.equals is not, and a timing
        // side channel on a signature check is a real, if slow, way to forge one.
        boolean ok = MessageDigest.isEqual(expected, decodeBase64(presented))
                || MessageDigest.isEqual(expected, decodeHex(presented));
        if (!ok) {
            log.warn("Rejecting a Healthie webhook: signature does not match");
        }
        return ok;
    }

    /** {@code Content-Digest: SHA-256=<base64>}, sometimes RFC-9530 wrapped as {@code :<base64>:}. */
    private static boolean digestMatchesBody(String contentDigest, byte[] body) {
        int eq = contentDigest.indexOf('=');
        if (eq < 0) {
            return false;
        }
        String value = contentDigest.substring(eq + 1).trim();
        if (value.startsWith(":") && value.endsWith(":") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body);
            return MessageDigest.isEqual(actual, Base64.getDecoder().decode(value));
        } catch (NoSuchAlgorithmException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Turns {@code sig1=<value>} into {@code <value>}, and unwraps RFC 9421 colons. */
    private static String stripLabel(String signature) {
        String value = signature.trim();
        int eq = value.indexOf('=');
        // Only treat it as a label when what precedes the '=' looks like one - base64 itself ends in
        // '=' padding, and cutting there would corrupt the value.
        if (eq > 0 && eq < 12 && value.substring(0, eq).matches("[A-Za-z0-9_-]+")) {
            value = value.substring(eq + 1).trim();
        }
        if (value.startsWith(":") && value.endsWith(":") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static byte[] decodeHex(String value) {
        try {
            return HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
