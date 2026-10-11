package com.visiondigitallab.visionone.integration.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads appointments out of Healthie.
 *
 * <p>Three things about this adapter are deliberate rather than incidental.
 *
 * <p><b>The query is the PHI boundary.</b> Healthie's Appointment type also exposes
 * {@code location}, {@code contact_type}, {@code provider}, {@code form_answer_group}, and an
 * {@code attendees} array carrying {@code phone_number} and {@code avatar_url}. None of it is
 * requested below. Not filtering it out after the fact - never asking for it. Data that was never
 * fetched cannot be logged by accident, cannot appear in an exception message, and cannot be
 * persisted by a later change to the mapping.
 *
 * <p><b>Healthie cannot filter by date range.</b> Its {@code appointments} query takes
 * {@code filter} ("past", "upcoming", "all"), {@code offset} and {@code page_size}, and nothing
 * else. So the range the port promises is applied here, after paging. For one clinic that is a few
 * hundred rows; {@code maxPages} stops it becoming unbounded if an account turns out to be larger
 * than expected.
 *
 * <p><b>The patient is reduced on the way in.</b> {@code user.full_name} becomes "Dana W." before it
 * is returned, and Healthie's own record id is never carried at all - only the appointment id, which
 * is what a later change has to be matched against.
 */
@Component
@ConditionalOnProperty(name = "visionone.providers.scheduling", havingValue = "HEALTHIE")
public class HealthieSchedulingProvider implements SchedulingProvider {

    private static final Logger log = LoggerFactory.getLogger(HealthieSchedulingProvider.class);

