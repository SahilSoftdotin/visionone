package com.visiondigitallab.visionone.eventing.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visiondigitallab.visionone.eventing.api.DomainEventPublisher;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.eventing.domain.OutboxEvent;
import com.visiondigitallab.visionone.eventing.repository.OutboxEventRepository;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * MANDATORY: publishing outside a transaction is a bug, not a convenience. The whole point of
     * the outbox is that the event and the change it describes commit together.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(
            EventType type, UUID organizationId, String aggregateType, UUID aggregateId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Event payload for " + type.wireName() + " is not serializable", ex);
        }
        outbox.save(new OutboxEvent(
                UUID.randomUUID(),
                organizationId,
                type.wireName(),
                aggregateType,
                aggregateId,
                json,
                correlationId()));
    }

    private UUID correlationId() {
        String traceId = MDC.get("traceId");
        if (traceId != null && traceId.length() == 32) {
            try {
                return UUID.fromString(traceId.replaceFirst(
                        "(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
            } catch (IllegalArgumentException ignored) {
                // Fall through to a fresh correlation id.
            }
        }
        return UUID.randomUUID();
    }
}
