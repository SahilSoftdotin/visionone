-- A human-readable lead reference.
--
-- Vision and the client need to name a specific lead on a phone call without reading out a
-- UUID. The sequence is global rather than per-organization: it avoids a read-modify-write race
-- on insert, and a reference is an opaque handle, not a per-client counter anyone reasons about.

CREATE SEQUENCE lead_reference_seq START WITH 1000;

ALTER TABLE lead ADD COLUMN reference varchar(20);

-- Backfill any existing leads in creation order, so their references read chronologically.
-- A no-op on a fresh database, where the column default below does the work instead.
WITH ordered AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS n FROM lead
)
UPDATE lead l
SET reference = 'L-' || (999 + ordered.n)::text
FROM ordered
WHERE l.id = ordered.id;

-- Leave the sequence past everything the backfill used.
SELECT setval(
    'lead_reference_seq',
    coalesce((SELECT max(substring(reference from 3)::bigint) FROM lead), 999) + 1,
    false);

-- The default is what assigns references from here on: the seed, the API and any other insert
-- all get one without naming it, which is why the JPA mapping treats the column as read-only.
ALTER TABLE lead ALTER COLUMN reference SET DEFAULT ('L-' || nextval('lead_reference_seq'));

ALTER TABLE lead ALTER COLUMN reference SET NOT NULL;
ALTER TABLE lead ADD CONSTRAINT lead_reference_unique UNIQUE (reference);
