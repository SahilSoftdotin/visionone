package com.visiondigitallab.visionone.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The iCalendar parser, against hand-written feeds.
 *
 * <p>Hand-written rather than captured, deliberately: a real export is a clinic's appointment book,
 * and there is no version of "a fixture for the test suite" that makes committing one acceptable.
 * Every feed below is invented.
 *
 * <p>What these prove is that the parser handles the shapes iCalendar permits. What they cannot
 * prove is which of those shapes Healthie actually emits - that is unverified until a real export
 * has been read, and the adapter is written to degrade rather than guess precisely because of it.
 */
class IcsCalendarParserTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static String feed(String... eventLines) {
        StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Healthie//EN\r\n");
        out.append("BEGIN:VEVENT\r\n");
        for (String line : eventLines) {
            out.append(line).append("\r\n");
        }
        out.append("END:VEVENT\r\nEND:VCALENDAR\r\n");
        return out.toString();
    }

    @Test
    @DisplayName("reads an absolute UTC appointment")
    void readsUtc() {
        var events = IcsCalendarParser.parse(
                feed(
                        "UID:appt-12345@gethealthie.com",
                        "DTSTART:20261015T143000Z",
                        "DTEND:20261015T151500Z",
                        "SUMMARY:Hormone Therapy with Dana Whitfield",
                        "STATUS:CONFIRMED"),
                NEW_YORK);

        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event.uid()).isEqualTo("appt-12345@gethealthie.com");
        assertThat(event.startsAt()).isEqualTo(Instant.parse("2026-10-15T14:30:00Z"));
        assertThat(event.durationMinutes()).isEqualTo(45);
        assertThat(event.summary()).isEqualTo("Hormone Therapy with Dana Whitfield");
        assertThat(event.status()).isEqualTo("CONFIRMED");
        assertThat(event.recurring()).isFalse();
    }

    @Test
    @DisplayName("resolves a TZID against the real tz database, including the DST offset")
    void resolvesTzid() {
        var events = IcsCalendarParser.parse(
                feed(
                        "UID:a",
                        "DTSTART;TZID=America/New_York:20261015T093000",
                        "DTEND;TZID=America/New_York:20261015T103000"),
                ZoneId.of("UTC"));

        // 15 October is EDT, so 09:30 local is 13:30Z. Getting this wrong by the standard-time
        // offset would put every appointment in the wrong hour for half the year.
        assertThat(events.get(0).startsAt()).isEqualTo(Instant.parse("2026-10-15T13:30:00Z"));
        assertThat(events.get(0).durationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("reads a floating timestamp in the configured zone")
    void readsFloating() {
        var events = IcsCalendarParser.parse(
                feed("UID:a", "DTSTART:20261015T093000", "DTEND:20261015T101500"), NEW_YORK);

        assertThat(events.get(0).startsAt()).isEqualTo(Instant.parse("2026-10-15T13:30:00Z"));
    }

    @Test
    @DisplayName("falls back to the configured zone for a TZID the JVM does not know")
    void unknownTzidFallsBack() {
        // Windows-style zone names appear in exports from some producers. An hour out is
        // recoverable; dropping the appointment is not.
        var events = IcsCalendarParser.parse(
                feed("UID:a", "DTSTART;TZID=Eastern Standard Time:20261015T093000"), NEW_YORK);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).startsAt()).isEqualTo(Instant.parse("2026-10-15T13:30:00Z"));
    }

    @Test
    @DisplayName("derives the end from DURATION when there is no DTEND")
    void usesDuration() {
        var events = IcsCalendarParser.parse(
                feed("UID:a", "DTSTART:20261015T143000Z", "DURATION:PT1H30M"), NEW_YORK);

        assertThat(events.get(0).durationMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("reports an unstated length as null rather than zero")
    void noLengthIsNull() {
        var events = IcsCalendarParser.parse(feed("UID:a", "DTSTART:20261015T143000Z"), NEW_YORK);

        // The adapter substitutes its configured default. A zero here would reach the database as a
        // zero-minute appointment.
        assertThat(events.get(0).durationMinutes()).isNull();
    }

    @Test
    @DisplayName("unfolds a continuation line back onto its property")
    void unfolds() {
        // A UID past 75 octets is wrapped with CRLF and a leading space. Reading line by line
        // without undoing this yields half a UID and a stray fragment - and the damage is silent.
        String ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\n"
                + "UID:appointment-0123456789-0123456789-0123456789-0123456789-01234\r\n 56789@gethealthie.com\r\n"
                + "DTSTART:20261015T143000Z\r\n"
                + "SUMMARY:Consultation with Dana\r\n"
                + "END:VEVENT\r\nEND:VCALENDAR\r\n";

        var events = IcsCalendarParser.parse(ics, NEW_YORK);

        assertThat(events.get(0).uid())
                .isEqualTo("appointment-0123456789-0123456789-0123456789-0123456789-0123456789@gethealthie.com");
    }

    @Test
    @DisplayName("unescapes a SUMMARY containing escaped punctuation")
    void unescapesText() {
        var events = IcsCalendarParser.parse(
                feed("UID:a", "DTSTART:20261015T143000Z", "SUMMARY:Consult\\, follow-up\\; with Dana W"),
                NEW_YORK);

        assertThat(events.get(0).summary()).isEqualTo("Consult, follow-up; with Dana W");
    }

    @Test
    @DisplayName("does not split a line on a colon inside a quoted parameter")
    void quotedParameterColon() {
        var property = IcsCalendarParser.parseLine("DTSTART;TZID=\"GMT+01:00\":20261015T143000");

        assertThat(property).isNotNull();
        assertThat(property.name()).isEqualTo("DTSTART");
        // Splitting on the first colon would have left the value as "00\":20261015T143000".
        assertThat(property.value()).isEqualTo("20261015T143000");
        assertThat(property.parameters()).containsEntry("TZID", "GMT+01:00");
    }

    @Test
    @DisplayName("flags a recurring event instead of silently taking its first occurrence")
    void flagsRecurrence() {
        var events = IcsCalendarParser.parse(
                feed("UID:a", "DTSTART:20261015T143000Z", "RRULE:FREQ=WEEKLY;COUNT=8"), NEW_YORK);

        // The adapter refuses these and logs. Expanding RRULE is the one part of iCalendar worth a
        // library, so the contract is "notice it", not "handle it".
        assertThat(events.get(0).recurring()).isTrue();
    }

    @Test
    @DisplayName("skips an event with no readable start but keeps the rest of the feed")
    void skipsUnplaceableEvent() {
        String ics = "BEGIN:VCALENDAR\r\n"
                + "BEGIN:VEVENT\r\nUID:broken\r\nDTSTART:not-a-date\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nUID:good\r\nDTSTART:20261015T143000Z\r\nEND:VEVENT\r\n"
                + "END:VCALENDAR\r\n";

        var events = IcsCalendarParser.parse(ics, NEW_YORK);

        // One malformed VEVENT must not cost the practice its whole calendar.
        assertThat(events).hasSize(1);
        assertThat(events.get(0).uid()).isEqualTo("good");
    }

    @Test
    @DisplayName("ignores a VTIMEZONE block rather than reading its DTSTART as an appointment")
    void ignoresVtimezone() {
        // VTIMEZONE contains DTSTART lines of its own. A parser that does not scope to VEVENT
        // invents appointments in 1970 out of them.
        String ics = "BEGIN:VCALENDAR\r\n"
                + "BEGIN:VTIMEZONE\r\nTZID:America/New_York\r\n"
                + "BEGIN:DAYLIGHT\r\nDTSTART:19700308T020000\r\nTZOFFSETFROM:-0500\r\n"
                + "TZOFFSETTO:-0400\r\nEND:DAYLIGHT\r\nEND:VTIMEZONE\r\n"
                + "BEGIN:VEVENT\r\nUID:real\r\nDTSTART:20261015T143000Z\r\nEND:VEVENT\r\n"
                + "END:VCALENDAR\r\n";

        var events = IcsCalendarParser.parse(ics, NEW_YORK);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).uid()).isEqualTo("real");
    }

    @Test
    @DisplayName("reads feeds with LF line endings as well as CRLF")
    void toleratesLf() {
        String ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:a\nDTSTART:20261015T143000Z\nEND:VEVENT\nEND:VCALENDAR\n";

        assertThat(IcsCalendarParser.parse(ics, NEW_YORK)).hasSize(1);
    }

    @Test
    @DisplayName("returns nothing for empty or absent input")
    void handlesEmpty() {
        assertThat(IcsCalendarParser.parse(null, NEW_YORK)).isEmpty();
        assertThat(IcsCalendarParser.parse("", NEW_YORK)).isEmpty();
        assertThat(IcsCalendarParser.parse("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n", NEW_YORK)).isEmpty();
    }

    @Test
    @DisplayName("converts a week-long DURATION that Duration.parse rejects")
    void parsesWeekDuration() {
        assertThat(IcsCalendarParser.toDuration("P2W")).isEqualTo(java.time.Duration.ofDays(14));
        assertThat(IcsCalendarParser.toDuration("PT45M")).isEqualTo(java.time.Duration.ofMinutes(45));
        assertThat(IcsCalendarParser.toDuration("nonsense")).isNull();
    }

    @Test
    @DisplayName("takes the first DTSTART when a malformed event repeats it")
    void firstOccurrenceWins() {
        var events = IcsCalendarParser.parse(
                feed("UID:a", "DTSTART:20261015T143000Z", "DTSTART:20991231T000000Z"), NEW_YORK);

        assertThat(events.get(0).startsAt()).isEqualTo(Instant.parse("2026-10-15T14:30:00Z"));
    }

    @Test
    @DisplayName("reads every event in a multi-event feed")
    void readsMultipleEvents() {
        String ics = "BEGIN:VCALENDAR\r\n"
                + "BEGIN:VEVENT\r\nUID:one\r\nDTSTART:20261015T143000Z\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nUID:two\r\nDTSTART:20261016T143000Z\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nUID:three\r\nDTSTART:20261017T143000Z\r\nEND:VEVENT\r\n"
                + "END:VCALENDAR\r\n";

        List<IcsCalendarParser.IcsEvent> events = IcsCalendarParser.parse(ics, NEW_YORK);

        assertThat(events).extracting(IcsCalendarParser.IcsEvent::uid)
                .containsExactly("one", "two", "three");
    }
}
