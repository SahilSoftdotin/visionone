package com.visiondigitallab.visionone.eventing.api;

import com.visiondigitallab.visionone.eventing.domain.ProcessedEvent;
import com.visiondigitallab.visionone.eventing.repository.ProcessedEventRepository;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a consumer's work exactly once per event, whatever Kafka delivers.
 *
 * <p>Insert-then-apply: the unique key on {@code processed_event} is what rejects a redelivery,
 * so the check and the work commit together and a crash between them cannot lose either.
 *
 * <p>Consumers have no HTTP request and therefore no organization context. They read the
 * organization id from the envelope and pass it explicitly; a consumer that reaches for the
 * ambient context is a tenant-isolation bug.
 */
@Component
public class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final ProcessedEventRepository processed;

    public IdempotentConsumer(ProcessedEventRepository processed) {
        this.processed = processed;
    }

    @Transactional
    public void once(String consumerGroup, EventEnvelope envelope, Consumer<EventEnvelope> work) {
        if (envelope.eventVersion() != 1) {
            throw new IllegalStateException(
                    "Consumer " + consumerGroup + " does not understand " + envelope.eventType()
                            + " version " + envelope.eventVersion());
        }
        try {
            processed.saveAndFlush(new ProcessedEvent(consumerGroup, envelope.eventId()));
        } catch (DataIntegrityViolationException alreadyHandled) {
            log.debug("{} already handled event {}", consumerGroup, envelope.eventId());
            return;
        }
        work.accept(envelope);
    }
}
