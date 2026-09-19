-- Front Desk is operational, not surveillance: no recordings, no transcripts,
-- no caller identity beyond a synthetic display reference.

CREATE TABLE call (
    id               uuid PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization(id),
    external_ref     varchar(120),
    direction        varchar(16) NOT NULL,
    started_at       timestamptz NOT NULL,
    duration_seconds integer     NOT NULL DEFAULT 0,
    outcome          varchar(32) NOT NULL,
    answered         boolean     NOT NULL,
    after_hours      boolean     NOT NULL DEFAULT false,
    transferred      boolean     NOT NULL DEFAULT false,
    lead_id          uuid        REFERENCES lead(id),
    provider_code    varchar(40) NOT NULL DEFAULT 'DEMO',
    caller_label     varchar(60) NOT NULL,
    CONSTRAINT call_direction_ck CHECK (direction IN ('INBOUND','OUTBOUND')),
    CONSTRAINT call_outcome_ck   CHECK (outcome IN
        ('BOOKED','ENQUIRY_ANSWERED','MESSAGE_TAKEN','TRANSFERRED','MISSED','VOICEMAIL','CANCELLED','RESCHEDULED')),
    CONSTRAINT call_duration_ck  CHECK (duration_seconds >= 0)
);
CREATE INDEX call_org_started_idx ON call (organization_id, started_at DESC);

-- A pointer to an appointment in a scheduling system. Never an appointment reason.
CREATE TABLE appointment_reference (
    id               uuid PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization(id),
    external_ref     varchar(120),
    lead_id          uuid        REFERENCES lead(id),
    call_id          uuid        REFERENCES call(id),
    requested_at     timestamptz NOT NULL,
    scheduled_for    timestamptz,
    status           varchar(24) NOT NULL,
    provider_code    varchar(40) NOT NULL DEFAULT 'DEMO',
    CONSTRAINT appointment_status_ck CHECK (status IN
        ('REQUESTED','BOOKED','ATTENDED','CANCELLED','NO_SHOW','RESCHEDULED'))
);
CREATE INDEX appointment_org_sched_idx ON appointment_reference (organization_id, status, scheduled_for);
CREATE INDEX appointment_lead_idx      ON appointment_reference (lead_id);
