package com.visiondigitallab.visionone.integration.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The port a telephony system is reached through. Retell and Vonage are the intended adapters.
 *
 * <p><b>This interface is a privacy boundary.</b> A voice platform holds recordings and transcripts
 * of conversations with patients. What an adapter may hand back is {@link CallRecord}, and there is
 * deliberately no method returning audio, a transcript, or any body of what was said. VisionOne
 * answers how the phone performed, not what was discussed on it.
 *
 * <p>Caller numbers arrive already masked. Masking belongs in the adapter, not in the browser and
 * not in a template: a full number that reaches the database has already escaped.
 *
 * <p>A live adapter belongs in {@code integration.internal}, selected by
 * {@code visionone.providers.voice}, and must report {@link ProviderStatus#ERROR} rather than an
 * empty list. No calls and no connection look identical on a dashboard and mean opposite things.
 */
public interface VoiceProvider {

    /** Which provider this is, for display and for {@code integration_connection.provider_code}. */
    String providerCode();

    /** Live connection state. Never inferred from whether the last call returned rows. */
    ProviderStatus status(UUID organizationId);

    /**
     * Calls in a closed date range.
     *
     * @throws VoiceUnavailableException when the upstream cannot be reached, so the caller can
     *     distinguish "a quiet week" from "we do not know"
     */
    List<CallRecord> calls(UUID organizationId, LocalDate from, LocalDate to);

    /**
     * What VisionOne is allowed to know about a call.
     *
     * @param externalRef the provider's own id, so a sync is idempotent
     * @param startedAt when the call began
     * @param durationSeconds how long it lasted; zero for a missed call
     * @param maskedNumber masked by the adapter before it leaves this boundary
     * @param handledBy who or what answered
     * @param outcome what the call produced
     * @param afterHours whether it arrived outside published hours, decided against the
     *     organization's own timezone rather than the server's
     * @param channelCode the marketing channel credited with the call, or null when unknown -
     *     null is honest, "Direct" is a guess
     */
    record CallRecord(
            String externalRef,
            Instant startedAt,
            int durationSeconds,
            String maskedNumber,
            Handler handledBy,
            CallOutcome outcome,
            boolean afterHours,
            String channelCode) {}

    enum Handler {
        AI_FRONT_DESK,
        PRACTICE_TEAM,
        VOICEMAIL
    }

    enum CallOutcome {
        BOOKED,
        ENQUIRY,
        TRANSFERRED,
        MISSED,
        CANCELLED,
        RESCHEDULED
    }

    /** Thrown when the upstream is unreachable, so absence is never mistaken for emptiness. */
    class VoiceUnavailableException extends RuntimeException {
        public VoiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
