import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Period selector for a screen that reads `?month=YYYY-MM`.
 *
 * Offers January to the current month of the current year and nothing else. A future month has no
 * data by definition, and a previous year's would imply history this deployment does not hold.
 * The current month is the default, so arriving at the screen with no parameter is the same as
 * choosing today.
 */

export function currentMonthKey(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function monthsThisYear(): { key: string; label: string }[] {
  const now = new Date();
  const year = now.getFullYear();
  const out: { key: string; label: string }[] = [];
  for (let m = 0; m <= now.getMonth(); m += 1) {
    const key = `${year}-${String(m + 1).padStart(2, '0')}`;
    const label = new Date(year, m, 1).toLocaleDateString(undefined, {
      month: 'long',
      year: 'numeric',
    });
    out.push({ key, label });
  }
  // Most recent first: the month someone wants is nearly always the latest one.
  return out.reverse();
}

export function MonthPicker() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [open, setOpen] = useState(false);
  const wrapper = useRef<HTMLDivElement>(null);

  const options = monthsThisYear();
  const selected = searchParams.get('month') ?? currentMonthKey();
  const label = options.find((o) => o.key === selected)?.label ?? selected;

  // A menu that stays open after a click elsewhere is a menu people fight with.
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

  const choose = (key: string) => {
    const next = new URLSearchParams(searchParams);
    if (key === currentMonthKey()) {
      // The default needs no parameter, so a shared link stays clean.
      next.delete('month');
    } else {
      next.set('month', key);
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
        {label}
        <ChevronDown
          className={cn('h-4 w-4 text-muted-foreground transition-transform', open && 'rotate-180')}
          aria-hidden
        />
      </button>

      {open && (
        <ul
          role="listbox"
          aria-label="Choose a month"
          className="absolute right-0 z-30 mt-1.5 max-h-72 w-48 overflow-auto rounded-lg border border-border bg-card p-1 elev-lg"
        >
          {options.map((o) => {
            const active = o.key === selected;
            return (
              <li key={o.key}>
                <button
                  type="button"
                  role="option"
                  aria-selected={active}
                  onClick={() => choose(o.key)}
                  className={cn(
                    'flex w-full items-center justify-between gap-2 rounded-md px-3 py-2 text-left text-sm transition-colors',
                    active
                      ? 'bg-primary-soft font-semibold text-primary-text'
                      : 'hover:bg-muted hover:text-foreground',
                  )}
                >
                  {o.label}
                  {active && <Check className="h-4 w-4" aria-hidden />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
