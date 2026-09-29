import { useQuery } from '@tanstack/react-query';
import { apiGet, queryKeys } from '@/lib/api';
import { currentMonthKey } from './MonthPicker';
import type {
  ContentListResponse,
  LeadListResponse,
  WorkListResponse,
  CallActivity,
} from '@/lib/types';

/**
 * The shell's own reads, for the search palette and the notification bell.
 *
 * <p>These deliberately use the same query keys as the screens, so nothing is fetched twice: on a
 * screen that has already loaded its leads, the bell reads them out of the cache for free. They are
 * separate from the feature hooks only because the shell needs to be able to hold them back until
 * they are wanted - {@code enabled} is the whole reason this file exists.
 *
 * <p>A long stale time is right here. The bell is chrome, not a live monitor, and a badge that
 * refetches four endpoints on every navigation costs the client more than the freshness is worth.
 */
const SHELL_STALE_TIME = 300_000;

interface FrontDeskWire {
  summary: CallActivity['summary'];
  hourly: number[];
  /** Per-outcome totals for the month. The tray needs MISSED specifically - see Notifications. */
  outcomeCounts: { outcome: string; count: number }[];
}

export function useShellLeads(orgId: string, enabled = true) {
  const month = currentMonthKey();
  return useQuery({
    queryKey: queryKeys.leads(orgId, month),
    queryFn: () => apiGet<LeadListResponse>(`/orgs/${orgId}/leads`, { month }),
    enabled: enabled && orgId !== '',
    staleTime: SHELL_STALE_TIME,
  });
}

export function useShellWork(orgId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.work(orgId),
    queryFn: () => apiGet<WorkListResponse>(`/orgs/${orgId}/work`),
    enabled: enabled && orgId !== '',
    staleTime: SHELL_STALE_TIME,
  });
}

export function useShellContent(orgId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.content(orgId),
    queryFn: () => apiGet<ContentListResponse>(`/orgs/${orgId}/content`),
    enabled: enabled && orgId !== '',
    staleTime: SHELL_STALE_TIME,
  });
}

export function useShellFrontDesk(orgId: string, enabled = true) {
  const month = currentMonthKey();
  return useQuery({
    queryKey: queryKeys.frontDesk(orgId, month),
    queryFn: () => apiGet<FrontDeskWire>(`/orgs/${orgId}/frontdesk`, { month }),
    enabled: enabled && orgId !== '',
    staleTime: SHELL_STALE_TIME,
  });
}

/**
 * Only the fields the search index reads. Each is a subset of what the Growth and Calendar screens
 * cache under the same key, so the palette can read their cache and they can read the palette's.
 */
interface GrowthSearchWire {
  allocations: { channelCode: string; displayName: string; leads: number; booked: number }[];
  campaigns: { id: string; name: string; status: string }[];
}

interface CalendarSearchWire {
  days: {
    date: string;
    appointments: {
      id: string;
      time: string;
      label: string;
      serviceCategory: string | null;
      status: string;
    }[];
  }[];
}

export function useShellGrowth(orgId: string, enabled = true) {
  const month = currentMonthKey();
  return useQuery({
    queryKey: queryKeys.growth(orgId, month),
    queryFn: () => apiGet<GrowthSearchWire>(`/orgs/${orgId}/growth/plan`, { month }),
    enabled: enabled && orgId !== '',
    staleTime: SHELL_STALE_TIME,
  });
}

export function useShellCalendar(orgId: string, enabled = true) {
  const month = currentMonthKey();
  return useQuery({
    queryKey: queryKeys.calendar(orgId, month),
    queryFn: () => apiGet<CalendarSearchWire>(`/orgs/${orgId}/calendar`, { month }),
    enabled: enabled && orgId !== '',
    staleTime: SHELL_STALE_TIME,
  });
}
