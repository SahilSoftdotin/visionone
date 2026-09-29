package com.visiondigitallab.visionone.lead.domain;

import java.util.Set;

/**
 * The Phase-1 pipeline.
 *
 * <p>Deliberately not a CRM's stage model: no sequences, no scoring, no automation. The rank is
 * what the funnel counts, and it is why a lead that jumps straight to BOOKED still appears at
 * every earlier stage.
 */
public enum LeadStatus {
    NEW(0),
    CONTACTED(1),
    QUALIFIED(2),
    APPOINTMENT_REQUESTED(3),
    BOOKED(4),
    ATTENDED(5),
    NOT_CONVERTED(1),
    DUPLICATE(0);

    private final int rank;

    LeadStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean isQualifiedOrBeyond() {
        return this == QUALIFIED || this == APPOINTMENT_REQUESTED || this == BOOKED || this == ATTENDED;
    }

    public boolean isBooked() {
        return this == BOOKED || this == ATTENDED;
    }

    /** Terminal states: a lead here is finished, and moving it out needs a deliberate reopen. */
    public static final Set<LeadStatus> TERMINAL = Set.of(NOT_CONVERTED, DUPLICATE);
}
