import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Snowflake } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import type { ApiError } from '@/lib/api';
import type { MonthlyReportResponse } from '@/lib/types';
import { useReportAdmin } from './useReports';

/**
 * Every figure the report freezes, and the screen it is entered or recorded on.
 *
 * The report has no number inputs of its own, deliberately: it only ever reads what the other
 * screens hold, which is why it cannot disagree with them. So the useful thing to show Vision here
 * is where each figure comes from, and a way to go and correct it before freezing.
 */
const FROZEN_SOURCES: { figures: string; source: string; path: string }[] = [
  { figures: 'Planned and actual investment, utilisation', source: 'Growth', path: 'growth' },
  { figures: 'Spend, leads, booked and cost per booked, by channel', source: 'Growth and Leads', path: 'growth' },
  { figures: 'New leads, qualified, lead funnel, cost per lead', source: 'Leads', path: 'leads' },
  { figures: 'Appointments booked', source: 'Calendar', path: 'calendar' },
  { figures: 'Calls, answered, after hours, booked from calls', source: 'Front Desk', path: 'front-desk' },
  { figures: 'Work completed and content published in the month', source: 'Work & Content', path: 'work' },
];

const fieldClass =
  'mt-1.5 w-full rounded-md border border-input bg-background px-3 py-2 text-sm leading-relaxed';
const primaryButton =
  'rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-60';
const secondaryButton =
  'rounded-md border border-border bg-card px-3 py-1.5 text-sm font-medium transition-colors hover:bg-muted disabled:opacity-60';

/**
 * Vision Digital Lab's report controls: freeze the figures, write the reading, share.
 *
 * Only rendered when the API says {@code editable} - Vision, before the report is shared. The
 * endpoints refuse everyone else regardless; the hiding is a courtesy, the refusal is the control.
 *
 * Sharing is the one irreversible action in VisionOne, so it takes two clicks, and the button says
 * why it is unavailable rather than simply greying out.
 */
