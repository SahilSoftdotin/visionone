package com.visiondigitallab.visionone.eventing.api;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a handler's work exactly once per event, however many times it is delivered.
 *
 * <p>Claim-then-apply: the insert either wins the row or reports zero rows affected, and the work
 * runs only for the winner. One statement, so the claim and the decision cannot disagree.
 *
 * <p>This deliberately does not go through JPA. {@code processed_event} has a composite key, and
 * Spring Data treats an entity with a populated id as already persistent, so {@code save} issues a
 * merge: a SELECT followed by an UPDATE. A merge never raises the unique violation this guard was
 * relying on, which made the check silently pass every time and every handler run on every
 * delivery. {@code on conflict do nothing} states the intent directly and cannot be misread.
 *
 * <p>REQUIRES_NEW matters too. Each handler runs in its own transaction, so one failing handler
 * does not roll back another's work or the dispatcher's batch. A failed handler leaves its event
 * unpublished for retry, and handlers that already succeeded keep their claim.
 */
@Component
public class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final JdbcClient jdbc;

    public IdempotentConsumer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void once(String consumerGroup, EventEnvelope envelope, Consumer<EventEnvelope> work) {
        if (envelope.eventVersion() != 1) {
            throw new IllegalStateException(
                    "Consumer " + consumerGroup + " does not understand " + envelope.eventType()
                            + " version " + envelope.eventVersion());
        }

        int claimed = jdbc.sql("""
                insert into processed_event (consumer_group, event_id)
                values (:consumerGroup, :eventId)
                on conflict (consumer_group, event_id) do nothing
                """)
                .param("consumerGroup", consumerGroup)
                .param("eventId", envelope.eventId())
                .update();

        if (claimed == 0) {
            log.debug("{} already handled event {}", consumerGroup, envelope.eventId());
            return;
        }

        work.accept(envelope);
    }
}
