package com.visiondigitallab.visionone.eventing.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visiondigitallab.visionone.eventing.api.DomainEventHandler;
import com.visiondigitallab.visionone.eventing.api.EventEnvelope;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.eventing.api.IdempotentConsumer;
import com.visiondigitallab.visionone.eventing.domain.OutboxEvent;
import com.visiondigitallab.visionone.eventing.repository.OutboxEventRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the outbox to the handlers that care about each event.
 *
 * <p>This is the whole event transport. Postgres is the queue: a row with a null
 * {@code published_at} is a message not yet delivered, and {@code FOR UPDATE SKIP LOCKED} lets
 * more than one instance drain the same table safely.
 *
 * <p>A row is marked published only once every interested handler has succeeded. If one fails,
 * the row stays unpublished and is retried, and the handlers that already ran are skipped by
 * their own {@code processed_event} rows. That is why idempotency is per consumer group.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final IdempotentConsumer idempotent;
    private final List<DomainEventHandler> handlers;
    private final Map<String, EventType> byWireName;
    private final int batchSize;

    public OutboxDispatcher(
            OutboxEventRepository outbox,
            ObjectMapper objectMapper,
            IdempotentConsumer idempotent,
            List<DomainEventHandler> handlers,
            @Value("${visionone.eventing.dispatcher.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.idempotent = idempotent;
        this.handlers = handlers;
        this.batchSize = batchSize;
        this.byWireName = java.util.Arrays.stream(EventType.values())
                .collect(Collectors.toMap(EventType::wireName, Function.identity()));
    }

    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outbox.claimUnpublished(MAX_ATTEMPTS, PageRequest.of(0, batchSize));
        for (OutboxEvent event : batch) {
            dispatch(event);
        }
    }

    private void dispatch(OutboxEvent event) {
        Optional<EventType> type = Optional.ofNullable(byWireName.get(event.getEventType()));
        if (type.isEmpty()) {
            // An unknown type is a bug, not a transient failure. Record it and stop retrying.
            event.markFailed("No EventType maps to '" + event.getEventType() + "'");
            log.error("Outbox event {} has unmappable type {}", event.getId(), event.getEventType());
            return;
        }

        EventEnvelope envelope;
        try {
            envelope = envelope(event);
        } catch (JsonProcessingException ex) {
            event.markFailed("Stored payload is not valid JSON: " + ex.getOriginalMessage());
            log.error("Outbox event {} has an unreadable payload", event.getId(), ex);
            return;
        }

        boolean allSucceeded = true;
        for (DomainEventHandler handler : handlers) {
            if (!handler.handles().contains(type.get())) {
                continue;
            }
            try {
                idempotent.once(handler.consumerGroup(), envelope, handler::handle);
            } catch (RuntimeException ex) {
                allSucceeded = false;
                event.markFailed(handler.consumerGroup() + ": " + ex.getMessage());
                log.warn("Handler {} failed on event {} (attempt {}): {}",
                        handler.consumerGroup(), event.getId(), event.getAttempts(), ex.getMessage());
            }
        }

        if (allSucceeded) {
            event.markPublished();
        }
    }

    private EventEnvelope envelope(OutboxEvent event) throws JsonProcessingException {
        return new EventEnvelope(
                event.getId(),
                event.getEventType(),
                event.getEventVersion(),
                event.getOrganizationId(),
                event.getOccurredAt(),
                event.getCorrelationId(),
                "visionone." + event.getAggregateType().toLowerCase(Locale.ROOT),
                objectMapper.readTree(event.getPayloadJson()));
    }
}
