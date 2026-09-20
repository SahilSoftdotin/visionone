import { useEffect, useRef } from 'react';
import { Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import { ApiError, apiGet, queryKeys } from '@/lib/api';
import type { SessionResponse } from '@/lib/types';

/**
 * Sends the caller to their organization.
 *
 * The organization id is in the URL from day one, matching the API, so a second tenant needs no
 * routing change.
 */
export function OrganizationRouter() {
  const auth = useAuth();
  const token = auth.user?.access_token;
  const renewing = useRef(false);

  const { data, isLoading, error } = useQuery({
    queryKey: queryKeys.session,
    queryFn: () => apiGet<SessionResponse>('/me'),
    staleTime: 5 * 60_000,
    // Belt and braces alongside the render-time token: never ask until there is one to ask with.
    enabled: Boolean(token),
    // An expired token is not worth retrying; it needs a new one.
    retry: (count, err) => !(err instanceof ApiError && err.isUnauthenticated) && count < 1,
  });

  // Only a session we actually held can expire. Without this, a 401 from any other cause
  // restarts sign-in, and the loop is indistinguishable from a broken login.
  const expired = Boolean(token) && error instanceof ApiError && error.isUnauthenticated;

  // Renew once. A ref, not state, so a re-render cannot fire a second redirect.
  useEffect(() => {
    if (!expired || renewing.current) return;
    renewing.current = true;
    void auth.signinRedirect();
  }, [expired, auth]);

  if (!token || isLoading || expired) {
    return <p className="p-6 text-sm text-muted-foreground">Loading your practice&hellip;</p>;
  }

  if (error || !data) {
    return (
      <div className="p-6">
        <p className="text-sm font-medium">We could not load your practice</p>
        <p className="mt-1 text-sm text-muted-foreground">
          {error instanceof ApiError ? error.message : 'The service did not respond.'} Try again in
          a moment, or contact Vision Digital Lab if it persists.
        </p>
      </div>
    );
  }

  if (data.organizations.length === 0) {
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
