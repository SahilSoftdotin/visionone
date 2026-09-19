/**
 * Synthetic Growth and Leads fixtures for the Phase-1 demonstration.
 *
 * THESE FIGURES ARE INVENTED. No endpoint serves them yet - GrowthFinanceService and
 * LeadMetricsService exist in the backend but no controller exposes them. Every screen built on
 * this module must carry a "Demo data" badge, because the master prompt is explicit that
 * integration status is shown truthfully and a successful integration is never fabricated.
 *
 * Replacing this module with real queries is the Week-2/3 backend increment: the shapes below
 * deliberately mirror the tables that already exist (growth_plan, budget_allocation, campaign,
 * channel_source, lead, lead_status_history) so the swap is a fetch, not a redesign.
 *
 * Names are synthetic. The master prompt forbids real patient data, and a broad
 * `serviceInterest` is the only clinical-adjacent field permitted.
 */

import type { Money } from './types';

const usd = (minor: number): Money => ({ amountMinor: minor, currency: 'USD' });

/* ------------------------------------------------------------------ Growth */

export interface ChannelAllocation {
  channelCode: string;
  displayName: string;
  planned: Money;
  actual: Money;
  leads: number;
  booked: number;
  costPerLead: Money | null;
  costPerBooked: Money | null;
}

export interface CampaignRow {
  id: string;
  name: string;
  channelCode: string;
  status: 'ACTIVE' | 'PAUSED' | 'ENDED';
  spend: Money;
  leads: number;
  booked: number;
}

export interface GrowthPlanDemo {
  periodMonth: string;
  status: 'DRAFT' | 'ACTIVE' | 'CLOSED';
  plannedTotal: Money;
  actualTotal: Money;
  remaining: Money;
  utilizationPercent: number;
  allocations: ChannelAllocation[];
  campaigns: CampaignRow[];
}

export const growthDemo: GrowthPlanDemo = {
  periodMonth: '2026-09-01',
  status: 'ACTIVE',
  // The $5,000 configurable monthly budget fixture the master prompt names for Week 2.
  plannedTotal: usd(500000),
  actualTotal: usd(310000),
  remaining: usd(190000),
  utilizationPercent: 62,
  allocations: [
    {
      channelCode: 'GOOGLE_ADS',
      displayName: 'Google Ads',
      planned: usd(200000),
      actual: usd(136400),
      leads: 21,
      booked: 5,
      costPerLead: usd(6495),
      costPerBooked: usd(27280),
    },
    {
      channelCode: 'ORGANIC_SEARCH',
      displayName: 'Organic Search',
      planned: usd(90000),
      actual: usd(62000),
      leads: 12,
      booked: 3,
      costPerLead: usd(5167),
      costPerBooked: usd(20667),
    },
    {
      channelCode: 'GOOGLE_MAPS',
      displayName: 'Google Maps',
      planned: usd(60000),
      actual: usd(41200),
      leads: 9,
      booked: 3,
      costPerLead: usd(4578),
      costPerBooked: usd(13733),
    },
    {
      channelCode: 'AI_FRONT_DESK',
      displayName: 'AI Front Desk',
      planned: usd(80000),
      actual: usd(44000),
      leads: 8,
      booked: 2,
      costPerLead: usd(5500),
      costPerBooked: usd(22000),
    },
    {
      channelCode: 'SOCIAL',
      displayName: 'Instagram / Facebook',
      planned: usd(50000),
      actual: usd(26400),
      leads: 5,
      booked: 1,
      costPerLead: usd(5280),
      costPerBooked: usd(26400),
    },
    {
      channelCode: 'DIRECT_REFERRAL',
      displayName: 'Direct / Referral',
      planned: usd(20000),
      actual: usd(0),
      leads: 2,
      booked: 1,
      costPerLead: null,
      costPerBooked: null,
    },
  ],
  campaigns: [
    {
      id: 'c1',
      name: 'Hormone Therapy — Athens 25mi',
      channelCode: 'GOOGLE_ADS',
      status: 'ACTIVE',
      spend: usd(82400),
      leads: 13,
      booked: 3,
    },
    {
      id: 'c2',
      name: 'Sentinel Advanced — Brand',
      channelCode: 'GOOGLE_ADS',
      status: 'ACTIVE',
      spend: usd(54000),
      leads: 8,
      booked: 2,
    },
    {
      id: 'c3',
      name: 'Longevity Panel — Retargeting',
      channelCode: 'SOCIAL',
      status: 'PAUSED',
      spend: usd(26400),
      leads: 5,
      booked: 1,
    },
  ],
};

/* ------------------------------------------------------------------- Leads */

/** Exactly the statuses the master prompt enumerates - no more, no fewer. */
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
  name: string;
  /** Broad category only. A check constraint enforces the same list in the database. */
  serviceInterest: string;
  source: string;
  campaign: string | null;
  createdAt: string;
  status: LeadStatus;
  owner: string;
  /** Minutes from creation to first response; null when never responded to. */
  responseMinutes: number | null;
  booked: boolean;
}

