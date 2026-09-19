import { Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { apiGet, queryKeys } from '@/lib/api';
import type { SessionResponse } from '@/lib/types';

/**
 * Sends the caller to their organization.
 *
 * The organization id is in the URL from day one, matching the API, so a second tenant needs no
 * routing change.
 */
export function OrganizationRouter() {
  const { data, isLoading, error } = useQuery({
    queryKey: queryKeys.session,
    queryFn: () => apiGet<SessionResponse>('/me'),
    staleTime: 5 * 60_000,
  });

  if (isLoading) {
    return <p className="p-6 text-sm text-muted-foreground">Loading your practice&hellip;</p>;
  }

  if (error || !data || data.organizations.length === 0) {
    return (
      <div className="p-6">
        <p className="text-sm font-medium">No practice is linked to your account</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Ask Vision Digital Lab to add you to a practice, then sign in again.
        </p>
      </div>
    );
  }

  return <Navigate to={`/orgs/${data.organizations[0]!.id}/overview`} replace />;
}
