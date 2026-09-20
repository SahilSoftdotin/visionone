package com.visiondigitallab.visionone.integration.internal;

import com.visiondigitallab.visionone.integration.api.AdvertisingProvider;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Phase-1 advertising adapter: synthetic spend, reported as {@link ProviderStatus#DEMO}.
 *
 * <p>Phase 1 spend is entered manually by Vision Digital Lab rather than read from a platform. This
 * adapter exists so the shape is already right when Google Ads and Meta arrive: minor units, one
 * row per channel per day, and a currency that travels with the amount rather than being assumed.
 */
@Component
@ConditionalOnProperty(
        name = "visionone.providers.advertising",
        havingValue = "DEMO",
        matchIfMissing = true)
public class DemoAdvertisingProvider implements AdvertisingProvider {

    private static final String[] CHANNELS = {
        "GOOGLE_ADS", "ORGANIC_SEARCH", "GOOGLE_MAPS", "AI_FRONT_DESK", "SOCIAL"
    };

    @Override
    public String providerCode() {
        return "DEMO_ADVERTISING";
    }

    @Override
    public ProviderStatus status(UUID organizationId) {
        return ProviderStatus.DEMO;
    }

    @Override
    public List<ChannelSpend> spend(UUID organizationId, LocalDate from, LocalDate to) {
        List<ChannelSpend> out = new ArrayList<>();
        int i = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            for (String channel : CHANNELS) {
                // Only paid channels carry spend. Organic showing a cost would be a lie the
                // dashboard then reports as cost per lead.
                boolean paid = channel.equals("GOOGLE_ADS") || channel.equals("SOCIAL");
                long spendMinor = paid ? 3000L + ((i * 431L) % 5000L) : 0L;

                out.add(new ChannelSpend(
                        day,
                        channel,
                        paid ? "DEMO-CAMPAIGN-%s".formatted(channel) : null,
                        spendMinor,
                        "USD",
                        paid ? 400L + ((i * 97L) % 2000L) : 0L,
                        paid ? 12L + ((i * 7L) % 60L) : 0L));
                i += 1;
            }
        }
        return List.copyOf(out);
    }
}
