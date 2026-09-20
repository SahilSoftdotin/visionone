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
     * Appointments in a closed date range, in the organization's own timezone.
     *
     * @throws SchedulingUnavailableException when the upstream cannot be reached, so the caller can
     *     distinguish "no appointments" from "we do not know"
     */
    List<AppointmentReference> appointments(UUID organizationId, LocalDate from, LocalDate to);

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
