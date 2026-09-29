package com.visiondigitallab.visionone.eventing.internal;

import com.visiondigitallab.visionone.eventing.api.InboxHandler;
import com.visiondigitallab.visionone.eventing.domain.InboxEvent;
import com.visiondigitallab.visionone.eventing.repository.InboxEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the inbox: the slow half of webhook handling, outside the provider's request.
 *
 * <p>An unhandled event type is marked processed rather than retried forever. Providers send more
 * event types than any consumer cares about, and a permanently failing row would sit at the head
 * of the queue reporting a problem that is not one.
 */
@Component
public class InboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboxProcessor.class);
    private static final int MAX_ATTEMPTS = 10;

    private final InboxEventRepository inbox;
    private final List<InboxHandler> handlers;
    private final int batchSize;

    public InboxProcessor(
            InboxEventRepository inbox,
            List<InboxHandler> handlers,
            @Value("${visionone.eventing.inbox.batch-size:50}") int batchSize) {
        this.inbox = inbox;
        this.handlers = handlers;
        this.batchSize = batchSize;
    }

    @Transactional
    public void drain() {
        List<InboxEvent> batch = inbox.claimUnprocessed(MAX_ATTEMPTS, PageRequest.of(0, batchSize));
        for (InboxEvent event : batch) {
            process(event);
        }
    }

    private void process(InboxEvent event) {
        // A signature that did not verify is never acted on. The row is kept as evidence.
        if (!event.isSignatureVerified()) {
            event.markFailed("Signature was not verified; refusing to process");
            log.warn("Inbox event {} from {} has an unverified signature",
                    event.getId(), event.getSource());
            return;
        }

        Optional<InboxHandler> handler = handlers.stream()
                .filter(candidate -> candidate.source() == event.getSource())
                .filter(candidate -> candidate.handles(event.getEventType()))
                .findFirst();

        if (handler.isEmpty()) {
            // Nothing wants this event type. Acknowledged, not retried.
            event.markProcessed(event.getOrganizationId());
            log.debug("No handler for {} {}; acknowledged", event.getSource(), event.getEventType());
            return;
        }

        try {
            UUID organizationId = handler.get().process(event);
            event.markProcessed(organizationId);
        } catch (RuntimeException ex) {
            event.markFailed(ex.getMessage());
            log.warn("Inbox event {} ({} {}) failed on attempt {}: {}",
                    event.getId(), event.getSource(), event.getEventType(),
                    event.getAttempts(), ex.getMessage());
        }
    }
}
