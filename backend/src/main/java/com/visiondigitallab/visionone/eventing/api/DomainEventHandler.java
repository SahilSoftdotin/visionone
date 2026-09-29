package com.visiondigitallab.visionone.eventing.api;

import java.util.Set;

/**
 * Something that reacts to a domain event.
 *
 * <p>Implementations are found by Spring and driven by the outbox dispatcher. A handler never
 * subscribes to a transport, which is what let Kafka be removed without touching any of them and
 * what would let it return.
 *
 * <p>A handler has no HTTP request and therefore no organization context. It reads
 * {@code organizationId} from the envelope and passes it explicitly; reaching for the ambient
 * context here is a tenant-isolation bug.
 */
public interface DomainEventHandler {

    /**
     * Identifies this handler for idempotency purposes. Named "consumer group" because that is
     * what it is, and because the concept outlives whichever transport carries the events.
     */
    String consumerGroup();

    Set<EventType> handles();

    void handle(EventEnvelope envelope);
}
