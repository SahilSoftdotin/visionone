package com.visiondigitallab.visionone.integration.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.visiondigitallab.visionone.integration.api.AdvertisingProvider;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Google Ads adapter, against a stubbed Google.
 *
 * <p>The test that matters most is {@link #microsBecomeMinorUnits()}. Google reports cost in micros
 * - millionths - and the port wants minor units, which are hundredths. The divisor is 10,000, and
 * using 1,000,000 by reflex is a silent factor of a hundred on every figure a client sees. Nothing
 * about the dashboard would look broken: the numbers stay internally consistent, cost per lead stays
 * proportional, and £380 of spend reads as £3.80 for as long as nobody checks it against an invoice.
 *
 * <p>These tests prove the adapter is internally correct. They cannot prove the field names are
 * right - those come from Google's published schema, and the account this was built for is still
 * "Setup in progress", so no live query has ever succeeded against it.
 */
class GoogleAdsAdvertisingProviderTest {

    private static final UUID ORG = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    private WireMockServer google;
    private GoogleAdsAdvertisingProvider provider;

    @BeforeEach
    void start() {
        google = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        google.start();
        stubToken();
        var client = new GoogleAdsClient(
                google.baseUrl(), google.baseUrl() + "/token", "v22",
                "cid", "secret", "refresh", "4676443058", 2000, 5000);
        provider = new GoogleAdsAdvertisingProvider(client, "9049878541", "USD");
    }

    @AfterEach
    void stop() {
        google.stop();
    }

    private void stubToken() {
        google.stubFor(post(urlPathMatching("/token"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"ya29.test\",\"expires_in\":3599}")));
    }

    private void stubSearch(String body) {
        google.stubFor(post(urlPathMatching(".*googleAds:searchStream"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    @DisplayName("micros become minor units - the divisor is 10,000, not 1,000,000")
    void microsBecomeMinorUnits() {
        // $380.00 of spend is 380_000_000 micros and 38_000 cents.
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits("380000000")).isEqualTo(38_000L);
        // $1.00
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits("1000000")).isEqualTo(100L);
        // One cent
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits("10000")).isEqualTo(1L);
        // Half a cent rounds up rather than truncating, so a month of days does not drift down.
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits("5000")).isEqualTo(1L);
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits("4999")).isEqualTo(0L);
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits("0")).isZero();
        // Junk is zero rather than an exception that kills a whole month's sync.
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits("not a number")).isZero();
        assertThat(GoogleAdsAdvertisingProvider.toMinorUnits(null)).isZero();
    }

    @Test
    @DisplayName("maps a day of campaign spend onto the port")
    void mapsADay() {
        stubSearch("""
                [{"results":[
                  {"campaign":{"id":"111","name":"Hormone Therapy - Search",
                               "advertisingChannelType":"SEARCH"},
                   "customer":{"currencyCode":"USD"},
                   "segments":{"date":"2026-09-14"},
                   "metrics":{"costMicros":"42500000","impressions":"1840","clicks":"73"}}
                ]}]
                """);

        var out = provider.spend(ORG, FROM, TO);

        assertThat(out).hasSize(1);
        var s = out.get(0);
        assertThat(s.day()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(s.campaignRef()).isEqualTo("111");
        assertThat(s.channelCode()).isEqualTo("GOOGLE_ADS");
        // $42.50
        assertThat(s.spendMinor()).isEqualTo(4_250L);
        assertThat(s.currency()).isEqualTo("USD");
        assertThat(s.impressions()).isEqualTo(1840L);
        assertThat(s.clicks()).isEqualTo(73L);
    }

    @Test
    @DisplayName("metrics arrive as JSON strings and are parsed as such")
    void metricsAreStrings() {
        // Reading these as numbers yields 0 without complaining, which is how a dashboard ends up
        // confidently reporting no spend on a month that cost thousands.
        stubSearch("""
                [{"results":[
                  {"campaign":{"id":"1","name":"c","advertisingChannelType":"SEARCH"},
                   "customer":{"currencyCode":"GBP"},
                   "segments":{"date":"2026-09-02"},
                   "metrics":{"costMicros":"9876543","impressions":"12345","clicks":"678"}}
                ]}]
                """);
        var s = provider.spend(ORG, FROM, TO).get(0);
        assertThat(s.spendMinor()).isEqualTo(988L);
        assertThat(s.impressions()).isEqualTo(12345L);
        assertThat(s.clicks()).isEqualTo(678L);
        assertThat(s.currency()).isEqualTo("GBP");
    }

    @Test
    @DisplayName("results are flattened across the chunks searchStream returns")
    void flattensChunks() {
        stubSearch("""
                [
                  {"results":[
                    {"campaign":{"id":"1","name":"a","advertisingChannelType":"SEARCH"},
                     "customer":{"currencyCode":"USD"},"segments":{"date":"2026-09-01"},
                     "metrics":{"costMicros":"1000000","impressions":"1","clicks":"1"}}]},
                  {"results":[
                    {"campaign":{"id":"2","name":"b","advertisingChannelType":"SEARCH"},
                     "customer":{"currencyCode":"USD"},"segments":{"date":"2026-09-02"},
                     "metrics":{"costMicros":"2000000","impressions":"2","clicks":"2"}}]}
                ]
                """);
        assertThat(provider.spend(ORG, FROM, TO))
                .extracting(AdvertisingProvider.ChannelSpend::campaignRef)
                .containsExactly("1", "2");
    }

    @Test
    @DisplayName("campaign types map onto channels the practice already reports on")
    void mapsCampaignTypes() {
        assertThat(GoogleAdsAdvertisingProvider.channelCodeFor("SEARCH")).isEqualTo("GOOGLE_ADS");
        assertThat(GoogleAdsAdvertisingProvider.channelCodeFor("LOCAL")).isEqualTo("GOOGLE_MAPS");
        assertThat(GoogleAdsAdvertisingProvider.channelCodeFor("VIDEO")).isEqualTo("YOUTUBE");
        // Anything Google adds later is still Google advertising, not a new channel.
        assertThat(GoogleAdsAdvertisingProvider.channelCodeFor("DEMAND_GEN")).isEqualTo("GOOGLE_ADS");
        assertThat(GoogleAdsAdvertisingProvider.channelCodeFor(null)).isEqualTo("GOOGLE_ADS");
    }

    @Test
    @DisplayName("an outage is an outage, never a month of zero spend")
    void outageIsNotZeroSpend() {
        google.stubFor(post(urlPathMatching(".*googleAds:searchStream"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> provider.spend(ORG, FROM, TO))
                .isInstanceOf(AdvertisingProvider.AdvertisingUnavailableException.class);
    }

    @Test
    @DisplayName("a revoked refresh token is reported, not swallowed")
    void revokedTokenIsReported() {
        google.stubFor(post(urlPathMatching("/token"))
                .willReturn(aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\"}")));

        assertThatThrownBy(() -> provider.spend(ORG, FROM, TO))
                .isInstanceOf(AdvertisingProvider.AdvertisingUnavailableException.class);
    }

    @Test
    @DisplayName("the access token is minted once and reused, not fetched per request")
    void accessTokenIsCached() {
        stubSearch("[{\"results\":[]}]");
        provider.spend(ORG, FROM, TO);
        provider.spend(ORG, FROM, TO);
        provider.spend(ORG, FROM, TO);

        assertThat(google.findAll(postRequestedFor(urlPathMatching("/token")))).hasSize(1);
    }

    @Test
    @DisplayName("the manager account is sent as login-customer-id, and the query carries the range")
    void sendsManagerHeaderAndDateRange() {
        stubSearch("[{\"results\":[]}]");
        provider.spend(ORG, FROM, TO);

        var sent = google.findAll(postRequestedFor(urlPathMatching(".*googleAds:searchStream"))).get(0);
        assertThat(sent.getHeader("login-customer-id")).isEqualTo("4676443058");
        assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer ya29.test");
        String body = sent.getBodyAsString();
        assertThat(body).contains("segments.date BETWEEN '2026-09-01' AND '2026-09-30'");
        assertThat(body).contains("metrics.cost_micros");
    }

    @Test
    @DisplayName("status reports CONNECTED only when the credentials actually work")
    void statusReflectsReality() {
        assertThat(provider.status(ORG)).isEqualTo(ProviderStatus.CONNECTED);

        google.resetAll();
        google.stubFor(post(urlPathMatching("/token")).willReturn(aResponse().withStatus(401)));
        var client = new GoogleAdsClient(
                google.baseUrl(), google.baseUrl() + "/token", "v22",
                "cid", "secret", "bad", "", 2000, 5000);
        assertThat(new GoogleAdsAdvertisingProvider(client, "9049878541", "USD").status(ORG))
                .isEqualTo(ProviderStatus.ERROR);
    }

    @Test
    @DisplayName("a row with no usable date is skipped rather than reported on the wrong day")
    void unparseableDateIsSkipped() {
        stubSearch("""
                [{"results":[
                  {"campaign":{"id":"1","name":"bad","advertisingChannelType":"SEARCH"},
                   "customer":{"currencyCode":"USD"},"segments":{"date":"not a date"},
                   "metrics":{"costMicros":"1000000","impressions":"1","clicks":"1"}},
                  {"campaign":{"id":"2","name":"good","advertisingChannelType":"SEARCH"},
                   "customer":{"currencyCode":"USD"},"segments":{"date":"2026-09-05"},
                   "metrics":{"costMicros":"2000000","impressions":"2","clicks":"2"}}
                ]}]
                """);
        assertThat(provider.spend(ORG, FROM, TO))
                .extracting(AdvertisingProvider.ChannelSpend::campaignRef)
                .containsExactly("2");
    }
}
