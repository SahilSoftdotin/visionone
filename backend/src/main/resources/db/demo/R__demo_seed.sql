-- Synthetic THRIVE dataset for the local and demo profiles.
--
-- Deterministic by construction: every value derives from a row index, never from random().
-- The same seed produces the same dashboard every time, so a number questioned in a demo can be
-- reproduced and explained. Names are drawn from a fixed synthetic list and no contact details
-- are stored. Nothing here is clinical.
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
    m0   date := date '2026-07-01';
    m1   date := date '2026-08-01';
    m2   date := date '2026-09-01';
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
 ('0199a1d0-0003-7000-8000-000000000001', org, m0, 500000, cur, 'Baseline month. Ads weighted to Longevity Program.'),
 ('0199a1d0-0003-7000-8000-000000000002', org, m1, 500000, cur, 'Shifted spend toward Google Maps after July local-search gains.'),
 ('0199a1d0-0003-7000-8000-000000000003', org, m2, 500000, cur, 'Current month. Meta budget held pending creative refresh.');

-- Planned and actual per channel, per month. Actual is entered manually in Phase 1.
INSERT INTO budget_allocation (id, organization_id, growth_plan_id, channel_source_id, planned_minor, actual_minor)
SELECT
    ('0199a1d0-0004-7000-8000-' || lpad((p.n * 10 + c.n)::text, 12, '0'))::uuid,
    org,
    p.plan_id,
    c.channel_id,
    c.planned,
    -- Spend lands close to plan but never exactly on it, and the current month is part-spent.
    CASE WHEN p.n = 3 THEN (c.planned * 62) / 100 ELSE (c.planned * (92 + ((p.n * 7 + c.n * 3) % 9))) / 100 END
FROM (VALUES
        (1, '0199a1d0-0003-7000-8000-000000000001'::uuid),
        (2, '0199a1d0-0003-7000-8000-000000000002'::uuid),
        (3, '0199a1d0-0003-7000-8000-000000000003'::uuid)
     ) AS p(n, plan_id)
CROSS JOIN (VALUES
        (1, '0199a1d0-0001-7000-8000-000000000001'::uuid, 220000),  -- Google Ads
        (2, '0199a1d0-0001-7000-8000-000000000002'::uuid,  80000),  -- Organic Search
        (3, '0199a1d0-0001-7000-8000-000000000003'::uuid,  60000),  -- Google Maps
        (4, '0199a1d0-0001-7000-8000-000000000004'::uuid,  45000),  -- AI Front Desk
        (5, '0199a1d0-0001-7000-8000-000000000005'::uuid,  75000),  -- Meta / Instagram
        (6, '0199a1d0-0001-7000-8000-000000000006'::uuid,  20000)   -- Direct / Referral
     ) AS c(n, channel_id, planned);

-- ---------------------------------------------------------------- leads
-- 180 leads, 60 per month, distributed across channels by a fixed weighting.
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
    CASE WHEN s.rank >= 1 THEN ts.created_at + CASE
             -- Answered live at the desk.
             WHEN (i * 31) % 100 < 36 THEN make_interval(mins => 2 + (i * 7) % 13)
             -- Picked up later from voicemail or a form.
             ELSE make_interval(mins => 22 + (i * 17) % 200) END END,
    CASE WHEN s.rank >= 4 THEN ts.created_at + make_interval(hours => 26 + (i * 11) % 90) END,
    -- Untouched leads stay unassigned; anything worked on has an owner.
    CASE WHEN s.rank >= 1 THEN '0199a1d0-0002-7000-8000-000000000001'::uuid END
