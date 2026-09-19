/**
 * API contract types.
 *
 * Hand-written for Week 1. From Week 2 these are generated from the backend's OpenAPI spec so a
 * contract drift breaks the build rather than a screen in front of the client.
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

export interface OverviewResponse {
  organizationName: string;
  periodMonth: string;
  currency: string;
  kpis: OverviewKpis;
  funnel: OverviewFunnel;
  sourcePerformance: SourcePerformance[];
  investment: Investment;
  visionActivity: VisionActivity;
  recommendation: Recommendation | null;
}

/** RFC 9457 problem+json. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  violations?: { field: string; message: string }[];
}
