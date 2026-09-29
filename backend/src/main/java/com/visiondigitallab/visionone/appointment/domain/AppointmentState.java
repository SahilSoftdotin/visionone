package com.visiondigitallab.visionone.appointment.domain;

/** The lifecycle VisionOne reports. Anything richer belongs to the practice's own system. */
public enum AppointmentState {
    REQUESTED,
    BOOKED,
    ATTENDED,
    CANCELLED,
    NO_SHOW,
    RESCHEDULED
}
