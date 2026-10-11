package com.visiondigitallab.visionone.integration.internal;

import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * Reads appointments out of Healthie's calendar export, without the paid API.
 *
 * <p><b>Why this exists alongside {@link HealthieSchedulingProvider}.</b> Healthie's GraphQL API is
 * a priced add-on - quoted at $5,700 for the first year and escalating after. The .ics export is a
 * feature of the plan the practice already pays for, and it answers the one question VisionOne
 * actually asks: when are the appointments. So this adapter is the cheap path, and the GraphQL one
 * stays for when richer data is worth paying for. They are chosen between with
 * {@code visionone.providers.scheduling}, and nothing outside this package knows which is bound.
 *
 * <p><b>What the export cannot do, stated plainly.</b>
 *
 * <ul>
 *   <li><b>No attendance.</b> iCalendar has CONFIRMED, TENTATIVE and CANCELLED and no way to say
 *       "attended" or "no-show". Those are Healthie-internal states this feed simply does not
 *       carry, so VisionOne owns them - which is why {@link #reportsAttendance()} is false and the
 *       sync is told not to flatten a human's entry back to BOOKED on the next pass.
 *   <li><b>No webhooks.</b> There is nothing to subscribe to, so the scheduled sync is the only
 *       path. Polling an export costs nothing per call, so the interval can be dropped well below
 *       the hourly default without a bill attached.
 *   <li><b>No structured patient or appointment-type fields.</b> There is one SUMMARY string, and
 *       its shape is the practice's own wording rather than a schema. See {@link #patientLabel}.
 *   <li><b>It is an undocumented surface.</b> Healthie publishes no contract for the export's
 *       contents and could change them. Everything here degrades to skipping an event rather than
 *       failing a sync, and a sync that produces nothing is reported as an error rather than as an
 *       empty calendar.
 * </ul>
 *
 * <p><b>The URL is a bearer credential.</b> The access token sits in the query string, so anyone
 * holding the URL can read the practice's calendar. It is never logged: {@link #redact} strips the
 * query before any message is written, including exception messages that reach the inbox.
 */
@Component
@ConditionalOnProperty(name = "visionone.providers.scheduling", havingValue = "HEALTHIE_ICS")
public class HealthieIcsSchedulingProvider implements SchedulingProvider {

    private static final Logger log = LoggerFactory.getLogger(HealthieIcsSchedulingProvider.class);

    /**
     * A single clinic's export is tens of kilobytes. The cap is here because the body is read into
     * memory and the size is decided by someone else's server, which is reason enough not to let it
     * be unbounded.
     */
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final HttpClient http;
    private final String icsUrl;
    private final ZoneId zone;
    private final int defaultDurationMinutes;
    private final Duration readTimeout;

    public HealthieIcsSchedulingProvider(
            @Value("${visionone.healthie.ics-url:}") String icsUrl,
            @Value("${visionone.healthie.ics-zone:America/New_York}") String icsZone,
            @Value("${visionone.healthie.default-duration-minutes:45}") int defaultDurationMinutes,
            @Value("${visionone.healthie.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${visionone.healthie.read-timeout-ms:20000}") long readTimeoutMs) {
        this.icsUrl = normalizeUrl(icsUrl);
        this.zone = resolveZone(icsZone);
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                // Follow redirects: an export URL that moves host is Healthie's business, not ours.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Accepts a {@code webcal://} URL as well as an https one.
     *
     * <p>Healthie presents this feed as a calendar subscription link - the thing you paste into
     * Apple Calendar - and subscription links are conventionally handed out with the {@code webcal}
     * scheme. It is not a protocol: it is the same HTTPS URL with a different word in front, and
     * calendar clients rewrite it before fetching. Java's HTTP client does not, so without this the
     * configured URL would be rejected for its scheme and the message would blame the URL rather
     * than name the one-word difference.
     */
    static String normalizeUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.regionMatches(true, 0, "webcal://", 0, "webcal://".length())) {
            return "https://" + trimmed.substring("webcal://".length());
        }
        return trimmed;
    }

    private static ZoneId resolveZone(String configured) {
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException e) {
            log.warn("visionone.healthie.ics-zone '{}' is not a known zone; falling back to UTC", configured);
            return ZoneOffset.UTC;
        }
    }

    /**
     * Distinct from {@code HEALTHIE} on purpose. It is what lands in
     * {@code integration_connection.provider_code} and in {@code appointment_reference}, so the two
     * Healthie routes stay distinguishable in the data - and, because the scheduler only syncs
     * organizations whose connection row matches, switching routes is a deliberate act rather than
     * something a property change does behind the data's back.
     */
    @Override
    public String providerCode() {
        return "HEALTHIE_ICS";
    }

    /**
     * False: see the class comment. The export cannot express attendance, so a sync from here must
     * not be allowed to overwrite an outcome a human recorded in VisionOne.
     */
    @Override
    public boolean reportsAttendance() {
        return false;
    }

    /**
     * True: the export is the practice's whole calendar, so an appointment that is no longer in it
     * is no longer on it.
     *
     * <p>Two things make this necessary rather than merely nice. The feed carries no
     * {@code STATUS} property at all - not one of the 5,256 events has one - so a cancellation can
     * only ever show up as a disappearance. And because identity is derived from start, end and
     * summary, a <em>rescheduled</em> appointment arrives as a new row and leaves the old one
     * behind. Without reconciliation the calendar would accumulate ghosts of every appointment
     * ever moved, each still reading as booked.
     *
     * <p>Safe only because a short read is refused rather than parsed: see the truncation check in
     * {@code fetch()}. Absence has to mean absence, not "the download stopped early".
     */
    @Override
    public boolean reconcilesByAbsence() {
        return true;
    }

    @Override
    public ProviderStatus status(UUID organizationId) {
        if (icsUrl.isEmpty()) {
            return ProviderStatus.NOT_CONNECTED;
        }
        try {
            fetch();
            return ProviderStatus.CONNECTED;
        } catch (SchedulingUnavailableException e) {
            return ProviderStatus.ERROR;
        }
    }

    @Override
    public List<AppointmentReference> appointments(UUID organizationId, LocalDate from, LocalDate to) {
        if (icsUrl.isEmpty()) {
            throw new SchedulingUnavailableException("No Healthie ICS URL is configured", null);
        }

        // The export carries the whole calendar - it takes no date parameters - so the window the
        // port promises is applied here, exactly as the GraphQL adapter has to do for its own
        // reason.
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<IcsCalendarParser.IcsEvent> events = IcsCalendarParser.parse(fetch(), zone);
        if (events.isEmpty()) {
            // An export with no VEVENT at all is far more likely to be the wrong token - a feed
            // scoped to a user with no calendar of their own - than a clinic with no appointments
            // in a five-month window. Saying so beats rendering an empty calendar as fact.
            throw new SchedulingUnavailableException(
                    "Healthie ICS export at " + redact(icsUrl) + " contained no events", null);
        }

        List<AppointmentReference> out = new ArrayList<>();
        int skippedRecurring = 0;
        int skippedUnusable = 0;
        // Counted across the whole feed rather than the window, so an appointment's identity does
        // not change when the window moves past one of its twins.
        java.util.Map<String, Integer> occurrences = new java.util.HashMap<>();

        for (IcsCalendarParser.IcsEvent event : events) {
            if (event.recurring()) {
                skippedRecurring += 1;
                continue;
            }
            if (event.summary() == null || event.summary().isBlank()) {
                // The summary is two thirds of the identity below. Without it, two appointments in
                // the same slot would key identically and collapse onto one row.
                skippedUnusable += 1;
                continue;
            }
            int occurrence = occurrences.merge(
                    occurrenceKey(event), 1, Integer::sum) - 1;
            if (event.startsAt().isBefore(fromInstant) || !event.startsAt().isBefore(toInstant)) {
                continue;
            }
            Integer minutes = event.durationMinutes();
            out.add(new AppointmentReference(
                    contentRef(event.startsAt(), event.endsAt(), event.summary(), occurrence),
                    event.startsAt(),
                    minutes == null ? defaultDurationMinutes : minutes,
                    patientLabel(event.summary()),
                    // Same keyword mapping as the GraphQL adapter, reused rather than restated so
                    // the two routes cannot drift into categorising the same practice differently.
                    HealthieSchedulingProvider.serviceCategory(event.summary()),
                    // Healthie neither knows nor cares which advertisement produced a patient. Null
                    // is honest; Vision credits the channel by hand on the Calendar.
                    null,
                    state(event.status())));
        }

        if (skippedRecurring > 0) {
            // Loud on purpose. This is the assumption in IcsCalendarParser that would otherwise
            // fail silently, and it is cheaper to learn about it from a log than from a client.
            log.warn(
                    "Healthie ICS export carried {} recurring event(s), which this adapter does not "
                            + "expand; they are missing from the calendar for organization {}. "
                            + "Recurrence support needs a real iCalendar library.",
                    skippedRecurring,
                    organizationId);
        }
        if (skippedUnusable > 0) {
            log.warn("Skipped {} Healthie ICS event(s) with no summary to identify them by", skippedUnusable);
        }
        return out;
    }

    /**
     * The export is the whole calendar in one document, so there is no cheaper way to answer this
     * than to read it and pick one event out.
     *
     * <p>Nothing calls it on the ICS route today - webhooks are what need it, and this route has
     * none - but the port requires it and a wrong answer is worse than a slow one.
     */
    @Override
    public AppointmentReference appointment(UUID organizationId, String externalRef) {
        if (externalRef == null || externalRef.isBlank()) {
            return null;
        }
        java.util.Map<String, Integer> occurrences = new java.util.HashMap<>();
        for (IcsCalendarParser.IcsEvent event : IcsCalendarParser.parse(fetch(), zone)) {
            if (event.summary() == null || event.summary().isBlank()) {
                continue;
            }
            int occurrence = occurrences.merge(
                    occurrenceKey(event), 1, Integer::sum) - 1;
            String ref = contentRef(event.startsAt(), event.endsAt(), event.summary(), occurrence);
            if (externalRef.equals(ref)) {
                Integer minutes = event.durationMinutes();
                return new AppointmentReference(
                        ref,
                        event.startsAt(),
                        minutes == null ? defaultDurationMinutes : minutes,
                        patientLabel(event.summary()),
                        HealthieSchedulingProvider.serviceCategory(event.summary()),
                        null,
                        state(event.status()));
            }
        }
        return null;
    }

    /** @throws SchedulingUnavailableException so an outage never reads as an empty calendar */
    private String fetch() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(icsUrl))
                    .timeout(readTimeout)
                    .header("Accept", "text/calendar")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new SchedulingUnavailableException("Healthie ICS URL is not a valid URL", e);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SchedulingUnavailableException(
                    "Healthie ICS export at " + redact(icsUrl) + " is unreachable", e);
        } catch (InterruptedException e) {
            // Restore the flag rather than swallowing it: this runs on a scheduler thread that a
            // shutdown is entitled to interrupt.
            Thread.currentThread().interrupt();
            throw new SchedulingUnavailableException("Interrupted while reading the Healthie ICS export", e);
        }

        if (response.statusCode() / 100 != 2) {
            // A revoked or regenerated token is the likeliest cause of a 401/403, and it is worth
            // naming because the failure mode is a calendar that silently stops updating.
            throw new SchedulingUnavailableException(
                    "Healthie ICS export at " + redact(icsUrl) + " returned " + response.statusCode()
                            + (response.statusCode() == 401 || response.statusCode() == 403
                                    ? " - the access token may have been regenerated"
                                    : ""),
                    null);
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new SchedulingUnavailableException("Healthie ICS export returned an empty body", null);
        }
        if (body.length() > MAX_BYTES) {
            throw new SchedulingUnavailableException(
                    "Healthie ICS export was larger than the " + MAX_BYTES + " byte cap", null);
        }
        if (!body.contains("BEGIN:VCALENDAR")) {
            // A sign-in page answered with 200 rather than a redirect is the classic shape of an
            // expired token, and it would otherwise parse to zero events and look like a quiet
            // month. Checked before the terminator so the message names the real cause.
            throw new SchedulingUnavailableException(
                    "Healthie ICS export at " + redact(icsUrl) + " did not return a calendar", null);
        }
        if (!body.stripTrailing().endsWith("END:VCALENDAR")) {
            // A response cut off mid-document still parses: iCalendar is a flat sequence, so a
            // truncated feed yields a shorter, entirely valid-looking list of events. That matters
            // far more than usual here, because reconcileByAbsence treats anything missing from a
            // sync as cancelled - a half-delivered feed would cancel the other half of the
            // practice's calendar. Checking the terminator makes truncation loud instead.
            throw new SchedulingUnavailableException(
                    "Healthie ICS export at " + redact(icsUrl) + " ended mid-document", null);
        }
        return body;
    }

    /**
     * Reduces one SUMMARY string to a label a front desk can recognise and a stranger cannot
     * identify someone from.
     *
     * <p><b>The shape of SUMMARY is the least certain thing in this adapter.</b> Healthie publishes
     * no contract for it, and observed exports use wordings like "Initial Consultation with Dana
     * Whitfield" or "Dana Whitfield - Hormone Therapy". So this takes the part after " with " when
     * there is one, the part before " - " otherwise, and reduces whatever is left to a first name
     * and a last initial. When the guess is wrong the cost is a label that reads oddly, never a
     * missing appointment and never a fuller name than intended - which is the right way round for
     * something unverified. Worth checking against a real export and tightening once the actual
     * wording is known.
     */
    static String patientLabel(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String text = summary.trim();

        int with = text.toLowerCase(Locale.ROOT).indexOf(" with ");
        if (with >= 0) {
            text = text.substring(with + " with ".length()).trim();
        } else {
            for (String separator : new String[] {" - ", " – ", " | ", ": "}) {
                int at = text.indexOf(separator);
                if (at > 0) {
                    text = text.substring(0, at).trim();
                    break;
                }
            }
        }
        // Reused rather than restated, for the same reason as serviceCategory above.
        return HealthieSchedulingProvider.displayLabel(text);
    }

    /**
     * iCalendar's three states onto the port's five. ATTENDED and NO_SHOW are unreachable from here
     * by design; see {@link #reportsAttendance()}.
     */
    static AppointmentState state(String icsStatus) {
        if (icsStatus == null || icsStatus.isBlank()) {
            // iCalendar makes STATUS optional, and an appointment on the calendar without one is a
            // booking.
            return AppointmentState.BOOKED;
        }
        return switch (icsStatus.trim().toUpperCase(Locale.ROOT)) {
            case "CANCELLED", "CANCELED" -> AppointmentState.CANCELLED;
            // TENTATIVE is still a booking as far as a marketing dashboard is concerned.
            default -> AppointmentState.BOOKED;
        };
    }

    /**
     * A stable identity for an appointment, derived from its content rather than its UID.
     *
     * <p><b>Healthie's export does not carry a usable UID.</b> It is generated by the Ruby
     * {@code icalendar} gem, which mints a fresh random UUID for any event that does not already
     * have one - and Healthie does not set one. Two fetches of the same calendar, seconds apart,
     * return the same 5,256 appointments under 5,256 completely different UIDs. Measured, not
     * assumed: the overlap between consecutive fetches is exactly zero.
     *
     * <p>That makes the UID worse than useless as an idempotency key. Keyed on it, every sync
     * inserts the entire calendar again - a local run reached 10,152 rows for 1,128 real
     * appointments in nine passes before it was stopped. {@code DTSTAMP} is no better; it is the
     * moment the document was rendered, identical across every event and different on every fetch.
     *
     * <p>So the key is a digest of the three fields that do hold still: start, end and summary.
     * Properties worth stating plainly:
     *
     * <ul>
     *   <li>An unchanged appointment keys the same on every fetch, which is what the upsert needs.
     *   <li>A <b>rescheduled</b> appointment produces a new key and orphans the old row. That is
     *       why {@link #reconcilesByAbsence()} is true - without it, moving an appointment would
     *       leave the original sitting on the calendar forever.
     *   <li>A collision needs two appointments with the same patient name starting and ending at
     *       the same minute, for a single provider. That is a double-booking of one person, and
     *       collapsing it to one row is the right answer anyway.
     * </ul>
     *
     * <p>The digest covers a patient name, so it is worth being explicit: it is one-way, it is
     * never shown, and it reveals nothing the row does not already hold in {@code display_label}
     * and {@code scheduled_for}. It is an identifier, not a record.
     */
    /**
     * The grouping an occurrence index counts within: the same three fields the digest uses.
     *
     * <p>Keyed on the full triple rather than just start and summary, so two genuinely different
     * appointments do not renumber each other. Only true twins - identical start, end and name -
     * share a counter, which is the smallest group that can churn when one of them disappears.
     */
    private static String occurrenceKey(IcsCalendarParser.IcsEvent event) {
        return event.startsAt().getEpochSecond()
                + "|" + (event.endsAt() == null ? "" : Long.toString(event.endsAt().getEpochSecond()))
                + "|" + event.summary();
    }

    static String contentRef(Instant startsAt, Instant endsAt, String summary) {
        return contentRef(startsAt, endsAt, summary, 0);
    }

    /**
     * @param occurrence which of several identical events this is, counted in feed order.
     *     <p>The export really does repeat a triple: 1,052 of them appear twice and some up to
     *     eight times, and no other field tells them apart, so without this they would collapse
     *     onto one row and the practice would lose a third of its calendar. The feed's event order
     *     is identical between fetches - measured, by comparing two downloads byte for byte - so
     *     the position of a repeat within its own group is something that can be keyed on.
     *     <p>It is the weakest link here, and worth being honest about: if one of three identical
     *     appointments is cancelled, the remaining two renumber, their keys change, and the sync
     *     sees two new appointments and two vanished ones. Reconciliation cancels the stale pair,
     *     so it converges on the right answer within a pass rather than drifting - but it churns
     *     where a real identifier would not. The real fix is Healthie emitting a stable UID.
     */
    static String contentRef(Instant startsAt, Instant endsAt, String summary, int occurrence) {
        String basis = startsAt.getEpochSecond()
                + "|" + (endsAt == null ? "" : Long.toString(endsAt.getEpochSecond()))
                + "|" + (summary == null ? "" : summary.trim())
                + (occurrence == 0 ? "" : "|" + occurrence);
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(basis.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("ics-");
            // 20 bytes of the digest. 160 bits makes an accidental collision impossible at this
            // scale, and the whole thing is 44 characters against a varchar(120) column.
            for (int i = 0; i < 20; i += 1) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK, so this is unreachable rather than handled.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /**
     * Drops the query string, which is where the access token lives.
     *
     * <p>Every message that names the URL goes through this. A log line is copied into tickets and
     * screenshots, and a token in one is a token that has to be rotated.
     */
    static String redact(String url) {
        if (url == null) {
            return "(none)";
        }
        int query = url.indexOf('?');
        return query < 0 ? url : url.substring(0, query) + "?...";
    }
}
