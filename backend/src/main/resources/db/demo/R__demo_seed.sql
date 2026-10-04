-- THRIVE dataset for the local and demo profiles.
--
-- Deterministic by construction: every value derives from a row index, never from random(). The
-- same seed produces the same dashboard every time, so a number questioned in a demo can be
-- reproduced and explained. Names are drawn from a fixed synthetic list and no contact details are
-- stored. Nothing here is clinical.
--
-- The record starts when the engagement started, not three months earlier. ENGAGEMENT_START below
-- is the one date that matters: the portal is a record of this engagement, so it shows nothing
-- before the engagement existed, and it fills forward a day at a time from there. An earlier
-- version carried three months of invented history that predated the relationship, which is a
-- strange thing to hand a client who knows exactly when they signed.
--
-- Work items are the real ones. They are not synthetic and they are not padding: they are what
-- Vision is actually doing for THRIVE this quarter, which is why there are three of them rather
-- than thirty. Everything after them is authored in the product by Vision Admin, which is what
-- the Work & Content screen is for.
--
-- There are deliberately NO content items. The content pipeline starts empty and fills as posts
-- and articles are actually drafted, each one going to the practice for approval. A pipeline
-- pre-populated with imaginary blog posts invites a client to approve something nobody wrote.
--
-- Repeatable migration: it runs when its checksum changes and is a no-op once data exists.
--
-- Every insert is guarded, either by the IF EXISTS check at the top of its DO block or by an
-- ON CONFLICT clause. recommendation and monthly_report sit outside any DO block, so they had
-- neither: editing this file re-ran them and startup died on recommendation_pkey. A repeatable
-- migration that only works on an empty database is a repeatable migration in name only.

DO $$
DECLARE
    org  uuid := '0199a1d0-0000-7000-8000-000000000001';
    cur  char(3) := 'USD';

    -- ENGAGEMENT_START. Change this one line to move the whole record.
    --
    -- Fixed rather than relative. A rolling window keeps the dashboard populated but it also
    -- silently invents history: in January it would claim leads from the previous November. This
    -- is a client-facing record, so it begins on a real date and grows.
    start_on date := date '2026-10-01';

    -- Four leads, and that is the whole dataset. See the leads section for why there is no
    -- generator here any more.
    lead_count int := 4;

    -- The plan runs to the end of the engagement's first calendar year. Plans are forward-looking,
    -- so unlike leads and calls these legitimately sit in the future.
    m0 date := date_trunc('month', start_on)::date;
    m1 date := (date_trunc('month', start_on) + interval '1 month')::date;
    m2 date := (date_trunc('month', start_on) + interval '2 months')::date;
BEGIN

IF EXISTS (SELECT 1 FROM lead WHERE organization_id = org) THEN
    RAISE NOTICE 'VisionOne demo seed already present; skipping.';
    RETURN;
END IF;

-- ---------------------------------------------------------------- people
INSERT INTO membership (id, organization_id, keycloak_user_id, role, display_name, email) VALUES
 ('0199a1d0-0002-7000-8000-000000000001', org, '11111111-1111-4111-8111-111111111111',
  'VISION_ADMIN', 'Sahil Arora', 'sahil@visiondigitallab.com'),
 ('0199a1d0-0002-7000-8000-000000000002', org, '22222222-2222-4222-8222-222222222222',
  'CLIENT_OWNER', 'Dr. Gary Adams', 'gary@thrivelongevitycenter.com')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- growth plans
INSERT INTO growth_plan (id, organization_id, period_month, planned_total_minor, currency, notes) VALUES
 ('0199a1d0-0003-7000-8000-000000000001', org, m0, 500000, cur,
  'First month of the engagement. Budget weighted to Google Ads and local search while the Longevity Program pages are rebuilt.'),
 ('0199a1d0-0003-7000-8000-000000000002', org, m1, 500000, cur,
  'Planned. Shifts toward local search and content once the first month of search data is in.'),
 ('0199a1d0-0003-7000-8000-000000000003', org, m2, 500000, cur,
  'Planned. Holds budget for the festive period, when enquiry volume in this category falls and cost per click rises.');

