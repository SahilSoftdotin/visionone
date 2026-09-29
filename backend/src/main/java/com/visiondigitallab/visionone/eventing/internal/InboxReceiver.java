package com.visiondigitallab.visionone.eventing.internal;

import com.visiondigitallab.visionone.eventing.domain.InboxSource;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an incoming webhook and nothing else.
 *
 * <p>Everything here runs inside the provider's HTTP request, so it does the least possible work:
 * one insert, no fetch, no join, no mapping. Healthie disables a webhook that keeps timing out.
 *
 * <p>{@code on conflict do nothing} on {@code (source, external_event_id)} is what makes a
 * redelivery a no-op, in one statement, with no race between checking and inserting.
 */
@Service
public class InboxReceiver {

    private static final Logger log = LoggerFactory.getLogger(InboxReceiver.class);

    private final JdbcClient jdbc;

    public InboxReceiver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return true when the event was newly recorded, false when it was a redelivery. Either way
     *     the caller answers 200: telling a provider "already had it" is not an error, and an
     *     error would make it retry for three days.
     */
    @Transactional
    public boolean receive(
            InboxSource source,
            String externalEventId,
            String eventType,
            String resourceId,
            String resourceType,
            String payloadJson,
            boolean signatureVerified) {
        int inserted = jdbc.sql("""
                insert into inbox_event (id, source, external_event_id, event_type,
                                         resource_id, resource_type, payload_json, signature_verified)
                values (:id, :source, :externalEventId, :eventType,
                        :resourceId, :resourceType, cast(:payloadJson as jsonb), :signatureVerified)
                on conflict (source, external_event_id) do nothing
                """)
                .param("id", UUID.randomUUID())
                .param("source", source.name())
                .param("externalEventId", externalEventId)
                .param("eventType", eventType)
                .param("resourceId", resourceId)
                .param("resourceType", resourceType)
                .param("payloadJson", payloadJson == null ? "{}" : payloadJson)
                .param("signatureVerified", signatureVerified)
                .update();

        if (inserted == 0) {
            log.debug("{} redelivered event {}", source, externalEventId);
            return false;
        }
        return true;
    }
}