FROM generate_series(0, 179) AS i
CROSS JOIN LATERAL (
    SELECT (CASE i / 60 WHEN 0 THEN m0 WHEN 1 THEN m1 ELSE m2 END)
           + make_interval(days => (i % 60) % 27, hours => 8 + (i % 11), mins => (i * 7) % 60) AS created_at
) AS ts
CROSS JOIN LATERAL (
    SELECT v.status, v.rank FROM (
        SELECT CASE
            WHEN (i * 13) % 100 <  5 THEN 'DUPLICATE'
            WHEN (i * 13) % 100 < 22 THEN 'NEW'
            WHEN (i * 13) % 100 < 38 THEN 'CONTACTED'
            WHEN (i * 13) % 100 < 55 THEN 'QUALIFIED'
            WHEN (i * 13) % 100 < 65 THEN 'APPOINTMENT_REQUESTED'
            WHEN (i * 13) % 100 < 82 THEN 'BOOKED'
            WHEN (i * 13) % 100 < 90 THEN 'ATTENDED'
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
    l.created_at + make_interval(hours => step.n * 9),
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

RAISE NOTICE 'VisionOne demo seed: leads and history inserted.';

END $$;

-- ---------------------------------------------------------------- appointments, calls, work, content
DO $$
DECLARE
    org uuid := '0199a1d0-0000-7000-8000-000000000001';
    m0  date := date '2026-07-01';
    m1  date := date '2026-08-01';
    m2  date := date '2026-09-01';
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
    coalesce(l.booked_at, l.created_at) + interval '5 days',
    CASE WHEN l.status = 'ATTENDED' THEN 'ATTENDED' ELSE 'BOOKED' END,
    'DEMO'
FROM lead l
WHERE l.organization_id = org AND l.status IN ('BOOKED', 'ATTENDED');

-- 240 calls across the three months. ~18% missed, ~22% after hours.
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
FROM generate_series(0, 239) AS i
CROSS JOIN LATERAL (
    SELECT (CASE i / 80 WHEN 0 THEN m0 WHEN 1 THEN m1 ELSE m2 END)
           + make_interval(days => (i % 80) % 27, hours => hr, mins => (i * 13) % 60) AS started,
           (i % 100) >= 18 AS answered,
           (hr < 9 OR hr >= 18) AS after_hours
    FROM (SELECT (i * 7) % 24 AS hr) h
) AS c;

-- Work items. Four sit in WAITING_FOR_CLIENT so the Overview attention list is not empty.
INSERT INTO work_item (id, organization_id, title, category, status, business_reason, owner_name,
                       target_date, client_dependency, client_visible_update, completed_at)
SELECT
    ('0199a1d0-4000-7000-8000-' || lpad(i::text, 12, '0'))::uuid,
    org,
    w.title,
    w.category,
    w.status,
    w.reason,
    CASE WHEN i % 3 = 0 THEN 'Sahil Arora' ELSE 'Vision Digital Lab' END,
    (m2 + make_interval(days => (i * 3) % 40))::date,
    w.status = 'WAITING_FOR_CLIENT',
    w.update_text,
    CASE WHEN w.status = 'COMPLETED'
         THEN (CASE i % 3 WHEN 0 THEN m0 WHEN 1 THEN m1 ELSE m2 END)
              + make_interval(days => (i * 5) % 25) END
FROM generate_series(0, 29) AS i
CROSS JOIN LATERAL (
    SELECT
      (ARRAY['Longevity Program landing page rebuild','Google Business Profile photo refresh',
             'Hormone optimization blog cluster','Competitor keyword gap analysis',
             'Review response workflow','Call tracking number setup',
             'Meta creative refresh','Local citation cleanup','Conversion tracking audit',
             'Appointment booking flow test'])[(i % 10) + 1] AS title,
      (ARRAY['SEO','LOCAL_SEARCH','CONTENT','ANALYTICS','REPUTATION','INTEGRATION',
             'SOCIAL','LOCAL_SEARCH','ANALYTICS','AUTOMATION'])[(i % 10) + 1] AS category,
      CASE
        WHEN i % 10 IN (0, 3, 6) THEN 'COMPLETED'
        WHEN i % 10 IN (1, 7)    THEN 'IN_PROGRESS'
        WHEN i % 10 = 2          THEN 'WAITING_FOR_CLIENT'
        WHEN i % 10 = 8          THEN 'BLOCKED'
        ELSE 'PLANNED'
      END AS status,
      (ARRAY['Longevity Program pages convert below the site average.',
             'Profile photos are over two years old and suppress map engagement.',
             'No depth on hormone terms competitors already rank for.',
             'We cannot prioritise spend without knowing the gaps.',
             'Unanswered reviews cost trust at the exact moment of decision.',
             'Call volume by channel is currently unattributable.',
             'Creative fatigue is showing in declining click-through.',
             'Inconsistent listings weaken local ranking signals.',
             'Conversions are under-reported, so cost per lead looks worse than it is.',
             'Booking abandonment has never been measured.'])[(i % 10) + 1] AS reason,
      (ARRAY['Draft copy ready for your review.','Awaiting clinic photos from your team.',
             'Two of four articles published.','Analysis complete, shared in this month''s report.',
             'Workflow live; responses within one business day.',
             'Tracking numbers active on all paid channels.',
             'New creative in production.','48 of 61 listings corrected.',
             'Fix deployed, verifying over the next week.',
             'Test scheduled once the new page is live.'])[(i % 10) + 1] AS update_text
) AS w;

-- Content. Three items are waiting on Gary, which is what Week 3's approval demo needs.
INSERT INTO content_item (id, organization_id, title, content_type, status, author_name,
                          draft_url, published_url, published_at, summary)
SELECT
    ('0199a1d0-5000-7000-8000-' || lpad(i::text, 12, '0'))::uuid,
    org,
    c.title,
    c.content_type,
    c.status,
    'Vision Digital Lab',
    CASE WHEN c.status <> 'PUBLISHED' THEN 'https://drafts.visiondigitallab.com/thrive/' || i END,
    CASE WHEN c.status =  'PUBLISHED' THEN 'https://thrivelongevitycenter.com/insights/' || i END,
    CASE WHEN c.status =  'PUBLISHED'
         THEN (CASE i % 3 WHEN 0 THEN m0 WHEN 1 THEN m1 ELSE m2 END) + make_interval(days => (i * 4) % 25) END,
    c.summary
FROM generate_series(0, 23) AS i
CROSS JOIN LATERAL (
    SELECT
      (ARRAY['What a longevity panel actually measures','Five signs it is time to review your hormones',
             'Inside a THRIVE first visit','Sleep, recovery and biological age',
             'Why we test before we treat','Metabolic health after 40',
             'IV therapy: what the evidence supports'])[(i % 7) + 1] AS title,
      (ARRAY['BLOG','SOCIAL_POST','SHORT_VIDEO','GOOGLE_BUSINESS_POST','BLOG','FAQ',
             'LANDING_PAGE','SOCIAL_POST'])[(i % 8) + 1] AS content_type,
      (ARRAY['What the panel covers, what it does not, and how long results take.',
             'Short post pointing at the longevity panel explainer.',
             'Ninety seconds inside a first visit, filmed at the clinic.',
             'Profile post on sleep and recovery, aimed at local search.',
             'Why testing comes before treatment, in plain language.',
             'Carousel on metabolic health after forty.',
             'What the evidence actually supports, and what it does not.'])[(i % 7) + 1] AS summary,
      CASE
        WHEN i % 8 IN (0, 4) THEN 'PUBLISHED'
        WHEN i % 8 = 1       THEN 'CLIENT_REVIEW'
        WHEN i % 8 = 2       THEN 'DRAFTING'
        WHEN i % 8 = 3       THEN 'APPROVED'
        WHEN i % 8 = 5       THEN 'INTERNAL_REVIEW'
        WHEN i % 8 = 6       THEN 'SCHEDULED'
        ELSE 'IDEA'
      END AS status
) AS c;

END $$;

-- ---------------------------------------------------------------- recommendations
-- One per month. expected_effect is written as a hypothesis, never as a promise.
INSERT INTO recommendation (id, organization_id, period_month, observation, proposed_action,
                            rationale, expected_effect, decision_required, status, decided_at)
VALUES
 ('0199a1d0-6000-7000-8000-000000000001',
  '0199a1d0-0000-7000-8000-000000000001', date '2026-07-01',
  'Google Ads produced the most leads but the highest cost per booked appointment. Google Maps produced fewer leads at roughly a third of the cost.',
  'Move $600 of the monthly budget from Google Ads to local search and Google Business Profile work.',
  'Map-sourced leads booked at a higher rate in July, and local search spend compounds rather than stopping when the budget stops.',
  'If the July pattern holds, we would expect cost per booked appointment to fall. This is a hypothesis to test over one month, not a guarantee.',
  'Approve the budget shift for August.',
  'ACCEPTED', timestamptz '2026-07-28 14:10:00+00'),

 ('0199a1d0-6000-7000-8000-000000000002',
  '0199a1d0-0000-7000-8000-000000000001', date '2026-08-01',
  'Roughly one call in five went unanswered, and a fifth of all calls arrived outside clinic hours.',
  'Pilot the AI Front Desk on after-hours calls only, for four weeks.',
  'After-hours calls are currently lost entirely. Handling them does not change how the clinic runs during the day.',
  'Recovering even half of the after-hours calls would be a meaningful increase in booked appointments. The pilot exists to find out whether that holds.',
  'Confirm the clinic is willing to run a four-week after-hours pilot.',
  'ACCEPTED', timestamptz '2026-08-26 09:30:00+00'),

 ('0199a1d0-6000-7000-8000-000000000003',
  '0199a1d0-0000-7000-8000-000000000001', date '2026-09-01',
  'Meta click-through has fallen for three consecutive weeks on unchanged creative, while spend has held steady.',
  'Pause Meta spend for two weeks and reallocate it to the Longevity Program landing page rebuild and new creative.',
  'Creative fatigue is the most likely explanation. Spending into it buys progressively less, and the landing page is where the paid traffic lands anyway.',
  'We expect a lower cost per lead once new creative is live. The size of the change is unknown until we test it.',
  'Approve pausing Meta spend for two weeks.',
  'OPEN', NULL)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- monthly reports
-- next_actions and decisions_required hold one item per line; the API splits on newlines. A
-- textarea is what Vision actually writes these in, and a JSON array in a text column would be a
-- schema pretending to be something it is not.
--
-- payload_json is deliberately left at its '{}' default here rather than hand-written: the figures
-- belong to the generator, and a payload typed out in a seed file is the one number in VisionOne
-- that nothing checks. A report with no frozen payload reads as not-yet-generated and the API
-- composes its figures live, which in a synthetic dataset gives the same answer generation would.
INSERT INTO monthly_report (id, organization_id, period_month, status, key_learning, next_actions,
                            decisions_required, generated_at)
VALUES
 ('0199a1d0-7000-7000-8000-000000000001',
  '0199a1d0-0000-7000-8000-000000000001', date '2026-07-01', 'SHARED',
  'Paid search brings volume; local search brings efficiency. The mix matters more than the total.',
  E'Shift budget toward local search.\nBegin the hormone content cluster.\nAdd call tracking to the paid search landing pages.',
  E'Approve the August budget shift.', timestamptz '2026-08-01 08:00:00+00'),
 ('0199a1d0-7000-7000-8000-000000000002',
  '0199a1d0-0000-7000-8000-000000000001', date '2026-08-01', 'SHARED',
  'Missed and after-hours calls are the largest single source of lost opportunity we can currently see.',
  E'Run the after-hours AI Front Desk pilot.\nPublish the remaining hormone articles.\nReview the three highest-cost paid search terms.',
  E'Confirm the after-hours pilot.\nDecide whether Saturday morning hours are worth trialling.',
  timestamptz '2026-09-01 08:00:00+00'),
 -- September is still open: a DRAFT report, narrative half written, figures not yet frozen. The
 -- client cannot see this one, which is the point of having the status.
 ('0199a1d0-7000-7000-8000-000000000003',
  '0199a1d0-0000-7000-8000-000000000001', date '2026-09-01', 'DRAFT',
  'Early signal: the after-hours pilot is converting, but the sample is still too small to act on.',
  E'Hold the pilot for a second month before drawing a conclusion.',
  NULL, NULL)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- campaigns
-- Paid channels carry campaigns; organic and referral do not. Leads are attributed
-- deterministically by position within their channel, so campaign counts are real rather
-- than asserted. Campaign-level SPEND is deliberately absent: Phase 1 budgets by channel,
-- and the Google Ads adapter fills per-campaign spend in Phase 2.
DO $$
DECLARE
    org uuid := '0199a1d0-0000-7000-8000-000000000001';
BEGIN

IF EXISTS (SELECT 1 FROM campaign WHERE organization_id = org) THEN
    RETURN;
END IF;

INSERT INTO campaign (id, organization_id, channel_source_id, name, status, started_on, ended_on) VALUES
 ('0199a1d0-0008-7000-8000-000000000001', org, '0199a1d0-0001-7000-8000-000000000001',
  'Hormone Therapy - 25mi radius', 'ACTIVE', date '2026-07-01', NULL),
 ('0199a1d0-0008-7000-8000-000000000002', org, '0199a1d0-0001-7000-8000-000000000001',
  'Longevity Program - Brand', 'ACTIVE', date '2026-07-01', NULL),
 ('0199a1d0-0008-7000-8000-000000000003', org, '0199a1d0-0001-7000-8000-000000000005',
  'Longevity Panel - Retargeting', 'PAUSED', date '2026-07-15', date '2026-09-10'),
 ('0199a1d0-0008-7000-8000-000000000004', org, '0199a1d0-0001-7000-8000-000000000005',
  'Diagnostics - Cold Audience', 'ACTIVE', date '2026-08-01', NULL);

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
-- which is the pilot the August recommendation proposed.
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
