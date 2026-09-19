/**
 * Synthetic Front Desk, Work, Content and Report fixtures.
 *
 * THESE FIGURES ARE INVENTED, like lib/demoData.ts. Front Desk in particular is synthetic by
 * design, not by omission: the master prompt states Phase 1 uses demo call data and that live AI
 * telephony belongs to a later phase behind a VoiceProvider abstraction.
 *
 * Call records are masked at source. Recent-call information has to stay operational and
 * privacy-conscious, so a full caller number never reaches the browser.
 */

export interface CallRow {
  id: string;
  maskedNumber: string;
  handledBy: 'AI Front Desk' | 'Practice Team' | 'Voicemail';
  outcome: 'BOOKED' | 'ENQUIRY' | 'TRANSFERRED' | 'MISSED' | 'CANCELLED' | 'RESCHEDULED';
  afterHours: boolean;
  durationSeconds: number;
  startedAt: string;
}

export const frontDeskDemo = {
  totalCalls: 214,
  answered: 187,
  missed: 27,
  afterHours: 78,
  appointmentsBooked: 41,
  cancelled: 6,
  rescheduled: 11,
  transferred: 23,
  averageDurationSeconds: 164,
  bookingConversionPercent: 21.9,
  /** Call volume by hour, 0-23. Makes the after-hours gap legible at a glance. */
  hourly: [2, 1, 0, 0, 1, 2, 5, 9, 14, 18, 17, 15, 12, 16, 19, 17, 13, 11, 9, 8, 7, 5, 3, 2],
  recent: [
    { id: 'C-8821', maskedNumber: '+1 (256)000-4471', handledBy: 'AI Front Desk', outcome: 'BOOKED', afterHours: true, durationSeconds: 212, startedAt: '2026-09-18T23:41:00Z' },
    { id: 'C-8820', maskedNumber: '+1 (256) 000-1180', handledBy: 'Practice Team', outcome: 'ENQUIRY', afterHours: false, durationSeconds: 145, startedAt: '2026-09-18T18:22:00Z' },
    { id: 'C-8819', maskedNumber: '+1 (205) 000-9032', handledBy: 'Voicemail', outcome: 'MISSED', afterHours: true, durationSeconds: 0, startedAt: '2026-09-18T02:14:00Z' },
    { id: 'C-8818', maskedNumber: '+1 (256) 000-7765', handledBy: 'AI Front Desk', outcome: 'RESCHEDULED', afterHours: true, durationSeconds: 178, startedAt: '2026-09-17T21:58:00Z' },
    { id: 'C-8817', maskedNumber: '+1 (256) 000-2219', handledBy: 'Practice Team', outcome: 'TRANSFERRED', afterHours: false, durationSeconds: 96, startedAt: '2026-09-17T15:30:00Z' },
    { id: 'C-8816', maskedNumber: '+1 (931) 000-5540', handledBy: 'AI Front Desk', outcome: 'BOOKED', afterHours: true, durationSeconds: 240, startedAt: '2026-09-17T06:12:00Z' },
    { id: 'C-8815', maskedNumber: '+1 (256) 000-8814', handledBy: 'Voicemail', outcome: 'MISSED', afterHours: false, durationSeconds: 0, startedAt: '2026-09-16T12:44:00Z' },
    { id: 'C-8814', maskedNumber: '+1 (256) 000-3307', handledBy: 'AI Front Desk', outcome: 'CANCELLED', afterHours: true, durationSeconds: 121, startedAt: '2026-09-16T04:03:00Z' },
  ] as CallRow[],
};

/* ------------------------------------------------------------ Work & Content */

export type WorkStatus =
  | 'PLANNED'
  | 'IN_PROGRESS'
  | 'BLOCKED'
  | 'WAITING_FOR_CLIENT'
  | 'COMPLETED'
  | 'CANCELLED';

export type WorkCategory =
  | 'SEO'
  | 'PAID_ACQUISITION'
  | 'LOCAL_SEARCH'
  | 'CONTENT'
  | 'SOCIAL'
  | 'REPUTATION'
  | 'ANALYTICS'
  | 'INTEGRATION'
  | 'AUTOMATION';

export interface WorkItemRow {
  id: string;
  title: string;
  category: WorkCategory;
  status: WorkStatus;
  owner: string;
  targetDate: string;
  /** Why this matters to the practice, in the client's language rather than ours. */
  businessReason: string;
  clientDependency: string | null;
  clientUpdate: string;
}

