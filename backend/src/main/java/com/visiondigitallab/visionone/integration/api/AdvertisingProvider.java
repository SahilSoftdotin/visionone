package com.visiondigitallab.visionone.integration.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The port an advertising platform is reached through. Google Ads and Meta are the intended
 * adapters.
 *
 * <p>No protected health information passes here, but money does, and a wrong figure on this screen
 * is a wrong budget decision. Two rules follow from that.
 *
 * <p>First, spend is a {@code long} in minor units. Ad platforms report micros, decimals and
 * strings, in a mixture of currencies. Converting once, inside the adapter, puts that conversion
 * somewhere it can be reviewed; a float reaching the domain has already lost cents.
 *
 * <p>Second, platforms revise recent figures for days afterwards. {@link ChannelSpend} carries the
 * day it describes so a sync recomputes a period rather than accumulating into it - the same reason
 * {@code metric_snapshot} is recomputed rather than incremented in place.
 */
public interface AdvertisingProvider {

    /** Which provider this is, for display and for {@code integration_connection.provider_code}. */
    String providerCode();

    /** Live connection state. Never inferred from whether the last call returned rows. */
    ProviderStatus status(UUID organizationId);

    /**
     * Spend and delivery per channel per day, for a closed range.
     *
     * @throws AdvertisingUnavailableException when the upstream cannot be reached, so zero spend is
     *     never confused with an unreachable account
     */
    List<ChannelSpend> spend(UUID organizationId, LocalDate from, LocalDate to);

    /**
     * One channel on one day.
     *
     * @param day the day these figures describe, so a later revision replaces rather than adds
     * @param channelCode the VisionOne channel, mapped by the adapter from the platform's own naming
     * @param campaignRef the platform's campaign id, or null when the figure is channel-level
     * @param spendMinor spend in minor units of {@code currency}
     * @param currency ISO 4217
     * @param impressions may be zero, never negative
     * @param clicks may be zero, never negative
     */
    record ChannelSpend(
            LocalDate day,
            String channelCode,
            String campaignRef,
            long spendMinor,
            String currency,
            long impressions,
            long clicks) {}

    /** Thrown when the upstream is unreachable, so absence is never mistaken for zero spend. */
    class AdvertisingUnavailableException extends RuntimeException {
        public AdvertisingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
