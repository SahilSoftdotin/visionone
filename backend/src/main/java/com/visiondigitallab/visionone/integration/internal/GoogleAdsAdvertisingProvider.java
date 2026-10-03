package com.visiondigitallab.visionone.integration.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.visiondigitallab.visionone.integration.api.AdvertisingProvider;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads spend and delivery out of Google Ads.
 *
 * <p><b>The money conversion is the part worth reading twice.</b> Google reports
 * {@code metrics.cost_micros} in micros - millionths of the account's currency unit. The port wants
 * minor units, which are hundredths. So the divisor is <b>10,000</b>, not 1,000,000. Getting that
 * wrong is a silent factor of a hundred in both directions on every figure the client sees, and a
 * dashboard reading £3.80 instead of £380 looks plausible enough to go unnoticed for a month.
 *
 * <p>Rounding is half-up through {@link BigDecimal} rather than integer division, because integer
 * division truncates and a month of truncated days drifts. The same reasoning as {@code Money}
 * elsewhere in the codebase: a float that reaches the domain has already lost cents.
 *
 * <p><b>Every figure is keyed to its day.</b> Google restates recent spend for days afterwards as
 * invalid clicks are credited back, which is precisely why {@link ChannelSpend} carries the day it
 * describes: a sync recomputes a period rather than accumulating into it.
 *
 * <p><b>Metrics arrive as JSON strings</b>, not numbers - {@code "cost_micros": "1234567"}. Reading
 * them as numbers yields zero without complaint, so they are parsed explicitly.
 */
@Component
@ConditionalOnProperty(name = "visionone.providers.advertising", havingValue = "GOOGLE_ADS")
public class GoogleAdsAdvertisingProvider implements AdvertisingProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleAdsAdvertisingProvider.class);
    private static final DateTimeFormatter GAQL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    /** Micros are millionths; minor units are hundredths. */
    private static final BigDecimal MICROS_PER_MINOR_UNIT = BigDecimal.valueOf(10_000L);

    private final GoogleAdsClient client;
    private final String customerId;
    private final String defaultCurrency;

    public GoogleAdsAdvertisingProvider(
            GoogleAdsClient client,
            @Value("${visionone.googleads.customer-id}") String customerId,
            @Value("${visionone.googleads.default-currency:USD}") String defaultCurrency) {
        this.client = client;
        this.customerId = customerId;
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public String providerCode() {
        return "GOOGLE_ADS";
    }

    @Override
    public ProviderStatus status(UUID organizationId) {
        return client.reachable() ? ProviderStatus.CONNECTED : ProviderStatus.ERROR;
    }

    @Override
    public List<ChannelSpend> spend(UUID organizationId, LocalDate from, LocalDate to) {
        String gaql = """
                SELECT campaign.id, campaign.name, campaign.advertising_channel_type,
                       customer.currency_code, segments.date,
                       metrics.cost_micros, metrics.impressions, metrics.clicks
                FROM campaign
                WHERE segments.date BETWEEN '%s' AND '%s'
                """.formatted(GAQL_DATE.format(from), GAQL_DATE.format(to));

        List<JsonNode> rows;
        try {
            rows = client.query(customerId, gaql);
        } catch (GoogleAdsClient.GoogleAdsUnavailableException e) {
            throw new AdvertisingUnavailableException("Could not read spend from Google Ads", e);
        }

        List<ChannelSpend> out = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            ChannelSpend spend = toSpend(row);
            if (spend != null) {
                out.add(spend);
            }
        }
        return out;
    }

    /** Returns null for a row that cannot be placed on a day, which is a row that cannot be reported. */
    private ChannelSpend toSpend(JsonNode row) {
        LocalDate day = parseDay(row.path("segments").path("date").asText(null));
        if (day == null) {
            log.debug("Skipping a Google Ads row with no usable date");
            return null;
        }
        JsonNode campaign = row.path("campaign");
        JsonNode metrics = row.path("metrics");

        return new ChannelSpend(
                day,
                channelCodeFor(campaign.path("advertisingChannelType").asText(null)),
                campaign.path("id").asText(null),
                toMinorUnits(metrics.path("costMicros").asText("0")),
                currencyOf(row),
                parseLong(metrics.path("impressions").asText("0")),
                parseLong(metrics.path("clicks").asText("0")));
    }

    /**
     * Micros to minor units, rounded half-up.
     *
     * <p>Package-private so the arithmetic can be tested directly. It is the one calculation here
     * that is wrong in a way nobody notices: every figure stays internally consistent, so the
     * dashboard looks fine and is simply off by a factor of a hundred.
     */
    static long toMinorUnits(String costMicros) {
        if (costMicros == null || costMicros.isBlank()) {
            return 0L;
        }
        try {
            return new BigDecimal(costMicros.trim())
                    .divide(MICROS_PER_MINOR_UNIT, 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            log.warn("Unparseable cost_micros from Google Ads: {}", costMicros);
            return 0L;
        }
    }

    static long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static LocalDate parseDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), GAQL_DATE);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** The account's own currency where Google states it, rather than an assumption. */
    private String currencyOf(JsonNode row) {
        String code = row.path("customer").path("currencyCode").asText(null);
        return (code == null || code.isBlank()) ? defaultCurrency : code;
    }

    /**
     * Google's campaign types onto VisionOne's own channels.
     *
     * <p>Everything from this account is Google advertising, so it maps to the GOOGLE_ADS channel.
     * The campaign type is kept separate for the two cases a clinic genuinely reports on
     * differently: a Local campaign is what puts them on the map rather than in search results, and
     * a video campaign is a different conversation about a different budget. Anything Google adds
     * later falls through to GOOGLE_ADS rather than inventing a channel the practice has never
     * heard of.
     */
    static String channelCodeFor(String advertisingChannelType) {
        if (advertisingChannelType == null) {
            return "GOOGLE_ADS";
        }
        return switch (advertisingChannelType.trim().toUpperCase(Locale.ROOT)) {
            case "LOCAL", "LOCAL_SERVICES" -> "GOOGLE_MAPS";
            case "VIDEO" -> "YOUTUBE";
            default -> "GOOGLE_ADS";
        };
    }
}
