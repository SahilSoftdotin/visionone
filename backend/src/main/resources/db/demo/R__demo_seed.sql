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

    -- Days of the engagement that have actually happened. Floored at 1 so the seed still produces
    -- something if it runs on or before the start date, which is what happens in a test.
    days int := greatest((CURRENT_DATE - start_on) + 1, 1);

    -- Volume per day, not per month. A cash-pay longevity clinic on a modest ad budget: a few
    -- enquiries and a handful of calls a day. Deriving the totals from elapsed days is what keeps
    -- the dataset honest on day three of an engagement - there is no way to show a full month of
    -- activity three days in without dating most of it in the future.
    leads_per_day int := 4;
    calls_per_day int := 8;
    lead_count int := days * leads_per_day;
    call_count int := days * calls_per_day;

    -- The plan runs to the end of the engagement's first calendar year. Plans are forward-looking,
    -- so unlike leads and calls these legitimately sit in the future.
    m0 date := date_trunc('month', start_on)::date;
    m1 date := (date_trunc('month', start_on) + interval '1 month')::date;
    m2 date := (date_trunc('month', start_on) + interval '2 months')::date;
    this_month date := date_trunc('month', CURRENT_DATE)::date;
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

-- Planned and actual per channel, per month. Actual is entered manually in Phase 1.
INSERT INTO budget_allocation (id, organization_id, growth_plan_id, channel_source_id, planned_minor, actual_minor)
SELECT
    ('0199a1d0-0004-7000-8000-' || lpad((p.n * 10 + c.n)::text, 12, '0'))::uuid,
    org,
    p.plan_id,
    c.channel_id,
    c.planned,
    -- Three cases, and the third is the one that matters. A month already finished spent close to
    -- plan. The month in progress has spent the fraction of it that has elapsed. A month that has
    -- not started has spent nothing - showing a plausible-looking actual against a future month
    -- would be inventing spend that no invoice will ever match.
    CASE
        WHEN p.month < this_month THEN (c.planned * (92 + ((p.n * 7 + c.n * 3) % 9))) / 100
        WHEN p.month = this_month THEN
            (c.planned * extract(day from CURRENT_DATE)::int)
            / extract(day from (date_trunc('month', CURRENT_DATE) + interval '1 month - 1 day'))::int
        ELSE 0
    END
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
-- leads_per_day for every day of the engagement so far, distributed across channels by a fixed
-- weighting.
INSERT INTO lead (id, organization_id, channel_source_id, display_name, contact_hash,
                  service_interest, status, created_at, first_response_at, booked_at,
                  owner_membership_id)
SELECT
    ('0199a1d0-1000-7000-8000-' || lpad(i::text, 12, '0'))::uuid,
    org,
    CASE
        WHEN i % 20 <= 6  THEN '0199a1d0-0001-7000-8000-000000000001'::uuid
        WHEN i % 20 <= 11 THEN '0199a1d0-0001-7000-8000-000000000002'::uuid
        WHEN i % 20 <= 14 THEN '0199a1d0-0001-7000-8000-000000000003'::uuid
        WHEN i % 20 <= 16 THEN '0199a1d0-0001-7000-8000-000000000004'::uuid
        WHEN i % 20 <= 18 THEN '0199a1d0-0001-7000-8000-000000000005'::uuid
        ELSE                   '0199a1d0-0001-7000-8000-000000000006'::uuid
    END,
    (ARRAY['Avery Sloan','Jordan Reyes','Casey Lin','Morgan Patel','Riley Novak','Quinn Baptiste',
           'Harper Oyelaran','Rowan Castellano','Emerson Kidd','Sawyer Ibarra','Peyton Marchetti',
           'Finley Oduya'])[(i % 12) + 1] || ' ' || (i + 1)::text,
    encode(sha256(('synthetic-contact-' || i)::bytea), 'hex'),
    (ARRAY['LONGEVITY_PROGRAM','HORMONE_OPTIMIZATION','DIAGNOSTICS','WEIGHT_MANAGEMENT',
           'IV_THERAPY','GENERAL_ENQUIRY'])[((i * 5) % 6) + 1],
    s.status,
    ts.created_at,
    -- least(..., now()) on both: these record things that have already happened, and on an
    -- engagement a few days old the offsets below would otherwise answer an enquiry tomorrow.
    CASE WHEN s.rank >= 1 THEN least(ts.created_at + CASE
             -- Answered live at the desk.
             WHEN (i * 31) % 100 < 36 THEN make_interval(mins => 2 + (i * 7) % 13)
             -- Picked up later from voicemail or a form.
             ELSE make_interval(mins => 22 + (i * 17) % 200) END, now()) END,
    CASE WHEN s.rank >= 4 THEN least(ts.created_at + make_interval(hours => 26 + (i * 11) % 90), now()) END,
    -- Untouched leads stay unassigned; anything worked on has an owner.
    CASE WHEN s.rank >= 1 THEN '0199a1d0-0002-7000-8000-000000000001'::uuid END
