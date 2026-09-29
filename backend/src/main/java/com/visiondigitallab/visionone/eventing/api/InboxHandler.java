package com.visiondigitallab.visionone.eventing.api;

import com.visiondigitallab.visionone.eventing.domain.InboxEvent;
import com.visiondigitallab.visionone.eventing.domain.InboxSource;
import java.util.UUID;

/**
 * Processes a webhook that has already been received and recorded.
 *
 * <p>Implementations do the slow work the endpoint deliberately skipped: fetch the resource from
 * the provider, map it onto VisionOne's own records, and return which organization it belonged to.
 *
 * <p>Phase 2 adds the Healthie implementation here. Nothing else about the inbox changes when it
 * does, which is the point of building the plumbing first.
 */
public interface InboxHandler {

    InboxSource source();

    boolean handles(String eventType);

    /**
     * Applies the event.
     *
     * @return the organization the resource belonged to, recorded on the row for traceability
     */
    UUID process(InboxEvent event);
}
