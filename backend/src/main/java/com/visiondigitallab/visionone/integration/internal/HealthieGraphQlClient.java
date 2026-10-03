package com.visiondigitallab.visionone.integration.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
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
 * The only thing in VisionOne that speaks to Healthie.
 *
 * <p>Healthie is a GraphQL API: one endpoint, one POST per query. Authentication is an API key sent
 * as {@code Authorization: Basic <key>} alongside {@code AuthorizationSource: API} - note that
 * despite the scheme name it is not base64 user:pass, it is the key verbatim.
 *
 * <p>The API version is pinned in a header. Healthie defaults an unpinned caller to a date-based
 * version that moves, which means a field could change shape without a deploy on our side. Pinning
 * turns that into a decision we make when we are looking.
 *
 * <p>Timeouts are short and deliberate. Every call happens on a scheduler, never on a request
 * thread, but a scheduler that blocks for a minute on a slow upstream is a scheduler that stops
 * draining the inbox.
 */
@Component
@ConditionalOnProperty(name = "visionone.providers.scheduling", havingValue = "HEALTHIE")
public class HealthieGraphQlClient {

    private static final Logger log = LoggerFactory.getLogger(HealthieGraphQlClient.class);

    private final RestClient http;
    private final String endpoint;

    public HealthieGraphQlClient(
            @Value("${visionone.healthie.endpoint:https://api.gethealthie.com/graphql}") String endpoint,
            @Value("${visionone.healthie.api-key}") String apiKey,
            @Value("${visionone.healthie.api-version:2024-06-01}") String apiVersion,
            @Value("${visionone.healthie.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${visionone.healthie.read-timeout-ms:15000}") long readTimeoutMs) {
        this.endpoint = endpoint;
        // The JDK client straight from spring-web rather than Boot's builder: Boot has moved and
        // renamed that helper twice across 3.x, and this adapter has no reason to care.
        var jdk = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                // HTTP/1.1 explicitly. The JDK client defaults to HTTP/2 and, over plaintext,
                // attempts an h2c upgrade that an ordinary HTTP/1.1 server refuses - which surfaces
                // as an opaque IOException from the HTTP/2 stream layer rather than anything that
                // names the cause. GraphQL is one POST per query and gains nothing from HTTP/2, and
                // pinning it is also what makes this adapter testable against a local stub.
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(endpoint)
                // Not base64. Healthie wants the raw key behind the Basic scheme.
                .defaultHeader("Authorization", "Basic " + apiKey)
                .defaultHeader("AuthorizationSource", "API")
                .defaultHeader("Healthie-GraphQL-API-Version", apiVersion)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Run one query and return its {@code data} node.
     *
     * @throws HealthieUnavailableException on any transport failure, non-2xx status, or a GraphQL
     *     {@code errors} array. A caller must never see an empty result that was really a failure:
     *     "no appointments" and "we could not ask" mean opposite things on a dashboard.
     */
    public JsonNode query(String query, Map<String, Object> variables) {
        JsonNode body;
        try {
            body = http.post()
                    .body(Map.of("query", query, "variables", variables == null ? Map.of() : variables))
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        JsonNode parsed = response.bodyTo(JsonNode.class);
                        if (status.isError()) {
                            // The body is included for 4xx because Healthie explains itself there,
                            // and withheld for 5xx where it is usually an opaque proxy page.
                            throw new HealthieUnavailableException(
                                    "Healthie returned " + status.value()
                                            + (status.is4xxClientError() ? ": " + summarise(parsed) : ""),
                                    null);
                        }
                        return parsed;
                    });
        } catch (HealthieUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new HealthieUnavailableException("Healthie at " + endpoint + " is unreachable", e);
        }

        if (body == null) {
            throw new HealthieUnavailableException("Healthie returned an empty body", null);
        }
        // GraphQL answers 200 with an errors array, so a successful status proves nothing.
        JsonNode errors = body.get("errors");
        if (errors != null && errors.isArray() && !errors.isEmpty()) {
            throw new HealthieUnavailableException("Healthie reported: " + summarise(errors), null);
        }
        JsonNode data = body.get("data");
        if (data == null || data.isNull()) {
            throw new HealthieUnavailableException("Healthie returned no data node", null);
        }
        return data;
    }

    /** Whether the key is accepted at all, used for connection status rather than for data. */
    public boolean reachable() {
        try {
            // currentUser is the cheapest authenticated query: it proves the key works without
            // reading anything about a patient.
            query("query { currentUser { id } }", Map.of());
            return true;
        } catch (HealthieUnavailableException e) {
            log.warn("Healthie probe failed: {}", e.getMessage());
            return false;
        }
    }

    /** Keeps whatever Healthie said short, and keeps it out of a response to a browser. */
    private static String summarise(JsonNode node) {
        if (node == null) {
            return "no detail";
        }
        String text = node.toString();
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /** Distinguishes "we could not ask Healthie" from "Healthie says there is nothing". */
    public static class HealthieUnavailableException extends RuntimeException {
        public HealthieUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