FROM generate_series(0, lead_count - 1) AS i
CROSS JOIN LATERAL (
    -- i / leads_per_day is the day index, so the rows fill forward from the engagement start one
    -- day at a time. Clamped, because the hour offset can still land a row later this afternoon.
    SELECT least(start_on
           + make_interval(days => i / leads_per_day,
                           hours => 8 + (i % 11), mins => (i * 7) % 60), now()) AS created_at
) AS ts
CROSS JOIN LATERAL (
    SELECT v.status, v.rank FROM (
        SELECT CASE
            WHEN (i * 37) % 100 <  5 THEN 'DUPLICATE'
            WHEN (i * 37) % 100 < 22 THEN 'NEW'
            WHEN (i * 37) % 100 < 38 THEN 'CONTACTED'
            WHEN (i * 37) % 100 < 55 THEN 'QUALIFIED'
            WHEN (i * 37) % 100 < 65 THEN 'APPOINTMENT_REQUESTED'
            WHEN (i * 37) % 100 < 82 THEN 'BOOKED'
            WHEN (i * 37) % 100 < 90 THEN 'ATTENDED'
            ELSE 'NOT_CONVERTED'
        END AS status
    ) raw
    CROSS JOIN LATERAL (
        SELECT raw.status AS status, CASE raw.status
            WHEN 'DUPLICATE' THEN 0 WHEN 'NEW' THEN 0 WHEN 'CONTACTED' THEN 1
            WHEN 'NOT_CONVERTED' THEN 1 WHEN 'QUALIFIED' THEN 2
            WHEN 'APPOINTMENT_REQUESTED' THEN 3 WHEN 'BOOKED' THEN 4 ELSE 5 END AS rank
    ) v
) AS s;

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
DO $$
DECLARE
    org uuid := '0199a1d0-0000-7000-8000-000000000001';
    start_on date := date '2026-10-01';
    days int := greatest((CURRENT_DATE - start_on) + 1, 1);
    calls_per_day int := 8;
    call_count int := days * calls_per_day;
BEGIN

IF EXISTS (SELECT 1 FROM call WHERE organization_id = org) THEN
    RETURN;
END IF;

-- One appointment per booked or attended lead. Never an appointment reason, only a reference.
INSERT INTO appointment_reference (id, organization_id, external_ref, lead_id, requested_at,
                                   scheduled_for, status, provider_code)
SELECT
    ('0199a1d0-2000-7000-8000-' || lpad((row_number() OVER (ORDER BY l.id))::text, 12, '0'))::uuid,
    org,
    'DEMO-APPT-' || lpad((row_number() OVER (ORDER BY l.id))::text, 5, '0'),
    l.id,
    coalesce(l.booked_at, l.created_at),
    -- Deliberately not clamped. An appointment five days after a booking made this week falls in
    -- the future, which is exactly right: those are the upcoming bookings the Calendar is for.
    coalesce(l.booked_at, l.created_at) + interval '5 days',
    CASE WHEN l.status = 'ATTENDED' THEN 'ATTENDED' ELSE 'BOOKED' END,
    'DEMO'
