import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Bell,
  FileText,
  PhoneMissed,
  TriangleAlert,
  UserPlus,
  type LucideIcon,
} from 'lucide-react';
import { leadsDemo } from '@/lib/demoData';
import { contentDemo, frontDeskDemo, workDemo } from '@/lib/demoOperations';
import { cn } from '@/lib/utils';

/**
 * What needs the practice, derived rather than stored.
 *
 * Every item is computed from the same fixtures the screens read, so the badge cannot claim
 * something the screen then fails to show. Each one links to where it can be acted on - a
 * notification that only announces is a notification people learn to ignore.
 */

type Tone = 'primary' | 'caution' | 'critical';

interface Note {
  id: string;
  title: string;
  detail: string;
  to: string;
  icon: LucideIcon;
  tone: Tone;
}

const toneClass: Record<Tone, string> = {
  primary: 'bg-primary-soft text-primary-text',
  caution: 'bg-caution-soft text-caution-text',
  critical: 'bg-critical-soft text-critical-text',
};

function build(orgId: string): Note[] {
  const at = (p: string) => `/orgs/${orgId}/${p}`;
  const out: Note[] = [];

  const awaitingReview = contentDemo.filter((c) => c.status === 'CLIENT_REVIEW');
  if (awaitingReview.length > 0) {
    out.push({
      id: 'content-review',
      title: `${awaitingReview.length} item${awaitingReview.length === 1 ? '' : 's'} need your review`,
      detail: awaitingReview.map((c) => c.title).join(' · '),
      to: at('work'),
      icon: FileText,
      tone: 'caution',
    });
  }

  const waiting = workDemo.filter((w) => w.status === 'WAITING_FOR_CLIENT');
  if (waiting.length > 0) {
    out.push({
      id: 'work-waiting',
      title: `${waiting.length} task${waiting.length === 1 ? '' : 's'} blocked on the practice`,
      detail: waiting.map((w) => w.clientDependency ?? w.title).join(' · '),
      to: at('work'),
      icon: TriangleAlert,
      tone: 'caution',
    });
  }

  const blocked = workDemo.filter((w) => w.status === 'BLOCKED');
  if (blocked.length > 0) {
    out.push({
      id: 'work-blocked',
      title: `${blocked.length} task${blocked.length === 1 ? '' : 's'} blocked`,
      detail: blocked.map((w) => w.title).join(' · '),
      to: at('work'),
      icon: TriangleAlert,
      tone: 'critical',
    });
  }

  const unassigned = leadsDemo.filter((l) => l.status === 'NEW');
  if (unassigned.length > 0) {
    out.push({
      id: 'leads-new',
      title: `${unassigned.length} new lead${unassigned.length === 1 ? '' : 's'} unassigned`,
      detail: unassigned.map((l) => `${l.name} · ${l.source}`).join(' · '),
      to: at('leads'),
      icon: UserPlus,
      tone: 'primary',
    });
  }

  const never = leadsDemo.filter((l) => l.responseMinutes === null && l.status !== 'DUPLICATE');
  if (never.length > 0) {
    out.push({
      id: 'leads-noresponse',
      title: `${never.length} lead${never.length === 1 ? '' : 's'} never answered`,
      detail: 'No first response recorded',
      to: at('leads'),
      icon: TriangleAlert,
      tone: 'critical',
    });
  }

  if (frontDeskDemo.missed > 0) {
    out.push({
      id: 'calls-missed',
      title: `${frontDeskDemo.missed} missed calls this month`,
      detail: `${frontDeskDemo.afterHours} calls arrived outside published hours`,
      to: at('front-desk'),
      icon: PhoneMissed,
      tone: 'critical',
    });
  }

  return out;
}

export function Notifications() {
  const { orgId = '' } = useParams();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const wrapper = useRef<HTMLDivElement>(null);

  const notes = build(orgId);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (wrapper.current && !wrapper.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="relative" ref={wrapper}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={`Notifications${notes.length ? `, ${notes.length} needing attention` : ''}`}
        aria-expanded={open}
        className="relative grid h-10 w-10 place-items-center rounded-full text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
      >
        <Bell className="h-[18px] w-[18px]" aria-hidden />
        {notes.length > 0 && (
          <span className="absolute right-1.5 top-1.5 grid h-4 min-w-4 place-items-center rounded-full bg-critical px-1 text-[9px] font-bold text-white ring-2 ring-card">
            {notes.length}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-1.5 w-80 overflow-hidden rounded-lg border border-border bg-card elev-lg">
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <p className="text-sm font-bold">Needs attention</p>
            <span className="text-xs text-muted-foreground">{notes.length}</span>
          </div>

          {notes.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-muted-foreground">
              Nothing needs you right now.
            </p>
          ) : (
            <ul className="max-h-80 overflow-auto">
              {notes.map((n) => {
                const Icon = n.icon;
                return (
                  <li key={n.id} className="border-b border-border last:border-0">
                    <button
                      type="button"
                      onClick={() => {
                        setOpen(false);
                        navigate(n.to);
                      }}
                      className="flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-muted"
                    >
                      <span
                        className={cn(
                          'grid h-8 w-8 shrink-0 place-items-center rounded-lg',
                          toneClass[n.tone],
                        )}
                        aria-hidden
                      >
                        <Icon className="h-4 w-4" />
                      </span>
                      <span className="min-w-0">
                        <span className="block text-sm font-semibold">{n.title}</span>
                        <span className="line-clamp-2 block text-xs text-muted-foreground">
                          {n.detail}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}

          <p className="border-t border-border px-4 py-2 text-[11px] text-muted-foreground">
            Derived from this month&rsquo;s demo data.
          </p>
        </div>
      )}
    </div>
  );
}
