import { useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { formatMoney, formatMonth } from '@/lib/format';
import type { ApiError } from '@/lib/api';
import { useGrowthAdmin, type AllocationEdit } from './useGrowthPlan';
import type { Money } from '@/lib/types';

interface Plan {
  currency: string;
  plannedTotal: Money;
  notes: string | null;
  allocations: {
    channelSourceId: string;
    displayName: string;
    planned: Money;
    actual: Money;
  }[];
}

type Rows = Record<string, { planned: string; actual: string }>;

const inputClass =
  'w-full rounded-md border border-input bg-background px-3 py-2 text-sm tabular-nums';
const saveButton =
  'rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50';

const toUnits = (money: Money) => (money.amountMinor / 100).toString();
const toMinor = (value: string): number => Math.round(Number(value || '0') * 100);

function rowsFrom(plan: Plan): Rows {
  return Object.fromEntries(
    plan.allocations.map((a) => [
      a.channelSourceId,
      { planned: toUnits(a.planned), actual: toUnits(a.actual) },
    ]),
  );
}

/**
 * Vision Admin's budget controls.
 *
 * Only rendered when the API says {@code editable}, and the endpoints refuse a CLIENT_OWNER
 * regardless - the hiding is a courtesy, the refusal is the control.
 *
 * Amounts are entered in whole currency units and converted to minor units on the way out, so no
 * fractional cent ever reaches the API.
 *
 * Each save states its outcome beside its own button. A form that saves silently is
 * indistinguishable from one that failed silently, and an error printed at the foot of a long card
 * is an error nobody scrolls down to read.
 *
 * The parent keys this component by month. Without that, values typed for one month would still be
 * in the boxes after switching to another - and saving would write them into the wrong month.
 */
export function BudgetEditor({ plan, orgId, month }: { plan: Plan; orgId: string; month: string }) {
  const { setBudget, setAllocations } = useGrowthAdmin(orgId, month);

  const [total, setTotal] = useState(() => toUnits(plan.plannedTotal));
  const [notes, setNotes] = useState(plan.notes ?? '');
  const [rows, setRows] = useState<Rows>(() => rowsFrom(plan));

  // Compared with what the server last returned, so "unsaved" is always about the real state.
  const budgetDirty =
    toMinor(total) !== plan.plannedTotal.amountMinor || notes !== (plan.notes ?? '');
  const rowsDirty = plan.allocations.some((a) => {
    const row = rows[a.channelSourceId];
    return (
      !row ||
      toMinor(row.planned) !== a.planned.amountMinor ||
      toMinor(row.actual) !== a.actual.amountMinor
    );
  });

  const allocatedMinor = plan.allocations.reduce(
    (sum, a) => sum + toMinor(rows[a.channelSourceId]?.planned ?? '0'),
    0,
  );
  const plannedMinor = toMinor(total);
  const drift = allocatedMinor - plannedMinor;
  const busy = setBudget.isPending || setAllocations.isPending;

  return (
    <Card className="border-primary/30">
      <CardHeader
        title={`Vision Digital Lab controls · ${formatMonth(month)}`}
        action={<Badge tone="neutral">Admin only</Badge>}
      />
      <CardBody className="space-y-6">
        <div className="grid gap-4 sm:grid-cols-[200px_1fr] sm:items-start">
          <label className="block">
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Monthly budget ({plan.currency})
            </span>
            <input
              type="number"
              min={0}
              step="0.01"
              value={total}
              onChange={(event) => setTotal(event.target.value)}
              className={`mt-1.5 ${inputClass}`}
            />
          </label>
          <label className="block">
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Note for this month
            </span>
            <input
              type="text"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              placeholder="Why the budget is shaped this way"
              className="mt-1.5 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            />
          </label>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            disabled={busy || !budgetDirty}
            onClick={() =>
              setBudget.mutate({ plannedTotalMinor: plannedMinor, notes: notes || null })
            }
            className={saveButton}
          >
            {setBudget.isPending ? 'Saving…' : 'Save monthly budget'}
          </button>
          <SaveStatus
            dirty={budgetDirty}
            pending={setBudget.isPending}
            error={setBudget.error as ApiError | null}
            savedAt={setBudget.isSuccess ? setBudget.submittedAt : null}
          />
        </div>

        <div className="border-t border-border pt-5">
          <p className="mb-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Per-channel plan and actual spend ({plan.currency})
          </p>
          <div className="space-y-2">
            <div
              className="hidden gap-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground sm:grid sm:grid-cols-[1fr_140px_140px]"
              aria-hidden
            >
              <span>Channel</span>
              <span>Planned</span>
              <span>Actual spend</span>
            </div>
            {plan.allocations.map((allocation) => {
              const row = rows[allocation.channelSourceId] ?? { planned: '0', actual: '0' };
              return (
                <div
                  key={allocation.channelSourceId}
                  className="grid items-center gap-2 sm:grid-cols-[1fr_140px_140px]"
                >
                  <span className="text-sm">{allocation.displayName}</span>
                  {(['planned', 'actual'] as const).map((field) => (
                    <input
                      key={field}
                      type="number"
                      min={0}
                      step="0.01"
                      aria-label={`${allocation.displayName} ${field === 'planned' ? 'planned' : 'actual spend'}`}
                      value={row[field]}
                      onChange={(event) =>
                        setRows((current) => ({
                          ...current,
                          [allocation.channelSourceId]: {
                            ...(current[allocation.channelSourceId] ?? { planned: '0', actual: '0' }),
                            [field]: event.target.value,
                          },
                        }))
                      }
                      className={inputClass}
                    />
                  ))}
                </div>
              );
            })}
          </div>

          {/* Allocations need not sum to the total, but a silent mismatch is worth naming. */}
          {drift !== 0 && (
            <p className="mt-3 text-xs text-caution-text">
              Channel plans total {formatMoney({ amountMinor: allocatedMinor, currency: plan.currency })},
              which is {formatMoney({ amountMinor: Math.abs(drift), currency: plan.currency })}{' '}
              {drift > 0 ? 'more' : 'less'} than the monthly budget.
            </p>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-3">
            <button
              type="button"
              disabled={busy || !rowsDirty}
              onClick={() =>
                setAllocations.mutate(
                  plan.allocations.map<AllocationEdit>((a) => ({
                    channelSourceId: a.channelSourceId,
                    plannedMinor: toMinor(rows[a.channelSourceId]?.planned ?? '0'),
                    actualMinor: toMinor(rows[a.channelSourceId]?.actual ?? '0'),
                  })),
                )
              }
              className={saveButton}
            >
              {setAllocations.isPending ? 'Saving…' : 'Save channel spend'}
            </button>
            <SaveStatus
              dirty={rowsDirty}
              pending={setAllocations.isPending}
              error={setAllocations.error as ApiError | null}
              savedAt={setAllocations.isSuccess ? setAllocations.submittedAt : null}
            />
          </div>
        </div>
      </CardBody>
    </Card>
  );
}

/**
 * What happened to the last save, beside the button that made it.
 *
 * Unsaved edits take precedence over an old "saved": once the values have moved on, the earlier
 * confirmation is no longer true of what is on screen.
 */
function SaveStatus({
  dirty,
  pending,
  error,
  savedAt,
}: {
  dirty: boolean;
  pending: boolean;
  error: ApiError | null;
  savedAt: number | null;
}) {
  if (pending) return null;
  if (error) {
    return (
      <span role="alert" className="text-sm text-critical-text">
        Not saved: {error.message}
      </span>
    );
  }
  if (dirty) {
    return <span className="text-sm text-caution-text">Unsaved changes</span>;
  }
  if (savedAt) {
    return (
      <span role="status" className="inline-flex items-center gap-1.5 text-sm text-positive-text">
        <CheckCircle2 className="h-4 w-4" aria-hidden />
        Saved at{' '}
        {new Date(savedAt).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}
      </span>
    );
  }
  return null;
}
