package com.visiondigitallab.visionone.integration.internal;

import com.visiondigitallab.visionone.integration.api.AnalyticsProvider;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Phase-1 analytics adapter: synthetic discovery, reported as {@link ProviderStatus#DEMO}.
 *
 * <p>It withholds a figure now and then, returning null rather than zero, because GA4 genuinely
 * does that under thresholding. Anything consuming this port needs to handle the null before a live
 * adapter starts producing them in front of a client, not after.
 */
@Component
@ConditionalOnProperty(
        name = "visionone.providers.analytics",
        havingValue = "DEMO",
        matchIfMissing = true)
public class DemoAnalyticsProvider implements AnalyticsProvider {

    private static final String[] CHANNELS = {
        "GOOGLE_ADS", "ORGANIC_SEARCH", "GOOGLE_MAPS", "SOCIAL", "DIRECT_REFERRAL"
    };

    @Override
    public String providerCode() {
        return "DEMO_ANALYTICS";
    }

    @Override
    public ProviderStatus status(UUID organizationId) {
        return ProviderStatus.DEMO;
    }

    @Override
    public List<ChannelDiscovery> discovery(UUID organizationId, LocalDate from, LocalDate to) {
        List<ChannelDiscovery> out = new ArrayList<>();
        int i = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            for (String channel : CHANNELS) {
                // Every eleventh figure is withheld, as a thresholded platform would.
                boolean withheld = i % 11 == 0;
                Long sessions = withheld ? null : 20L + ((i * 13L) % 180L);
                Long engaged = withheld ? null : (sessions * 6) / 10;
                Long conversions = withheld ? null : (sessions / 25);

                out.add(new ChannelDiscovery(day, channel, sessions, engaged, conversions));
                i += 1;
            }
        }
        return List.copyOf(out);
    }
}
