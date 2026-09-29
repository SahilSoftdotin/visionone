package com.visiondigitallab.visionone.eventing.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the inbox processor on a timer.
 *
 * <p>Slower than the outbox by default: an inbound webhook has already been acknowledged, so a few
 * seconds of lag costs nothing, and each row may involve a call back to the provider.
 */
@Component
@ConditionalOnProperty(
        name = "visionone.eventing.inbox.enabled", havingValue = "true", matchIfMissing = true)
public class InboxDrainScheduler {

    private final InboxProcessor processor;

    public InboxDrainScheduler(InboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${visionone.eventing.inbox.interval-ms:5000}")
    public void drain() {
        processor.drain();
    }
}
