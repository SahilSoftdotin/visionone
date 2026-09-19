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
    <div className="group relative overflow-hidden rounded-lg glass p-5 transition-all duration-200 hover:-translate-y-0.5 hover:elev-lg">
      {/* A hairline of brand colour on hover - presence without decoration. */}
      <span
        aria-hidden
        className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/50 to-transparent opacity-0 transition-opacity duration-200 group-hover:opacity-100"
      />
      <p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
        {label}
      </p>
      <p className="mt-2 text-[28px] font-semibold leading-none tabular-nums tracking-[-0.02em]">
        {value}
      </p>
      <div className="mt-3 flex min-h-5 flex-wrap items-center gap-1.5 text-xs">
        {movement && movement.direction !== 'flat' ? (
          <span
            className={cn(
              'inline-flex items-center rounded-full px-1.5 py-0.5 text-xs font-semibold tabular-nums',
              isGood
                ? 'bg-positive/10 text-positive'
                : 'bg-critical/10 text-critical',
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