    /** Healthie renders timestamps as "2026-05-08 21:00:00 UTC". */
    private static final DateTimeFormatter HEALTHIE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT);

    private static final String APPOINTMENTS_QUERY = """
            query visiononeAppointments($offset: Int, $page_size: Int) {
              appointments(
                is_org: true
                filter: "all"
                should_paginate: true
                with_all_statuses: true
                order_by: "date_desc"
                offset: $offset
                page_size: $page_size
              ) {
                id
                date
                length
                pm_status
                appointment_type { name }
                user { full_name }
              }
            }
            """;

    private static final String ONE_APPOINTMENT_QUERY = """
            query visiononeAppointment($id: ID) {
              appointment(id: $id) {
                id
                date
                length
                pm_status
                appointment_type { name }
                user { full_name }
              }
            }
            """;

    private final HealthieGraphQlClient client;
    private final int pageSize;
    private final int maxPages;
    private final int defaultDurationMinutes;

    public HealthieSchedulingProvider(
            HealthieGraphQlClient client,
            @Value("${visionone.healthie.page-size:100}") int pageSize,
            @Value("${visionone.healthie.max-pages:20}") int maxPages,
            @Value("${visionone.healthie.default-duration-minutes:45}") int defaultDurationMinutes) {
        this.client = client;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        this.defaultDurationMinutes = defaultDurationMinutes;
    }

    @Override
    public String providerCode() {
        return "HEALTHIE";
    }

    @Override
    public ProviderStatus status(UUID organizationId) {
        return client.reachable() ? ProviderStatus.CONNECTED : ProviderStatus.ERROR;
    }

    @Override
    public List<AppointmentReference> appointments(UUID organizationId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<AppointmentReference> out = new ArrayList<>();
        try {
            for (int page = 0; page < maxPages; page += 1) {
                JsonNode rows = client
                        .query(APPOINTMENTS_QUERY, Map.of("offset", page * pageSize, "page_size", pageSize))
                        .path("appointments");
                if (!rows.isArray() || rows.isEmpty()) {
                    break;
                }
                for (JsonNode row : rows) {
                    AppointmentReference ref = toReference(row);
                    if (ref == null) {
                        continue;
                    }
                    if (!ref.startsAt().isBefore(fromInstant) && ref.startsAt().isBefore(toInstant)) {
                        out.add(ref);
                    }
                }
                if (rows.size() < pageSize) {
                    break;
                }
                if (page == maxPages - 1) {
                    // Say so rather than silently returning a truncated month.
                    log.warn(
                            "Healthie paging stopped at the {}-page cap for organization {}; "
                                    + "raise visionone.healthie.max-pages",
                            maxPages,
                            organizationId);
                }
            }
        } catch (HealthieGraphQlClient.HealthieUnavailableException e) {
            throw new SchedulingUnavailableException("Could not read appointments from Healthie", e);
        }
        return out;
    }

    @Override
    public AppointmentReference appointment(UUID organizationId, String externalRef) {
        try {
            JsonNode node = client
                    .query(ONE_APPOINTMENT_QUERY, Map.of("id", externalRef))
                    .path("appointment");
            return node.isMissingNode() || node.isNull() ? null : toReference(node);
        } catch (HealthieGraphQlClient.HealthieUnavailableException e) {
            throw new SchedulingUnavailableException("Could not read appointment " + externalRef, e);
        }
    }

    /** Returns null for a row we cannot place in time, which is a row we cannot show on a calendar. */
    private AppointmentReference toReference(JsonNode row) {
        String id = row.path("id").asText(null);
        Instant startsAt = parseDate(row.path("date").asText(null));
        if (id == null || startsAt == null) {
            log.debug("Skipping a Healthie appointment with no id or unparseable date");
            return null;
        }
        int length = row.path("length").asInt(0);
        return new AppointmentReference(
                id,
                startsAt,
                length > 0 ? length : defaultDurationMinutes,
                displayLabel(row.path("user").path("full_name").asText(null)),
                serviceCategory(row.path("appointment_type").path("name").asText(null)),
                // Healthie neither knows nor cares which advertisement produced a patient. Null is
                // honest; Vision credits the channel by hand on the Calendar.
                null,
                state(row.path("pm_status").asText(null)));
    }

    static Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), HEALTHIE_DATE).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // Some fields come back as ISO-8601 depending on the pinned API version.
            try {
                return Instant.parse(raw.trim());
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }

    /**
     * "Dana Whitfield" becomes "Dana W." - enough for a front desk to recognise the booking, not
     * enough to identify someone from the screen alone.
     */
    static String displayLabel(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0];
        if (parts.length == 1) {
            return first;
        }
        String last = parts[parts.length - 1];
        return first + " " + Character.toUpperCase(last.charAt(0)) + ".";
    }

    /**
     * Healthie's appointment type names are the practice's own words, and the port forbids carrying a
     * clinical taxonomy through. This keeps the name only when it matches one of the categories the
     * product already reports, and otherwise says nothing.
     */
    static String serviceCategory(String appointmentTypeName) {
        if (appointmentTypeName == null || appointmentTypeName.isBlank()) {
            return null;
        }
        String normalised = appointmentTypeName.toUpperCase(Locale.ROOT);
        if (normalised.contains("HORMONE")) {
            return "HORMONE_OPTIMIZATION";
        }
        if (normalised.contains("WEIGHT")) {
            return "WEIGHT_MANAGEMENT";
        }
        if (normalised.contains("IV ") || normalised.contains("INFUSION")) {
            return "IV_THERAPY";
        }
        if (normalised.contains("PANEL") || normalised.contains("LAB") || normalised.contains("DIAGNOST")) {
            return "DIAGNOSTICS";
        }
        if (normalised.contains("LONGEVITY") || normalised.contains("CONSULT")) {
            return "LONGEVITY_PROGRAM";
        }
        // Deliberately not a guess and deliberately not Healthie's own string: an unmapped type
        // reads as "we do not categorise this" rather than inventing a category or leaking theirs.
        return null;
    }

    static AppointmentState state(String pmStatus) {
        if (pmStatus == null || pmStatus.isBlank()) {
            // A future booking has no outcome yet, which is exactly BOOKED.
            return AppointmentState.BOOKED;
        }
        return switch (pmStatus.trim().toLowerCase(Locale.ROOT)) {
            case "occurred" -> AppointmentState.ATTENDED;
            case "no-show", "no show" -> AppointmentState.NO_SHOW;
            case "re-scheduled", "rescheduled" -> AppointmentState.RESCHEDULED;
            case "cancelled", "canceled", "late cancellation" -> AppointmentState.CANCELLED;
            // "Checked-In" and anything Healthie adds later: still a live booking as far as a
            // marketing dashboard is concerned.
            default -> AppointmentState.BOOKED;
        };
    }
}