export function ReportAdminPanel({ orgId, report }: { orgId: string; report: MonthlyReportResponse }) {
  const { generate, writeNarrative, share } = useReportAdmin(orgId);

  const [keyLearning, setKeyLearning] = useState(report.keyLearning ?? '');
  const [nextActions, setNextActions] = useState(report.nextActions.join('\n'));
  const [decisions, setDecisions] = useState(report.decisionsRequired.join('\n'));
  const [confirmingShare, setConfirmingShare] = useState(false);

  const month = report.periodMonth.slice(0, 7);
  const savedKeyLearning = (report.keyLearning ?? '').trim();
  const dirty =
    keyLearning !== (report.keyLearning ?? '') ||
    nextActions !== report.nextActions.join('\n') ||
    decisions !== report.decisionsRequired.join('\n');

  // Share needs frozen figures and a saved key learning - the server enforces both.
  const shareBlocker = !report.frozen
    ? 'Freeze the figures first.'
    : savedKeyLearning === ''
      ? 'Write and save the key learning first.'
      : dirty
        ? 'Save the narrative first.'
        : null;

  const busy = generate.isPending || writeNarrative.isPending || share.isPending;
  const failure = (generate.error ?? writeNarrative.error ?? share.error) as ApiError | null;

  return (
    <Card className="reveal border-primary/30">
      <CardHeader
        title="Vision Digital Lab controls"
        action={<Badge tone="neutral">Admin only</Badge>}
      />
      <CardBody className="space-y-6">
        <p className="text-sm text-muted-foreground">
          {report.frozen
            ? 'Figures are frozen. This report is not visible to the practice until you share it.'
            : 'Figures are still live and will move as the month’s data lands. This report is not visible to the practice.'}
        </p>

        <div className="rounded-lg bg-muted/50 p-4">
          <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
            <Snowflake className="h-3.5 w-3.5" aria-hidden />
            What freezing captures, and where to change it first
          </p>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Nothing is typed into the report itself. Budget and spend are entered on Growth; the
            rest is recorded as the month happens. Correct anything at its source, then freeze.
          </p>
          <ul className="mt-3 divide-y divide-border/60 text-sm">
            {FROZEN_SOURCES.map((row) => (
              <li key={row.figures} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5 py-2">
                <span>{row.figures}</span>
                <Link
                  to={`/orgs/${orgId}/${row.path}?month=${month}`}
                  className="shrink-0 text-xs font-medium text-primary-text underline-offset-2 hover:underline"
                >
                  {row.source} →
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={() => generate.mutate(month)}
            className={report.frozen ? secondaryButton : primaryButton}
          >
            {generate.isPending
              ? 'Freezing…'
              : report.frozen
                ? 'Refresh frozen figures'
                : 'Freeze figures'}
          </button>
          <span className="text-xs text-muted-foreground">
            {report.frozen
              ? 'Replaces the frozen figures with today’s data. Only possible until shared.'
              : 'Takes a snapshot of this month’s figures. The report will keep saying this.'}
          </span>
        </div>

        <form
          className="space-y-4 border-t border-border pt-5"
          onSubmit={(event) => {
            event.preventDefault();
            writeNarrative.mutate({
              reportId: report.id,
              keyLearning,
              nextActions,
              decisionsRequired: decisions,
            });
          }}
        >
          <label className="block">
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Key learning <span className="normal-case tracking-normal">(required to share)</span>
            </span>
            <textarea
              rows={3}
              maxLength={2000}
              value={keyLearning}
              onChange={(event) => setKeyLearning(event.target.value)}
              placeholder="The one thing this month taught us about growing the practice."
              className={fieldClass}
            />
          </label>
          <div className="grid gap-4 lg:grid-cols-2">
            <label className="block">
              <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Next actions <span className="normal-case tracking-normal">(one per line)</span>
              </span>
              <textarea
                rows={4}
                maxLength={2000}
                value={nextActions}
                onChange={(event) => setNextActions(event.target.value)}
                className={fieldClass}
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Decisions required <span className="normal-case tracking-normal">(one per line)</span>
              </span>
              <textarea
                rows={4}
                maxLength={2000}
                value={decisions}
                onChange={(event) => setDecisions(event.target.value)}
                className={fieldClass}
              />
            </label>
          </div>
          <button
            type="submit"
            disabled={busy || !dirty || keyLearning.trim() === ''}
            className={primaryButton}
          >
            {writeNarrative.isPending ? 'Saving…' : 'Save narrative'}
          </button>
        </form>

        <div className="border-t border-border pt-5">
          {!confirmingShare ? (
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                disabled={busy || shareBlocker !== null}
                onClick={() => setConfirmingShare(true)}
                className={primaryButton}
              >
                Share with the practice
              </button>
              <span className="text-xs text-muted-foreground">
                {shareBlocker ?? 'Once shared, the report cannot be changed.'}
              </span>
            </div>
          ) : (
            <div
              role="alertdialog"
              aria-label="Confirm sharing"
              className="rounded-lg bg-caution-soft p-4 ring-1 ring-inset ring-caution/30"
            >
              <p className="text-sm font-medium text-caution-text">
                Share this report with the practice?
              </p>
              <p className="mt-1 text-sm text-caution-text/90">
                They will see it straight away, and neither the figures nor the narrative can be
                changed afterwards.
              </p>
              <div className="mt-3 flex gap-2">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() =>
                    share.mutate(report.id, { onSettled: () => setConfirmingShare(false) })
                  }
                  className={primaryButton}
                >
                  {share.isPending ? 'Sharing…' : 'Yes, share it'}
                </button>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => setConfirmingShare(false)}
                  className={secondaryButton}
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>

        {failure && (
          <p role="alert" className="text-sm text-critical">
            {failure.message}
          </p>
        )}
      </CardBody>
    </Card>
  );
}
