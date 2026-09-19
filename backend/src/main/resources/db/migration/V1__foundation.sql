-- Foundation: tenancy, identity, audit and the transactional outbox.

CREATE TABLE organization (
    id              uuid PRIMARY KEY,
    name            varchar(200)  NOT NULL,
    slug            varchar(80)   NOT NULL UNIQUE,
    timezone        varchar(64)   NOT NULL,
    currency        char(3)       NOT NULL,
    status          varchar(24)   NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT organization_status_ck CHECK (status IN ('ACTIVE','SUSPENDED'))
);

CREATE TABLE membership (
    id                 uuid PRIMARY KEY,
    organization_id    uuid         NOT NULL REFERENCES organization(id),
    keycloak_user_id   varchar(64)  NOT NULL,
    role               varchar(32)  NOT NULL,
    display_name       varchar(160) NOT NULL,
    email              varchar(200),
    status             varchar(24)  NOT NULL DEFAULT 'ACTIVE',
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT membership_role_ck   CHECK (role IN ('VISION_ADMIN','CLIENT_OWNER')),
    CONSTRAINT membership_status_ck CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT membership_unique    UNIQUE (organization_id, keycloak_user_id)
);
CREATE INDEX membership_org_idx  ON membership (organization_id, status);
CREATE INDEX membership_user_idx ON membership (keycloak_user_id);

CREATE TABLE audit_event (
    id               uuid PRIMARY KEY,
    organization_id  uuid         NOT NULL REFERENCES organization(id),
    actor            varchar(160) NOT NULL,
    action           varchar(80)  NOT NULL,
    entity_type      varchar(80)  NOT NULL,
    entity_id        uuid,
    occurred_at      timestamptz  NOT NULL DEFAULT now(),
    detail_json      jsonb        NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX audit_event_org_time_idx ON audit_event (organization_id, occurred_at DESC);

-- Outbox: a domain event and the write that caused it commit in one transaction.
CREATE TABLE outbox_event (
    id               uuid PRIMARY KEY,
    organization_id  uuid         NOT NULL REFERENCES organization(id),
    event_type       varchar(80)  NOT NULL,
    event_version    integer      NOT NULL DEFAULT 1,
    aggregate_type   varchar(80)  NOT NULL,
    aggregate_id     uuid         NOT NULL,
    payload_json     jsonb        NOT NULL,
    correlation_id   uuid         NOT NULL,
    occurred_at      timestamptz  NOT NULL DEFAULT now(),
    published_at     timestamptz,
    attempts         integer      NOT NULL DEFAULT 0,
    last_error       text
);
-- Partial index: the relay only ever scans unpublished rows.
CREATE INDEX outbox_unpublished_idx ON outbox_event (occurred_at) WHERE published_at IS NULL;

-- Consumer idempotency: at-least-once delivery made safe.
CREATE TABLE processed_event (
    consumer_group  varchar(120) NOT NULL,
    event_id        uuid         NOT NULL,
    processed_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_group, event_id)
);
