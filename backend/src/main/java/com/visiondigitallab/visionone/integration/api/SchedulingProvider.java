package com.visiondigitallab.visionone.integration.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The port a scheduling system is reached through. Healthie is the first intended adapter.
 *
 * <p><b>This interface is the PHI boundary.</b> An adapter talks to a system full of protected
 * health information; what it is permitted to hand back is {@link AppointmentReference}, and
 * nothing else. There is deliberately no method here that could return a diagnosis, a note, a lab
 * result, a medication or a reason for visit, because a method that cannot be called cannot leak.
 * A future adapter that needs more must widen this interface in review, not quietly return more.
 *
 * <p>Phase 1 is served by the demo adapter. A Healthie implementation belongs in
 * {@code integration.internal} as {@code HealthieSchedulingProvider}, selected by the
 * {@code visionone.providers.scheduling} property, and must:
 *
 * <ul>
 *   <li>map Healthie's appointment id to {@code externalRef} and never store the patient record id
 *   <li>reduce the patient to {@code displayLabel} - initials or a first name, enough for a front
 *       desk to recognise the booking and not enough to identify someone from the screen alone
 *   <li>map the appointment type onto the practice's own broad service categories, never carry
 *       Healthie's clinical taxonomy through
 *   <li>report {@link ProviderStatus} honestly, including {@code ERROR}, rather than returning an
 *       empty list that reads on screen as "a quiet week"
 * </ul>
 *
 * <p>Implementations are called from a scheduled sync, never from a request thread: a practice
 * management system being slow must not make VisionOne slow.
 */
public interface SchedulingProvider {

    /** Which provider this is, for display and for {@code integration_connection.provider_code}. */
    String providerCode();

    /** Live connection state. Never inferred from whether the last call returned rows. */
    ProviderStatus status(UUID organizationId);

    /**
     * Whether this provider is the authority on whether an appointment was attended.
     *
     * <p>A second deliberate widening, for a problem that only appears with a second adapter. The
     * sync mirrors provider state over VisionOne's row on every pass, which is correct when the
     * provider knows the outcome: Healthie's GraphQL API reports {@code pm_status}, so if a booking
     * is corrected from attended back to pending there, that correction should land here too.
     *
     * <p>The .ics export cannot express attendance at all - iCalendar has CONFIRMED, TENTATIVE and
     * CANCELLED and nothing else. A provider like that reports every past appointment as BOOKED
     * forever, so mirroring it would erase an ATTENDED or NO_SHOW that Vision recorded by hand,
     * every hour, with no trace of what happened. The alternative to this flag was never writing
     * attendance from a sync at all, which would have broken the richer adapter to accommodate the
     * poorer one.
     *
     * <p>Defaults to true so an adapter that does know says nothing, and only one that does not has
     * to declare it. {@code AppointmentSyncService} is what honours it.
     */
    default boolean reportsAttendance() {
        return true;
    }

    /**
     * Whether an appointment missing from a sync should be treated as cancelled.
     *
     * <p>A third widening, and the counterpart to the one above. Some feeds are a complete
     * statement of the calendar: the .ics export lists every appointment the practice has, carries
     * no status field whatsoever, and simply stops listing one that is cancelled. For a feed like
     * that, disappearance is the <em>only</em> signal a cancellation ever produces, and ignoring it
     * leaves cancelled appointments reading as booked indefinitely.
     *
     * <p>Defaults to false, because for most providers absence means something far less certain -
     * a paging bug, a filter, a partial response - and acting on it would cancel a practice's
     * calendar over a transient fault. The Healthie GraphQL adapter leaves it false and relies on
     * webhooks, which say explicitly what happened.
     *
     * <p>An adapter returning true is promising something specific: that a response it accepted is
     * a complete one. {@code HealthieIcsSchedulingProvider} earns that by refusing a body that does
     * not end where a calendar should.
     */
    default boolean reconcilesByAbsence() {
        return false;
    }

    /**
     * Appointments in a closed date range, in the organization's own timezone.
     *
     * @throws SchedulingUnavailableException when the upstream cannot be reached, so the caller can
     *     distinguish "no appointments" from "we do not know"
     */
    List<AppointmentReference> appointments(UUID organizationId, LocalDate from, LocalDate to);

    /**
     * One appointment by the provider's own id, or null if the provider no longer has it.
     *
     * <p>This is a deliberate widening of the port, reviewed rather than slipped in. It is needed
     * because a webhook is a notification, not data: Healthie sends
     * {@code {resource_id, resource_id_type, event_type}} and nothing else, so the only way to learn
     * what actually changed is to ask. Returning the same {@link AppointmentReference} as the range
     * query keeps the PHI boundary exactly where it was - this adds a way to ask about one booking,
     * not a way to learn more about it.
     *
     * <p>Null means the provider says it is gone, which a caller should treat as a cancellation it
     * can no longer read, not as an error.
     *
     * @throws SchedulingUnavailableException when the upstream cannot be reached, so "deleted" and
     *     "we could not ask" stay distinguishable
     */
    AppointmentReference appointment(UUID organizationId, String externalRef);

    /**
     * What VisionOne is allowed to know about an appointment.
     *
     * @param externalRef the provider's own id, so a sync is idempotent and a later change can be
     *     matched to the row it belongs to
     * @param startsAt when it begins
     * @param durationMinutes how long it is booked for
     * @param displayLabel a recognisable but non-identifying label, never a full medical record
     *     name
     * @param serviceCategory one of the practice's broad categories, never a diagnosis
     * @param channelCode the marketing channel credited with the booking, or null when unknown -
     *     null is honest, "Direct" is a guess
     * @param status where the appointment stands
     */
    record AppointmentReference(
            String externalRef,
            Instant startsAt,
            int durationMinutes,
            String displayLabel,
            String serviceCategory,
            String channelCode,
            AppointmentState status) {}

    /** The lifecycle VisionOne reports. Anything richer belongs to the practice's own system. */
    enum AppointmentState {
        BOOKED,
        ATTENDED,
        CANCELLED,
        RESCHEDULED,
        NO_SHOW
    }

    /** Thrown when the upstream is unreachable, so absence is never mistaken for emptiness. */
    class SchedulingUnavailableException extends RuntimeException {
        public SchedulingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