-- Planned per channel, per month. The plan is real - it is what the budget is meant to do -
-- and actual stays zero until a connected platform reports a spend.
INSERT INTO budget_allocation (id, organization_id, growth_plan_id, channel_source_id, planned_minor, actual_minor)
SELECT
    ('0199a1d0-0004-7000-8000-' || lpad((p.n * 10 + c.n)::text, 12, '0'))::uuid,
    org,
    p.plan_id,
    c.channel_id,
    c.planned,
    -- Zero, every month, every channel. Not a single invoice exists: the Google Ads account has
    -- no billing method on it yet, so nothing has been spent anywhere. This used to compute a
    -- plausible-looking actual from the fraction of the month elapsed, which put ~$484 of spend
    -- and a cost-per-lead figure in front of a client who could check the ad account and find
    -- nothing there. Spend appears here when the advertising adapter reports it, not before.
    0
FROM (VALUES
        (1, '0199a1d0-0003-7000-8000-000000000001'::uuid, m0),
        (2, '0199a1d0-0003-7000-8000-000000000002'::uuid, m1),
        (3, '0199a1d0-0003-7000-8000-000000000003'::uuid, m2)
     ) AS p(n, plan_id, month)
CROSS JOIN (VALUES
        (1, '0199a1d0-0001-7000-8000-000000000001'::uuid, 220000),  -- Google Ads
        (2, '0199a1d0-0001-7000-8000-000000000002'::uuid,  80000),  -- Organic Search
        (3, '0199a1d0-0001-7000-8000-000000000003'::uuid,  60000),  -- Google Maps
        (4, '0199a1d0-0001-7000-8000-000000000004'::uuid,  45000),  -- AI Front Desk
        (5, '0199a1d0-0001-7000-8000-000000000005'::uuid,  75000),  -- Meta / Instagram
        (6, '0199a1d0-0001-7000-8000-000000000006'::uuid,  20000)   -- Direct / Referral
     ) AS c(n, channel_id, planned);

-- ---------------------------------------------------------------- leads
-- Four, all from Organic Search, all NEW.
--
-- The generator that used to live here produced 180 leads across six channels and eight statuses.
-- Almost all of it was a claim about work nobody has done: a lead marked CONTACTED says someone
-- rang them, BOOKED says an appointment exists, and an owner says a person is accountable for it.
-- On an engagement this young none of that is true, and a client can check.
--
-- Organic Search is the one channel that produces anything before the ad accounts go live, and
-- NEW is the only status that is honest on day one. Vision Admin moves them on from there in the
-- product, as the work actually happens - which is what the Leads screen's admin controls are for.
INSERT INTO lead (id, organization_id, channel_source_id, display_name, contact_hash,
                  service_interest, status, created_at, first_response_at, booked_at,
                  owner_membership_id)
SELECT
    ('0199a1d0-1000-7000-8000-' || lpad(i::text, 12, '0'))::uuid,
    org,
    '0199a1d0-0001-7000-8000-000000000002'::uuid,   -- Organic Search
    (ARRAY['Avery Sloan','Jordan Reyes','Casey Lin','Morgan Patel'])[i + 1],
    encode(sha256(('synthetic-contact-' || i)::bytea), 'hex'),
    (ARRAY['LONGEVITY_PROGRAM','HORMONE_OPTIMIZATION','DIAGNOSTICS','WEIGHT_MANAGEMENT'])[i + 1],
    'NEW',
    -- Within the last few days, but never before the engagement began. greatest() is what stops
    -- the four of them sitting in early October forever once the record is a month old.
    least(greatest(start_on, CURRENT_DATE - 3)::timestamptz
          + make_interval(days => i, hours => 9 + (i * 3) % 8, mins => (i * 17) % 60), now()),
    NULL,   -- no first response: nobody has replied yet
    NULL,   -- no booking: the Calendar fills from Healthie, not from here
    NULL    -- no owner: nothing has been picked up
FROM generate_series(0, lead_count - 1) AS i;

