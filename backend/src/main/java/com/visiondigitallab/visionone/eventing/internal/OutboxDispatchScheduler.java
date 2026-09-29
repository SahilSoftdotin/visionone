package com.visiondigitallab.visionone.eventing.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the outbox dispatcher on a timer.
 *
 * <p>Kept separate from {@link OutboxDispatcher} on purpose: the property switches the schedule
 * off, not the capability. Tests turn the timer off and call {@code drain()} themselves, which is
 * how they assert on exact counts without racing a poller.
 */
@Component
@ConditionalOnProperty(
        name = "visionone.eventing.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatchScheduler {

    private final OutboxDispatcher dispatcher;

    public OutboxDispatchScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${visionone.eventing.dispatcher.interval-ms:1000}")
    public void drain() {
        dispatcher.drain();
    }
}
