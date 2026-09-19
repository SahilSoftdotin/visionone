import type { OverviewFunnel } from '@/lib/types';
import { formatCount, formatPercent } from '@/lib/format';

const STAGES = [
  { key: 'leads', label: 'Lead' },
  { key: 'qualified', label: 'Qualified' },
  { key: 'appointmentRequested', label: 'Appointment requested' },
  { key: 'booked', label: 'Booked' },
] as const;

/**
 * Four stages, each measurable from lead status history.
 *
 * Counted as "ever reached", so the funnel only ever decreases and a lead that jumped straight to
 * booked still appears at every stage before it.
 */
export function FunnelChart({ funnel }: { funnel: OverviewFunnel }) {
  const top = funnel.leads;

  return (
    <div className="space-y-3">
      {STAGES.map(({ key, label }, index) => {
        const value = funnel[key];
        const width = top === 0 ? 0 : (value / top) * 100;
        const previous = index === 0 ? null : funnel[STAGES[index - 1]!.key];
        const stageRate = previous && previous > 0 ? (value / previous) * 100 : null;

        return (
          <div key={key}>
            <div className="mb-1 flex items-baseline justify-between gap-2 text-sm">
              <span className="font-medium">{label}</span>
              <span className="tabular-nums">
                {formatCount(value)}
                {stageRate !== null && (
                  <span className="ml-2 text-xs text-muted-foreground">
                    {formatPercent(stageRate)} of previous
                  </span>
                )}
              </span>
            </div>
            <div
              className="h-2.5 overflow-hidden rounded-full bg-muted"
              role="img"
              aria-label={`${label}: ${value} leads`}
            >
              <div
                className="h-full rounded-full bg-[hsl(var(--chart-1))] transition-all"
                style={{ width: `${width}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
