package com.visiondigitallab.visionone.integration.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the handful of iCalendar properties VisionOne needs out of an .ics feed.
 *
 * <p><b>Hand-written rather than ical4j, deliberately.</b> This needs five properties - UID,
 * DTSTART, DTEND/DURATION, SUMMARY, STATUS - out of a feed produced by one known vendor. A full
 * iCalendar library brings parsing for alarms, free/busy blocks, journals, to-dos, attachments and
 * recurrence arithmetic, none of which is wanted here, and the same "never fetch what you do not
 * need" argument that shapes the GraphQL query applies to a dependency that can parse things we
 * would rather not accept.
 *
 * <p><b>Recurrence is detected, not expanded.</b> {@code RRULE} expansion is the one genuinely hard
 * part of iCalendar and the reason a library would be worth it. Healthie models each appointment -
 * including each occurrence of a series - as its own record with its own id, so an export is
 * expected to carry one VEVENT per appointment and no RRULE at all. That is an expectation, not a
 * verified fact, so a recurring event is flagged rather than quietly mishandled: the caller logs it
 * and skips it, which turns "we had this wrong for six months" into "we found out on the first
 * sync". If real feeds do carry RRULE, that is the moment to add ical4j.
 *
 * <p>Everything here is static and takes a string. No HTTP, no Spring, no clock.
 */
final class IcsCalendarParser {

