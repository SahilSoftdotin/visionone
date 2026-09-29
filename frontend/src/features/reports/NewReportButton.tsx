import { useCallback, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ChevronDown, Plus } from 'lucide-react';
import { formatMonth } from '@/lib/format';
import { FloatingMenu } from '@/components/ui/FloatingMenu';
import type { ApiError } from '@/lib/api';
import type { ReportListResponse } from '@/lib/types';
import { useReportAdmin } from './useReports';

/**
 * Starting a report for a month that has none. Vision Digital Lab only.
 *
 * Offers this year's months up to the current one that do not yet have a report, newest first.
 * Starting one freezes its figures straight away; the narrative is written afterwards.
 */
export function NewReportButton({
  orgId,
  reports,
}: {
  orgId: string;
  reports: ReportListResponse['reports'];
}) {
  const { generate } = useReportAdmin(orgId);
  const [searchParams, setSearchParams] = useSearchParams();
  const [open, setOpen] = useState(false);
  const close = useCallback(() => setOpen(false), []);
  const wrapper = useRef<HTMLDivElement>(null);


  const existing = new Set(reports.map((r) => r.periodMonth.slice(0, 7)));
  const now = new Date();
  const available: string[] = [];
  for (let m = now.getMonth(); m >= 0; m -= 1) {
    const key = `${now.getFullYear()}-${String(m + 1).padStart(2, '0')}`;
    if (!existing.has(key)) available.push(key);
  }

  if (available.length === 0) return null;

  const start = (month: string) => {
    generate.mutate(month, {
      onSuccess: () => {
        const next = new URLSearchParams(searchParams);
        next.set('month', month);
        setSearchParams(next, { replace: true });
        setOpen(false);
      },
    });
  };

  return (
    <div className="relative" ref={wrapper}>
      <button
        type="button"
        disabled={generate.isPending}
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="inline-flex min-h-10 items-center gap-1.5 rounded-lg border border-border bg-card px-3 text-sm font-medium transition-colors hover:border-primary/40 hover:bg-primary-soft disabled:opacity-60"
      >
        <Plus className="h-4 w-4" aria-hidden />
        {generate.isPending ? 'Starting…' : 'New report'}
        <ChevronDown className="h-4 w-4 text-muted-foreground" aria-hidden />
      </button>

      <FloatingMenu anchorRef={wrapper} open={open} onClose={close}>
        <ul
          role="menu"
          aria-label="Start a report for"
          className="max-h-72 w-52 overflow-auto rounded-lg border border-border bg-card p-1 elev-lg"
        >
          {available.map((month) => (
            <li key={month}>
              <button
                type="button"
                role="menuitem"
                onClick={() => start(month)}
                className="w-full rounded-md px-3 py-2 text-left text-sm transition-colors hover:bg-muted"
              >
                {formatMonth(month)}
              </button>
            </li>
          ))}
        </ul>
      </FloatingMenu>

      {generate.error && (
        <p role="alert" className="absolute right-0 mt-1 w-64 text-xs text-critical">
          {(generate.error as ApiError).message}
        </p>
      )}
    </div>
  );
}
