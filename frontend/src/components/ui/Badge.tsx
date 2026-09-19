import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

type Tone = 'neutral' | 'positive' | 'caution' | 'critical' | 'demo';

const toneClasses: Record<Tone, string> = {
  neutral: 'bg-muted text-muted-foreground',
  positive: 'bg-positive/10 text-positive',
  caution: 'bg-caution/10 text-caution',
  critical: 'bg-critical/10 text-critical',
  demo: 'bg-caution/10 text-caution ring-1 ring-inset ring-caution/30',
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
