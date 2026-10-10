import type { CSSProperties } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
} from 'recharts';
import {
  CalendarCheck,
  Coins,
  Percent,
  PhoneCall,
  Receipt,
  UserCheck,
  Users,
  Wallet,
} from 'lucide-react';
import { Card, CardBody } from '@/components/ui/Card';
import { DataStateBoundary } from '@/components/ui/DataStateBoundary';
import { KpiTile, TILE_TONES } from '@/components/KpiTile';
import { ArrowBadge, IconChip, PersonCell, StatusPill } from '@/components/ui/Chips';
import { MonthPicker, currentMonthKey } from '@/components/ui/MonthPicker';
import { ChartTooltip, hoverCursor } from '@/components/charts/ChartTooltip';
import { EM_DASH, formatCount, formatMoney, formatMonth, formatPercent } from '@/lib/format';
import { useOverview } from './useOverview';
import { OverviewSkeleton } from './OverviewSkeleton';
import { RecommendationCard } from './RecommendationCard';

/**
 * Overview, laid out as the Modernize "modern" dashboard: a row of tinted tiles, a wide primary
 * chart with a figure column beside it, a donut, a sparkline card, a solid-brand panel, and a
 * table with avatars and status pills.
 *
 * The composition is theirs; the data, the questions it answers and the artwork are ours.
 */

/** Card title with its subtitle, the pattern used on every panel of that page. */
function PanelHeading({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div>
      <h2 className="text-lg font-bold tracking-[-0.01em]">{title}</h2>
      {subtitle && <p className="mt-0.5 text-sm text-muted-foreground">{subtitle}</p>}
    </div>
  );
}

const statusTone: Record<string, 'neutral' | 'positive' | 'caution' | 'critical' | 'secondary'> = {
  NEW: 'caution',
  CONTACTED: 'neutral',
  QUALIFIED: 'secondary',
  APPOINTMENT_REQUESTED: 'caution',
  BOOKED: 'positive',
  ATTENDED: 'positive',
  NOT_CONVERTED: 'critical',
  DUPLICATE: 'neutral',
};

