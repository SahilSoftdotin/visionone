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
              // min-h-11 = 44px: the touch-target floor. At py-2 these were ~36px.
              'relative flex min-h-11 items-center gap-2.5 rounded-md px-3 py-2 text-sm transition-all duration-150',
              isActive
                ? 'bg-primary-soft font-semibold text-primary before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:rounded-full before:bg-primary'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )
          }
        >
          <Icon className="h-4 w-4 shrink-0" aria-hidden />
          <span>{label}</span>
          {!ready && (
            <span className="ml-auto rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
              soon
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="app-sky min-h-screen">
      <header className="sticky top-0 z-20 border-b border-white/60 bg-card/60 backdrop-blur-xl">
        <div className="flex h-14 items-center gap-3 px-4 sm:px-6">
          <button
            type="button"
            className="-ml-2 grid h-11 w-11 shrink-0 place-items-center rounded-md hover:bg-muted lg:hidden"
            aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'}
            aria-expanded={mobileOpen}
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

      <div className="mx-auto flex w-full max-w-[1500px] gap-5 px-3 pb-6 pt-4 sm:px-5">
        {/* The rail floats on the sky rather than being carved out of it by a border. */}
        <aside
          className={cn(
            'w-full shrink-0 rounded-lg glass p-3 lg:sticky lg:top-[4.5rem] lg:block lg:h-[calc(100vh-6rem)] lg:w-56',
            mobileOpen ? 'block' : 'hidden lg:block',
          )}
        >
          <div className="flex h-full flex-col">
            {nav}
            <div className="mt-6 border-t border-border/60 pt-2 lg:mt-auto">
              <ParentBrandLine />
            </div>
          </div>
        </aside>

        <main className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
