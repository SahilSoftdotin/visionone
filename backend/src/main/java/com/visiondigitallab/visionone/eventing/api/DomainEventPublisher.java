package com.visiondigitallab.visionone.eventing.api;

import java.util.UUID;

/**
 * Publishes a domain event by writing it to the outbox inside the caller's transaction.
 *
 * <p>Nothing is delivered here. The dispatcher does that after the transaction commits, which is
 * what makes it impossible to publish an event for a change that rolled back. Callers do not know
 * how events travel, which is why the transport could change without touching any of them.
 */
public interface DomainEventPublisher {

    void publish(EventType type, UUID organizationId, String aggregateType, UUID aggregateId, Object payload);
}