FROM lead l
WHERE l.organization_id = org AND l.status IN ('BOOKED', 'ATTENDED');

-- calls_per_day across the engagement so far. ~18% missed, ~22% after hours.
INSERT INTO call (id, organization_id, external_ref, direction, started_at, duration_seconds,
                  outcome, answered, after_hours, transferred, provider_code, caller_label)
SELECT
    ('0199a1d0-3000-7000-8000-' || lpad(i::text, 12, '0'))::uuid,
    org,
    'DEMO-CALL-' || lpad(i::text, 5, '0'),
    CASE WHEN i % 9 = 0 THEN 'OUTBOUND' ELSE 'INBOUND' END,
    started,
    CASE WHEN answered THEN 180 + (i * 23) % 540 ELSE 0 END,
    CASE
        WHEN NOT answered AND i % 3 = 0 THEN 'VOICEMAIL'
        WHEN NOT answered              THEN 'MISSED'
        WHEN i % 17 = 0                THEN 'TRANSFERRED'
        WHEN i % 11 = 0                THEN 'RESCHEDULED'
        WHEN i % 23 = 0                THEN 'CANCELLED'
        WHEN i % 4  = 0                THEN 'BOOKED'
        WHEN i % 5  = 0                THEN 'MESSAGE_TAKEN'
        ELSE 'ENQUIRY_ANSWERED'
    END,
    answered,
    after_hours,
    i % 17 = 0,
    'DEMO',
    'Caller ' || lpad(((i * 37) % 900 + 100)::text, 3, '0')
FROM generate_series(0, call_count - 1) AS i
CROSS JOIN LATERAL (
    SELECT least(start_on
           + make_interval(days => i / calls_per_day, hours => hr, mins => (i * 13) % 60),
           now()) AS started,
           ((i * 61) % 100) >= 18 AS answered,
           (hr < 9 OR hr >= 18) AS after_hours
    FROM (SELECT CASE
                   WHEN ((i * 29) % 100) < 78 THEN 9 + ((i * 7) % 9)
                   WHEN i % 2 = 0             THEN (i * 5) % 9
                   ELSE 18 + ((i * 3) % 6)
                 END AS hr) h
) AS c;

END $$;

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
  'Roughly one call in five is going unanswered, and about a fifth of all calls arrive outside clinic hours. Those calls are currently lost rather than delayed - nobody calls back, because nobody knows they rang.',
  'Put the AI Front Desk on after-hours calls only, for four weeks, and measure how many of them turn into booked appointments.',
  'After-hours calls are lost entirely today, so there is nothing to protect. Handling them changes nothing about how the clinic runs during the day, and four weeks is long enough to see a pattern without committing to anything.',
  'If even half of the after-hours calls can be captured it would be a meaningful increase in booked appointments. The pilot exists to find out whether that holds, not to prove it.',
  'Confirm the practice is willing to run a four-week after-hours pilot.',
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
  'Too early to draw a conclusion from one partial month. The pattern worth watching is that unanswered and after-hours calls look like the largest single source of lost opportunity we can currently see.',
  E'Get Google Ads and the Business Profile live and verified.\nContinue the search visibility fixes in priority order.\nFirst articles and social posts to the practice for approval.',
  E'Billing method on the Google Ads account.\nConfirm the after-hours front desk pilot.', NULL)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- campaigns
-- Paid channels carry campaigns; organic and referral do not. Leads are attributed to a campaign
-- only on the channels that have them.
DO $$
DECLARE
    org uuid := '0199a1d0-0000-7000-8000-000000000001';
    start_on date := date '2026-10-01';
BEGIN

IF EXISTS (SELECT 1 FROM campaign WHERE organization_id = org) THEN
    RETURN;
END IF;

