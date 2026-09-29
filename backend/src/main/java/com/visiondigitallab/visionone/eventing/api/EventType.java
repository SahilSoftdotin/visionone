package com.visiondigitallab.visionone.eventing.api;

/**
 * The seven Phase-1 events.
 *
 * <p>Each one decouples a side effect from a write path. Ordinary CRUD stays synchronous; a new
 * constant here needs a reason beyond architectural symmetry.
 *
 * <p>There is no topic or stream name here any more. Which aggregate an event belongs to is
 * already recorded as {@code outbox_event.aggregate_type}, so a second place to say it would only
 * be a second place to get it wrong. If a broker is introduced later, it derives its topics from
 * that column.
 */
public enum EventType {
    LEAD_CREATED("LeadCreated"),
    LEAD_STATUS_CHANGED("LeadStatusChanged"),
    CALL_COMPLETED("CallCompleted"),
    APPOINTMENT_BOOKED("AppointmentBooked"),
    BUDGET_UPDATED("BudgetUpdated"),
    CONTENT_APPROVED("ContentApproved"),
    WORK_COMPLETED("WorkCompleted");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    /** The name that appears in the envelope and in stored rows. Never renamed. */
    public String wireName() {
        return wireName;
    }
}
