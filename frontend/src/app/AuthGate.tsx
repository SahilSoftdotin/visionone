import { useEffect, useRef, type ReactNode } from 'react';
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

/**
 * The /login page.
 *
 * It hands straight over to the identity provider rather than asking for a click first. The
 * provider's own page now carries the same design and the actual credential fields, so stopping
 * here showed two near-identical screens in a row and the button appeared to do nothing.
 *
 * The screen still renders, and is what a caller sees if the redirect fails or is slow, and it is
 * where a sign-in error comes back to - with the retry under their control rather than looping.
 */
export function LoginRoute() {
  const auth = useAuth();
  const started = useRef(false);

  useEffect(() => {
    if (auth.isAuthenticated || auth.error || started.current) return;
    started.current = true;
    void auth.signinRedirect();
  }, [auth]);

  if (auth.isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <AuthScreen
      state={auth.error ? 'error' : 'working'}
      message={auth.error?.message}
      onSignIn={() => {
        started.current = true;
        void auth.signinRedirect();
      }}
    />
  );
}
