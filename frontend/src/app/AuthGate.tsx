import { useEffect, type ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { setTokenProvider } from '@/lib/api';
import { AuthScreen, AuthSplash } from './AuthScreen';

/**
 * Authentication, mapped onto real URLs.
 *
 * Signing in is a redirect rather than a login form: VisionOne never sees a password, which is
 * the point of using an identity provider at all. The redirect starts when the caller presses the
 * button, so `/login` is a page the app owns rather than a flash before leaving for Keycloak.
 */

/** Wraps the routes: publishes the access token and holds the screen while the session resolves. */
export function AuthBootstrap({ children }: { children: ReactNode }) {
  const auth = useAuth();

  useEffect(() => {
    setTokenProvider(() => auth.user?.access_token);
  }, [auth.user]);

  // Returning from the identity provider, or restoring a stored session. Deciding anything here
  // would race the callback and bounce the caller back to /login with a valid code in hand.
  if (auth.isLoading || auth.activeNavigator) {
    return (
      <AuthSplash>
        <p className="text-sm text-muted-foreground">Signing you in&hellip;</p>
      </AuthSplash>
    );
  }

  return <>{children}</>;
}

/** Everything behind it requires a session; anyone without one is sent to /login. */
export function RequireAuth() {
  const auth = useAuth();
  const location = useLocation();

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  return <Outlet />;
}

/** The /login page itself. Already signed in? There is nothing to do here. */
export function LoginRoute() {
  const auth = useAuth();

  if (auth.isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <AuthScreen
      state={auth.error ? 'error' : 'idle'}
      message={auth.error?.message}
      onSignIn={() => void auth.signinRedirect()}
    />
  );
}
