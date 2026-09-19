import type { CSSProperties } from 'react';
import { CalendarX, Moon, PhoneCall, PhoneMissed, PhoneForwarded, Repeat, Timer, Zap } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { KpiTile } from '@/components/KpiTile';
import { frontDeskDemo, type CallRow } from '@/lib/demoOperations';
import { formatCount, formatPercent } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * Screen 4 - Front Desk.
 *
 * Phase 1 runs on synthetic call data behind a VoiceProvider abstraction; no live telephony is
 * connected. The point of the screen is the after-hours gap: the hours when nobody answers are
 * where the highest-intent enquiries land.
 */

const outcomeTone: Record<CallRow['outcome'], 'neutral' | 'positive' | 'caution' | 'critical'> = {
  BOOKED: 'positive',
  ENQUIRY: 'neutral',
  TRANSFERRED: 'neutral',
  RESCHEDULED: 'caution',
  CANCELLED: 'caution',
  MISSED: 'critical',
};

/** Published hours: Mon-Thu 10-5, Fri 9-3. Everything else is the gap. */
const OPEN_FROM = 9;
const OPEN_TO = 17;

export function FrontDeskScreen() {
  const d = frontDeskDemo;
  const peak = Math.max(...d.hourly);
  const answerRate = (d.answered / d.totalCalls) * 100;

  return (
    <div className="space-y-6">
      <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">Front Desk</h1>
          <p className="text-sm text-muted-foreground">Call performance and booked outcomes</p>
        </div>
        <Badge tone="demo">Demo data</Badge>
      </header>

      <section aria-label="Call volume" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          { label: 'Total calls', value: formatCount(d.totalCalls), icon: PhoneCall, hint: 'this month' },
          { label: 'Answered', value: formatCount(d.answered), icon: Zap, hint: `${answerRate.toFixed(0)}% answer rate` },
          { label: 'Missed', value: formatCount(d.missed), icon: PhoneMissed, hint: 'went unanswered' },
          { label: 'After hours', value: formatCount(d.afterHours), icon: Moon, hint: `${((d.afterHours / d.totalCalls) * 100).toFixed(0)}% of all calls` },
        ].map((t, i) => (
          <div key={t.label} className="reveal" style={{ '--i': i + 1 } as CSSProperties}>
            <KpiTile label={t.label} value={t.value} icon={t.icon} hint={t.hint} />
          </div>
        ))}
      </section>

      {/* The argument the whole service rests on, drawn rather than asserted. */}
      <Card className="reveal glow-ring group" style={{ '--i': 5 } as CSSProperties}>
        <CardHeader
          title="When calls arrive"
          action={
            <span className="flex items-center gap-3 text-xs text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-[hsl(var(--primary))]" aria-hidden />
                Open
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-critical" aria-hidden />
                Nobody answers
              </span>
            </span>
          }
        />
        <CardBody>
          <div
            className="flex h-44 items-end gap-1"
            role="img"
            aria-label={`Call volume by hour of day. ${d.afterHours} of ${d.totalCalls} calls arrive outside published hours.`}
          >
            {d.hourly.map((count, hour) => {
              const open = hour >= OPEN_FROM && hour < OPEN_TO;
              return (
                <div key={hour} className="group/bar flex flex-1 flex-col items-center gap-1">
                  <div className="flex w-full flex-1 items-end">
                    <div
                      className={cn(
                        'w-full rounded-t-full transition-all duration-500 ease-out',
                        open
                          ? 'bg-gradient-to-t from-[hsl(var(--primary))] to-[hsl(199_89%_52%)]'
                          : 'bg-gradient-to-t from-critical/70 to-critical/40',
                        'group-hover/bar:brightness-110',
                      )}
                      style={{ height: `${Math.max((count / peak) * 100, 3)}%` }}
                      title={`${hour}:00 — ${count} calls`}
                    />
                  </div>
                  {hour % 4 === 0 && (
                    <span className="text-[10px] tabular-nums text-muted-foreground">{hour}</span>
                  )}
                </div>
              );
            })}
          </div>
          <p className="mt-4 text-sm text-muted-foreground">
            <span className="font-semibold text-foreground">
              {formatCount(d.afterHours)} of {formatCount(d.totalCalls)} calls
            </span>{' '}
            arrive when the practice is closed. That is the window the AI front desk covers.
          </p>
        </CardBody>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="reveal" style={{ '--i': 6 } as CSSProperties}>
          <CardHeader title="Appointment outcomes" />
          <CardBody>
            <dl className="grid grid-cols-2 gap-4">
              {[
                { label: 'Booked', value: d.appointmentsBooked, icon: Zap, tone: 'text-positive' },
                { label: 'Transferred', value: d.transferred, icon: PhoneForwarded, tone: '' },
                { label: 'Rescheduled', value: d.rescheduled, icon: Repeat, tone: 'text-caution' },
                { label: 'Cancelled', value: d.cancelled, icon: CalendarX, tone: 'text-critical' },
              ].map(({ label, value, icon: Icon, tone }) => (
                <div
                  key={label}
                  className="rounded-lg bg-muted/50 p-4 ring-1 ring-inset ring-border/60"
                >
                  <Icon className="h-4 w-4 text-muted-foreground" aria-hidden />
                  <dt className="mt-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                    {label}
                  </dt>
                  <dd className={cn('mt-1 text-2xl font-semibold tabular-nums tracking-[-0.02em]', tone)}>
                    {formatCount(value)}
                  </dd>
                </div>
              ))}
            </dl>
          </CardBody>
        </Card>

        <Card className="reveal" style={{ '--i': 7 } as CSSProperties}>
          <CardHeader title="Conversation quality" />
          <CardBody className="space-y-5">
            <div>
              <div className="flex items-baseline justify-between">
                <span className="text-sm text-muted-foreground">Booking conversion</span>
                <span className="text-2xl font-semibold tabular-nums tracking-[-0.02em]">
                  {formatPercent(d.bookingConversionPercent)}
                </span>
              </div>
              <div className="mt-2 h-2.5 overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-[hsl(var(--primary))] to-[hsl(186_85%_45%)] transition-[width] duration-700"
                  style={{ width: `${d.bookingConversionPercent}%` }}
                />
              </div>
              <p className="mt-2 text-xs text-muted-foreground">
                Share of answered calls that became an appointment.
              </p>
            </div>

            <div className="flex items-center gap-3 border-t border-border pt-4">
              <span className="grid h-10 w-10 place-items-center rounded-lg bg-primary-soft text-primary">
                <Timer className="h-4 w-4" aria-hidden />
              </span>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                  Average duration
                </p>
                <p className="text-lg font-semibold tabular-nums">
                  {Math.floor(d.averageDurationSeconds / 60)}m {d.averageDurationSeconds % 60}s
                </p>
              </div>
            </div>
          </CardBody>
        </Card>
      </div>

      <Card className="reveal" style={{ '--i': 8 } as CSSProperties}>
        <CardHeader
          title="Recent calls"
          action={<span className="text-xs text-muted-foreground">Numbers masked</span>}
        />
        <CardBody className="px-0 py-0">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[620px] text-sm">
              <caption className="sr-only">Recent call activity with outcome and handler</caption>
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th scope="col" className="px-5 py-3 font-semibold sm:px-6">Caller</th>
                  <th scope="col" className="px-3 py-3 font-semibold">Handled by</th>
                  <th scope="col" className="px-3 py-3 font-semibold">Outcome</th>
                  <th scope="col" className="px-3 py-3 font-semibold">When</th>
                  <th scope="col" className="px-5 py-3 text-right font-semibold sm:px-6">Duration</th>
                </tr>
              </thead>
              <tbody>
                {d.recent.map((c, i) => (
                  <tr
                    key={c.id}
                    className="reveal border-b border-border last:border-0 transition-colors hover:bg-primary/[0.04]"
                    style={{ '--i': i } as CSSProperties}
                  >
                    <th scope="row" className="px-5 py-3 text-left font-mono text-xs font-medium sm:px-6">
                      {c.maskedNumber}
                    </th>
                    <td className="px-3 py-3">
                      <span className={cn(c.handledBy === 'AI Front Desk' && 'font-medium text-primary')}>
                        {c.handledBy}
                      </span>
                    </td>
                    <td className="px-3 py-3">
                      <Badge tone={outcomeTone[c.outcome]}>{c.outcome.toLowerCase()}</Badge>
                    </td>
                    <td className="px-3 py-3 text-muted-foreground">
                      {new Date(c.startedAt).toLocaleString(undefined, {
                        month: 'short',
                        day: 'numeric',
                        hour: 'numeric',
                        minute: '2-digit',
                      })}
                      {c.afterHours && (
                        <Moon className="ml-1.5 inline h-3 w-3 text-caution" aria-label="after hours" />
                      )}
                    </td>
                    <td className="px-5 py-3 text-right tabular-nums sm:px-6">
                      {c.durationSeconds === 0
                        ? '—'
                        : `${Math.floor(c.durationSeconds / 60)}m ${c.durationSeconds % 60}s`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardBody>
      </Card>

      <p className="reveal text-xs text-muted-foreground" style={{ '--i': 9 } as CSSProperties}>
        Call records are masked before they reach this screen and carry no conversation content.
        Phase 1 runs on a demo voice adapter; a live provider connects in Phase 2 behind the same
        interface.
      </p>
    </div>
  );
}