export function OverviewScreen() {
  const { orgId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const month = searchParams.get('month') ?? currentMonthKey();
  const { data, isLoading, error, refetch } = useOverview(orgId, month);

  return (
    <DataStateBoundary
      isLoading={isLoading}
      error={error}
      skeleton={<OverviewSkeleton />}
      onRetry={() => void refetch()}
    >
      {data && (
        <div className="space-y-5">
          <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
            <PanelHeading title="Overview" subtitle={formatMonth(data.periodMonth)} />
          </header>

          {/* Row 1 - six tinted tiles. */}
          <section
            aria-label="Key performance indicators"
            className="grid grid-cols-2 gap-5 sm:grid-cols-3 xl:grid-cols-6"
          >
            {[
              { label: 'New leads', value: formatCount(data.kpis.newLeads), icon: Users },
              { label: 'Qualified', value: formatCount(data.kpis.qualifiedLeads), icon: UserCheck },
              {
                label: 'Booked',
                value: formatCount(data.kpis.bookedAppointments),
                icon: CalendarCheck,
              },
              {
                label: 'Spend',
                value: formatMoney(data.kpis.actualSpend, { compact: true }),
                icon: Wallet,
              },
              { label: 'Cost / lead', value: formatMoney(data.kpis.costPerLead), icon: Coins },
              {
                label: 'Lead to book',
                value: formatPercent(data.kpis.leadToBookConversionPercent),
                icon: Percent,
              },
            ].map((t, i) => (
              <div key={t.label} className="reveal" style={{ '--i': i } as CSSProperties}>
                <KpiTile
                  label={t.label}
                  value={t.value}
                  icon={t.icon}
                  tone={TILE_TONES[i % TILE_TONES.length]}
                  hint=""
                />
              </div>
            ))}
          </section>

          {/* Row 2 - wide chart with a figure column, plus two stacked cards. */}
          <div className="grid gap-5 lg:grid-cols-3">
            <Card className="reveal lg:col-span-2" style={{ '--i': 1 } as CSSProperties}>
              <CardBody className="space-y-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <PanelHeading title="Growth investment" subtitle="Spend against leads produced" />
                  <MonthPicker />
                </div>

                <div className="grid gap-6 sm:grid-cols-[1.6fr_1fr] sm:items-center">
                  <div className="draw-in h-60 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart
                        data={data.sourcePerformance.map((s) => ({
                          name: s.displayName.split(' ')[0],
                          leads: s.leads,
                          booked: s.booked,
                        }))}
                        margin={{ top: 8, right: 4, bottom: 4, left: 4 }}
                        barGap={4}
                      >
                        <Tooltip
                          cursor={hoverCursor}
                          content={
                            <ChartTooltip
                              formatter={(v, n) => `${v} ${n === 'leads' ? 'leads' : 'booked'}`}
                            />
                          }
                        />
                        <XAxis
                          dataKey="name"
                          tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                          axisLine={false}
                          tickLine={false}
                        />
                        <Bar
                          dataKey="leads"
                          name="Leads"
                          fill="hsl(var(--chart-1))"
                          radius={[999, 999, 999, 999]}
                          maxBarSize={12}
                        />
                        <Bar
                          dataKey="booked"
                          name="Booked"
                          fill="hsl(var(--chart-4))"
                          radius={[999, 999, 999, 999]}
                          maxBarSize={12}
                        />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>

                  <div className="space-y-5">
                    <div className="flex items-center gap-3">
                      <IconChip icon={Wallet} tone="primary" />
                      <div>
                        <p className="text-2xl font-bold tabular-nums tracking-[-0.02em]">
                          {formatMoney(data.investment.actual, { compact: true })}
                        </p>
                        <p className="text-sm text-muted-foreground">Invested this month</p>
                      </div>
                    </div>

                    <ul className="space-y-3">
                      {[
                        {
                          dot: 'bg-[hsl(var(--chart-1))]',
                          label: 'Leads produced',
                          value: formatCount(data.kpis.newLeads),
                        },
                        {
                          dot: 'bg-[hsl(var(--chart-4))]',
                          label: 'Booked appointments',
                          value: formatCount(data.kpis.bookedAppointments),
                        },
                      ].map((row) => (
                        <li key={row.label} className="flex items-start gap-2.5">
                          <span
                            className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${row.dot}`}
                            aria-hidden
                          />
                          <span>
                            <span className="block text-sm text-muted-foreground">{row.label}</span>
                            <span className="block font-bold tabular-nums">{row.value}</span>
                          </span>
                        </li>
                      ))}
                    </ul>

                    <a
                      href={`/orgs/${orgId}/reports`}
                      className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary text-sm font-semibold text-primary-foreground transition-colors hover:brightness-95"
                    >
                      View full report
                    </a>
                  </div>
                </div>
              </CardBody>
            </Card>

            <div className="space-y-5">
              {/* Donut: where leads come from. */}
              <Card className="reveal" style={{ '--i': 2 } as CSSProperties}>
                <CardBody>
                  <PanelHeading title="Lead sources" />
                  <div className="mt-4 flex items-center justify-between gap-4">
                    <div>
                      <p className="text-2xl font-bold tabular-nums tracking-[-0.02em]">
                        {formatCount(data.kpis.newLeads)}
                      </p>
                      <div className="mt-2">
                        <ArrowBadge
                          up={data.kpis.newLeads >= data.kpis.priorMonth.newLeads}
                          label="vs last month"
                        />
                      </div>
                      <ul className="mt-4 space-y-1.5">
                        {data.sourcePerformance.slice(0, 2).map((s, i) => (
                          <li
                            key={s.channelCode}
                            className="flex items-center gap-2 text-sm text-muted-foreground"
                          >
                            <span
                              className="h-2 w-2 rounded-full"
                              style={{ background: `hsl(var(--chart-${i + 1}))` }}
                              aria-hidden
                            />
                            {s.displayName}
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div className="h-[120px] w-[120px] shrink-0">
                      <ResponsiveContainer width="100%" height="100%">
                        <PieChart>
                          <Tooltip content={<ChartTooltip formatter={(v) => `${v} leads`} />} />
                          <Pie
                            data={data.sourcePerformance.map((s) => ({
                              name: s.displayName,
                              value: s.leads,
                            }))}
                            dataKey="value"
                            innerRadius={38}
                            outerRadius={58}
                            paddingAngle={2}
                            stroke="none"
                          >
                            {data.sourcePerformance.map((_, i) => (
                              <Cell key={i} fill={`hsl(var(--chart-${(i % 6) + 1}))`} />
                            ))}
                          </Pie>
                        </PieChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                </CardBody>
              </Card>

              {/* Sparkline card, chart bleeding to the card edge. */}
              <Card className="reveal overflow-hidden" style={{ '--i': 3 } as CSSProperties}>
                <CardBody className="pb-0">
                  <div className="flex items-start justify-between">
                    <PanelHeading title="Calls answered" />
                    <IconChip icon={PhoneCall} tone="secondary" className="rounded-full" />
                  </div>
                  <p className="mt-3 text-2xl font-bold tabular-nums tracking-[-0.02em]">
                    {formatCount(data.frontDesk.summary.answered)}
                  </p>
                  <div className="mt-2">
                    <ArrowBadge up label={`${data.frontDesk.summary.afterHours} after hours`} />
                  </div>
                </CardBody>
                <div className="h-20 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={data.frontDesk.hourly.map((v, i) => ({ i, v }))}>
                      <Tooltip
                        cursor={hoverCursor}
                        content={
                          <ChartTooltip
                            labelFormatter={(l) => `${String(l).padStart(2, '0')}:00`}
                            formatter={(v) => `${v} calls`}
                          />
                        }
                      />
                      <Area
                        type="monotone"
                        dataKey="v"
                        name="Calls"
                        stroke="hsl(var(--chart-4))"
                        strokeWidth={2}
                        fill="hsl(var(--chart-4) / 0.15)"
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              </Card>
            </div>
          </div>

          {/* Row 3 - funnel, two compact stats, and the solid brand panel. */}
          <div className="grid gap-5 lg:grid-cols-3">
            <Card className="reveal" style={{ '--i': 4 } as CSSProperties}>
              <CardBody>
                <PanelHeading title="Enquiry to appointment" subtitle="This month" />
                <div className="draw-in mt-5 h-44 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={[
                        { name: 'Leads', v: data.funnel.leads },
                        { name: 'Qualified', v: data.funnel.qualified },
                        { name: 'Requested', v: data.funnel.appointmentRequested },
                        { name: 'Booked', v: data.funnel.booked },
                      ]}
                      margin={{ top: 4, right: 4, bottom: 4, left: 4 }}
                    >
                      <Tooltip
                        cursor={hoverCursor}
                        content={<ChartTooltip formatter={(v) => `${v} leads`} />}
                      />
                      <XAxis
                        dataKey="name"
                        tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
                        axisLine={false}
                        tickLine={false}
                      />
                      <Bar dataKey="v" name="Leads" radius={[7, 7, 7, 7]} maxBarSize={34}>
                        {/* A colour per stage. Modernize greys every bar but the last, which on a
                            four-stage funnel left three identical grey columns - the chart read as
                            one number with padding either side rather than as a sequence. The ramp
                            runs cool to warm so it still reads as a progression rather than four
                            unrelated categories, and Booked keeps the brand colour because it is
                            the column this panel exists to report. All four tokens already carry
                            their own dark-mode values. */}
                        {[
                          'hsl(var(--chart-4))', // Leads - everything that came in
                          'hsl(var(--chart-2))', // Qualified
                          'hsl(var(--chart-3))', // Requested - waiting on a booking
                          'hsl(var(--chart-1))', // Booked - the outcome
                        ].map((fill, i) => (
                          <Cell key={i} fill={fill} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
                <div className="mt-4 grid grid-cols-2 gap-4 border-t border-border pt-4">
                  <div className="flex items-center gap-3">
                    <IconChip icon={Receipt} tone="primary" />
                    <div>
                      <p className="text-sm text-muted-foreground">Cost / booked</p>
                      <p className="font-bold tabular-nums">
                        {formatMoney(data.kpis.costPerBookedAppointment)}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <IconChip icon={Percent} tone="positive" />
                    <div>
                      <p className="text-sm text-muted-foreground">Conversion</p>
                      <p className="font-bold tabular-nums">
                        {formatPercent(data.kpis.leadToBookConversionPercent)}
                      </p>
                    </div>
                  </div>
                </div>
              </CardBody>
            </Card>

            <div className="grid gap-5 sm:grid-cols-2">
              {[
                {
                  title: 'Budget used',
                  value: formatPercent(data.investment.utilizationPercent),
                  up: true,
                  hint: `${formatMoney(data.investment.remaining, { compact: true })} left`,
                },
                {
                  title: 'Qualified rate',
                  value: formatPercent(
                    data.kpis.newLeads
                      ? (data.kpis.qualifiedLeads / data.kpis.newLeads) * 100
                      : null,
                  ),
                  up: data.kpis.qualifiedLeads >= data.kpis.priorMonth.qualifiedLeads,
                  hint: 'of all leads',
                },
              ].map((s, i) => (
                <Card key={s.title} className="reveal" style={{ '--i': 5 + i } as CSSProperties}>
                  <CardBody>
                    <p className="text-sm text-muted-foreground">{s.title}</p>
                    <p className="mt-1 text-2xl font-bold tabular-nums tracking-[-0.02em]">
                      {s.value}
                    </p>
                    <div className="mt-2">
                      <ArrowBadge up={s.up} label={s.hint} />
                    </div>
                  </CardBody>
                </Card>
              ))}
            </div>

            {/* The solid-brand panel, with a white inner card of progress rows. */}
            <Card
              className="reveal border-0 bg-primary text-primary-foreground"
              style={{ '--i': 7 } as CSSProperties}
            >
              <CardBody className="space-y-5">
                <div>
                  <h2 className="text-lg font-bold tracking-[-0.01em]">Best performing channels</h2>
                  <p className="mt-0.5 text-sm text-primary-foreground/80">
                    Share of booked appointments
                  </p>
                </div>

                <div className="space-y-4 rounded-lg bg-card p-4">
                  {data.sourcePerformance
                    .slice()
                    .sort((a, b) => b.booked - a.booked)
                    .slice(0, 3)
                    .map((s) => {
                      const total =
                        data.sourcePerformance.reduce((sum, x) => sum + x.booked, 0) || 1;
                      const pct = Math.round((s.booked / total) * 100);
                      return (
                        <div key={s.channelCode}>
                          <div className="flex items-baseline justify-between gap-2">
                            <div className="min-w-0">
                              <p className="truncate text-sm font-semibold text-foreground">
                                {s.displayName}
                              </p>
                              <p className="text-xs tabular-nums text-muted-foreground">
                                {formatMoney(s.spend, { compact: true })} spent
                              </p>
                            </div>
                            <StatusPill tone="primary">{pct}%</StatusPill>
                          </div>
                          <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
                            <div
                              className="h-full rounded-full bg-primary transition-[width] duration-700"
                              style={{ width: `${pct}%` }}
                            />
                          </div>
                        </div>
                      );
                    })}
                </div>
              </CardBody>
            </Card>
          </div>

          {/* Row 4 - the table with avatars and status pills. */}
          <Card className="reveal" style={{ '--i': 8 } as CSSProperties}>
            <CardBody className="space-y-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <PanelHeading
                  title="Recent leads"
                  subtitle="Latest enquiries and where they stand"
                />
                <MonthPicker />
              </div>

              <div className="-mx-5 overflow-x-auto sm:-mx-6">
                <table className="w-full min-w-[620px] text-sm">
                  <caption className="sr-only">
                    Recent leads with source, status and response time
                  </caption>
                  <thead>
                    <tr className="border-b border-border text-left text-muted-foreground">
                      <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                        Lead
                      </th>
                      <th scope="col" className="px-3 py-3 font-semibold">
                        Source
                      </th>
                      <th scope="col" className="px-3 py-3 font-semibold">
                        Status
                      </th>
                      <th scope="col" className="px-5 py-3 text-right font-semibold sm:px-6">
                        Response
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentLeads.length === 0 && (
                      <tr>
                        <td
                          colSpan={4}
                          className="px-5 py-6 text-center text-muted-foreground sm:px-6"
                        >
                          No leads yet this period.
                        </td>
                      </tr>
                    )}
                    {data.recentLeads.map((l) => (
                      <tr key={l.id} className="border-b border-border last:border-0">
                        <td className="px-5 py-3.5 sm:px-6">
                          <PersonCell name={l.name} role={l.serviceInterest} />
                        </td>
                        <td className="px-3 py-3.5 text-muted-foreground">{l.source}</td>
                        <td className="px-3 py-3.5">
                          <StatusPill tone={statusTone[l.status] ?? 'neutral'}>
                            {l.status.toLowerCase().replace(/_/g, ' ')}
                          </StatusPill>
                        </td>
                        <td className="px-5 py-3.5 text-right tabular-nums sm:px-6">
                          {l.responseMinutes === null ? EM_DASH : `${l.responseMinutes}m`}
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
