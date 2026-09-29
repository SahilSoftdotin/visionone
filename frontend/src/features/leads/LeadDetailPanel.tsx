import { useState } from 'react';
import { X } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { LEAD_STATUSES, type LeadStatus } from '@/lib/types';
import { ApiError } from '@/lib/api';
import { cn } from '@/lib/utils';
import { useLeadDetail, useLeadTransition } from './useLeads';

const statusTone: Record<LeadStatus, 'neutral' | 'positive' | 'caution' | 'critical'> = {
  NEW: 'caution',
  CONTACTED: 'neutral',
  QUALIFIED: 'neutral',
  APPOINTMENT_REQUESTED: 'caution',
  BOOKED: 'positive',
  ATTENDED: 'positive',
  NOT_CONVERTED: 'critical',
  DUPLICATE: 'neutral',
};

const pretty = (s: LeadStatus) => s.toLowerCase().replace(/_/g, ' ');

interface Props {
  orgId: string;
  month: string;
  leadId: string;
  editable: boolean;
  onClose: () => void;
}

/**
 * One lead and everything that happened to it.
 *
 * The history is the interesting part: it is append-only, and it is what the Overview funnel
 * counts. Moving a lead here is what the Week-3 demo shows changing on the Overview.
 */
export function LeadDetailPanel({ orgId, month, leadId, editable, onClose }: Props) {
  const { data, isLoading, error } = useLeadDetail(orgId, leadId);
  const transition = useLeadTransition(orgId, month);
  const [reason, setReason] = useState('');

  return (
    <div className="fixed inset-0 z-40 flex justify-end" role="dialog" aria-label="Lead detail">
      <button
        type="button"
        aria-label="Close lead detail"
        onClick={onClose}
        className="flex-1 bg-black/25 backdrop-blur-[1px]"
      />
      <aside className="flex h-full w-full max-w-md flex-col overflow-y-auto border-l border-border bg-card shadow-xl">
        <header className="sticky top-0 flex items-start justify-between gap-3 border-b border-border bg-card px-5 py-4">
          <div>
            {data && (
              <>
                <p className="font-mono text-xs text-muted-foreground">{data.lead.reference}</p>
                <h2 className="mt-0.5 text-base font-semibold">{data.lead.name}</h2>
              </>
            )}
            {isLoading && <p className="text-sm text-muted-foreground">Loading&hellip;</p>}
          </div>
          <button type="button" onClick={onClose} aria-label="Close" className="mt-0.5">
            <X className="h-4 w-4" />
          </button>
        </header>

        {error && (
          <p role="alert" className="px-5 py-4 text-sm text-critical">
            {error instanceof ApiError ? error.message : 'Could not load this lead.'}
          </p>
        )}

        {data && (
          <div className="space-y-6 px-5 py-5">
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              {[
                ['Status', <Badge key="s" tone={statusTone[data.lead.status]}>{pretty(data.lead.status)}</Badge>],
                ['Interest', data.lead.serviceInterest.toLowerCase().replace(/_/g, ' ')],
                ['Source', data.lead.source],
                ['Campaign', data.lead.campaign ?? '—'],
                ['Owner', data.lead.owner],
                [
                  'First response',
                  data.lead.responseMinutes === null
                    ? 'never'
                    : data.lead.responseMinutes < 60
                      ? `${data.lead.responseMinutes} min`
                      : `${Math.round(data.lead.responseMinutes / 60)} h`,
                ],
              ].map(([label, value]) => (
                <div key={String(label)}>
                  <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
                  <dd className="mt-0.5 capitalize">{value}</dd>
                </div>
              ))}
            </dl>

            <section>
              <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                What happened
              </h3>
              <ol className="space-y-0">
                {data.history.map((entry, index) => (
                  <li key={`${entry.toStatus}-${entry.changedAt}`} className="flex gap-3">
                    <div className="flex flex-col items-center">
                      <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-[hsl(var(--chart-1))]" />
                      {index < data.history.length - 1 && (
                        <span className="w-px flex-1 bg-border" aria-hidden />
                      )}
                    </div>
                    <div className="pb-4">
                      <p className="text-sm font-medium capitalize">{pretty(entry.toStatus)}</p>
                      <p className="text-xs text-muted-foreground">
                        {new Date(entry.changedAt).toLocaleString(undefined, {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                        {' · '}
                        {entry.changedBy}
                      </p>
                      {entry.reason && <p className="mt-1 text-xs italic">{entry.reason}</p>}
                    </div>
                  </li>
                ))}
              </ol>
            </section>

            {editable && (
              <section className="border-t border-border pt-5">
                <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  Move this lead
                </h3>
                <input
                  type="text"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Why (optional, kept in the history)"
                  className="mb-3 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                />
                <div className="flex flex-wrap gap-1.5">
                  {LEAD_STATUSES.filter((s) => s !== data.lead.status).map((s) => (
                    <button
                      key={s}
                      type="button"
                      disabled={transition.isPending}
                      onClick={() => {
                        transition.mutate(
                          { leadId, toStatus: s, reason: reason || undefined },
                          { onSuccess: () => setReason('') },
                        );
                      }}
                      className={cn(
                        'rounded-full border border-border px-3 py-1.5 text-xs font-medium capitalize',
                        'hover:border-primary hover:text-primary disabled:opacity-50',
                      )}
                    >
                      {pretty(s)}
                    </button>
                  ))}
                </div>
                {transition.error && (
                  <p role="alert" className="mt-3 text-sm text-critical">
                    {(transition.error as ApiError).message}
                  </p>
                )}
              </section>
            )}
          </div>
        )}
      </aside>
    </div>
  );
}
