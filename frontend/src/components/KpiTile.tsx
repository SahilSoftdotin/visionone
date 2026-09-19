import { TrendingDown, TrendingUp, type LucideIcon } from 'lucide-react';
import { delta, EM_DASH } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * Modernize's signature stat tile: a flat pastel block, no border, no shadow at rest, with the
 * label and figure both carrying the tint's own colour.
 *
 * The text uses each tint's `-text` variant rather than the fill colour. Modernize prints the
 * label in the raw accent, which measures as low as 1.7:1 on its own pastel ground; the darker
 * variant keeps the hue and makes the number legible.
 */

export type TileTone = 'primary' | 'secondary' | 'positive' | 'caution' | 'critical';

const toneClasses: Record<TileTone, { bg: string; text: string; icon: string }> = {
  primary: { bg: 'bg-primary-soft', text: 'text-primary-text', icon: 'bg-primary/15 text-primary-text' },
  secondary: { bg: 'bg-secondary-soft', text: 'text-secondary-text', icon: 'bg-secondary/15 text-secondary-text' },
  positive: { bg: 'bg-positive-soft', text: 'text-positive-text', icon: 'bg-positive/20 text-positive-text' },
  caution: { bg: 'bg-caution-soft', text: 'text-caution-text', icon: 'bg-caution/20 text-caution-text' },
  critical: { bg: 'bg-critical-soft', text: 'text-critical-text', icon: 'bg-critical/20 text-critical-text' },
};

interface Props {
  label: string;
  value: string;
  current?: number | null;
  prior?: number | null;
  /** True when a rise is bad news, as with cost per lead. */
  invertDirection?: boolean;
  hint?: string;
  icon?: LucideIcon;
  tone?: TileTone;
}

export function KpiTile({
  label,
  value,
  current,
  prior,
  invertDirection,
  hint,
  icon: Icon,
  tone = 'primary',
}: Props) {
  const movement = delta(current, prior);
  const isGood = movement
    ? invertDirection
      ? movement.direction === 'down'
      : movement.direction === 'up'
    : null;
  const t = toneClasses[tone];

  return (
    <div className={cn('tint-tile p-5', t.bg)}>
      {Icon && (
        <span className={cn('grid h-11 w-11 place-items-center rounded-lg', t.icon)} aria-hidden>
          <Icon className="h-5 w-5" />
        </span>
      )}
      <p className={cn('mt-3 text-sm font-semibold', t.text)}>{label}</p>
      <p className={cn('mt-1 text-[28px] font-bold leading-tight tabular-nums', t.text)}>{value}</p>

      <div className="mt-1.5 flex min-h-5 flex-wrap items-center gap-1.5 text-xs">
        {movement && movement.direction !== 'flat' ? (
          <span
            className={cn(
              'inline-flex items-center gap-1 rounded-full bg-white/70 px-2 py-0.5 text-[11px] font-bold tabular-nums',
              isGood ? 'text-positive-text' : 'text-critical-text',
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

/** Modernize cycles its tints across a row rather than colouring by meaning. */
export const TILE_TONES: TileTone[] = ['primary', 'caution', 'secondary', 'critical', 'positive'];
