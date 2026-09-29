import { useCallback, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { FloatingMenu } from '@/components/ui/FloatingMenu';
import { formatMonth } from '@/lib/format';
import type { ReportListResponse } from '@/lib/types';

/**
 * Period selector for the Reports screen.
 *
 * Deliberately not the shared MonthPicker. That one offers every month of the year, which is right
 * for a screen reading live data and wrong here: a month with no report is not a period you can
 * look at, and offering it invites a dead end. This lists the reports that exist, newest first, and
 * the newest is the default so no parameter means "the latest report".
 */
export function ReportPicker({
  reports,
  selectedMonth,
}: {
  reports: ReportListResponse['reports'];
  selectedMonth: string;
}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [open, setOpen] = useState(false);
  const close = useCallback(() => setOpen(false), []);
  const wrapper = useRef<HTMLDivElement>(null);


  if (reports.length < 2) return null;

  const latestMonth = reports[0]?.periodMonth.slice(0, 7);

  const choose = (month: string) => {
    const next = new URLSearchParams(searchParams);
    if (month === latestMonth) {
      // The default needs no parameter, so a shared link stays clean.
      next.delete('month');
    } else {
      next.set('month', month);
    }
    setSearchParams(next, { replace: true });
    setOpen(false);
  };

  return (
    <div className="relative" ref={wrapper}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        className="inline-flex min-h-10 items-center gap-1.5 rounded-lg border border-border bg-card px-3 text-sm font-medium transition-colors hover:border-primary/40 hover:bg-primary-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        {formatMonth(selectedMonth)}
        <ChevronDown
          className={cn('h-4 w-4 text-muted-foreground transition-transform', open && 'rotate-180')}
          aria-hidden
        />
      </button>

      <FloatingMenu anchorRef={wrapper} open={open} onClose={close}>
        <ul
          role="listbox"
          aria-label="Choose a report"
          className="max-h-72 w-56 overflow-auto rounded-lg border border-border bg-card p-1 elev-lg"
        >
          {reports.map((r) => {
            const month = r.periodMonth.slice(0, 7);
            const active = month === selectedMonth;
            return (
              <li key={r.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={active}
                  onClick={() => choose(month)}
                  className={cn(
                    'flex w-full items-center justify-between gap-2 rounded-md px-3 py-2 text-left text-sm transition-colors',
                    active
                      ? 'bg-primary-soft font-semibold text-primary-text'
                      : 'hover:bg-muted hover:text-foreground',
                  )}
                >
                  <span>
                    {formatMonth(month)}
                    {r.status !== 'SHARED' && (
                      <span className="ml-1.5 text-xs font-normal text-muted-foreground">
                        {r.status.toLowerCase()}
                      </span>
                    )}
                  </span>
                  {active && <Check className="h-4 w-4 shrink-0" aria-hidden />}
                </button>
              </li>
            );
          })}
        </ul>
      </FloatingMenu>
    </div>
  );
}
