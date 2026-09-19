-- Growth pipeline. Synthetic names only; no contact details stored in clear.
-- service_interest is a broad category, never a condition. This table is not an intake form.

CREATE TABLE lead (
    id                   uuid PRIMARY KEY,
    organization_id      uuid         NOT NULL REFERENCES organization(id),
    channel_source_id    uuid         NOT NULL REFERENCES channel_source(id),
    campaign_id          uuid         REFERENCES campaign(id),
    display_name         varchar(120) NOT NULL,
    contact_hash         varchar(64),
    service_interest     varchar(60)  NOT NULL,
    status               varchar(32)  NOT NULL DEFAULT 'NEW',
    owner_membership_id  uuid         REFERENCES membership(id),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    first_response_at    timestamptz,
    booked_at            timestamptz,
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT lead_status_ck CHECK (status IN
        ('NEW','CONTACTED','QUALIFIED','APPOINTMENT_REQUESTED','BOOKED','ATTENDED','NOT_CONVERTED','DUPLICATE')),
    CONSTRAINT lead_service_interest_ck CHECK (service_interest IN
        ('LONGEVITY_PROGRAM','HORMONE_OPTIMIZATION','DIAGNOSTICS','WEIGHT_MANAGEMENT','IV_THERAPY','GENERAL_ENQUIRY'))
);
CREATE INDEX lead_org_created_idx ON lead (organization_id, created_at DESC);
CREATE INDEX lead_org_status_idx  ON lead (organization_id, status);
CREATE INDEX lead_org_source_idx  ON lead (organization_id, channel_source_id);

-- Append-only. Drives the funnel and response-time metrics; never updated in place.
CREATE TABLE lead_status_history (
    id               uuid PRIMARY KEY,
    organization_id  uuid         NOT NULL REFERENCES organization(id),
    lead_id          uuid         NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    from_status      varchar(32),
    to_status        varchar(32)  NOT NULL,
    changed_at       timestamptz  NOT NULL DEFAULT now(),
    changed_by       varchar(160) NOT NULL,
    reason           varchar(400)
);
CREATE INDEX lead_status_history_lead_idx ON lead_status_history (lead_id, changed_at);
CREATE INDEX lead_status_history_org_idx  ON lead_status_history (organization_id, to_status, changed_at);
