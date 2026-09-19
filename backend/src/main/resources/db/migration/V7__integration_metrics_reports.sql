-- Integration status is shown verbatim in the UI. VisionOne never fabricates a connection.
CREATE TABLE integration_connection (
    id               uuid PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization(id),
    provider_code    varchar(40) NOT NULL,
    provider_kind    varchar(32) NOT NULL,
    status           varchar(32) NOT NULL,
    last_checked_at  timestamptz,
    detail           varchar(500),
    CONSTRAINT integration_unique  UNIQUE (organization_id, provider_code),
    CONSTRAINT integration_kind_ck CHECK (provider_kind IN ('SCHEDULING','VOICE','ADVERTISING','ANALYTICS')),
    CONSTRAINT integration_status_ck CHECK (status IN
        ('DEMO','NOT_CONNECTED','NEEDS_AUTHORIZATION','CONNECTED','ERROR'))
);

-- Derived, never a source of truth. Recomputed for a period, not incremented in place,
-- so a late or out-of-order event converges instead of drifting.
CREATE TABLE metric_snapshot (
    id               uuid PRIMARY KEY,
    organization_id  uuid          NOT NULL REFERENCES organization(id),
    metric_key       varchar(60)   NOT NULL,
    dimension_key    varchar(60)   NOT NULL DEFAULT '',
    period_start     date          NOT NULL,
    period_end       date          NOT NULL,
    value_numeric    numeric(18,4) NOT NULL,
    computed_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT metric_snapshot_unique UNIQUE (organization_id, metric_key, dimension_key, period_start, period_end)
);

-- payload_json is frozen at generation so a report Gary read last month still says what it said.
CREATE TABLE monthly_report (
    id                 uuid PRIMARY KEY,
    organization_id    uuid        NOT NULL REFERENCES organization(id),
    period_month       date        NOT NULL,
    status             varchar(24) NOT NULL DEFAULT 'DRAFT',
    key_learning       text,
    next_actions       text,
    decisions_required text,
    generated_at       timestamptz,
    payload_json       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT monthly_report_unique    UNIQUE (organization_id, period_month),
    CONSTRAINT monthly_report_month_ck  CHECK (date_trunc('month', period_month) = period_month),
    CONSTRAINT monthly_report_status_ck CHECK (status IN ('DRAFT','GENERATED','SHARED'))
);

INSERT INTO integration_connection (id, organization_id, provider_code, provider_kind, status, detail) VALUES
 ('0199a1d0-0007-7000-8000-000000000001','0199a1d0-0000-7000-8000-000000000001','DEMO_SCHEDULING','SCHEDULING','DEMO','Synthetic appointment data. Healthie planned for Phase 2.'),
 ('0199a1d0-0007-7000-8000-000000000002','0199a1d0-0000-7000-8000-000000000001','DEMO_VOICE','VOICE','DEMO','Synthetic call data. Vonage or Retell planned for Phase 2.'),
 ('0199a1d0-0007-7000-8000-000000000003','0199a1d0-0000-7000-8000-000000000001','DEMO_ADVERTISING','ADVERTISING','DEMO','Spend entered manually by Vision Digital Lab.'),
 ('0199a1d0-0007-7000-8000-000000000004','0199a1d0-0000-7000-8000-000000000001','DEMO_ANALYTICS','ANALYTICS','DEMO','Channel performance derived from VisionOne data.'),
 ('0199a1d0-0007-7000-8000-000000000005','0199a1d0-0000-7000-8000-000000000001','GOOGLE_ADS','ADVERTISING','NOT_CONNECTED','No credentials configured.'),
 ('0199a1d0-0007-7000-8000-000000000006','0199a1d0-0000-7000-8000-000000000001','HEALTHIE','SCHEDULING','NOT_CONNECTED','No credentials configured.');