INSERT INTO campaign (id, organization_id, channel_source_id, name, status, started_on, ended_on) VALUES
 ('0199a1d0-0008-7000-8000-000000000001', org, '0199a1d0-0001-7000-8000-000000000001',
  'Hormone Therapy - 25mi radius', 'ACTIVE', start_on, NULL),
 ('0199a1d0-0008-7000-8000-000000000002', org, '0199a1d0-0001-7000-8000-000000000001',
  'Longevity Program - Brand', 'ACTIVE', start_on, NULL),
 ('0199a1d0-0008-7000-8000-000000000003', org, '0199a1d0-0001-7000-8000-000000000005',
  'Longevity Panel - Retargeting', 'ACTIVE', start_on, NULL),
 ('0199a1d0-0008-7000-8000-000000000004', org, '0199a1d0-0001-7000-8000-000000000005',
  'Diagnostics - Cold Audience', 'ACTIVE', start_on, NULL);

WITH ranked AS (
    SELECT l.id,
           l.channel_source_id,
           row_number() OVER (PARTITION BY l.channel_source_id ORDER BY l.created_at, l.id) AS n
    FROM lead l
    WHERE l.organization_id = org
      AND l.channel_source_id IN ('0199a1d0-0001-7000-8000-000000000001',
                                  '0199a1d0-0001-7000-8000-000000000005')
)
UPDATE lead l
SET campaign_id = CASE
        WHEN r.channel_source_id = '0199a1d0-0001-7000-8000-000000000001'
             THEN CASE WHEN r.n % 3 = 0 THEN '0199a1d0-0008-7000-8000-000000000002'::uuid
                       ELSE '0199a1d0-0008-7000-8000-000000000001'::uuid END
        ELSE CASE WHEN r.n % 2 = 0 THEN '0199a1d0-0008-7000-8000-000000000004'::uuid
                  ELSE '0199a1d0-0008-7000-8000-000000000003'::uuid END
    END
FROM ranked r
WHERE l.id = r.id;

END $$;

-- ---------------------------------------------------------------- front desk and calendar detail
-- Fills the columns V12 added on the rows seeded above.
--
-- Note the division by 60 in every modulo. The seeded timestamps sit on whole minutes, so their
-- epoch is always a multiple of 60 and `epoch % 3` is constantly zero - which silently made every
-- answered call an AI Front Desk call and every appointment exactly 30 minutes. Measured, not
-- assumed: the distributions below are checked, not hoped for.
DO $$
DECLARE org uuid := '0199a1d0-0000-7000-8000-000000000001';
BEGIN

IF NOT EXISTS (SELECT 1 FROM call WHERE organization_id = org) THEN
    RETURN;
END IF;

-- Who answered. A missed call went to voicemail; the AI desk takes the after-hours traffic,
-- which is the pilot this month's recommendation proposes.
UPDATE call SET handled_by = CASE
        WHEN NOT answered THEN 'VOICEMAIL'
        WHEN after_hours  THEN 'AI_FRONT_DESK'
        WHEN ((extract(epoch from started_at)::bigint / 60) % 3) = 0 THEN 'AI_FRONT_DESK'
        ELSE 'PRACTICE_TEAM'
    END
WHERE organization_id = org AND handled_by IS NULL;

-- Credit the AI Front Desk channel only for calls it actually handled. The rest stay uncredited:
-- null is honest, "Direct" would be a guess.
UPDATE call SET channel_source_id = '0199a1d0-0001-7000-8000-000000000004'
WHERE organization_id = org AND handled_by = 'AI_FRONT_DESK' AND channel_source_id IS NULL;

UPDATE appointment_reference a SET
    display_label = split_part(l.display_name, ' ', 1) || ' '
                    || left(split_part(l.display_name, ' ', 2), 1) || '.',
    duration_minutes = 30 + 15 * ((extract(epoch from a.requested_at)::bigint / 60) % 3),
    service_category = l.service_interest,
    channel_source_id = l.channel_source_id
FROM lead l
WHERE a.lead_id = l.id AND a.organization_id = org AND a.display_label IS NULL;

END $$;
