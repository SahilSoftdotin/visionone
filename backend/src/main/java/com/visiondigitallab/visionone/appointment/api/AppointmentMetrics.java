package com.visiondigitallab.visionone.appointment.api;

import java.time.Instant;
import java.util.UUID;

public interface AppointmentMetrics {

    /** Distinct booked appointments in the window, deduplicated to one per lead. */
    long bookedCount(UUID organizationId, Instant from, Instant to);
}
