-- Money is bigint minor units. No float, no BigDecimal columns.

CREATE TABLE growth_plan (
    id                   uuid PRIMARY KEY,
    organization_id      uuid        NOT NULL REFERENCES organization(id),
    period_month         date        NOT NULL,
    planned_total_minor  bigint      NOT NULL DEFAULT 0,
    currency             char(3)     NOT NULL,
    status               varchar(24) NOT NULL DEFAULT 'ACTIVE',
    notes                text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT growth_plan_unique    UNIQUE (organization_id, period_month),
    CONSTRAINT growth_plan_month_ck  CHECK (date_trunc('month', period_month) = period_month),
    CONSTRAINT growth_plan_total_ck  CHECK (planned_total_minor >= 0),
    CONSTRAINT growth_plan_status_ck CHECK (status IN ('DRAFT','ACTIVE','CLOSED'))
);

CREATE TABLE campaign (
    id                 uuid PRIMARY KEY,
    organization_id    uuid         NOT NULL REFERENCES organization(id),
    channel_source_id  uuid         NOT NULL REFERENCES channel_source(id),
    external_ref       varchar(120),
    name               varchar(200) NOT NULL,
    status             varchar(24)  NOT NULL DEFAULT 'ACTIVE',
    started_on         date,
    ended_on           date,
    CONSTRAINT campaign_status_ck CHECK (status IN ('ACTIVE','PAUSED','ENDED'))
);
CREATE INDEX campaign_org_idx ON campaign (organization_id, channel_source_id);

CREATE TABLE budget_allocation (
    id                 uuid PRIMARY KEY,
    organization_id    uuid        NOT NULL REFERENCES organization(id),
    growth_plan_id     uuid        NOT NULL REFERENCES growth_plan(id) ON DELETE CASCADE,
    channel_source_id  uuid        NOT NULL REFERENCES channel_source(id),
    planned_minor      bigint      NOT NULL DEFAULT 0,
    actual_minor       bigint      NOT NULL DEFAULT 0,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT budget_allocation_unique   UNIQUE (growth_plan_id, channel_source_id),
    CONSTRAINT budget_allocation_planned_ck CHECK (planned_minor >= 0),
    CONSTRAINT budget_allocation_actual_ck  CHECK (actual_minor  >= 0)
);
CREATE INDEX budget_allocation_org_idx ON budget_allocation (organization_id, growth_plan_id);
