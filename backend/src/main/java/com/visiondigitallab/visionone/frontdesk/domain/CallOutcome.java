package com.visiondigitallab.visionone.frontdesk.domain;

/**
 * What a call produced.
 *
 * <p>Richer than {@code VoiceProvider.CallOutcome} on purpose: the port speaks the vocabulary every
 * telephony vendor shares, while this is what the practice's own reporting distinguishes. An
 * adapter maps into this, never the reverse.
 */
public enum CallOutcome {
    BOOKED,
    ENQUIRY_ANSWERED,
    MESSAGE_TAKEN,
    TRANSFERRED,
    MISSED,
    VOICEMAIL,
    CANCELLED,
    RESCHEDULED
}
