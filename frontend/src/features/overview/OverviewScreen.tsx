import { useParams, useSearchParams } from 'react-router-dom';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { DataStateBoundary } from '@/components/ui/DataStateBoundary';
import { KpiTile } from '@/components/KpiTile';
import { FunnelChart } from '@/components/charts/FunnelChart';
import { SourceBarChart } from '@/components/charts/SourceBarChart';
import { formatCount, formatMoney, formatMonth, formatPercent } from '@/lib/format';
import { useOverview } from './useOverview';
import { OverviewSkeleton } from './OverviewSkeleton';
import { RecommendationCard } from './RecommendationCard';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * The most important screen in VisionOne.
 *
 * A practice owner should understand the month in about thirty seconds: what was invested, what
 * it produced, what we did, and what needs them.
 */
export function OverviewScreen() {
  const { orgId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const month = searchParams.get('month') ?? currentMonth();
  const { data, isLoading, error, refetch } = useOverview(orgId, month);

  return (
    <DataStateBoundary
      isLoading={isLoading}
      error={error}
      skeleton={<OverviewSkeleton />}
      onRetry={() => void refetch()}
    >
      {data && (
        <div className="space-y-6">
          <header className="flex flex-wrap items-baseline justify-between gap-2">
            <div>
              <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">{data.organizationName}</h1>
              <p className="text-sm text-muted-foreground">{formatMonth(data.periodMonth)}</p>
            </div>
            <Badge tone="demo">Demo data</Badge>
          </header>

          <section aria-label="Key performance indicators">
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <KpiTile
                label="Growth budget"
                value={formatMoney(data.kpis.monthlyGrowthBudget, { compact: true })}
                hint="planned this month"
              />
              <KpiTile
                label="Actual spend"
                value={formatMoney(data.kpis.actualSpend, { compact: true })}
                current={data.kpis.actualSpend.amountMinor}
                prior={data.kpis.priorMonth.actualSpend.amountMinor}
              />
              <KpiTile
                label="New leads"
                value={formatCount(data.kpis.newLeads)}
                current={data.kpis.newLeads}
                prior={data.kpis.priorMonth.newLeads}
              />
              <KpiTile
                label="Qualified leads"
                value={formatCount(data.kpis.qualifiedLeads)}
                current={data.kpis.qualifiedLeads}
                prior={data.kpis.priorMonth.qualifiedLeads}
              />
              <KpiTile
                label="Booked appointments"
                value={formatCount(data.kpis.bookedAppointments)}
                current={data.kpis.bookedAppointments}
                prior={data.kpis.priorMonth.bookedAppointments}
              />
              <KpiTile
                label="Cost per lead"
                value={formatMoney(data.kpis.costPerLead)}
                invertDirection
              />
              <KpiTile
                label="Cost per booked appt."
                value={formatMoney(data.kpis.costPerBookedAppointment)}
                invertDirection
              />
              <KpiTile
                label="Lead to book"
                value={formatPercent(data.kpis.leadToBookConversionPercent)}
                current={data.kpis.leadToBookConversionPercent}
                prior={data.kpis.priorMonth.leadToBookConversionPercent}
              />
            </div>
          </section>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader title="From enquiry to appointment" />
              <CardBody>
                <FunnelChart funnel={data.funnel} />
              </CardBody>
            </Card>

            <Card>
              <CardHeader title="Growth investment" />
              <CardBody className="space-y-4">
                <dl className="grid grid-cols-3 gap-3">
                  {[
                    { label: 'Planned', value: formatMoney(data.investment.planned, { compact: true }) },
                    { label: 'Spent', value: formatMoney(data.investment.actual, { compact: true }) },
                    { label: 'Remaining', value: formatMoney(data.investment.remaining, { compact: true }) },
                  ].map(({ label, value }) => (
                    <div key={label}>
                      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
                      <dd className="mt-1 text-lg font-semibold tabular-nums">{value}</dd>
                    </div>
                  ))}
                </dl>
                <div>
                  <div className="mb-1 flex justify-between text-xs text-muted-foreground">
                    <span>Budget used</span>
                    <span className="tabular-nums">{formatPercent(data.investment.utilizationPercent)}</span>
                  </div>
                  <div className="h-2.5 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-[hsl(var(--chart-2))]"
                      style={{ width: `${Math.min(data.investment.utilizationPercent, 100)}%` }}
                    />
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-3 border-t border-border pt-4">
                  {[
                    { label: 'Completed', value: data.visionActivity.completed, tone: 'text-positive' },
                    { label: 'In progress', value: data.visionActivity.inProgress, tone: '' },
                    {
                      label: 'Waiting for you',
                      value: data.visionActivity.waitingForClient,
                      tone: 'text-caution',
                    },
                  ].map(({ label, value, tone }) => (
                    <div key={label}>
                      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
                      <p className={`mt-1 text-lg font-semibold tabular-nums ${tone}`}>
                        {formatCount(value)}
                      </p>
                    </div>
                  ))}
                </div>
              </CardBody>
            </Card>
          </div>

          <Card>
            <CardHeader title="Where leads come from" />
            <CardBody className="space-y-5">
              <SourceBarChart sources={data.sourcePerformance} />
              <div className="-mx-4 overflow-x-auto sm:-mx-5">
                <table className="w-full min-w-[620px] text-sm">
                  <thead>
                    <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                      <th className="px-4 py-2 font-medium sm:px-5">Source</th>
                      <th className="px-3 py-2 text-right font-medium">Leads</th>
                      <th className="px-3 py-2 text-right font-medium">Qualified</th>
                      <th className="px-3 py-2 text-right font-medium">Booked</th>
                      <th className="px-3 py-2 text-right font-medium">Spend</th>
                      <th className="px-3 py-2 text-right font-medium">Cost / lead</th>
                      <th className="px-4 py-2 text-right font-medium sm:px-5">Cost / booked</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.sourcePerformance.map((source) => (
                      <tr key={source.channelCode} className="border-b border-border/60 last:border-0">
                        <td className="px-4 py-2.5 font-medium sm:px-5">{source.displayName}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{formatCount(source.leads)}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{formatCount(source.qualified)}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{formatCount(source.booked)}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">
                          {formatMoney(source.spend, { compact: true })}
                        </td>
                        <td className="px-3 py-2.5 text-right tabular-nums">
                          {formatMoney(source.costPerLead)}
                        </td>
                        <td className="px-4 py-2.5 text-right tabular-nums sm:px-5">
                          {formatMoney(source.costPerBookedAppointment)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardBody>
          </Card>

          {data.recommendation && <RecommendationCard recommendation={data.recommendation} />}
        </div>
      )}
    </DataStateBoundary>
  );
}
