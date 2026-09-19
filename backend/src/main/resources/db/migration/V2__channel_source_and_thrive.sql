-- Channels are rows, not modules. Adding a channel is data, not code.

CREATE TABLE channel_source (
    id               uuid PRIMARY KEY,
    organization_id  uuid         NOT NULL REFERENCES organization(id),
    code             varchar(40)  NOT NULL,
    display_name     varchar(120) NOT NULL,
    category         varchar(40)  NOT NULL,
    sort_order       integer      NOT NULL DEFAULT 0,
    active           boolean      NOT NULL DEFAULT true,
    CONSTRAINT channel_source_unique   UNIQUE (organization_id, code),
    CONSTRAINT channel_source_category_ck CHECK (category IN ('PAID','ORGANIC','DIRECT','OWNED'))
);
CREATE INDEX channel_source_org_idx ON channel_source (organization_id, active);

-- Tenant #1. A second organization is an INSERT, not a code change.
INSERT INTO organization (id, name, slug, timezone, currency)
VALUES ('0199a1d0-0000-7000-8000-000000000001', 'THRIVE Longevity Center', 'thrive',
        'America/New_York', 'USD');

INSERT INTO channel_source (id, organization_id, code, display_name, category, sort_order) VALUES
 ('0199a1d0-0001-7000-8000-000000000001','0199a1d0-0000-7000-8000-000000000001','GOOGLE_ADS',     'Google Ads',        'PAID',    10),
 ('0199a1d0-0001-7000-8000-000000000002','0199a1d0-0000-7000-8000-000000000001','ORGANIC_SEARCH', 'Organic Search',    'ORGANIC', 20),
 ('0199a1d0-0001-7000-8000-000000000003','0199a1d0-0000-7000-8000-000000000001','GOOGLE_MAPS',    'Google Maps',       'ORGANIC', 30),
 ('0199a1d0-0001-7000-8000-000000000004','0199a1d0-0000-7000-8000-000000000001','AI_FRONT_DESK',  'AI Front Desk',     'OWNED',   40),
 ('0199a1d0-0001-7000-8000-000000000005','0199a1d0-0000-7000-8000-000000000001','META_SOCIAL',    'Instagram / Facebook','PAID',  50),
 ('0199a1d0-0001-7000-8000-000000000006','0199a1d0-0000-7000-8000-000000000001','DIRECT_REFERRAL','Direct / Referral', 'DIRECT',  60);
