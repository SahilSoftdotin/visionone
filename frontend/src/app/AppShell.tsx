import { useState } from 'react';
import { NavLink, Outlet, useParams } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { useQuery } from '@tanstack/react-query';
import { Menu, X } from 'lucide-react';
import { CLIENT_NAVIGATION } from './navigation';
import { apiGet, queryKeys } from '@/lib/api';
import type { SessionResponse } from '@/lib/types';
import { ClientMark, ParentBrandLine, VisionOneMark } from '@/components/ui/Brand';
import { cn } from '@/lib/utils';

export function AppShell() {
  const { orgId = '' } = useParams();
  const auth = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  // Same key as OrganizationRouter, so this is a cache read rather than a second request.
  const { data: session } = useQuery({
    queryKey: queryKeys.session,
    queryFn: () => apiGet<SessionResponse>('/me'),
    staleTime: 5 * 60_000,
  });
  const organization = session?.organizations.find((candidate) => candidate.id === orgId);

  const nav = (
    <nav className="space-y-0.5" aria-label="Main">
      {CLIENT_NAVIGATION.map(({ to, label, icon: Icon, ready }) => (
        <NavLink
          key={to}
          to={`/orgs/${orgId}/${to}`}
          onClick={() => setMobileOpen(false)}
          className={({ isActive }) =>
            cn(
              'relative flex items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-all duration-150',
              isActive
                ? 'bg-primary-soft font-semibold text-primary before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:rounded-full before:bg-primary'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )
          }
        >
          <Icon className="h-4 w-4 shrink-0" aria-hidden />
          <span>{label}</span>
          {!ready && (
            <span className="ml-auto rounded-full bg-muted px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
              soon
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 border-b border-border bg-card/80 backdrop-blur-md elev-sm">
        <div className="flex h-14 items-center gap-3 px-4 sm:px-6">
          <button
            type="button"
            className="lg:hidden"
            aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'}
            onClick={() => setMobileOpen((open) => !open)}
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
          <VisionOneMark />

          {organization && (
            <>
              {/* The platform and the practice, shown as a pairing rather than a breadcrumb. */}
              <span aria-hidden className="hidden h-5 w-px bg-border sm:block" />
              <ClientMark name={organization.name} className="hidden min-w-0 sm:flex" />
            </>
          )}

          <div className="ml-auto flex items-center gap-3">
            <span className="hidden text-sm font-medium text-muted-foreground sm:inline">
              {auth.user?.profile.name ?? auth.user?.profile.preferred_username}
            </span>
            <button
              type="button"
              onClick={() => void auth.signoutRedirect()}
              className="rounded-md border border-border px-2.5 py-1.5 text-sm font-medium transition-colors hover:border-primary/30 hover:bg-primary-soft hover:text-primary"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-[1400px]">
        <aside
          className={cn(
            'w-full shrink-0 border-b border-border px-4 py-3 lg:sticky lg:top-14 lg:block lg:h-[calc(100vh-3.5rem)] lg:w-56 lg:border-b-0 lg:border-r lg:px-3 lg:py-5',
            mobileOpen ? 'block' : 'hidden lg:block',
          )}
        >
          <div className="flex h-full flex-col">
            {nav}
            <div className="mt-6 border-t border-border pt-3 lg:mt-auto">
              <ParentBrandLine />
            </div>
          </div>
        </aside>

        <main className="min-w-0 flex-1 px-4 py-5 sm:px-6 sm:py-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