-- Status history: every stage the lead passed through, so the funnel counts "ever reached".
INSERT INTO lead_status_history (id, organization_id, lead_id, from_status, to_status, changed_at, changed_by)
SELECT
    ('0199a1d0-1100-7000-8000-' || lpad((row_number() OVER (ORDER BY l.id, step.n))::text, 12, '0'))::uuid,
    org,
    l.id,
    CASE WHEN step.n = 0 THEN NULL ELSE ladder.stages[step.n] END,
    ladder.stages[step.n + 1],
    least(l.created_at + make_interval(hours => step.n * 9), now()),
    'seed@visiondigitallab.com'
FROM lead l
CROSS JOIN LATERAL (
    SELECT CASE l.status
        WHEN 'DUPLICATE'             THEN ARRAY['NEW','DUPLICATE']
        WHEN 'NEW'                   THEN ARRAY['NEW']
        WHEN 'CONTACTED'             THEN ARRAY['NEW','CONTACTED']
        WHEN 'NOT_CONVERTED'         THEN ARRAY['NEW','CONTACTED','NOT_CONVERTED']
        WHEN 'QUALIFIED'             THEN ARRAY['NEW','CONTACTED','QUALIFIED']
        WHEN 'APPOINTMENT_REQUESTED' THEN ARRAY['NEW','CONTACTED','QUALIFIED','APPOINTMENT_REQUESTED']
        WHEN 'BOOKED'                THEN ARRAY['NEW','CONTACTED','QUALIFIED','APPOINTMENT_REQUESTED','BOOKED']
        ELSE ARRAY['NEW','CONTACTED','QUALIFIED','APPOINTMENT_REQUESTED','BOOKED','ATTENDED']
    END AS stages
) AS ladder
CROSS JOIN LATERAL generate_series(0, array_length(ladder.stages, 1) - 1) AS step(n)
WHERE l.organization_id = org;

RAISE NOTICE 'VisionOne demo seed: % leads inserted from %.', lead_count, start_on;

END $$;

-- ---------------------------------------------------------------- appointments and calls
--
-- There are none, and that is deliberate.
--
-- The Calendar is filled from Healthie and from nowhere else. Seeding appointments here would put
-- bookings in front of a practice that can open its own scheduler and see that they do not exist.
--
-- The Front Desk reports on telephony. No AI front desk is connected yet, so there are no calls
-- to report; an answered/missed breakdown over invented calls is a measurement of nothing. Both
-- screens read empty until their provider is connected, which is the honest state.

-- ---------------------------------------------------------------- the actual work
-- Three items, because three is what is actually in flight. They are written from the business
-- reason rather than the task, which is the whole premise of the screen: a practice owner reads
-- "why" before "what", and "Technical SEO audit" tells him nothing he can weigh.
--
-- Vision Admin adds to this list in the product from here on. The practice sees every item and can
-- change none of them - the one thing it decides is content approval, which is a different screen
-- and a different table.
DO $$
DECLARE org uuid := '0199a1d0-0000-7000-8000-000000000001';
BEGIN

IF EXISTS (SELECT 1 FROM work_item WHERE organization_id = org) THEN
    RETURN;
END IF;

