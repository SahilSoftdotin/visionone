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
}

export function KpiTile({ label, value, current, prior, invertDirection, hint }: Props) {
  const movement = delta(current, prior);
  const isGood = movement
    ? invertDirection
      ? movement.direction === 'down'
      : movement.direction === 'up'
    : null;

  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="mt-1.5 text-2xl font-semibold tabular-nums tracking-tight">{value}</p>
      <div className="mt-1 flex min-h-5 items-center gap-1.5 text-xs">
        {movement && movement.direction !== 'flat' ? (
          <span
            className={cn(
              'font-medium tabular-nums',
              isGood ? 'text-positive' : 'text-critical',
            )}
          >
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