export const workDemo: WorkItemRow[] = [
  {
    id: 'W-311',
    title: 'Hormone therapy landing page rebuild',
    category: 'SEO',
    status: 'IN_PROGRESS',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-26',
    businessReason:
      'The current page ranks below three competitors for the highest-intent search in Athens.',
    clientDependency: null,
    clientUpdate: 'Draft copy written; physician review scheduled for Monday.',
  },
  {
    id: 'W-310',
    title: 'Google Business Profile photo refresh',
    category: 'LOCAL_SEARCH',
    status: 'WAITING_FOR_CLIENT',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-22',
    businessReason:
      'Profiles with recent photography turn map views into calls at a measurably higher rate.',
    clientDependency: 'Eight to ten interior photographs from the practice.',
    clientUpdate: 'Waiting on photographs from THRIVE.',
  },
  {
    id: 'W-309',
    title: 'Call tracking numbers per channel',
    category: 'ANALYTICS',
    status: 'COMPLETED',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-15',
    businessReason:
      'Without per-channel numbers there is no way to say which advert produced which call.',
    clientDependency: null,
    clientUpdate: 'Live. Every call now carries its source.',
  },
  {
    id: 'W-308',
    title: 'Review request sequence after appointments',
    category: 'REPUTATION',
    status: 'BLOCKED',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-30',
    businessReason: 'Review volume is the strongest lever on local map ranking.',
    clientDependency: 'Blocked until the scheduling connection is authorised in Phase 2.',
    clientUpdate: 'Paused pending the scheduling integration.',
  },
  {
    id: 'W-307',
    title: 'Sentinel Advanced campaign restructure',
    category: 'PAID_ACQUISITION',
    status: 'IN_PROGRESS',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-24',
    businessReason:
      'Brand and non-brand traffic share one budget, so brand absorbs spend that should be testing new demand.',
    clientDependency: null,
    clientUpdate: 'Split into two campaigns; gathering a week of data before judging it.',
  },
  {
    id: 'W-306',
    title: 'Instagram content cadence',
    category: 'SOCIAL',
    status: 'PLANNED',
    owner: 'Vision Digital Lab',
    targetDate: '2026-10-06',
    businessReason: 'Consistent posting keeps the practice visible between patient visits.',
    clientDependency: null,
    clientUpdate: 'Scheduled to begin in October.',
  },
];

export type ContentType =
  | 'BLOG'
  | 'SOCIAL_POST'
  | 'SHORT_VIDEO'
  | 'GOOGLE_BUSINESS_POST'
  | 'LANDING_PAGE'
  | 'FAQ';

export type ContentStatus =
  | 'IDEA'
  | 'DRAFTING'
  | 'INTERNAL_REVIEW'
  | 'CLIENT_REVIEW'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'SCHEDULED'
  | 'PUBLISHED';

export interface ContentItemRow {
  id: string;
  title: string;
  type: ContentType;
  status: ContentStatus;
  owner: string;
  targetDate: string;
  summary: string;
}

export const contentDemo: ContentItemRow[] = [
  {
    id: 'CT-88',
    title: 'What a longevity panel actually measures',
    type: 'BLOG',
    status: 'CLIENT_REVIEW',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-23',
    summary: 'Explains the panel in plain language and sets expectations before a first consultation.',
  },
  {
    id: 'CT-87',
    title: 'Hormone therapy: who it is and is not for',
    type: 'BLOG',
    status: 'CLIENT_REVIEW',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-25',
    summary: 'Addresses the qualifying question the front desk answers most often.',
  },
  {
    id: 'CT-86',
    title: 'September hours and holiday closure',
    type: 'GOOGLE_BUSINESS_POST',
    status: 'PUBLISHED',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-12',
    summary: 'Keeps the profile current; stale hours are a common source of missed calls.',
  },
  {
    id: 'CT-85',
    title: 'Peptide therapy FAQ',
    type: 'FAQ',
    status: 'APPROVED',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-28',
    summary: 'Six questions drawn from real front-desk enquiries.',
  },
  {
    id: 'CT-84',
    title: 'Meet Dr. Adams, a sixty second introduction',
    type: 'SHORT_VIDEO',
    status: 'DRAFTING',
    owner: 'Vision Digital Lab',
    targetDate: '2026-10-02',
    summary: 'Familiarity before a first visit reduces no-shows.',
  },
  {
    id: 'CT-83',
    title: 'Weight management programme overview',
    type: 'LANDING_PAGE',
    status: 'CHANGES_REQUESTED',
    owner: 'Vision Digital Lab',
    targetDate: '2026-09-27',
    summary: 'Physician asked for clearer separation of the medical and lifestyle elements.',
  },
];

/* ---------------------------------------------------------------- Reporting */

export const monthlyReportDemo = {
  periodMonth: '2026-09-01',
  status: 'DRAFT' as 'DRAFT' | 'GENERATED' | 'SHARED',
  keyLearning:
    'Calls arriving outside published hours converted at a higher rate than daytime calls this month. The after-hours gap is not a small tail: it is where the highest-intent enquiries are landing.',
  nextActions: [
    'Split brand and non-brand paid search so brand stops absorbing test budget.',
    'Publish both physician-reviewed articles before the end of the month.',
    'Collect interior photography to refresh the Google Business Profile.',
  ],
  decisionsRequired: [
    'Approve the two articles currently sitting in client review.',
    'Confirm whether October budget holds at $5,000 or increases to fund the non-brand test.',
  ],
};
