-- Make a provider sync idempotent at the database level.
--
-- Both tables have carried an external_ref since V6 - the provider's own id for a booking or a call -
-- and nothing stopped two rows sharing one. That was harmless while the only writer was a seed
-- inserting each row once. It stops being harmless the moment a sync or a webhook writes:
--
--   * Healthie webhooks carry no delivery identifier at all, only {resource_id, resource_id_type,
--     event_type}, so a retry is indistinguishable from a fresh notification. The handler therefore
--     cannot rely on seeing each notification once; it has to be safe to run twice.
--   * The safe shape is an upsert keyed on the provider's id, and an upsert needs something unique
--     to conflict against.
--
-- Partial, because external_ref is null for anything VisionOne created itself rather than mirrored,
-- and null is not a duplicate of null in a unique index - but being explicit is cheaper than relying
-- on that.
--
-- Scoped by organization rather than global: provider ids are only unique within the account they
-- came from, and two practices will have two Healthie accounts.
CREATE UNIQUE INDEX appointment_external_ref_unique
    ON appointment_reference (organization_id, external_ref)
    WHERE external_ref IS NOT NULL;

-- The voice adapter will have exactly the same problem, and the index costs the same today as it
-- will then.
CREATE UNIQUE INDEX call_external_ref_unique
    ON call (organization_id, external_ref)
    WHERE external_ref IS NOT NULL;
