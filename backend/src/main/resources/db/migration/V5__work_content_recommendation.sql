CREATE TABLE work_item (
    id                    uuid PRIMARY KEY,
    organization_id       uuid         NOT NULL REFERENCES organization(id),
    title                 varchar(240) NOT NULL,
    category              varchar(40)  NOT NULL,
    status                varchar(32)  NOT NULL DEFAULT 'PLANNED',
    business_reason       text         NOT NULL,
    owner_name            varchar(160) NOT NULL,
    target_date           date,
    client_dependency     boolean      NOT NULL DEFAULT false,
    client_visible_update text,
    completed_at          timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT work_item_category_ck CHECK (category IN
        ('SEO','PAID_ACQUISITION','LOCAL_SEARCH','CONTENT','SOCIAL','REPUTATION','ANALYTICS','INTEGRATION','AUTOMATION')),
    CONSTRAINT work_item_status_ck CHECK (status IN
        ('PLANNED','IN_PROGRESS','BLOCKED','WAITING_FOR_CLIENT','COMPLETED','CANCELLED'))
);
CREATE INDEX work_item_org_status_idx ON work_item (organization_id, status);

CREATE TABLE content_item (
    id                 uuid PRIMARY KEY,
    organization_id    uuid         NOT NULL REFERENCES organization(id),
    title              varchar(240) NOT NULL,
    content_type       varchar(40)  NOT NULL,
    status             varchar(32)  NOT NULL DEFAULT 'IDEA',
    channel_source_id  uuid         REFERENCES channel_source(id),
    author_name        varchar(160) NOT NULL,
    draft_url          varchar(500),
    published_url      varchar(500),
    scheduled_for      timestamptz,
    published_at       timestamptz,
    client_feedback    text,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT content_item_type_ck CHECK (content_type IN
        ('BLOG','SOCIAL_POST','SHORT_VIDEO','GOOGLE_BUSINESS_POST','LANDING_PAGE','FAQ')),
    CONSTRAINT content_item_status_ck CHECK (status IN
        ('IDEA','DRAFTING','INTERNAL_REVIEW','CLIENT_REVIEW','CHANGES_REQUESTED','APPROVED','SCHEDULED','PUBLISHED'))
);
CREATE INDEX content_item_org_status_idx ON content_item (organization_id, status);

-- expected_effect is stated as a hypothesis. VisionOne never displays a guaranteed result.
CREATE TABLE recommendation (
    id                 uuid PRIMARY KEY,
    organization_id    uuid        NOT NULL REFERENCES organization(id),
    period_month       date        NOT NULL,
    observation        text        NOT NULL,
    proposed_action    text        NOT NULL,
    rationale          text        NOT NULL,
    expected_effect    text        NOT NULL,
    decision_required  text        NOT NULL,
    status             varchar(24) NOT NULL DEFAULT 'OPEN',
    created_at         timestamptz NOT NULL DEFAULT now(),
    decided_at         timestamptz,
    decision_note      text,
    CONSTRAINT recommendation_month_ck  CHECK (date_trunc('month', period_month) = period_month),
    CONSTRAINT recommendation_status_ck CHECK (status IN ('OPEN','ACCEPTED','DEFERRED','DECLINED'))
);
CREATE INDEX recommendation_org_month_idx ON recommendation (organization_id, period_month DESC);
