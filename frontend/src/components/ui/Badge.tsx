import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

type Tone = 'neutral' | 'positive' | 'caution' | 'critical' | 'demo';

const toneClasses: Record<Tone, string> = {
  neutral: 'bg-muted text-muted-foreground',
  positive: 'bg-positive-soft text-positive-text',
  caution: 'bg-caution-soft text-caution-text',
  critical: 'bg-critical-soft text-critical-text',
  demo: 'bg-caution-soft text-caution-text ring-1 ring-inset ring-caution/40',
};

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
        toneClasses[tone],
      )}
    >
      {children}
    </span>
  );
}
