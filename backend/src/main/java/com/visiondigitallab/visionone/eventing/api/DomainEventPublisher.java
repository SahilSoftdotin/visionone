package com.visiondigitallab.visionone.eventing.api;

import java.util.UUID;

/**
 * Publishes a domain event by writing it to the outbox inside the caller's transaction.
 *
 * <p>Nothing reaches Kafka here. The relay does that after the transaction commits, which is what
 * makes it impossible to publish an event for a change that rolled back.
 */
public interface DomainEventPublisher {

    void publish(EventType type, UUID organizationId, String aggregateType, UUID aggregateId, Object payload);
}
