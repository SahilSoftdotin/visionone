-- Enough state to alert once per incident rather than once per failed sync.
--
-- The failure this exists for is a silence: a regenerated export token stops the calendar
-- updating and nothing on screen changes. V7 gave this table status, last_checked_at and detail,
-- and until recently nothing wrote them at all. Writing them is not enough on its own - a status
-- nobody looks at is only marginally better than no status - so these two columns let the sync
-- decide when a human needs telling.
--
-- Two columns rather than a failure counter, deliberately. A count means something different at
-- every polling interval: three failures is three hours at the hourly default and fifteen minutes
-- at the five-minute one. A timestamp means the same thing at any interval, which matters because
-- the interval is expected to change.

ALTER TABLE integration_connection
    -- When the current run of failures began, or null while healthy. Set on the first failure and
    -- cleared on the first success, so "failing for 40 minutes" is answerable after a restart.
    ADD COLUMN failing_since timestamptz,
    -- When someone was last told about the current incident, or null if nobody has been. Cleared
    -- on recovery, which is also what makes a recovery notice possible: without it there is no way
    -- to know whether anyone was ever worried.
    ADD COLUMN alerted_at    timestamptz,
    -- Fingerprint of what the last successful sync wrote, so an unchanged calendar costs nothing.
    --
    -- A sync re-states the whole window every pass: 1,128 upserts for THRIVE, almost always
    -- writing values identical to the ones already there. Hourly that is tolerable waste. The
    -- five-minute polling the AI front desk will need to avoid double-booking makes it 325,000
    -- writes a day to change nothing.
    --
    -- The fingerprint covers the day as well as the content, so an unchanged feed still forces
    -- one full pass every day. That is what keeps the shortcut honest: it re-establishes the
    -- truth as the sync window slides forward, and it bounds how long any out-of-band change to
    -- the table - a restore, a manual fix - can go uncorrected.
    ADD COLUMN content_digest varchar(64);

COMMENT ON COLUMN integration_connection.failing_since IS
    'Start of the current run of failures; null while healthy.';
COMMENT ON COLUMN integration_connection.alerted_at IS
    'When this incident was last reported to a human; null if unreported or recovered.';
COMMENT ON COLUMN integration_connection.content_digest IS
    'Fingerprint of the last synced window plus its date; equal means nothing to write.';
