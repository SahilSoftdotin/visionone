package com.visiondigitallab.visionone.integration.internal;

import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.VoiceProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Phase-1 voice adapter: synthetic calls, reported as {@link ProviderStatus#DEMO}.
 *
 * <p>The generated pattern puts most calls outside published hours on purpose. That is the claim
 * the service makes, and a demo that contradicts it would be quietly misleading in the one place
 * the product is trying to be persuasive.
 */
@Component
@ConditionalOnProperty(name = "visionone.providers.voice", havingValue = "DEMO", matchIfMissing = true)
public class DemoVoiceProvider implements VoiceProvider {

    private static final int OPEN_FROM = 9;
    private static final int OPEN_TO = 17;

    @Override
    public String providerCode() {
        return "DEMO_VOICE";
    }

    @Override
    public ProviderStatus status(UUID organizationId) {
        return ProviderStatus.DEMO;
    }

    @Override
    public List<CallRecord> calls(UUID organizationId, LocalDate from, LocalDate to) {
        List<CallRecord> out = new ArrayList<>();
        int i = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            int perDay = (day.getDayOfWeek().getValue() >= 6) ? 2 : 7;
            for (int n = 0; n < perDay; n += 1) {
                int hour = (n * 3 + i) % 24;
                boolean afterHours = hour < OPEN_FROM || hour >= OPEN_TO;
                Instant startedAt =
                        day.atTime(LocalTime.of(hour, (n % 4) * 15)).toInstant(ZoneOffset.UTC);

                CallOutcome outcome = switch (i % 6) {
                    case 0 -> CallOutcome.BOOKED;
                    case 1 -> CallOutcome.MISSED;
                    case 2 -> CallOutcome.ENQUIRY;
                    case 3 -> CallOutcome.TRANSFERRED;
                    case 4 -> CallOutcome.RESCHEDULED;
                    default -> CallOutcome.ENQUIRY;
                };
                Handler handler = outcome == CallOutcome.MISSED
                        ? Handler.VOICEMAIL
                        : (afterHours ? Handler.AI_FRONT_DESK : Handler.PRACTICE_TEAM);

                out.add(new CallRecord(
                        "DEMO-CALL-%s-%d".formatted(day, n),
                        startedAt,
                        outcome == CallOutcome.MISSED ? 0 : 60 + ((i * 17) % 200),
                        // Masked here, at the boundary, exactly as a live adapter must.
                        "+1 (256) 000-%04d".formatted(1000 + ((i * 37) % 8999)),
                        handler,
                        outcome,
                        afterHours,
                        afterHours ? "AI_FRONT_DESK" : "GOOGLE_ADS"));
                i += 1;
            }
        }
        return List.copyOf(out);
    }
}
