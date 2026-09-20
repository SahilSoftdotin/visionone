package com.visiondigitallab.visionone.integration.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The port a web analytics platform is reached through. GA4 and Search Console are the intended
 * adapters.
 *
 * <p>Analytics platforms sample, threshold and withhold. An adapter must not paper over that: a
 * figure the platform declined to report is null here, never zero. The difference decides a budget.
 * Zero says nobody came; null says we were not told.
 *
 * <p>Nothing here identifies a visitor. The port returns channel totals only - no client
 * identifiers, no user ids, no page-level session data - so a marketing tool cannot quietly become
 * a way to observe an individual researching a condition.
 */
public interface AnalyticsProvider {

    /** Which provider this is, for display and for {@code integration_connection.provider_code}. */
    String providerCode();

    /** Live connection state. Never inferred from whether the last call returned rows. */
    ProviderStatus status(UUID organizationId);

    /**
     * Discovery per channel per day, for a closed range.
     *
     * @throws AnalyticsUnavailableException when the upstream cannot be reached
     */
    List<ChannelDiscovery> discovery(UUID organizationId, LocalDate from, LocalDate to);

    /**
     * One channel on one day. A null figure means the platform did not report it, which is not the
     * same as reporting none.
     *
     * @param day the day these figures describe
     * @param channelCode the VisionOne channel, mapped by the adapter
     * @param sessions visits, or null when withheld or sampled out
     * @param engagedSessions visits that did something, or null when unavailable
     * @param conversions goal completions attributed by the platform, or null when unavailable
     */
    record ChannelDiscovery(
            LocalDate day,
            String channelCode,
            Long sessions,
            Long engagedSessions,
            Long conversions) {}

    /** Thrown when the upstream is unreachable, so absence is never mistaken for no traffic. */
    class AnalyticsUnavailableException extends RuntimeException {
        public AnalyticsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
