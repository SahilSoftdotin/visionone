import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend, queryKeys } from '@/lib/api';
import type { Money } from '@/lib/types';

/** Wire shape from GET /orgs/{orgId}/growth/plan. */
interface GrowthPlanWire {
  periodMonth: string;
  currency: string;
  status: 'DRAFT' | 'ACTIVE' | 'CLOSED';
  plannedTotal: Money;
  actualTotal: Money;
  remaining: Money;
  utilizationPercent: number;
  notes: string | null;
  editable: boolean;
  allocations: {
    channelSourceId: string;
    channelCode: string;
    displayName: string;
    category: string;
    planned: Money;
    actual: Money;
    variance: Money;
    leads: number;
    qualified: number;
    booked: number;
    costPerLead: Money | null;
    costPerBookedAppointment: Money | null;
  }[];
  campaigns: {
    id: string;
    name: string;
    channelCode: string;
    status: 'ACTIVE' | 'PAUSED' | 'ENDED';
    spend: Money | null;
    leads: number;
    booked: number;
  }[];
}

/**
 * The Growth screen's data.
 *
 * The API names cost per booked appointment in full, matching the Overview; the screen has always
 * called it costPerBooked. Mapping it here keeps one name on the wire and leaves the screen alone.
 */
export function useGrowthPlan(orgId: string, month: string) {
  const query = useQuery({
    queryKey: queryKeys.growth(orgId, month),
    queryFn: () => apiGet<GrowthPlanWire>(`/orgs/${orgId}/growth/plan`, { month }),
    staleTime: 30_000,
  });

  const plan = query.data
    ? {
        ...query.data,
        allocations: query.data.allocations.map((a) => ({
          ...a,
          costPerBooked: a.costPerBookedAppointment,
        })),
      }
    : undefined;

  return { ...query, plan };
}

export interface AllocationEdit {
  channelSourceId: string;
  plannedMinor: number;
  actualMinor: number;
}

/** Vision Admin's writes. Both return the recomputed plan, so the cache is filled, not invalidated. */
export function useGrowthAdmin(orgId: string, month: string) {
  const client = useQueryClient();

  const onSaved = (fresh: GrowthPlanWire) => {
    client.setQueryData(queryKeys.growth(orgId, month), fresh);
    // The Overview shows the same investment figures, so it must not keep a stale copy.
    void client.invalidateQueries({ queryKey: ['overview', orgId] });
  };

  const setBudget = useMutation({
    mutationFn: (input: { plannedTotalMinor: number; notes?: string | null }) =>
      apiSend<GrowthPlanWire>('PUT', `/orgs/${orgId}/admin/growth/plan`, input, { month }),
    onSuccess: onSaved,
  });

  const setAllocations = useMutation({
    mutationFn: (allocations: AllocationEdit[]) =>
      apiSend<GrowthPlanWire>('PUT', `/orgs/${orgId}/admin/growth/allocations`, { allocations }, { month }),
    onSuccess: onSaved,
  });

  return { setBudget, setAllocations };
}