    /** "20261015T143000Z" - the common case, already absolute. */
    private static final DateTimeFormatter UTC_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);

    /** "20261015T143000" - needs a zone, from TZID or the fallback. */
    private static final DateTimeFormatter LOCAL_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);

    /** "20261015" - an all-day entry. */
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private IcsCalendarParser() {}

    /**
     * One VEVENT, reduced to what the scheduling port can carry.
     *
     * <p>{@code endsAt} is null when the feed gave neither DTEND nor DURATION, which is legal
     * iCalendar and means the caller must supply a default length rather than assume zero.
     */
    record IcsEvent(
            String uid,
            Instant startsAt,
            Instant endsAt,
            String summary,
            String status,
            boolean recurring) {

        /** Null when the feed did not say, so the caller can tell "unstated" from "zero minutes". */
        Integer durationMinutes() {
            if (startsAt == null || endsAt == null) {
                return null;
            }
            long minutes = Duration.between(startsAt, endsAt).toMinutes();
            return minutes > 0 ? (int) minutes : null;
        }
    }

    /**
     * Parses a whole feed. Unreadable events are skipped rather than failing the batch: one
     * malformed VEVENT must not cost a practice its entire calendar.
     *
     * @param zone the zone to read floating timestamps in - those with neither a Z suffix nor a
     *     TZID. iCalendar says a floating time means "whatever local time is where this is read",
     *     which for a single-location clinic is the practice's own timezone.
     */
    static List<IcsEvent> parse(String ics, ZoneId zone) {
        List<IcsEvent> events = new ArrayList<>();
        if (ics == null || ics.isBlank()) {
            return events;
        }

        Map<String, Property> current = null;

        for (String line : unfold(ics)) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("BEGIN:VEVENT")) {
                current = new LinkedHashMap<>();
                continue;
            }
            if (upper.startsWith("END:VEVENT")) {
                if (current != null) {
                    IcsEvent event = toEvent(current, zone);
                    if (event != null) {
                        events.add(event);
                    }
                }
                current = null;
                continue;
            }
            if (current == null) {
                // Calendar-level properties (PRODID, VERSION) and any VTIMEZONE block. VTIMEZONE is
                // skipped on purpose: resolving a TZID against the JDK's own tz database is both
                // more correct and far less code than interpreting the feed's copy of the rules.
                continue;
            }
            Property property = parseLine(line);
            if (property != null) {
                // First occurrence wins. A VEVENT should not repeat DTSTART, and if one does, the
                // later value is more likely a malformed line than a correction.
                current.putIfAbsent(property.name(), property);
            }
        }
        return events;
    }

    private static IcsEvent toEvent(Map<String, Property> properties, ZoneId zone) {
        Property start = properties.get("DTSTART");
        Instant startsAt = start == null ? null : toInstant(start, zone);
        if (startsAt == null) {
            // An appointment with no readable start cannot go on a calendar, so there is nothing
            // useful to return. The caller counts these.
            return null;
        }

        Instant endsAt = null;
        Property end = properties.get("DTEND");
        if (end != null) {
            endsAt = toInstant(end, zone);
        }
        if (endsAt == null) {
            Property duration = properties.get("DURATION");
            if (duration != null) {
                Duration parsed = toDuration(duration.value());
                if (parsed != null && !parsed.isNegative() && !parsed.isZero()) {
                    endsAt = startsAt.plus(parsed);
                }
            }
        }

        return new IcsEvent(
                value(properties, "UID"),
                startsAt,
                endsAt,
                value(properties, "SUMMARY"),
                value(properties, "STATUS"),
                properties.containsKey("RRULE"));
    }

    private static String value(Map<String, Property> properties, String name) {
        Property property = properties.get(name);
        return property == null ? null : property.value();
    }

    /**
     * Resolves a date-time property against its own TZID, its Z suffix, or the fallback zone.
     *
     * <p>An all-day entry (VALUE=DATE) becomes the start of that day in the zone. Healthie
     * appointments have times, so that branch is defensive rather than expected.
     */
    static Instant toInstant(Property property, ZoneId zone) {
        String raw = property.value();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        ZoneId effective = zoneOf(property, zone);

        if (text.endsWith("Z")) {
            try {
                return LocalDateTime.parse(text, UTC_STAMP).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // Fall through to the other shapes rather than giving up on the event.
            }
        }
        try {
            return LocalDateTime.parse(text, LOCAL_STAMP).atZone(effective).toInstant();
        } catch (DateTimeParseException ignored) {
            // Not a date-time. Try a bare date.
        }
        try {
            return LocalDate.parse(text, DATE_ONLY).atStartOfDay(effective).toInstant();
        } catch (DateTimeParseException ignored) {
            // Some producers emit ISO-8601 despite the spec. Cheap to accept.
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static ZoneId zoneOf(Property property, ZoneId fallback) {
        String tzid = property.parameters().get("TZID");
        if (tzid == null || tzid.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(tzid.trim());
        } catch (RuntimeException ignored) {
            // An unknown or vendor-specific TZID ("Eastern Standard Time") falls back rather than
            // losing the appointment. Being an hour out is recoverable; vanishing is not.
            return fallback;
        }
    }

    /**
     * iCalendar durations are ISO-8601 with one wrinkle: weeks ({@code P2W}) are legal here and
     * rejected by {@link Duration#parse}. Converted rather than refused.
     */
    static Duration toDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().toUpperCase(Locale.ROOT);
        try {
            if (text.matches("[+-]?P\\d+W")) {
                long weeks = Long.parseLong(text.replaceAll("[^0-9]", ""));
                Duration weekly = Duration.ofDays(weeks * 7);
                return text.startsWith("-") ? weekly.negated() : weekly;
            }
            return Duration.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Joins continuation lines back onto their property.
     *
     * <p>iCalendar wraps any line past 75 octets by inserting CRLF and a single space or tab. A
     * parser that reads line by line without undoing this sees half a UID and then a fragment
     * starting with a space, and the damage is silent - which is why this is the first thing that
     * happens.
     */
    private static List<String> unfold(String ics) {
        List<String> out = new ArrayList<>();
        // Lone CR, CRLF and LF all appear in the wild; normalising first keeps the fold check to
        // one case.
        for (String raw : ics.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (raw.isEmpty()) {
                continue;
            }
            char first = raw.charAt(0);
            if ((first == ' ' || first == '\t') && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + raw.substring(1));
            } else {
                out.add(raw);
            }
        }
        return out;
    }

    /** {@code DTSTART;TZID=America/New_York:20261015T143000} split into its three parts. */
    static Property parseLine(String line) {
        int colon = unquotedColon(line);
        if (colon < 0) {
            return null;
        }
        String head = line.substring(0, colon);
        String value = unescape(line.substring(colon + 1));

        String[] parts = head.split(";");
        String name = parts[0].trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i += 1) {
            int equals = parts[i].indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = parts[i].substring(0, equals).trim().toUpperCase(Locale.ROOT);
            String parameterValue = parts[i].substring(equals + 1).trim();
            if (parameterValue.length() >= 2
                    && parameterValue.startsWith("\"")
                    && parameterValue.endsWith("\"")) {
                parameterValue = parameterValue.substring(1, parameterValue.length() - 1);
            }
            parameters.put(key, parameterValue);
        }
        return new Property(name, parameters, value);
    }

    /**
     * The first colon that actually separates name from value.
     *
     * <p>A quoted parameter may contain one - {@code DTSTART;TZID="GMT+01:00":20261015T143000} - so
     * splitting on the first colon found would cut the line in the wrong place and lose the
     * timestamp.
     */
    private static int unquotedColon(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i += 1) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ':' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    /** iCalendar escapes commas, semicolons, backslashes and newlines inside TEXT values. */
    static String unescape(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i += 1) {
            char c = value.charAt(i);
            if (c != '\\' || i == value.length() - 1) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n', 'N' -> out.append('\n');
                case ',' -> out.append(',');
                case ';' -> out.append(';');
                case '\\' -> out.append('\\');
                // An unknown escape keeps both characters: inventing a meaning for it would be
                // worse than showing it.
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    /** One iCalendar content line. */
    record Property(String name, Map<String, String> parameters, String value) {}
}
