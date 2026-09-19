package com.visiondigitallab.visionone.eventing.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visiondigitallab.visionone.eventing.api.EventEnvelope;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.eventing.domain.OutboxEvent;
import com.visiondigitallab.visionone.eventing.repository.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the outbox to Kafka.
 *
 * <p>If the broker is down, rows accumulate and every screen and every write still works. That is
 * a requirement of Phase 1, not a happy accident, and there is a test for it.
 */
@Component
@ConditionalOnProperty(name = "visionone.eventing.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public OutboxRelay(
            OutboxEventRepository outbox,
            KafkaTemplate<String, Object> kafka,
            ObjectMapper objectMapper,
            @Value("${visionone.eventing.relay.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${visionone.eventing.relay.interval-ms:1000}")
    @Transactional
    public void drain() {
        List<OutboxEvent> batch = outbox.claimUnpublished(MAX_ATTEMPTS, PageRequest.of(0, batchSize));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            try {
                kafka.send(topicFor(event), event.getOrganizationId().toString(), envelope(event))
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                event.markFailed("Interrupted while publishing");
                return;
            } catch (Exception ex) {
                // Left unpublished with the reason recorded, never silently dropped.
                event.markFailed(ex.getMessage());
                log.warn("Outbox event {} ({}) failed on attempt {}: {}",
                        event.getId(), event.getEventType(), event.getAttempts(), ex.getMessage());
            }
        }
    }

    private EventEnvelope envelope(OutboxEvent event) {
        try {
            return new EventEnvelope(
                    event.getId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getOrganizationId(),
                    event.getOccurredAt(),
                    event.getCorrelationId(),
                    "visionone." + event.getAggregateType().toLowerCase(java.util.Locale.ROOT),
                    objectMapper.readTree(event.getPayloadJson()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Stored outbox payload is not valid JSON: " + event.getId(), ex);
        }
    }

    private String topicFor(OutboxEvent event) {
        for (EventType type : EventType.values()) {
            if (type.wireName().equals(event.getEventType())) {
                return type.topic();
            }
        }
        throw new IllegalStateException("No topic mapped for event type " + event.getEventType());
    }
}
