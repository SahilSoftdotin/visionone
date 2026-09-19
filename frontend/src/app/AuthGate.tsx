import { useEffect, type ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { setTokenProvider } from '@/lib/api';

/**
 * Resolves the session before anything renders.
 *
 * Signing in is a redirect rather than a login form: VisionOne never sees a password, which is
 * the point of using an identity provider at all.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth();

  useEffect(() => {
    setTokenProvider(() => auth.user?.access_token);
  }, [auth.user]);

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator && !auth.error) {
      void auth.signinRedirect();
    }
  }, [auth]);

  if (auth.error) {
    return (
      <Centered>
        <p className="text-sm font-medium">Sign-in failed</p>
        <p className="mt-1 text-sm text-muted-foreground">{auth.error.message}</p>
        <button
          type="button"
          onClick={() => void auth.signinRedirect()}
          className="mt-4 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
        >
          Try again
        </button>
      </Centered>
    );
  }

  if (auth.isLoading || !auth.isAuthenticated) {
    return (
      <Centered>
        <p className="text-sm text-muted-foreground">Signing you in&hellip;</p>
      </Centered>
    );
  }

  return <>{children}</>;
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="max-w-sm text-center">
        <p className="mb-4 text-lg font-semibold tracking-tight">
          Vision<span className="text-primary">One</span>
        </p>
        {children}
      </div>
    </div>
  );
}
