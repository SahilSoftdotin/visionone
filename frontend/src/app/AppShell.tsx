import { useCallback, useState } from 'react';
import { Link, NavLink, Outlet, useParams } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { LogOut, Menu, X } from 'lucide-react';
import { CLIENT_NAVIGATION } from './navigation';
import { apiGet, queryKeys } from '@/lib/api';
import { signOut } from '@/lib/auth';
import { useIdleTimeout } from '@/lib/useIdleTimeout';
import { resolveIdlePolicy } from '@/lib/idlePolicy';
import { IdleWarningDialog } from '@/components/ui/IdleWarningDialog';
import { RouteErrorBoundary } from '@/components/ErrorBoundary';
import type { SessionResponse } from '@/lib/types';
import { ClientMark, ParentBrandLine, VisionOneMark } from '@/components/ui/Brand';
import { GlobalSearch } from '@/components/ui/GlobalSearch';
import { Notifications } from '@/components/ui/Notifications';
import { cn } from '@/lib/utils';

/**
 * Modernize's mini-sidebar shell: a narrow icon rail on the left, a white top bar, and content on
 * plain white.
 *
 * The rail shows icons with their labels beneath at the collapsed width, rather than icons alone.
 * Icon-only navigation forces people to learn six glyphs before they can use the product, and the
 * label costs ten pixels.
 */
export function AppShell() {
  const { orgId = '' } = useParams();
  const auth = useAuth();
  const queryClient = useQueryClient();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [idleDeadline, setIdleDeadline] = useState<number | null>(null);

  // Same key as OrganizationRouter, so this is a cache read rather than a second request.
  const { data: session } = useQuery({
    queryKey: queryKeys.session,
    queryFn: () => apiGet<SessionResponse>('/me'),
    staleTime: 5 * 60_000,
    // Same guard as OrganizationRouter: this shares the cache key, so an unauthenticated call
    // here would poison it for both.
    enabled: Boolean(auth.user?.access_token),
  });
  const organization = session?.organizations.find((candidate) => candidate.id === orgId);

  // Clients only, which is why this lives here rather than in AuthBootstrap: AppShell is the one
  // component that already knows which organization is open and therefore what role the caller
  // holds. Vision's own staff work from their own machines in long sessions; the client is the one
  // plausibly on a shared front-desk computer, and is who this protects.
  //
  // Keycloak cannot express the same rule. Session timeouts there are per-realm or per-client and
  // both accounts use the same client - and `ssoSessionIdleTimeout` would not bite anyway, because
  // automaticSilentRenew keeps refreshing the token and resetting the server's idle clock.
  const isClient = organization?.role === 'CLIENT_OWNER';

  // Served by the API rather than compiled in, so the figure can be changed with a restart. It is
  // undefined on the first render while /me is in flight, which resolveIdlePolicy treats as
  // "use the default" rather than as an error.
  const { idleMs, warnMs } = resolveIdlePolicy(session?.sessionPolicy);

  const endSession = useCallback(
    (reason?: 'idle') => {
      void signOut(auth, queryClient, reason ? { reason } : undefined);
    },
    [auth, queryClient],
  );

  const { reset: resetIdleTimer } = useIdleTimeout({
    enabled: isClient,
    idleMs,
    warnMs,
    onWarn: setIdleDeadline,
    onExpire: () => endSession('idle'),
    onReprieve: () => setIdleDeadline(null),
  });

  const rail = (
    <nav className="flex flex-col gap-1 px-3" aria-label="Main">
      {CLIENT_NAVIGATION.map(({ to, label, icon: Icon, ready }) => (
        <NavLink
          key={to}
          to={`/orgs/${orgId}/${to}`}
          onClick={() => setMobileOpen(false)}
          className={({ isActive }) =>
            cn(
              'group flex min-h-11 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors duration-150 lg:min-h-[68px] lg:flex-col lg:justify-center lg:gap-1.5 lg:px-2 lg:py-2.5',
              isActive
                ? 'bg-primary text-primary-foreground shadow-[0_2px_8px_-1px_hsl(224_100%_68%/0.45)]'
                : 'text-muted-foreground hover:bg-primary-soft hover:text-primary-text',
            )
          }
        >
          <Icon className="h-5 w-5 shrink-0" aria-hidden />
          <span className="text-center lg:text-[10px] lg:font-semibold lg:leading-tight">
            {label}
          </span>
          {!ready && (
            <span className="ml-auto rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase text-muted-foreground lg:ml-0">
              soon
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="app-sky min-h-screen lg:flex">
      {/* Left rail */}
      <aside
        className={cn(
          'shrink-0 border-border bg-card lg:sticky lg:top-0 lg:h-screen lg:w-[104px] lg:border-r',
          mobileOpen ? 'block border-b' : 'hidden lg:block',
        )}
      >
        <div className="flex h-full flex-col">
          <div className="hidden items-center justify-center py-5 lg:flex">
            {/* The mark is the way home, as it is on most products. */}
            <Link
              to={`/orgs/${orgId}/overview`}
              onClick={() => setMobileOpen(false)}
              aria-label="VisionOne overview"
              className="rounded-lg transition-transform duration-150 hover:scale-105 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              <img
                src="/brand/vision-digital-lab.svg"
                alt="Vision Digital Lab"
                className="h-9 w-9 rounded-lg"
              />
            </Link>
          </div>
          <div className="py-3 lg:py-0">{rail}</div>
          <div className="mt-auto hidden p-3 lg:block">
            <ParentBrandLine className="flex-col gap-1 px-1 text-center" />
          </div>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="sticky top-0 z-20 border-b border-border bg-card">
          <div className="flex h-[70px] items-center gap-3 px-4 sm:px-6">
            <button
              type="button"
              className="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-lg text-muted-foreground hover:bg-muted lg:hidden"
              aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'}
              aria-expanded={mobileOpen}
              onClick={() => setMobileOpen((open) => !open)}
            >
              {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>

            <Link
              to={`/orgs/${orgId}/overview`}
              className="lg:hidden"
              aria-label="VisionOne overview"
            >
              <VisionOneMark />
            </Link>

            {organization && (
              <ClientMark name={organization.name} className="hidden min-w-0 sm:flex" />
            )}

            <div className="ml-auto flex items-center gap-1.5">
              <GlobalSearch />
              <Notifications />

              <span className="mx-1 hidden h-6 w-px bg-border sm:block" aria-hidden />

              <span className="hidden text-sm font-semibold sm:inline">
                {auth.user?.profile.name ?? auth.user?.profile.preferred_username}
              </span>
              <button
                type="button"
                onClick={() => endSession()}
                className="ml-1 inline-flex min-h-10 items-center gap-1.5 rounded-lg bg-primary px-3.5 text-sm font-semibold text-primary-foreground transition-all duration-150 hover:brightness-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <LogOut className="h-4 w-4" aria-hidden />
                <span className="hidden sm:inline">Sign out</span>
              </button>
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-[1400px] px-4 py-6 sm:px-6">
          {/* Inside the shell on purpose: a screen that throws leaves the rail, the header and
              the organization switcher working, so the practice can move to another page instead
              of being stuck. */}
          <RouteErrorBoundary variant="screen">
            <Outlet />
          </RouteErrorBoundary>
        </main>
      </div>

      {idleDeadline !== null && (
        <IdleWarningDialog
          deadline={idleDeadline}
          onStaySignedIn={() => {
            setIdleDeadline(null);
            resetIdleTimer();
          }}
          onSignOutNow={() => endSession()}
        />
      )}
    </div>
  );
}
