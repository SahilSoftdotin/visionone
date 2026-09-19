import type { LucideIcon } from 'lucide-react';
import { ChevronDown, TrendingDown, TrendingUp } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * The small repeated parts of the Modernize dashboard: the arrow badge beside a delta, the tinted
 * icon chip beside a figure, the period pill, and the tinted status pill in a table.
 *
 * Text inside a tint always uses the `-text` variant. Modernize prints these in its raw accents,
 * which measure between 1.7:1 and 3.3:1 and are unreadable at this size.
 */

export type Tone = 'primary' | 'secondary' | 'positive' | 'caution' | 'critical' | 'neutral';

const toneBg: Record<Tone, string> = {
  primary: 'bg-primary-soft text-primary-text',
  secondary: 'bg-secondary-soft text-secondary-text',
  positive: 'bg-positive-soft text-positive-text',
  caution: 'bg-caution-soft text-caution-text',
  critical: 'bg-critical-soft text-critical-text',
  neutral: 'bg-muted text-muted-foreground',
};

/** Circle with an arrow. Green rising, red falling - direction carried by shape, not only hue. */
export function ArrowBadge({ up, label }: { up: boolean; label?: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span
        className={cn(
          'grid h-6 w-6 shrink-0 place-items-center rounded-full',
          up ? 'bg-positive-soft text-positive-text' : 'bg-critical-soft text-critical-text',
        )}
        aria-hidden
      >
        {up ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
      </span>
      {label && <span className="text-sm text-muted-foreground">{label}</span>}
    </span>
  );
}

/** Rounded square holding an icon, set on a tint. */
export function IconChip({
  icon: Icon,
  tone = 'primary',
  className,
}: {
  icon: LucideIcon;
  tone?: Tone;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'grid h-10 w-10 shrink-0 place-items-center rounded-lg',
        toneBg[tone],
        className,
      )}
      aria-hidden
    >
      <Icon className="h-[18px] w-[18px]" />
    </span>
  );
}

/** The bordered period selector in a card header. Display only in Phase 1. */
export function PeriodPill({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-foreground">
      {label}
      <ChevronDown className="h-4 w-4 text-muted-foreground" aria-hidden />
    </span>
  );
}

/** Tinted pill used for status and priority in tables. */
export function StatusPill({
  tone = 'neutral',
  children,
}: {
  tone?: Tone;
  children: React.ReactNode;
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-md px-2.5 py-1 text-xs font-semibold capitalize',
        toneBg[tone],
      )}
    >
      {children}
    </span>
  );
}

/** A name with a monogram avatar. Modernize uses photographs; those are licensed assets. */
export function PersonCell({ name, role }: { name: string; role: string }) {
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase();
  return (
    <span className="flex items-center gap-3">
      <span
        className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary-soft text-[11px] font-bold text-primary-text"
        aria-hidden
      >
        {initials}
      </span>
      <span className="min-w-0">
        <span className="block truncate font-semibold">{name}</span>
        <span className="block truncate text-xs text-muted-foreground">{role}</span>
      </span>
    </span>
  );
}
