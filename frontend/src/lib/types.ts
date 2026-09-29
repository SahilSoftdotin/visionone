/**
 * API contract types.
 *
 * Hand-written, mirroring the backend's records field for field. Generating them from the OpenAPI
 * spec is the planned next step, so that contract drift breaks the build rather than a screen in
 * front of the client; until then, a backend record change must be matched here by hand.
 */

export interface Money {
  amountMinor: number;
  currency: string;
}

export type Role = 'VISION_ADMIN' | 'CLIENT_OWNER';

export interface OrganizationMembership {
  id: string;
  name: string;
  slug: string;
  timezone: string;
  currency: string;
  role: Role;
}

export interface SessionResponse {
  subject: string;
  displayName: string;
  organizations: OrganizationMembership[];
}

export interface OverviewKpis {
  monthlyGrowthBudget: Money;
  actualSpend: Money;
  newLeads: number;
  qualifiedLeads: number;
  bookedAppointments: number;
  costPerLead: Money | null;
  costPerBookedAppointment: Money | null;
  leadToBookConversionPercent: number | null;
  priorMonth: {
    actualSpend: Money;
    newLeads: number;
    qualifiedLeads: number;
    bookedAppointments: number;
    leadToBookConversionPercent: number | null;
  };
}

export interface OverviewFunnel {
  leads: number;
  qualified: number;
  appointmentRequested: number;
  booked: number;
}

export interface SourcePerformance {
  channelCode: string;
  displayName: string;
  leads: number;
  qualified: number;
  booked: number;
  spend: Money;
  costPerLead: Money | null;
  costPerBookedAppointment: Money | null;
}

export interface Investment {
  planned: Money;
  actual: Money;
  remaining: Money;
  utilizationPercent: number;
}

export interface VisionActivity {
  completed: number;
  inProgress: number;
  waitingForClient: number;
}

export interface Recommendation {
  id: string;
  observation: string;
  proposedAction: string;
  rationale: string;
  expectedEffect: string;
  decisionRequired: string;
  status: 'OPEN' | 'ACCEPTED' | 'DEFERRED' | 'DECLINED';
}

export interface CallSummary {
  totalCalls: number;
  answered: number;
  missed: number;
  afterHours: number;
  appointmentsBooked: number;
  cancelled: number;
  rescheduled: number;
  transferred: number;
  aiHandled: number;
  teamHandled: number;
  averageDurationSeconds: number | null;
  bookingConversionPercent: number | null;
}

/** @property hourly 24 buckets, index 0 to 23, in the practice's own timezone. */
export interface CallActivity {
  summary: CallSummary;
  hourly: number[];
}

export interface OverviewResponse {
  organizationName: string;
  periodMonth: string;
  currency: string;
  kpis: OverviewKpis;
  funnel: OverviewFunnel;
  sourcePerformance: SourcePerformance[];
  investment: Investment;
  visionActivity: VisionActivity;
  frontDesk: CallActivity;
  /** The newest few leads. Same row shape as the Leads screen, so the two cannot disagree. */
  recentLeads: LeadRow[];
  recommendation: Recommendation | null;
}

/**
 * Lead pipeline. These are real domain enums enforced by a database check constraint, not
 * fixture shapes.
 */
export type LeadStatus =
  | 'NEW'
  | 'CONTACTED'
  | 'QUALIFIED'
  | 'APPOINTMENT_REQUESTED'
  | 'BOOKED'
  | 'ATTENDED'
  | 'NOT_CONVERTED'
  | 'DUPLICATE';

export const LEAD_STATUSES: LeadStatus[] = [
  'NEW',
  'CONTACTED',
  'QUALIFIED',
  'APPOINTMENT_REQUESTED',
  'BOOKED',
  'ATTENDED',
  'NOT_CONVERTED',
  'DUPLICATE',
];

export interface LeadRow {
  id: string;
  /** Human-readable handle, e.g. L-1042. Assigned by a database sequence. */
  reference: string;
  name: string;
  /** Broad commercial category, never a condition. */
  serviceInterest: string;
  source: string;
  campaign: string | null;
  createdAt: string;
  status: LeadStatus;
  owner: string;
  /** Minutes from creation to first response; null when nobody has replied. */
  responseMinutes: number | null;
  booked: boolean;
}

