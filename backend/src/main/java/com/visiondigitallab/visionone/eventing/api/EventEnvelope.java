package com.visiondigitallab.visionone.eventing.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * The single message shape on every VisionOne topic.
 *
 * <p>Consumers read {@code eventVersion} before {@code payload} and reject an unknown version
 * loudly rather than guessing. There is no schema registry in Phase 1; this envelope is the
 * contract.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID organizationId,
        Instant occurredAt,
        UUID correlationId,
        String source,
        JsonNode payload) {}
