package com.visiondigitallab.visionone.integration.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The whole of a Healthie webhook body.
 *
 * <p>It really is this small. Healthie sends
 * {@code {"resource_id": "...", "resource_id_type": "Appointment", "event_type": "appointment.updated"}},
 * plus a {@code changed_fields} array on updates. There is no timestamp, no delivery id, and no
 * copy of the resource - which is why the handler has to go and ask.
 *
 * <p>{@code changed_fields} is deliberately not read. Acting on it would mean trusting a webhook to
 * tell us what changed and patching only that; re-reading the whole appointment is both simpler and
 * correct when a notification is lost or arrives out of order.
 */
record HealthieWebhookPayload(String resourceId, String resourceType, String eventType) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Returns null for anything that is not a usable Healthie notification. */
    static HealthieWebhookPayload parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
        String eventType = text(node, "event_type");
        String resourceId = text(node, "resource_id");
        if (eventType == null || resourceId == null) {
            return null;
        }
        return new HealthieWebhookPayload(resourceId, text(node, "resource_id_type"), eventType);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
