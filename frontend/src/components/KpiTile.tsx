import { TrendingDown, TrendingUp, type LucideIcon } from 'lucide-react';
import { delta, EM_DASH } from '@/lib/format';
import { cn } from '@/lib/utils';

interface Props {
  label: string;
  value: string;
  current?: number | null;
  prior?: number | null;
  /** True when a rise is bad news, as with cost per lead. */
  invertDirection?: boolean;
  hint?: string;
  /** Anchors the tile so the grid is scannable by shape, not only by reading every label. */
  icon?: LucideIcon;
}

export function KpiTile({ label, value, current, prior, invertDirection, hint, icon: Icon }: Props) {
  const movement = delta(current, prior);
  const isGood = movement
    ? invertDirection
      ? movement.direction === 'down'
      : movement.direction === 'up'
    : null;

  return (
    <div className="group relative overflow-hidden rounded-lg glass p-5 transition-all duration-200 hover:-translate-y-0.5 hover:elev-lg">
      {/* A hairline of brand colour on hover - presence without decoration. */}
      <span
        aria-hidden
        className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/50 to-transparent opacity-0 transition-opacity duration-200 group-hover:opacity-100"
      />
      {Icon && (
        <span
          aria-hidden
          className="absolute right-4 top-4 grid h-9 w-9 place-items-center rounded-lg bg-primary-soft text-primary ring-1 ring-inset ring-primary/10 transition-colors duration-200 group-hover:bg-primary group-hover:text-primary-foreground"
        >
          <Icon className="h-4 w-4" />
        </span>
      )}
      <p className="pr-11 text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
        {label}
      </p>
      <p className="mt-2 text-[28px] font-semibold leading-none tabular-nums tracking-[-0.02em]">
        {value}
      </p>
      <div className="mt-3 flex min-h-5 flex-wrap items-center gap-1.5 text-xs">
        {movement && movement.direction !== 'flat' ? (
          <span
            className={cn(
              'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums',
              isGood
                ? 'bg-positive/10 text-positive'
                : 'bg-critical/10 text-critical',
            )}
          >
            {/* The arrow carries direction so colour is not the only cue. */}
            {movement.direction === 'up' ? (
              <TrendingUp className="h-3 w-3" aria-hidden />
            ) : (
              <TrendingDown className="h-3 w-3" aria-hidden />
            )}
            {movement.change > 0 ? '+' : ''}
            {movement.change.toFixed(0)}%
          </span>
        ) : (
          <span className="text-muted-foreground">{movement ? 'no change' : EM_DASH}</span>
        )}
        <span className="text-muted-foreground">{hint ?? 'vs last month'}</span>
      </div>
    </div>
  );
}
