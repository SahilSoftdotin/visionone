import type { CSSProperties, ReactNode } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { CheckCircle2, FileText, HelpCircle, Lightbulb, ListChecks } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { DataStateBoundary } from '@/components/ui/DataStateBoundary';
import { Skeleton } from '@/components/ui/Skeleton';
import { formatCount, formatMoney, formatMonth, formatPercent } from '@/lib/format';
import type { MonthlyReportResponse, ReportListResponse } from '@/lib/types';
import { NewReportButton } from './NewReportButton';
import { ReportAdminPanel } from './ReportAdminPanel';
import { ReportPicker } from './ReportPicker';
import { useReport, useReportAdmin, useReportList } from './useReports';

/**
 * Screen 6 - Reports.
 *
 * One executive monthly report, not a report builder. Every figure is composed on the server from
 * the same module read surfaces the other five screens use, so the report cannot disagree with the
 * dashboard - and once generated it is frozen, so a report Gary read in August still says in
 * December what it said in August.
 */
export function ReportsScreen() {
  const { orgId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const requestedMonth = searchParams.get('month');

  const list = useReportList(orgId);
  const reports = list.data?.reports ?? [];
  const canGenerate = list.data?.canGenerate ?? false;

  // A month in the URL picks that report; no month means the most recent one, which is what the
  // screen should open on - on the second of the month there is usually no report yet.
  const chosen = requestedMonth
    ? reports.find((r) => r.periodMonth.startsWith(requestedMonth))
    : undefined;
  const monthHasNoReport = Boolean(requestedMonth) && list.isSuccess && chosen === undefined;

  const report = useReport(orgId, chosen?.id);

  if (monthHasNoReport) {
    return (
      <div className="space-y-6">
        <ReportsHeader
          orgId={orgId}
          month={requestedMonth as string}
          reports={reports}
          canGenerate={canGenerate}
          status={null}
          frozen={false}
          generatedAt={null}
        />
        <EmptyNotice
          title={`No report for ${formatMonth(requestedMonth as string)}`}
          body={
            canGenerate
              ? 'Start one to freeze this month’s figures, then write the narrative.'
              : 'Vision Digital Lab publishes a report once the month has closed. Choose another period above.'
          }
          action={
            canGenerate ? (
              <StartReportButton orgId={orgId} month={requestedMonth as string} />
            ) : undefined
          }
        />
      </div>
    );
  }

  return (
    <DataStateBoundary
      isLoading={list.isLoading || report.isLoading}
      error={list.error ?? report.error}
      skeleton={<ReportsSkeleton />}
      onRetry={() => {
        void list.refetch();
        void report.refetch();
      }}
    >
      {report.isMissing ? (
        <EmptyNotice
          title="No reports yet"
          body={
            canGenerate
              ? 'Start the first report to freeze a month’s figures, then write the narrative.'
              : 'The first monthly report is published once a full month of activity has closed.'
          }
          action={canGenerate ? <NewReportButton orgId={orgId} reports={reports} /> : undefined}
        />
      ) : (
        report.data && (
          <ReportView orgId={orgId} r={report.data} reports={reports} canGenerate={canGenerate} />
        )
      )}
    </DataStateBoundary>
  );
}

function ReportsHeader({
  orgId,
  month,
  reports,
  canGenerate,
  status,
  frozen,
  generatedAt,
}: {
  orgId: string;
  month: string;
  reports: ReportListResponse['reports'];
  canGenerate: boolean;
  status: string | null;
  frozen: boolean;
  generatedAt: string | null;
}) {
  return (
    <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
      <div>
        <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">Monthly report</h1>
        <p className="text-sm text-muted-foreground">
          {formatMonth(month)}
          {status && ` · ${status.toLowerCase()}`}
          {/* Frozen or live is a claim about the figures, so it is stated rather than implied. */}
          {status &&
            (frozen && generatedAt
              ? ` · figures as at ${new Date(generatedAt).toLocaleDateString('en-US', {
                  day: 'numeric',
                  month: 'short',
                  year: 'numeric',
                })}`
              : ' · figures still live')}
        </p>
      </div>
      <div className="flex items-center gap-2">
        {canGenerate && <NewReportButton orgId={orgId} reports={reports} />}
        <ReportPicker reports={reports} selectedMonth={month} />
        <Badge tone="demo">Demo data</Badge>
      </div>
    </header>
  );
}

function ReportView({
  orgId,
  r,
  reports,
  canGenerate,
}: {
  orgId: string;
  r: MonthlyReportResponse;
  reports: ReportListResponse['reports'];
  canGenerate: boolean;
}) {
  const f = r.figures;

  return (
    <div className="space-y-6">
      <ReportsHeader
        orgId={orgId}
        month={r.periodMonth.slice(0, 7)}
        reports={reports}
        canGenerate={canGenerate}
        status={r.status}
        frozen={r.frozen}
        generatedAt={r.generatedAt}
      />

      {/* Keyed by report so switching months resets the draft narrative. */}
      {r.editable && <ReportAdminPanel key={r.id} orgId={orgId} report={r} />}

      <section
        className="reveal relative overflow-hidden rounded-lg p-6"
        style={{ '--i': 1 } as CSSProperties}
      >
        <p className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">
          Growth investment
        </p>
        <div className="mt-3 flex flex-wrap items-end gap-3">
          <span className="text-[40px] font-semibold leading-none tabular-nums tracking-[-0.03em]">
            {formatMoney(f.investment.actual, { compact: true })}
          </span>
          <span className="pb-1 text-sm text-muted-foreground">
            invested of {formatMoney(f.investment.planned, { compact: true })} planned
          </span>
        </div>

        <dl className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            { label: 'Leads', value: formatCount(f.headline.newLeads) },
            { label: 'Qualified', value: formatCount(f.headline.qualified) },
            { label: 'Appointments', value: formatCount(f.headline.appointments) },
            { label: 'Utilisation', value: formatPercent(f.investment.utilizationPercent) },
          ].map(({ label, value }) => (
            <div
              key={label}
              className="rounded-lg bg-primary-soft/60 p-3.5 ring-1 ring-inset ring-primary/10"
            >
              <dt className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                {label}
              </dt>
              <dd className="mt-1 text-xl font-semibold tabular-nums tracking-[-0.02em]">
                {value}
              </dd>
            </div>
          ))}
        </dl>
      </section>

      <Card className="reveal" style={{ '--i': 2 } as CSSProperties}>
        <CardHeader title="Source performance" />
        <CardBody className="px-0 py-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <caption className="sr-only">Leads, booked appointments and spend by channel</caption>
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                    Channel
                  </th>
                  <th scope="col" className="px-3 py-3 text-right font-semibold">
                    Spend
                  </th>
                  <th scope="col" className="px-3 py-3 text-right font-semibold">
                    Leads
                  </th>
                  <th scope="col" className="px-3 py-3 text-right font-semibold">
                    Booked
                  </th>
                  <th scope="col" className="px-5 py-3 text-right font-semibold sm:px-6">
                    Cost / booked
                  </th>
                </tr>
              </thead>
              <tbody>
                {f.sources.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-5 py-6 text-center text-muted-foreground sm:px-6">
                      No channel budget was set for this period.
                    </td>
                  </tr>
                )}
                {f.sources.map((s) => (
                  <tr
                    key={s.channelCode}
                    className="border-b border-border last:border-0 transition-colors hover:bg-primary/[0.04]"
                  >
                    <th scope="row" className="px-5 py-3 text-left font-medium sm:px-6">
                      {s.displayName}
                    </th>
                    <td className="px-3 py-3 text-right tabular-nums">
                      {formatMoney(s.spend, { compact: true })}
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums">{formatCount(s.leads)}</td>
                    <td className="px-3 py-3 text-right tabular-nums">{formatCount(s.booked)}</td>
                    <td className="px-5 py-3 text-right tabular-nums sm:px-6">
                      {formatMoney(s.costPerBooked)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardBody>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="reveal" style={{ '--i': 3 } as CSSProperties}>
          <CardHeader title="Front desk summary" />
          <CardBody>
            <dl className="grid grid-cols-2 gap-4">
              {[
                { label: 'Calls', value: formatCount(f.frontDesk.totalCalls) },
                { label: 'Answered', value: formatCount(f.frontDesk.answered) },
                { label: 'After hours', value: formatCount(f.frontDesk.afterHours) },
                { label: 'Booked', value: formatCount(f.frontDesk.appointmentsBooked) },
              ].map(({ label, value }) => (
                <div key={label}>
                  <dt className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                    {label}
                  </dt>
                  <dd className="mt-1 text-xl font-semibold tabular-nums">{value}</dd>
                </div>
              ))}
            </dl>
          </CardBody>
        </Card>

        <Card className="reveal" style={{ '--i': 4 } as CSSProperties}>
          <CardHeader title="Delivered this month" />
          <CardBody className="space-y-4">
            <div>
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                <CheckCircle2 className="h-3.5 w-3.5 text-positive-text" aria-hidden />
                Work completed
              </p>
              <ul className="mt-2 space-y-1.5 text-sm">
                {f.completedWork.length === 0 && (
                  <li className="text-muted-foreground">Nothing completed this period.</li>
                )}
                {f.completedWork.map((w) => (
                  <li key={`${w.title}-${w.completedAt}`}>{w.title}</li>
                ))}
              </ul>
            </div>
            <div className="border-t border-border pt-4">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                <FileText className="h-3.5 w-3.5 text-primary-text" aria-hidden />
                Content published
              </p>
              <ul className="mt-2 space-y-1.5 text-sm">
                {f.publishedContent.length === 0 && (
                  <li className="text-muted-foreground">Nothing published this period.</li>
                )}
                {f.publishedContent.map((c) => (
                  <li key={`${c.title}-${c.publishedAt}`}>{c.title}</li>
                ))}
              </ul>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* The three sections a practice owner actually reads. */}
      {r.keyLearning && (
        <Card className="reveal" style={{ '--i': 5 } as CSSProperties}>
          <CardHeader title="Key learning" />
          <CardBody>
            <p className="flex gap-3 text-sm leading-relaxed">
              <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-caution-text" aria-hidden />
              <span>{r.keyLearning}</span>
            </p>
          </CardBody>
        </Card>
      )}

      {(r.nextActions.length > 0 || r.decisionsRequired.length > 0) && (
        <div className="grid gap-6 lg:grid-cols-2">
          {r.nextActions.length > 0 && (
            <Card className="reveal" style={{ '--i': 6 } as CSSProperties}>
              <CardHeader title="Next actions" />
              <CardBody>
                <ul className="space-y-2.5 text-sm">
                  {r.nextActions.map((a) => (
                    <li key={a} className="flex gap-2.5">
                      <ListChecks
                        className="mt-0.5 h-4 w-4 shrink-0 text-primary-text"
                        aria-hidden
                      />
                      <span>{a}</span>
                    </li>
                  ))}
                </ul>
              </CardBody>
            </Card>
          )}

          {r.decisionsRequired.length > 0 && (
            <Card className="reveal border-caution/30" style={{ '--i': 7 } as CSSProperties}>
              <CardHeader title="Decisions required" />
              <CardBody>
                <ul className="space-y-2.5 text-sm">
                  {r.decisionsRequired.map((d) => (
                    <li key={d} className="flex gap-2.5">
                      <HelpCircle className="mt-0.5 h-4 w-4 shrink-0 text-caution-text" aria-hidden />
                      <span>{d}</span>
                    </li>
                  ))}
                </ul>
              </CardBody>
            </Card>
          )}
        </div>
      )}

      <p className="reveal text-xs text-muted-foreground" style={{ '--i': 8 } as CSSProperties}>
        This report is composed from the same figures as the rest of VisionOne rather than
        recalculating them, so it cannot disagree with the dashboard. Every figure is synthetic in
        Phase 1.
      </p>
    </div>
  );
}

function EmptyNotice({
  title,
  body,
  action,
}: {
  title: string;
  body: string;
  action?: ReactNode;
}) {
  return (
    <div className="reveal rounded-lg border border-border bg-card px-5 py-10 text-center">
      <p className="text-sm font-medium">{title}</p>
      <p className="mx-auto mt-1 max-w-sm text-sm text-muted-foreground">{body}</p>
      {action && <div className="mt-4 flex justify-center">{action}</div>}
    </div>
  );
}

function StartReportButton({ orgId, month }: { orgId: string; month: string }) {
  const { generate } = useReportAdmin(orgId);
  return (
    <div className="space-y-2">
      <button
        type="button"
        disabled={generate.isPending}
        onClick={() => generate.mutate(month)}
        className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-60"
      >
        {generate.isPending ? 'Starting…' : `Start the ${formatMonth(month)} report`}
      </button>
      {generate.error && (
        <p role="alert" className="text-xs text-critical">
          {generate.error.message}
        </p>
      )}
    </div>
  );
}

function ReportsSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-8 w-56" />
      <Skeleton className="h-40 w-full rounded-lg" />
      <Skeleton className="h-64 w-full rounded-lg" />
      <div className="grid gap-6 lg:grid-cols-2">
        <Skeleton className="h-44 w-full rounded-lg" />
        <Skeleton className="h-44 w-full rounded-lg" />
      </div>
    </div>
  );
}
