import { cn } from '@/lib/utils';

/** The same four tones the Badge component uses, so a status looks the same wherever it appears. */
export type StatusTone = 'neutral' | 'positive' | 'caution' | 'critical';

const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ');

/*
 * Pills carry their status's own colour, matching the Badge on the rows they filter to. Before
 * this they were all the same grey, so "Blocked 3" and "Completed 9" looked alike and the reader
 * had to map pill to row colour themselves every time they scanned the list.
 *
 * Selection is a ring rather than a fill: filling a critical pill solid red reads as an alert
 * about the filter, which is not what choosing a filter means.
 */
const restClass: Record<StatusTone, string> = {
  neutral: 'bg-muted text-muted-foreground ring-transparent hover:ring-border',
  positive: 'bg-positive-soft text-positive-text ring-transparent hover:ring-positive/40',
  caution: 'bg-caution-soft text-caution-text ring-transparent hover:ring-caution/40',
  critical: 'bg-critical-soft text-critical-text ring-transparent hover:ring-critical/40',
};

const activeClass: Record<StatusTone, string> = {
  neutral: 'bg-muted text-foreground ring-muted-foreground/50',
  positive: 'bg-positive-soft text-positive-text ring-positive',
  caution: 'bg-caution-soft text-caution-text ring-caution',
  critical: 'bg-critical-soft text-critical-text ring-critical',
};

/**
 * A row of status pills with counts, "All" first.
 *
 * Only statuses that have at least one item are offered - a pill that always filters to nothing is
 * noise - except the one currently selected, which stays so it can be seen and cleared.
 */
export function StatusFilter<S extends string>({
  label,
  order,
  counts,
  tones,
  total,
  selected,
  onSelect,
}: {
  label: string;
  order: readonly S[];
  counts: Partial<Record<S, number>>;
  /** The tone each status carries on its row, so the filter and the list agree. */
  tones: Record<S, StatusTone>;
  total: number;
  selected: S | null;
  onSelect: (status: S | null) => void;
}) {
  const shown = order.filter((s) => (counts[s] ?? 0) > 0 || s === selected);

  const pill = (tone: StatusTone, active: boolean) =>
    cn(
      'inline-flex min-h-8 items-center gap-1.5 rounded-full px-3 text-xs font-medium',
      'ring-1 ring-inset transition-colors',
      // A two-pixel ring on the selected pill, so selection survives being read at a glance
      // alongside seven other pills that are already coloured.
      active ? cn('ring-2', activeClass[tone]) : restClass[tone],
    );

  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-1.5">
      <button
        type="button"
        aria-pressed={selected === null}
        onClick={() => onSelect(null)}
        className={cn(
          'inline-flex min-h-8 items-center gap-1.5 rounded-full px-3 text-xs font-medium',
          'ring-1 ring-inset transition-colors',
          selected === null
            ? 'bg-primary-soft text-primary-text ring-2 ring-primary'
            : 'bg-muted text-muted-foreground ring-transparent hover:ring-border',
        )}
      >
        All <span className="tabular-nums opacity-70">{total}</span>
      </button>
      {shown.map((s) => (
        <button
          key={s}
          type="button"
          aria-pressed={selected === s}
          onClick={() => onSelect(selected === s ? null : s)}
          className={pill(tones[s], selected === s)}
        >
          {pretty(s)} <span className="tabular-nums opacity-70">{counts[s] ?? 0}</span>
        </button>
      ))}
    </div>
  );
}