export const leadsDemo: LeadRow[] = [
  {
    id: 'L-1042',
    name: 'Dana Whitfield',
    serviceInterest: 'Hormone Therapy',
    source: 'Google Ads',
    campaign: 'Hormone Therapy — Athens 25mi',
    createdAt: '2026-09-18T19:40:00Z',
    status: 'BOOKED',
    owner: 'Front Desk',
    responseMinutes: 4,
    booked: true,
  },
  {
    id: 'L-1041',
    name: 'Marcus Ellery',
    serviceInterest: 'Longevity Panel',
    source: 'Organic Search',
    campaign: null,
    createdAt: '2026-09-18T15:12:00Z',
    status: 'QUALIFIED',
    owner: 'Front Desk',
    responseMinutes: 11,
    booked: false,
  },
  {
    id: 'L-1040',
    name: 'Priya Raghunathan',
    serviceInterest: 'Weight Management',
    source: 'Google Maps',
    campaign: null,
    createdAt: '2026-09-18T13:02:00Z',
    status: 'APPOINTMENT_REQUESTED',
    owner: 'AI Front Desk',
    responseMinutes: 1,
    booked: false,
  },
  {
    id: 'L-1039',
    name: 'Tom Bradbury',
    serviceInterest: 'Hormone Therapy',
    source: 'AI Front Desk',
    campaign: null,
    createdAt: '2026-09-17T22:35:00Z',
    status: 'ATTENDED',
    owner: 'Front Desk',
    responseMinutes: 2,
    booked: true,
  },
  {
    id: 'L-1038',
    name: 'Alicia Moreno',
    serviceInterest: 'Peptide Therapy',
    source: 'Instagram / Facebook',
    campaign: 'Longevity Panel — Retargeting',
    createdAt: '2026-09-17T18:20:00Z',
    status: 'CONTACTED',
    owner: 'Front Desk',
    responseMinutes: 38,
    booked: false,
  },
  {
    id: 'L-1037',
    name: 'Gerald Simmons',
    serviceInterest: 'Longevity Panel',
    source: 'Google Ads',
    campaign: 'Sentinel Advanced — Brand',
    createdAt: '2026-09-17T16:05:00Z',
    status: 'BOOKED',
    owner: 'Front Desk',
    responseMinutes: 7,
    booked: true,
  },
  {
    id: 'L-1036',
    name: 'Nadia Kowalski',
    serviceInterest: 'IV Therapy',
    source: 'Direct / Referral',
    campaign: null,
    createdAt: '2026-09-17T11:48:00Z',
    status: 'NEW',
    owner: 'Unassigned',
    responseMinutes: null,
    booked: false,
  },
  {
    id: 'L-1035',
    name: 'Curtis Haywood',
    serviceInterest: 'Hormone Therapy',
    source: 'Google Ads',
    campaign: 'Hormone Therapy — Athens 25mi',
    createdAt: '2026-09-16T20:14:00Z',
    status: 'NOT_CONVERTED',
    owner: 'Front Desk',
    responseMinutes: 96,
    booked: false,
  },
  {
    id: 'L-1034',
    name: 'Beatrice Lin',
    serviceInterest: 'Weight Management',
    source: 'Organic Search',
    campaign: null,
    createdAt: '2026-09-16T14:30:00Z',
    status: 'QUALIFIED',
    owner: 'Front Desk',
    responseMinutes: 9,
    booked: false,
  },
  {
    id: 'L-1033',
    name: 'Owen Castellano',
    serviceInterest: 'Peptide Therapy',
    source: 'AI Front Desk',
    campaign: null,
    createdAt: '2026-09-16T02:10:00Z',
    status: 'BOOKED',
    owner: 'AI Front Desk',
    responseMinutes: 1,
    booked: true,
  },
  {
    id: 'L-1032',
    name: 'Simone Arquette',
    serviceInterest: 'Longevity Panel',
    source: 'Google Maps',
    campaign: null,
    createdAt: '2026-09-15T17:55:00Z',
    status: 'DUPLICATE',
    owner: 'Front Desk',
    responseMinutes: null,
    booked: false,
  },
  {
    id: 'L-1031',
    name: 'Hector Villalobos',
    serviceInterest: 'Hormone Therapy',
    source: 'Instagram / Facebook',
    campaign: 'Longevity Panel — Retargeting',
    createdAt: '2026-09-15T12:22:00Z',
    status: 'CONTACTED',
    owner: 'Front Desk',
    responseMinutes: 22,
    booked: false,
  },
  {
    id: 'L-1030',
    name: 'Yvonne Petrakis',
    serviceInterest: 'IV Therapy',
    source: 'Organic Search',
    campaign: null,
    createdAt: '2026-09-14T19:03:00Z',
    status: 'ATTENDED',
    owner: 'Front Desk',
    responseMinutes: 6,
    booked: true,
  },
  {
    id: 'L-1029',
    name: 'Raymond Osei',
    serviceInterest: 'Weight Management',
    source: 'Google Ads',
    campaign: 'Sentinel Advanced — Brand',
    createdAt: '2026-09-14T09:41:00Z',
    status: 'QUALIFIED',
    owner: 'Front Desk',
    responseMinutes: 14,
    booked: false,
  },
  {
    id: 'L-1028',
    name: 'Colette Duffy',
    serviceInterest: 'Longevity Panel',
    source: 'Direct / Referral',
    campaign: null,
    createdAt: '2026-09-13T21:17:00Z',
    status: 'NEW',
    owner: 'Unassigned',
    responseMinutes: null,
    booked: false,
  },
];

/** Response-time distribution, for the Leads header strip. */
export const leadResponseSummary = {
  medianMinutes: 8,
  underFifteenPercent: 68,
  neverResponded: 3,
};
