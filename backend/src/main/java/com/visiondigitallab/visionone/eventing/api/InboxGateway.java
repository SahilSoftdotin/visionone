package com.visiondigitallab.visionone.eventing.api;

import com.visiondigitallab.visionone.eventing.domain.InboxSource;

/**
 * How a webhook endpoint records an inbound event.
 *
 * <p>This exists because the receiving implementation lives in {@code eventing.internal}, which
 * {@code ModuleBoundaryTest} keeps private to the eventing module - and the endpoints that receive
 * webhooks belong with the provider they are specific to, not here. A provider's controller
 * therefore depends on this interface rather than reaching across the boundary.
 *
 * <p>The contract an implementation must keep is that this is <b>fast</b>. A webhook endpoint has
 * one job: decide whether the request is genuine, write it down, and answer. Every provider gives
 * up on a slow endpoint and retries, and Healthie disables a webhook that keeps failing after about
 * three days. The actual work belongs to {@link InboxHandler}, running on a drain.
 */
public interface InboxGateway {

    /**
     * Records an inbound event for later processing.
     *
     * @param source which provider sent it
     * @param externalEventId the key uniqueness is enforced on, so a redelivery is not applied
     *     twice. Where a provider gives no delivery identifier, pass something unique per delivery
     *     and make the handler idempotent instead - a key that collapses two genuinely different
     *     notifications into one is worse than processing one twice.
     * @param eventType the provider's own event name
     * @param resourceId which resource changed
     * @param resourceType what kind of resource it is
     * @param payloadJson the body as received, or null for an empty object
     * @param signatureVerified whether the signature was checked and passed. An implementation must
     *     store this verbatim; a handler refuses to act on an unverified event, so claiming a check
     *     that did not happen is how an unsigned request gets applied.
     * @return true if this was recorded, false if it had already been seen
     */
    boolean receive(
            InboxSource source,
            String externalEventId,
            String eventType,
            String resourceId,
            String resourceType,
            String payloadJson,
            boolean signatureVerified);
}
