package com.visiondigitallab.visionone.integration.web;

import com.visiondigitallab.visionone.eventing.api.InboxGateway;
import com.visiondigitallab.visionone.eventing.domain.InboxSource;
import com.visiondigitallab.visionone.integration.internal.HealthieSignatureVerifier;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Healthie's webhooks arrive.
 *
 * <p>This endpoint is unauthenticated, because Healthie has no VisionOne credential to present. The
 * signature <b>is</b> the authentication, which is why a failed check returns 401 and writes
 * nothing: an unverified request is not a request from Healthie, and recording it would let anyone
 * who can reach this URL queue work for the drain.
 *
 * <p>It does three things and deliberately nothing else - verify, record, answer. The payload is a
 * notification rather than data ({@code resource_id}, {@code resource_id_type}, {@code event_type}),
 * so there is nothing here worth applying; the fetch and the mapping happen on the drain, where
 * being slow is free. Healthie retries a failing endpoint with backoff for about three days and then
 * disables the webhook, so the cost of doing real work here is eventual silence.
 *
 * <p>Always 200 for a verified event, including one already seen. A duplicate is Healthie doing its
 * job; answering anything else invites it to keep trying.
 */
@RestController
@RequestMapping("/api/v1/webhooks/healthie")
public class HealthieWebhookController {

    private static final Logger log = LoggerFactory.getLogger(HealthieWebhookController.class);

    private final HealthieSignatureVerifier verifier;
    private final InboxGateway inbox;

    public HealthieWebhookController(HealthieSignatureVerifier verifier, InboxGateway inbox) {
        this.verifier = verifier;
        this.inbox = inbox;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) byte[] body, HttpServletRequest request) {

        byte[] raw = body == null ? new byte[0] : body;

        boolean verified = verifier.verify(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString() == null ? "" : request.getQueryString(),
                request.getHeader("Content-Digest"),
                request.getHeader("Content-Type"),
                request.getHeader("Content-Length"),
                request.getHeader("Signature"),
                raw);

        if (!verified) {
            // Nothing about why is returned. A caller probing this endpoint learns only that it
            // failed, not which part of the signature it got wrong.
            log.warn("Rejected an unverified Healthie webhook from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String payload = new String(raw, StandardCharsets.UTF_8);
        HealthieWebhookPayload parsed = HealthieWebhookPayload.parse(payload);
        if (parsed == null) {
            // Verified, so it really is from Healthie - but we cannot act on it. 400 rather than a
            // retry-provoking 500, because retrying will not make it parseable.
            log.warn("A verified Healthie webhook could not be parsed");
            return ResponseEntity.badRequest().build();
        }

        // Healthie sends no delivery identifier - no id, no nonce, no timestamp, in neither the body
        // nor the headers - so there is nothing stable to deduplicate on. A key built from
        // resource and event type would collapse two genuine notifications about the same
        // appointment into one and we would miss the second. A fresh id per delivery means a
        // retry is processed twice instead, which is harmless: the handler re-reads current state
        // from Healthie and upserts, so applying the same notification twice reaches one answer.
        boolean recorded = inbox.receive(
                InboxSource.HEALTHIE,
                UUID.randomUUID().toString(),
                parsed.eventType(),
                parsed.resourceId(),
                parsed.resourceType(),
                payload,
                true);

        if (!recorded) {
            log.debug("Healthie webhook for {} was already recorded", parsed.resourceId());
        }
        return ResponseEntity.ok().build();
    }
}
