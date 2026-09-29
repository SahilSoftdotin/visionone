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
import { cn } from '@/lib/utils';
import type { ContentListResponse, LeadListResponse, WorkListResponse } from '@/lib/types';
import { useShellContent, useShellFrontDesk, useShellLeads, useShellWork } from './useAppData';

/**
 * What needs the practice, derived rather than stored.
 *
 * Every item is computed from the same queries the screens read - the same query keys, so the same
 * cache - which means the badge cannot claim something the screen then fails to show. Each one
 * links to where it can be acted on; a notification that only announces is one people learn to
 * ignore. Nothing is shown for a source that has not loaded: a bell that guesses and then corrects
 * itself teaches people not to trust the number.
 */

type Tone = 'primary' | 'caution' | 'critical';

interface Note {
  id: string;
  title: string;
  detail: string;
  /** Category filter only. Shareable and safe to log. */
  to: string;
  /**
   * The single row this is about, when there is exactly one. Travels in history state, never in
   * the URL - see useRowFocus for why a lead or call id must not be in an address bar.
   */
  focus?: string;
  icon: LucideIcon;
  tone: Tone;
}

const toneClass: Record<Tone, string> = {
  primary: 'bg-primary-soft text-primary-text',
  caution: 'bg-caution-soft text-caution-text',
  critical: 'bg-critical-soft text-critical-text',
};

function build(
  orgId: string,
  leads: LeadListResponse | undefined,
  work: WorkListResponse | undefined,
  content: ContentListResponse | undefined,
  frontDesk:
    | {
        summary: { missed: number; afterHours: number };
        outcomeCounts: { outcome: string; count: number }[];
      }
    | undefined,
): Note[] {
  const at = (p: string) => `/orgs/${orgId}/${p}`;
  /**
   * Where a notification lands: the status, which filters the list to what the notification is
   * about. Only the category goes in the URL. The row, when there is exactly one, is returned
   * separately and travels in history state.
   */
  const filtered = (path: string, param: string, status: string) =>
    `${at(path)}?${new URLSearchParams({ [param]: status }).toString()}`;

  /** The one row this notification is about, or undefined when it covers several. */
  const only = (ids: string[]) => (ids.length === 1 ? ids[0] : undefined);
  const plural = (n: number, one: string, many: string) => (n === 1 ? one : many);
  const out: Note[] = [];

  const awaitingReview = (content?.items ?? []).filter((c) => c.status === 'CLIENT_REVIEW');
  if (awaitingReview.length > 0) {
    out.push({
      id: 'content-review',
      title: `${awaitingReview.length} ${plural(awaitingReview.length, 'item', 'items')} need your review`,
      detail: awaitingReview.map((c) => c.title).join(' · '),
      to: filtered('work', 'content', 'CLIENT_REVIEW'),
      focus: only(awaitingReview.map((c) => c.id)),
      icon: FileText,
      tone: 'caution',
    });
  }

  const waiting = (work?.items ?? []).filter((w) => w.status === 'WAITING_FOR_CLIENT');
  if (waiting.length > 0) {
    out.push({
      id: 'work-waiting',
      title: `${waiting.length} ${plural(waiting.length, 'task', 'tasks')} waiting on the practice`,
      detail: waiting.map((w) => w.title).join(' · '),
      to: filtered('work', 'work', 'WAITING_FOR_CLIENT'),
      focus: only(waiting.map((w) => w.id)),
      icon: TriangleAlert,
      tone: 'caution',
    });
  }

  const blocked = (work?.items ?? []).filter((w) => w.status === 'BLOCKED');
  if (blocked.length > 0) {
    out.push({
      id: 'work-blocked',
      title: `${blocked.length} ${plural(blocked.length, 'task', 'tasks')} blocked`,
      detail: blocked.map((w) => w.title).join(' · '),
      to: filtered('work', 'work', 'BLOCKED'),
      focus: only(blocked.map((w) => w.id)),
      icon: TriangleAlert,
      tone: 'critical',
    });
  }

  const unassigned = (leads?.leads ?? []).filter((l) => l.status === 'NEW');
  if (unassigned.length > 0) {
    out.push({
      id: 'leads-new',
      title: `${unassigned.length} new ${plural(unassigned.length, 'lead', 'leads')} unassigned`,
      detail: unassigned.map((l) => `${l.name} · ${l.source}`).join(' · '),
      to: filtered('leads', 'status', 'NEW'),
      focus: only(unassigned.map((l) => l.id)),
      icon: UserPlus,
      tone: 'primary',
    });
  }

  const never = (leads?.leads ?? []).filter(
    (l) => l.responseMinutes === null && l.status !== 'DUPLICATE',
  );
  if (never.length > 0) {
    out.push({
      id: 'leads-noresponse',
      title: `${never.length} ${plural(never.length, 'lead', 'leads')} never answered`,
      detail: 'No first response recorded',
      // No status describes "never answered", so this lands on the unfiltered list.
      to: at('leads'),
      focus: only(never.map((l) => l.id)),
      icon: TriangleAlert,
      tone: 'critical',
    });
  }

  /*
   * Two different numbers live here and must not be confused. summary.missed counts every call
   * nobody picked up, which is the MISSED outcome plus VOICEMAIL, and is what the "Unanswered"
   * tile shows. This notification links to the MISSED filter, so it counts and names MISSED -
   * saying "18" while landing on a list of 12 is how a reader stops believing the tray.
   */
  const rangOut = frontDesk?.outcomeCounts.find((o) => o.outcome === 'MISSED')?.count ?? 0;
  if (rangOut > 0) {
    const voicemail = frontDesk?.outcomeCounts.find((o) => o.outcome === 'VOICEMAIL')?.count ?? 0;
    out.push({
      id: 'calls-missed',
      title: `${rangOut} ${plural(rangOut, 'call', 'calls')} rang out this month`,
      detail: voicemail > 0
        ? `${voicemail} more went to voicemail · ${frontDesk?.summary.afterHours ?? 0} arrived outside published hours`
        : `${frontDesk?.summary.afterHours ?? 0} calls arrived outside published hours`,
      // A count, never one call, so there is no row to name - only the outcome to filter by.
      to: filtered('front-desk', 'outcome', 'MISSED'),
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

  const leads = useShellLeads(orgId);
  const work = useShellWork(orgId);
  const content = useShellContent(orgId);
  const frontDesk = useShellFrontDesk(orgId);

  const notes = build(orgId, leads.data, work.data, content.data, frontDesk.data);

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
                        // The row id rides in history state, so it never reaches the address bar or a log.
                        navigate(n.to, n.focus ? { state: { focus: n.focus } } : undefined);
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
            Derived from this month&rsquo;s data, not stored.
          </p>
        </div>
      )}
    </div>
  );
}
