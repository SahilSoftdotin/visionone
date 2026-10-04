import type { AuthProviderProps } from 'react-oidc-context';
import { WebStorageStateStore } from 'oidc-client-ts';

/**
 * Authorization code flow with PKCE against Keycloak.
 *
 * Tokens live in session storage and are renewed silently. Nothing vendor-specific is used, so
 * replacing Keycloak means changing this configuration and the backend's claim mapper.
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY,
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID,
  redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI,
  post_logout_redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI,
  response_type: 'code',
  scope: 'openid profile email',
  automaticSilentRenew: true,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

/**
 * Where an idle sign-out lands, and how the sign-in page knows to explain itself rather than
 * bouncing straight back to the provider. Covered by the realm's registered `{origin}/*` redirect
 * URI, so this needs no Keycloak change.
 */
export const IDLE_LOGOUT_RETURN = '/login?reason=idle';

/** Reads `?reason=idle` off the current URL. */
export function wasSignedOutForInactivity(search: string): boolean {
  return new URLSearchParams(search).get('reason') === 'idle';
}

/**
 * The one way out of the application.
 *
 * Extracted because sign-out now happens from two places - the header button and the idle timer -
 * and because neither of them used to clear the query cache. Navigating to the provider tears the
 * page down anyway, so this is belt and braces rather than a fix for an observed leak; it costs a
 * line and removes the question.
 */
export async function signOut(
  auth: { signoutRedirect: (args?: { post_logout_redirect_uri?: string }) => Promise<void> },
  queryClient: { clear: () => void },
  options?: { reason?: 'idle' },
): Promise<void> {
  queryClient.clear();
  if (options?.reason !== 'idle') {
    await auth.signoutRedirect();
    return;
  }
  await auth.signoutRedirect({
    post_logout_redirect_uri: new URL(
      IDLE_LOGOUT_RETURN,
      import.meta.env.VITE_OIDC_REDIRECT_URI,
    ).toString(),
  });
}
