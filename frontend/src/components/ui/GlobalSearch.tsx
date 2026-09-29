import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  BarChart3,
  CalendarDays,
  CornerDownLeft,
  FileText,
  LayoutDashboard,
  PhoneCall,
  Search,
  TrendingUp,
  Users,
  X,
  type LucideIcon,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  useShellCalendar,
  useShellContent,
  useShellGrowth,
  useShellLeads,
  useShellWork,
} from './useAppData';

/**
 * Search across every screen, over the same data the screens render.
 *
 * The index is built client-side from the screens' own queries - the same query keys, so the same
 * cache - rather than from a separate search service: at this size a linear scan is instant, and a
 * second source of truth would be a second thing to disagree with the dashboard. The queries run
 * only once the palette is opened, and a screen already visited costs nothing.
 *
 * Scope is the current month, which is what every screen opens on. Searching history belongs to a
 * server-side endpoint once there is enough history to need one.
 */

interface Hit {
  id: string;
  title: string;
  subtitle: string;
  group: string;
  to: string;
  icon: LucideIcon;
}

const lower = (v: string) => v.toLowerCase().replace(/_/g, ' ');

interface Sources {
  leads: ReturnType<typeof useShellLeads>['data'];
  work: ReturnType<typeof useShellWork>['data'];
  content: ReturnType<typeof useShellContent>['data'];
  calendar: ReturnType<typeof useShellCalendar>['data'];
  growth: ReturnType<typeof useShellGrowth>['data'];
}

function buildIndex(orgId: string, src: Sources): Hit[] {
  const at = (path: string) => `/orgs/${orgId}/${path}`;

  const screens: Hit[] = [
    {
      id: 's-overview',
      title: 'Overview',
      subtitle: 'KPIs, funnel and sources',
      group: 'Screens',
      to: at('overview'),
      icon: LayoutDashboard,
    },
    {
      id: 's-growth',
      title: 'Growth',
      subtitle: 'Budget, channels and campaigns',
      group: 'Screens',
      to: at('growth'),
      icon: TrendingUp,
    },
    {
      id: 's-leads',
      title: 'Leads',
      subtitle: 'Pipeline and response times',
      group: 'Screens',
      to: at('leads'),
      icon: Users,
    },
    {
      id: 's-front',
      title: 'Front Desk',
      subtitle: 'Calls and booked outcomes',
      group: 'Screens',
      to: at('front-desk'),
      icon: PhoneCall,
    },
    {
      id: 's-cal',
      title: 'Calendar',
      subtitle: 'Appointments by day',
      group: 'Screens',
      to: at('calendar'),
      icon: CalendarDays,
    },
    {
      id: 's-work',
      title: 'Work & Content',
      subtitle: 'What Vision is doing',
      group: 'Screens',
      to: at('work'),
      icon: FileText,
    },
    {
      id: 's-reports',
      title: 'Reports',
      subtitle: 'Monthly executive report',
      group: 'Screens',
      to: at('reports'),
      icon: BarChart3,
    },
  ];

  const leads: Hit[] = (src.leads?.leads ?? []).map((l) => ({
    id: `lead-${l.id}`,
    title: `${l.name} · ${l.reference}`,
    subtitle: `${l.serviceInterest} · ${l.source} · ${lower(l.status)}`,
    group: 'Leads',
    to: at('leads'),
    icon: Users,
  }));

  const appointments: Hit[] = (src.calendar?.days ?? []).flatMap((d) =>
    d.appointments.map((a) => ({
      id: `appt-${a.id}`,
      title: a.label,
      subtitle: `${d.date} ${a.time} · ${a.serviceCategory ?? 'appointment'} · ${lower(a.status)}`,
      group: 'Appointments',
      to: `${at('calendar')}?month=${d.date.slice(0, 7)}`,
      icon: CalendarDays,
    })),
  );

  const work: Hit[] = (src.work?.items ?? []).map((w) => ({
    id: `work-${w.id}`,
    title: w.title,
    subtitle: `${lower(w.category)} · ${lower(w.status)}`,
    group: 'Work',
    to: at('work'),
    icon: FileText,
  }));

  const content: Hit[] = (src.content?.items ?? []).map((c) => ({
    id: `content-${c.id}`,
    title: c.title,
    subtitle: `${lower(c.contentType)} · ${lower(c.status)}`,
    group: 'Content',
    to: at('work'),
    icon: FileText,
  }));

  const growth: Hit[] = [
    ...(src.growth?.campaigns ?? []).map((c) => ({
      id: `camp-${c.id}`,
      title: c.name,
      subtitle: `campaign · ${lower(c.status)}`,
      group: 'Growth',
      to: at('growth'),
      icon: TrendingUp,
    })),
    ...(src.growth?.allocations ?? []).map((a) => ({
      id: `chan-${a.channelCode}`,
      title: a.displayName,
      subtitle: `channel · ${a.leads} leads · ${a.booked} booked`,
      group: 'Growth',
      to: at('growth'),
      icon: TrendingUp,
    })),
  ];

  return [...screens, ...leads, ...appointments, ...work, ...content, ...growth];
}

