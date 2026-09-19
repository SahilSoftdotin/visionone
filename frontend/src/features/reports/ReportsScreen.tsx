import type { CSSProperties } from 'react';
import { CheckCircle2, FileText, HelpCircle, Lightbulb, ListChecks } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { growthDemo, leadsDemo } from '@/lib/demoData';
import { contentDemo, frontDeskDemo, monthlyReportDemo, workDemo } from '@/lib/demoOperations';
import { formatCount, formatMoney, formatMonth, formatPercent } from '@/lib/format';

/**
 * Screen 6 - Reports.
 *
 * One executive monthly report, not a report builder. It assembles what the other screens already
 * hold rather than introducing new figures, so the report can never disagree with the dashboard.
 */
export function ReportsScreen() {
  const r = monthlyReportDemo;

  const qualified = leadsDemo.filter((l) =>
    ['QUALIFIED', 'APPOINTMENT_REQUESTED', 'BOOKED', 'ATTENDED'].includes(l.status),
  ).length;
  const completedWork = workDemo.filter((w) => w.status === 'COMPLETED');
  const publishedContent = contentDemo.filter((c) => c.status === 'PUBLISHED');
  const totalLeads = growthDemo.allocations.reduce((s, a) => s + a.leads, 0);
  const totalBooked = growthDemo.allocations.reduce((s, a) => s + a.booked, 0);

  return (
    <div className="space-y-6">
      <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">Monthly report</h1>
          <p className="text-sm text-muted-foreground">
            {formatMonth(r.periodMonth)} · {r.status.toLowerCase()}
          </p>
        </div>
        <Badge tone="demo">Demo data</Badge>
      </header>

      <section
        className="reveal relative overflow-hidden rounded-lg p-6"
        style={{ '--i': 1 } as CSSProperties}
      >
        <p className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">
          Growth investment
        </p>
        <div className="mt-3 flex flex-wrap items-end gap-3">
          <span className="text-[40px] font-semibold leading-none tabular-nums tracking-[-0.03em]">
            {formatMoney(growthDemo.actualTotal, { compact: true })}
          </span>
          <span className="pb-1 text-sm text-muted-foreground">
            invested of {formatMoney(growthDemo.plannedTotal, { compact: true })} planned
          </span>
        </div>

        <dl className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
          {[
            { label: 'Leads', value: formatCount(totalLeads) },
            { label: 'Qualified', value: formatCount(qualified) },
            { label: 'Appointments', value: formatCount(totalBooked) },
            { label: 'Utilisation', value: formatPercent(growthDemo.utilizationPercent) },
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
                {growthDemo.allocations.map((a) => (
                  <tr
                    key={a.channelCode}
                    className="border-b border-border last:border-0 transition-colors hover:bg-primary/[0.04]"
                  >
                    <th scope="row" className="px-5 py-3 text-left font-medium sm:px-6">
                      {a.displayName}
                    </th>
                    <td className="px-3 py-3 text-right tabular-nums">
                      {formatMoney(a.actual, { compact: true })}
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums">{formatCount(a.leads)}</td>
                    <td className="px-3 py-3 text-right tabular-nums">{formatCount(a.booked)}</td>
                    <td className="px-5 py-3 text-right tabular-nums sm:px-6">
                      {formatMoney(a.costPerBooked)}
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
                { label: 'Calls', value: formatCount(frontDeskDemo.totalCalls) },
                { label: 'Answered', value: formatCount(frontDeskDemo.answered) },
                { label: 'After hours', value: formatCount(frontDeskDemo.afterHours) },
                { label: 'Booked', value: formatCount(frontDeskDemo.appointmentsBooked) },
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
                {completedWork.length === 0 && (
                  <li className="text-muted-foreground">Nothing completed this period.</li>
                )}
                {completedWork.map((w) => (
                  <li key={w.id}>{w.title}</li>
                ))}
              </ul>
            </div>
            <div className="border-t border-border pt-4">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                <FileText className="h-3.5 w-3.5 text-primary-text" aria-hidden />
                Content published
              </p>
              <ul className="mt-2 space-y-1.5 text-sm">
                {publishedContent.length === 0 && (
                  <li className="text-muted-foreground">Nothing published this period.</li>
                )}
                {publishedContent.map((c) => (
                  <li key={c.id}>{c.title}</li>
                ))}
              </ul>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* The three sections a practice owner actually reads. */}
      <Card className="reveal" style={{ '--i': 5 } as CSSProperties}>
        <CardHeader title="Key learning" />
        <CardBody>
          <p className="flex gap-3 text-sm leading-relaxed">
            <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-caution-text" aria-hidden />
            <span>{r.keyLearning}</span>
          </p>
        </CardBody>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="reveal" style={{ '--i': 6 } as CSSProperties}>
          <CardHeader title="Next actions" />
          <CardBody>
            <ul className="space-y-2.5 text-sm">
              {r.nextActions.map((a) => (
                <li key={a} className="flex gap-2.5">
                  <ListChecks className="mt-0.5 h-4 w-4 shrink-0 text-primary-text" aria-hidden />
                  <span>{a}</span>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>

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
      </div>

      <p className="reveal text-xs text-muted-foreground" style={{ '--i': 8 } as CSSProperties}>
        This report reads from the same figures as the rest of VisionOne rather than recalculating
        them, so it cannot disagree with the dashboard. Every figure is synthetic in Phase 1.
      </p>
    </div>
  );
}
