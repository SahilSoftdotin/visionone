-- The inbox: webhooks arriving from Healthie and, later, the phone provider.
--
-- The mirror of outbox_event. The endpoint verifies the signature, writes one row and returns 200
-- in milliseconds; a poller does the real work afterwards. Healthie retries a slow or failed
-- delivery for three days and then disables the webhook, so the request path must not fetch,
-- parse or join anything.
--
-- No PHI lands here: a Healthie webhook body carries a resource id, an event type and a list of
-- changed fields, never patient data.

CREATE TABLE inbox_event (
    id                  uuid PRIMARY KEY,
    source              varchar(40)  NOT NULL,
    external_event_id   varchar(200) NOT NULL,
    event_type          varchar(80)  NOT NULL,
    resource_id         varchar(120),
    resource_type       varchar(80),
    organization_id     uuid         REFERENCES organization(id),
    payload_json        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    signature_verified  boolean      NOT NULL DEFAULT false,
    received_at         timestamptz  NOT NULL DEFAULT now(),
    processed_at        timestamptz,
    attempts            integer      NOT NULL DEFAULT 0,
    last_error          text,
    CONSTRAINT inbox_event_source_ck CHECK (source IN ('HEALTHIE', 'VOICE', 'ADVERTISING', 'ANALYTICS')),
    -- A redelivery of the same webhook is rejected here. This is the whole idempotency story for
    -- incoming events, and it is why the endpoint is safe to retry.
    CONSTRAINT inbox_event_unique UNIQUE (source, external_event_id)
);

-- The poller only ever scans unprocessed rows.
CREATE INDEX inbox_event_unprocessed_idx ON inbox_event (received_at) WHERE processed_at IS NULL;
CREATE INDEX inbox_event_org_idx ON inbox_event (organization_id, received_at DESC);
