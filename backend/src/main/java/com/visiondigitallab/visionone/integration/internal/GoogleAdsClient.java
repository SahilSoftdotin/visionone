package com.visiondigitallab.visionone.integration.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The only thing in VisionOne that speaks to Google Ads.
 *
 * <p>Two things about Google's API shape the code here.
 *
 * <p><b>Access tokens last an hour; refresh tokens do not expire.</b> So the refresh token is the
 * credential in configuration, and an access token is minted from it and held until shortly before
 * it lapses. Minting one per request would work and would also be a needless round trip on every
 * sync.
 *
 * <p><b>Reporting runs through one endpoint.</b> {@code googleAds:searchStream} takes a GAQL query
 * and streams back chunks, each carrying a {@code results} array. There is no separate endpoint per
 * report; what you get is decided entirely by the query.
 *
 * <p>The API version is pinned rather than tracked. v22 is what this was verified against - v21 and
 * earlier now return 404, so Google retires versions on a schedule and an unpinned client would
 * break on their timetable rather than ours.
 */
@Component
@ConditionalOnProperty(name = "visionone.providers.advertising", havingValue = "GOOGLE_ADS")
public class GoogleAdsClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleAdsClient.class);

    private final RestClient oauth;
    private final RestClient ads;
    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String loginCustomerId;

    /** Guarded by {@code this}; an access token is small, mutable state shared by scheduled runs. */
    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    public GoogleAdsClient(
            @Value("${visionone.googleads.endpoint:https://googleads.googleapis.com}") String endpoint,
            // Configurable so this adapter can be pointed at a stub. A hardcoded constant here is
            // what made the first version of these tests reach out to the real Google and fail.
            @Value("${visionone.googleads.token-endpoint:https://oauth2.googleapis.com/token}")
                    String tokenEndpoint,
            @Value("${visionone.googleads.api-version:v22}") String apiVersion,
            @Value("${visionone.googleads.client-id}") String clientId,
            @Value("${visionone.googleads.client-secret}") String clientSecret,
            @Value("${visionone.googleads.refresh-token}") String refreshToken,
            @Value("${visionone.googleads.login-customer-id:}") String loginCustomerId,
            @Value("${visionone.googleads.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${visionone.googleads.read-timeout-ms:30000}") long readTimeoutMs) {

        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.loginCustomerId = loginCustomerId == null ? "" : loginCustomerId.replace("-", "").trim();

        var jdk = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                // HTTP/1.1 for the same reason as the Healthie client: the JDK default attempts an
                // h2c upgrade over plaintext that an ordinary server refuses, and the failure
                // surfaces as an opaque IOException from the HTTP/2 stream layer. It also makes
                // this testable against a local stub.
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.oauth = RestClient.builder().requestFactory(factory).build();
        this.ads = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(endpoint + "/" + apiVersion)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Runs a GAQL query against one customer and returns every result row, flattened across the
     * chunks {@code searchStream} returns.
     *
     * @throws GoogleAdsUnavailableException on any failure. A caller must never receive an empty
     *     list that was really an outage: zero spend and "we could not ask" are opposite findings.
     */
    public java.util.List<JsonNode> query(String customerId, String gaql) {
        String token = accessToken();
        String id = customerId.replace("-", "").trim();

        JsonNode body;
        try {
            body = ads.post()
                    .uri("/customers/{customerId}/googleAds:searchStream", id)
                    .header("Authorization", "Bearer " + token)
                    // Present only when going through a manager account. Sending it empty is an
                    // error, so it is added conditionally rather than always.
                    .headers(h -> {
                        if (!loginCustomerId.isBlank()) {
                            h.add("login-customer-id", loginCustomerId);
                        }
                    })
                    .body(Map.of("query", gaql))
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        JsonNode parsed = response.bodyTo(JsonNode.class);
                        if (status.isError()) {
                            throw new GoogleAdsUnavailableException(
                                    "Google Ads returned " + status.value() + ": " + describe(parsed),
                                    null);
                        }
                        return parsed;
                    });
        } catch (GoogleAdsUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GoogleAdsUnavailableException("Google Ads is unreachable", e);
        }

        if (body == null) {
            throw new GoogleAdsUnavailableException("Google Ads returned an empty body", null);
        }

        // searchStream answers with an array of chunks, each holding its own results array.
        java.util.List<JsonNode> rows = new java.util.ArrayList<>();
        for (JsonNode chunk : body.isArray() ? body : java.util.List.of(body)) {
            JsonNode results = chunk.path("results");
            if (results.isArray()) {
                results.forEach(rows::add);
            }
        }
        return rows;
    }

    /** Whether the credentials currently work, for connection status rather than for data. */
    public boolean reachable() {
        try {
            accessToken();
            return true;
        } catch (GoogleAdsUnavailableException e) {
            log.warn("Google Ads credential check failed: {}", e.getMessage());
            return false;
        }
    }

    private synchronized String accessToken() {
        // A minute of slack, so a token cannot expire between the check and the request using it.
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry.minusSeconds(60))) {
            return accessToken;
        }
        JsonNode response;
        try {
            response = oauth.post()
                    .uri(tokenEndpoint)
                    .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body("client_id=" + enc(clientId)
                            + "&client_secret=" + enc(clientSecret)
                            + "&refresh_token=" + enc(refreshToken)
                            + "&grant_type=refresh_token")
                    .exchange((request, res) -> {
                        JsonNode parsed = res.bodyTo(JsonNode.class);
                        if (res.getStatusCode().isError()) {
                            // Google names the cause here - invalid_grant usually means the refresh
                            // token was revoked, which no amount of retrying will fix.
                            throw new GoogleAdsUnavailableException(
                                    "Could not refresh the Google Ads access token: " + describe(parsed),
                                    null);
                        }
                        return parsed;
                    });
        } catch (GoogleAdsUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GoogleAdsUnavailableException("Google's token endpoint is unreachable", e);
        }

        String token = response == null ? null : response.path("access_token").asText(null);
        if (token == null) {
            throw new GoogleAdsUnavailableException("Google returned no access token", null);
        }
        long expiresIn = response.path("expires_in").asLong(3600);
        this.accessToken = token;
        this.accessTokenExpiry = Instant.now().plusSeconds(expiresIn);
        return token;
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Keeps Google's error short, and keeps a credential out of whatever logs this. */
    private static String describe(JsonNode node) {
        if (node == null) {
            return "no detail";
        }
        String text = node.toString();
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /** Distinguishes "we could not ask Google" from "Google says there was no spend". */
    public static class GoogleAdsUnavailableException extends RuntimeException {
        public GoogleAdsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
