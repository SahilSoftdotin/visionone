import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend, queryKeys } from '@/lib/api';
import type { LeadDetailResponse, LeadListResponse, LeadStatus } from '@/lib/types';

/**
 * The month's leads, fetched unfiltered.
 *
 * The API also filters by status and source server-side, but this screen wants counts for every
 * status chip, which needs the whole period anyway - about sixty rows at THRIVE's volume. The
 * server-side filters are there for when that stops being true.
 */
export function useLeads(orgId: string, month: string) {
  return useQuery({
    queryKey: queryKeys.leads(orgId, month),
    queryFn: () => apiGet<LeadListResponse>(`/orgs/${orgId}/leads`, { month }),
    staleTime: 30_000,
  });
}

export function useLeadDetail(orgId: string, leadId: string | null) {
  return useQuery({
    queryKey: queryKeys.leadDetail(orgId, leadId ?? 'none'),
    queryFn: () => apiGet<LeadDetailResponse>(`/orgs/${orgId}/leads/${leadId}`),
    enabled: leadId !== null,
    staleTime: 10_000,
  });
}

/**
 * Vision Admin moving a lead through the pipeline.
 *
 * The response carries the lead and its new history, so the detail panel updates from the write
 * itself. The list and the Overview funnel both derive from this, so they are invalidated.
 */
export function useLeadTransition(orgId: string, month: string) {
  const client = useQueryClient();

  return useMutation({
    mutationFn: ({ leadId, toStatus, reason }: { leadId: string; toStatus: LeadStatus; reason?: string }) =>
      apiSend<LeadDetailResponse>('POST', `/orgs/${orgId}/admin/leads/${leadId}/status`, { toStatus, reason }),
    onSuccess: (fresh) => {
      client.setQueryData(queryKeys.leadDetail(orgId, fresh.lead.id), fresh);
      void client.invalidateQueries({ queryKey: queryKeys.leads(orgId, month) });
      void client.invalidateQueries({ queryKey: ['overview', orgId] });
    },
  });
}
