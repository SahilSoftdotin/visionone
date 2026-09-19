package com.visiondigitallab.visionone.eventing.api;

/**
 * The seven Phase-1 events.
 *
 * <p>Each one decouples a side effect from a write path. Ordinary CRUD stays synchronous; a new
 * constant here needs a reason beyond architectural symmetry.
 */
public enum EventType {
    LEAD_CREATED("LeadCreated", Topics.LEAD),
    LEAD_STATUS_CHANGED("LeadStatusChanged", Topics.LEAD),
    CALL_COMPLETED("CallCompleted", Topics.FRONTDESK),
    APPOINTMENT_BOOKED("AppointmentBooked", Topics.APPOINTMENT),
    BUDGET_UPDATED("BudgetUpdated", Topics.GROWTH),
    CONTENT_APPROVED("ContentApproved", Topics.CONTENT),
    WORK_COMPLETED("WorkCompleted", Topics.WORK);

    private final String wireName;
    private final String topic;

    EventType(String wireName, String topic) {
        this.wireName = wireName;
        this.topic = topic;
    }

    public String wireName() {
        return wireName;
    }

    public String topic() {
        return topic;
    }

    /** Topic per aggregate, not per event type. */
    public static final class Topics {
        public static final String LEAD = "visionone.lead.v1";
        public static final String FRONTDESK = "visionone.frontdesk.v1";
        public static final String APPOINTMENT = "visionone.appointment.v1";
        public static final String GROWTH = "visionone.growth.v1";
        public static final String CONTENT = "visionone.content.v1";
        public static final String WORK = "visionone.work.v1";

        private Topics() {}
    }
}