export interface LeadSummary {
  total: number;
  qualified: number;
  booked: number;
  medianResponseMinutes: number | null;
  underFifteenMinutesPercent: number | null;
}

export interface LeadListResponse {
  summary: LeadSummary;
  leads: LeadRow[];
  editable: boolean;
}

export interface LeadHistoryEntry {
  fromStatus: LeadStatus | null;
  toStatus: LeadStatus;
  changedAt: string;
  changedBy: string;
  reason: string | null;
}

export interface LeadDetailResponse {
  lead: LeadRow;
  history: LeadHistoryEntry[];
}

/** Work and content. Domain enums, enforced by check constraints. */
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
  | 'AUTOMATION'
  | 'INTEGRATION';

export interface WorkItemRow {
  id: string;
  title: string;
  category: WorkCategory;
  status: WorkStatus;
  businessReason: string;
  owner: string;
  targetDate: string | null;
  clientDependency: boolean;
  clientUpdate: string | null;
  completedAt: string | null;
}

export interface WorkListResponse {
  summary: { completed: number; inProgress: number; blocked: number; waitingForClient: number };
  items: WorkItemRow[];
  editable: boolean;
}

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
  summary: string | null;
  contentType: ContentType;
  status: ContentStatus;
  author: string;
  draftUrl: string | null;
  publishedUrl: string | null;
  publishedAt: string | null;
  clientFeedback: string | null;
  /** True only while this item is waiting on the practice. */
  awaitingClient: boolean;
  /** Where Vision may move it next, from the server's transition table. Empty for the practice. */
  nextStatuses: ContentStatus[];
}

export interface ContentListResponse {
  summary: { awaitingClient: number; changesRequested: number; approved: number; published: number };
  items: ContentItemRow[];
  /** The client decides: true for CLIENT_OWNER, false for Vision. */
  canDecide: boolean;
  editable: boolean;
}

/* ------------------------------------------------------------------ reports */

export type ReportStatus = 'DRAFT' | 'GENERATED' | 'SHARED';

/** Every figure in a monthly report, as frozen at generation. */
export interface ReportPayload {
  organizationName: string;
  periodMonth: string;
  currency: string;
  investment: {
    planned: Money;
    actual: Money;
    remaining: Money;
    utilizationPercent: number;
  };
  headline: {
    newLeads: number;
    qualified: number;
    appointments: number;
    costPerLead: Money | null;
    costPerBooked: Money | null;
    bookingRatePercent: number | null;
  };
  funnel: { leads: number; qualified: number; appointmentRequested: number; booked: number };
  sources: {
    channelCode: string;
    displayName: string;
    leads: number;
    qualified: number;
    booked: number;
    spend: Money;
    costPerBooked: Money | null;
  }[];
  frontDesk: CallSummary;
  completedWork: {
    title: string;
    category: string;
    businessReason: string;
    completedAt: string;
  }[];
  publishedContent: {
    title: string;
    contentType: string;
    publishedAt: string;
    publishedUrl: string | null;
  }[];
}

export interface MonthlyReportResponse {
  id: string;
  periodMonth: string;
  status: ReportStatus;
  generatedAt: string | null;
  /**
   * True once the figures were frozen at generation. False means they are being composed live from
   * today's data and will still move - a different claim, and the screen says which it is making.
   */
  frozen: boolean;
  keyLearning: string | null;
  nextActions: string[];
  decisionsRequired: string[];
  figures: ReportPayload;
  /** Vision Digital Lab, before the report is shared. The endpoints refuse regardless. */
  editable: boolean;
}

export interface ReportListResponse {
  reports: {
    id: string;
    periodMonth: string;
    status: ReportStatus;
    generatedAt: string | null;
    frozen: boolean;
  }[];
  /** Whether the caller may start or refresh a report - Vision Digital Lab only. */
  canGenerate: boolean;
}

/** RFC 9457 problem+json. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  violations?: { field: string; message: string }[];
}