export function GlobalSearch() {
  const { orgId = '' } = useParams();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  // Held back until the palette opens; after that they stay cached with the screens.
  const leads = useShellLeads(orgId, open);
  const work = useShellWork(orgId, open);
  const content = useShellContent(orgId, open);
  const calendar = useShellCalendar(orgId, open);
  const growth = useShellGrowth(orgId, open);

  const index = useMemo(
    () =>
      buildIndex(orgId, {
        leads: leads.data,
        work: work.data,
        content: content.data,
        calendar: calendar.data,
        growth: growth.data,
      }),
    [orgId, leads.data, work.data, content.data, calendar.data, growth.data],
  );

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return index.filter((h) => h.group === 'Screens');
    return index
      .filter((h) => `${h.title} ${h.subtitle} ${h.group}`.toLowerCase().includes(q))
      .slice(0, 20);
  }, [query, index]);

  // Ctrl/Cmd-K opens it, Escape closes it. A search nobody can reach from the keyboard is a
  // search most people never use.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setOpen(true);
      }
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    if (open) {
      setQuery('');
      setActive(0);
      // Focus after paint, or the field is not there to receive it yet.
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  const go = (hit: Hit) => {
    setOpen(false);
    navigate(hit.to);
  };

  const onInputKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i) => Math.min(i + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const hit = results[active];
      if (hit) go(hit);
    }
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Search"
        className="grid h-10 w-10 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
      >
        <Search className="h-[18px] w-[18px]" aria-hidden />
      </button>

      {open && (
        <div
          className="fixed inset-0 z-50 flex items-start justify-center bg-foreground/20 p-4 pt-[12vh]"
          onClick={() => setOpen(false)}
          role="presentation"
        >
          <div
            className="w-full max-w-xl overflow-hidden rounded-lg border border-border bg-card elev-lg"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label="Search VisionOne"
          >
            <div className="flex items-center gap-2 border-b border-border px-4">
              <Search className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              <input
                ref={inputRef}
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setActive(0);
                }}
                onKeyDown={onInputKey}
                placeholder="Search leads, appointments, work, content, channels…"
                className="h-12 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Close search"
                className="grid h-8 w-8 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-muted"
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
            </div>

            <ul className="max-h-[50vh] overflow-auto p-1.5" role="listbox">
              {results.length === 0 && (
                <li className="px-3 py-8 text-center text-sm text-muted-foreground">
                  Nothing matches “{query}”.
                </li>
              )}
              {results.map((hit, i) => {
                const Icon = hit.icon;
                return (
                  <li key={hit.id}>
                    <button
                      type="button"
                      role="option"
                      aria-selected={i === active}
                      onMouseEnter={() => setActive(i)}
                      onClick={() => go(hit)}
                      className={cn(
                        'flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left transition-colors',
                        i === active ? 'bg-primary-soft' : 'hover:bg-muted',
                      )}
                    >
                      <Icon
                        className={cn(
                          'h-4 w-4 shrink-0',
                          i === active ? 'text-primary-text' : 'text-muted-foreground',
                        )}
                        aria-hidden
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-semibold">{hit.title}</span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {hit.subtitle}
                        </span>
                      </span>
                      <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                        {hit.group}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>

            <p className="flex items-center gap-2 border-t border-border px-4 py-2 text-[11px] text-muted-foreground">
              <CornerDownLeft className="h-3 w-3" aria-hidden />
              Enter to open · ↑↓ to move · Esc to close
            </p>
          </div>
        </div>
      )}
    </>
  );
}
