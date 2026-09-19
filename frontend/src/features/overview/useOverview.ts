import { useQuery } from '@tanstack/react-query';
import { apiGet, queryKeys } from '@/lib/api';
import type { OverviewResponse } from '@/lib/types';

export function useOverview(orgId: string, month: string) {
  return useQuery({
    queryKey: queryKeys.overview(orgId, month),
    queryFn: () => apiGet<OverviewResponse>(`/orgs/${orgId}/overview`, { month }),
    staleTime: 30_000,
  });
}