INSERT INTO work_item (id, organization_id, title, category, status, business_reason, owner_name,
                       target_date, client_dependency, client_visible_update, completed_at) VALUES

 ('0199a1d0-4000-7000-8000-000000000001', org,
  'Search visibility for thrivelongevitycenter.com', 'SEO', 'IN_PROGRESS',
  'The site does not currently rank for the treatments the practice actually sells. Someone in the area searching for hormone optimisation or a longevity panel does not find THRIVE, so every enquiry has to be paid for. Ranking for those terms is the only channel that keeps producing after the ad budget stops.',
  'Vision Digital Lab',
  (date_trunc('month', date '2026-10-01') + interval '2 months 30 days')::date,
  false,
  'Full technical and content audit of the site done. Working through the fixes in order of what moves rankings fastest: page titles and structure first, then the service pages, then site speed.',
  NULL),

 ('0199a1d0-4000-7000-8000-000000000002', org,
  'Google Ads and Google Business Profile live and verified', 'PAID_ACQUISITION', 'WAITING_FOR_CLIENT',
  'Until both are live we are reporting on a channel that is not running. Google Ads is what produces enquiries this quarter while search visibility is still being built, and a verified Business Profile is what puts the practice on the map for local searches and lets patients leave reviews that future patients read.',
  'Vision Digital Lab',
  (date_trunc('month', date '2026-10-01') + interval '24 days')::date,
  true,
  'The advertising account, API access and conversion tracking are built and connected to this dashboard. Two things are now on the practice: a billing method on the Google Ads account, and completing Google''s verification for the Business Profile. Spend and campaign performance appear on the Growth screen automatically once the account is active.',
  NULL),

 ('0199a1d0-4000-7000-8000-000000000003', org,
  'Monthly articles and social content for Instagram and Facebook', 'CONTENT', 'IN_PROGRESS',
  'The treatments THRIVE sells are ones people research before they book, and there is currently nothing to find. Articles answer the questions that come up in a first consultation, which both earns search traffic and shortens the consultation. Social keeps the practice visible to people who are not ready to book yet.',
  'Vision Digital Lab',
  (date_trunc('month', date '2026-10-01') + interval '1 month 14 days')::date,
  false,
  'Topics planned from the questions that come up most in enquiries. Each article and post appears on the Content screen for your approval before anything is published - nothing goes out in the practice''s name without it.',
  NULL);

END $$;

-- ---------------------------------------------------------------- the month's recommendation
-- One, for the month in progress, still open. expected_effect is written as a hypothesis, never as
-- a promise.
INSERT INTO recommendation (id, organization_id, period_month, observation, proposed_action,
                            rationale, expected_effect, decision_required, status, decided_at)
VALUES
 ('0199a1d0-6000-7000-8000-000000000001',
  '0199a1d0-0000-7000-8000-000000000001', date_trunc('month', CURRENT_DATE)::date,
  'Every enquiry so far has come from organic search, because that is the only channel currently running. Google Ads is built and connected but not yet live, and the Google Business Profile is not yet verified - so the practice is visible to people already searching for it by name, and to almost nobody else.',
  'Complete the two items the practice owns: a billing method on the Google Ads account, and Google''s verification for the Business Profile.',
  'Neither is work we can do on the practice''s behalf. Until both are done the budget below is a plan rather than a spend, and this dashboard reports on one channel out of six.',
  'Local search is where a clinic of this kind usually sees its first paid enquiries. We expect the first useful read on cost per lead about two weeks after the account goes live, not before.',
  'Add billing to the Google Ads account and complete Business Profile verification.',
  'OPEN', NULL)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- the month's report
-- The month in progress is a DRAFT: narrative half written, figures not frozen. The client cannot
-- see this one, which is the point of having the status. A report with no frozen payload reads as
-- not-yet-generated and the API composes its figures live.
INSERT INTO monthly_report (id, organization_id, period_month, status, key_learning, next_actions,
                            decisions_required, generated_at)
VALUES
 ('0199a1d0-7000-7000-8000-000000000001',
  '0199a1d0-0000-7000-8000-000000000001', date_trunc('month', CURRENT_DATE)::date, 'DRAFT',
  'Too early to draw a conclusion. The portal is reporting on one channel out of six because the rest are not connected yet, so these figures describe the setup rather than the marketing.',
  E'Get Google Ads and the Business Profile live and verified.\nContinue the search visibility fixes in priority order.\nFirst articles and social posts to the practice for approval.',
  E'Billing method on the Google Ads account.\nGoogle Business Profile verification.', NULL)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- campaigns
--
-- None. There were four here, all marked ACTIVE from the first of the month, and not one of them
-- exists in the Google Ads account - which is still waiting on a billing method. A campaign list
-- is the easiest thing on this dashboard for a client to check against the source, and the first
-- thing they would check once the spend beside it looked wrong.
--
-- Campaigns appear when the advertising adapter reads them back from a live account.
