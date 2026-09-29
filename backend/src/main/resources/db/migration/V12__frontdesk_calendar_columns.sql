-- Columns the provider ports already promise but the tables could not hold.
--
-- VoiceProvider.CallRecord carries handledBy and channelCode; SchedulingProvider's
-- AppointmentReference carries displayLabel, durationMinutes, serviceCategory and channelCode. A
-- sync had nowhere to put any of them, so the Front Desk and Calendar screens had nothing real to
-- read. Adding them now means the Healthie and voice adapters persist without a schema change.

ALTER TABLE call ADD COLUMN handled_by varchar(24);
ALTER TABLE call ADD COLUMN channel_source_id uuid REFERENCES channel_source(id);
ALTER TABLE call ADD CONSTRAINT call_handled_by_ck
    CHECK (handled_by IS NULL OR handled_by IN ('AI_FRONT_DESK', 'PRACTICE_TEAM', 'VOICEMAIL'));

-- display_label is a first name and an initial: enough for a front desk to recognise the booking,
-- not enough to identify someone from the screen alone. service_category is one of the practice's
-- broad commercial categories and never a diagnosis - the PHI boundary the port documents.
ALTER TABLE appointment_reference ADD COLUMN display_label varchar(80);
ALTER TABLE appointment_reference ADD COLUMN duration_minutes integer NOT NULL DEFAULT 45;
ALTER TABLE appointment_reference ADD COLUMN service_category varchar(60);
ALTER TABLE appointment_reference ADD COLUMN channel_source_id uuid REFERENCES channel_source(id);
ALTER TABLE appointment_reference ADD CONSTRAINT appointment_duration_ck
    CHECK (duration_minutes > 0);

-- The Calendar reads one day at a time in the practice's timezone.
CREATE INDEX appointment_org_day_idx ON appointment_reference (organization_id, scheduled_for);
CREATE INDEX call_org_handled_idx ON call (organization_id, handled_by);
