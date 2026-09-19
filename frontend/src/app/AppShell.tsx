import { useState } from 'react';
import { NavLink, Outlet, useParams } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Menu, X } from 'lucide-react';
import { CLIENT_NAVIGATION } from './navigation';
import { cn } from '@/lib/utils';

export function AppShell() {
  const { orgId = '' } = useParams();
  const auth = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

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
          <span className="flex items-center gap-2 text-base font-semibold tracking-[-0.01em]">
            <span
              aria-hidden
              className="h-5 w-5 rounded-md bg-gradient-to-br from-primary to-primary/70 elev-sm"
            />
            Vision<span className="text-primary">One</span>
          </span>
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
          {nav}
        </aside>

        <main className="min-w-0 flex-1 px-4 py-5 sm:px-6 sm:py-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
