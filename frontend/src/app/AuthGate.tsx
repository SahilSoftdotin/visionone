import { useEffect, type ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { setTokenProvider } from '@/lib/api';
import { AuthScreen, AuthSplash } from './AuthScreen';

/**
 * Resolves the session before anything renders.
 *
 * Signing in is a redirect rather than a login form: VisionOne never sees a password, which is
 * the point of using an identity provider at all. The redirect is started by the caller pressing
 * the button rather than automatically, so the sign-in screen is a real page - it states who
 * built this and for whom before handing over to Keycloak.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth();

  useEffect(() => {
    setTokenProvider(() => auth.user?.access_token);
  }, [auth.user]);

  if (auth.isAuthenticated) {
    return <>{children}</>;
  }

  // Returning from the identity provider, or restoring a session: no decision to offer yet.
  if (auth.activeNavigator || auth.isLoading) {
    return (
      <AuthSplash>
        <p className="text-sm text-muted-foreground">Signing you in&hellip;</p>
      </AuthSplash>
    );
  }

  return (
    <AuthScreen
      state={auth.error ? 'error' : 'idle'}
      message={auth.error?.message}
      onSignIn={() => void auth.signinRedirect()}
    />
  );
}
