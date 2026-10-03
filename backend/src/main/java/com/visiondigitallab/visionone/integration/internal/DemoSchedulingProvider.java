package com.visiondigitallab.visionone.integration.internal;

import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
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
 * Phase-1 scheduling adapter: synthetic appointments, reported as {@link ProviderStatus#DEMO}.
 *
 * <p>It reports DEMO rather than CONNECTED deliberately. A demo adapter that claims to be connected
 * is how a practice ends up believing a figure came from their own system.
 *
 * <p>Swapping this for Healthie is a property change and a new class in this package. Nothing
 * outside {@code integration} knows which adapter is bound, because everything outside talks to
 * {@link SchedulingProvider}.
 */
@Component
@ConditionalOnProperty(name = "visionone.providers.scheduling", havingValue = "DEMO", matchIfMissing = true)
public class DemoSchedulingProvider implements SchedulingProvider {

    private static final String[] CATEGORIES = {
        "Hormone Therapy", "Longevity Panel", "Weight Management", "Peptide Therapy", "IV Therapy"
    };
    private static final String[] CHANNELS = {
        "GOOGLE_ADS", "ORGANIC_SEARCH", "GOOGLE_MAPS", "AI_FRONT_DESK", "SOCIAL"
    };

    @Override
    public String providerCode() {
        return "DEMO_SCHEDULING";
    }

    @Override
    public ProviderStatus status(UUID organizationId) {
        return ProviderStatus.DEMO;
    }

    @Override
    public List<AppointmentReference> appointments(UUID organizationId, LocalDate from, LocalDate to) {
        List<AppointmentReference> out = new ArrayList<>();
        int i = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            // Weekends closed, and not every weekday carries a booking - a calendar with something
            // on every square does not look like a real practice.
            if (day.getDayOfWeek().getValue() >= 6 || day.getDayOfMonth() % 3 == 0) {
                continue;
            }
            int perDay = (day.getDayOfMonth() % 4 == 0) ? 2 : 1;
            for (int n = 0; n < perDay; n += 1) {
                Instant startsAt = day.atTime(LocalTime.of(9 + ((i + n) % 7), (n == 0) ? 0 : 30))
                        .toInstant(ZoneOffset.UTC);
                out.add(new AppointmentReference(
                        "DEMO-%s-%d".formatted(day, n),
                        startsAt,
                        (i % 2 == 0) ? 45 : 30,
                        // Non-identifying by construction, matching what a real adapter must reduce to.
                        "Patient %d".formatted(500 + i),
                        CATEGORIES[i % CATEGORIES.length],
                        CHANNELS[i % CHANNELS.length],
                        (i % 9 == 0) ? AppointmentState.CANCELLED : AppointmentState.BOOKED));
                i += 1;
            }
        }
        return List.copyOf(out);
    }

    /**
     * The demo refs encode their own day - "DEMO-2026-10-03-0" - so one can be found by regenerating
     * that single day and matching. Nothing in demo mode actually calls this, because the inbox only
     * processes events from a real provider; implementing it properly anyway keeps the two adapters
     * honestly interchangeable rather than leaving a hole that returns null and looks like a deletion.
     */
    @Override
    public AppointmentReference appointment(UUID organizationId, String externalRef) {
        if (externalRef == null || !externalRef.startsWith("DEMO-")) {
            return null;
        }
        String body = externalRef.substring("DEMO-".length());
        int lastDash = body.lastIndexOf('-');
        if (lastDash < 0) {
            return null;
        }
        LocalDate day;
        try {
            day = LocalDate.parse(body.substring(0, lastDash));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
        return appointments(organizationId, day, day).stream()
                .filter(a -> externalRef.equals(a.externalRef()))
                .findFirst()
                .orElse(null);
    }
}
