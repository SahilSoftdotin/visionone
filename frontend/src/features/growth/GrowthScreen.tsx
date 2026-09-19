import { Activity, Radio, Target, TrendingUp, Wallet } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { KpiTile, TILE_TONES } from '@/components/KpiTile';
import { growthDemo } from '@/lib/demoData';
import { formatCount, formatMoney, formatMonth } from '@/lib/format';
import { useCountUp } from '@/lib/useCountUp';
import { cn } from '@/lib/utils';

/**
 * Screen 2 - Growth.
 *
 * Plan vs actual, where the money went, and what it produced. One screen rather than a separate
 * application per marketing channel: a channel is a row, not a product.
 *
 * Every figure here is synthetic. See lib/demoData.ts.
 */
export function GrowthScreen() {
  const plan = growthDemo;
  const util = useCountUp(plan.utilizationPercent);

  return (
    <div className="space-y-6">
      <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">Growth</h1>
          <p className="text-sm text-muted-foreground">
            {formatMonth(plan.periodMonth)} · plan {plan.status.toLowerCase()}
          </p>
        </div>
        <Badge tone="demo">Demo data</Badge>
      </header>

      {/* The one figure this screen exists to answer: is the budget on track? */}
      <section
        className="reveal group relative overflow-hidden rounded-lg p-6"
        style={{ '--i': 1 } as React.CSSProperties}
      >
        <div className="grid gap-6 lg:grid-cols-[1.1fr_1fr] lg:items-center">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.1em] text-muted-foreground">
              Monthly growth plan
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <span className="text-[40px] font-semibold leading-none tabular-nums tracking-[-0.03em]">
                {formatMoney(plan.actualTotal, { compact: true })}
              </span>
              <span className="pb-1 text-sm text-muted-foreground">
                of {formatMoney(plan.plannedTotal, { compact: true })} planned
              </span>
            </div>

            <div className="mt-5">
              <div className="mb-1.5 flex justify-between text-xs font-medium">
                <span className="text-muted-foreground">Budget used</span>
                <span className="tabular-nums text-foreground">{util.toFixed(0)}%</span>
              </div>
              <div className="h-3 overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-[hsl(var(--primary))] to-[hsl(186_85%_45%)] transition-[width] duration-700 ease-out"
                  style={{ width: `${Math.min(util, 100)}%` }}
                />
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                {formatMoney(plan.remaining, { compact: true })} remaining with 11 days of the month
                left.
              </p>
            </div>
          </div>

          <dl className="grid grid-cols-3 gap-4">
            {[
              {
                label: 'Planned',
                value: formatMoney(plan.plannedTotal, { compact: true }),
                icon: Target,
              },
              {
                label: 'Spent',
                value: formatMoney(plan.actualTotal, { compact: true }),
                icon: Wallet,
              },
              {
                label: 'Remaining',
                value: formatMoney(plan.remaining, { compact: true }),
                icon: Activity,
              },
            ].map(({ label, value, icon: Icon }) => (
              <div
                key={label}
                className="rounded-lg bg-primary-soft/60 p-3.5 ring-1 ring-inset ring-primary/10"
              >
                <Icon className="h-4 w-4 text-primary-text" aria-hidden />
                <dt className="mt-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                  {label}
                </dt>
                <dd className="mt-1 text-lg font-semibold tabular-nums tracking-[-0.02em]">
                  {value}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      <section aria-label="Growth performance" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          { label: 'Leads produced', value: formatCount(totalLeads(plan)), icon: TrendingUp },
          { label: 'Booked from spend', value: formatCount(totalBooked(plan)), icon: Target },
          { label: 'Blended cost / lead', value: formatMoney(blendedCpl(plan)), icon: Wallet },
          {
            label: 'Active campaigns',
            value: formatCount(plan.campaigns.filter((c) => c.status === 'ACTIVE').length),
            icon: Radio,
          },
        ].map((tile, i) => (
          <div key={tile.label} className="reveal" style={{ '--i': i + 2 } as React.CSSProperties}>
            <KpiTile
              label={tile.label}
              value={tile.value}
              icon={tile.icon}
              hint="this month"
              tone={TILE_TONES[i % TILE_TONES.length]}
            />
          </div>
        ))}
      </section>

      <Card className="reveal" style={{ '--i': 6 } as React.CSSProperties}>
        <CardHeader
          title="Where the budget went"
          action={<span className="text-xs text-muted-foreground">Plan vs actual by channel</span>}
        />
        <CardBody className="space-y-4">
          {plan.allocations.map((a) => {
            const pct =
              a.planned.amountMinor === 0
                ? 0
                : Math.round((a.actual.amountMinor / a.planned.amountMinor) * 100);
            const over = pct > 100;
            return (
              <div key={a.channelCode} className="group/row">
                <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                  <span className="font-medium">{a.displayName}</span>
                  <span className="tabular-nums text-muted-foreground">
                    {formatMoney(a.actual, { compact: true })} /{' '}
                    {formatMoney(a.planned, { compact: true })}
                    <span
                      className={cn(
                        'ml-2 font-semibold',
                        over ? 'text-critical-text' : 'text-foreground',
                      )}
                    >
                      {pct}%
                    </span>
                  </span>
                </div>
                <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted">
                  <div
                    className={cn(
                      'h-full rounded-full transition-[width] duration-700 ease-out',
                      over
                        ? 'bg-critical'
                        : 'bg-gradient-to-r from-[hsl(var(--primary))] to-[hsl(199_89%_50%)]',
                    )}
                    style={{ width: `${Math.min(pct, 100)}%` }}
                  />
                </div>
                <div className="mt-1 flex gap-4 text-xs text-muted-foreground">
                  <span className="tabular-nums">{formatCount(a.leads)} leads</span>
                  <span className="tabular-nums">{formatCount(a.booked)} booked</span>
                  <span className="tabular-nums">CPL {formatMoney(a.costPerLead)}</span>
                  <span className="tabular-nums">CPA {formatMoney(a.costPerBooked)}</span>
                </div>
              </div>
            );
          })}
        </CardBody>
      </Card>

      <Card className="reveal" style={{ '--i': 7 } as React.CSSProperties}>
        <CardHeader title="Campaigns" />
        <CardBody className="px-0 py-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                    Campaign
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    Channel
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    Status
                  </th>
                  <th scope="col" className="px-3 py-3 text-right font-semibold">
                    Spend
                  </th>
                  <th scope="col" className="px-3 py-3 text-right font-semibold">
                    Leads
                  </th>
                  <th scope="col" className="px-5 py-3 text-right font-semibold sm:px-6">
                    Booked
                  </th>
                </tr>
              </thead>
              <tbody>
                {plan.campaigns.map((c) => (
                  <tr
                    key={c.id}
                    className="border-b border-border last:border-0 transition-colors hover:bg-primary/[0.04]"
                  >
                    <th scope="row" className="px-5 py-3 text-left font-medium sm:px-6">
                      {c.name}
                    </th>
                    <td className="px-3 py-3 text-muted-foreground">
                      {plan.allocations.find((a) => a.channelCode === c.channelCode)?.displayName ??
                        c.channelCode}
                    </td>
                    <td className="px-3 py-3">
                      <Badge
                        tone={
                          c.status === 'ACTIVE'
                            ? 'positive'
                            : c.status === 'PAUSED'
                              ? 'caution'
                              : 'neutral'
                        }
                      >
                        {c.status.toLowerCase()}
                      </Badge>
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums">
                      {formatMoney(c.spend, { compact: true })}
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums">{formatCount(c.leads)}</td>
                    <td className="px-5 py-3 text-right tabular-nums sm:px-6">
                      {formatCount(c.booked)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardBody>
      </Card>

      <p
        className="reveal text-xs text-muted-foreground"
        style={{ '--i': 8 } as React.CSSProperties}
      >
        Phase 1 uses manual and synthetic figures. Live Google Ads, Meta and Search Console
        connections arrive in Phase 2 and will replace this screen&rsquo;s source without changing
        its shape.
      </p>
    </div>
  );
}

function totalLeads(p: typeof growthDemo): number {
  return p.allocations.reduce((sum, a) => sum + a.leads, 0);
}

function totalBooked(p: typeof growthDemo): number {
  return p.allocations.reduce((sum, a) => sum + a.booked, 0);
}

function blendedCpl(p: typeof growthDemo) {
  const leads = totalLeads(p);
  if (leads === 0) return null;
  return { amountMinor: Math.round(p.actualTotal.amountMinor / leads), currency: 'USD' };
}
