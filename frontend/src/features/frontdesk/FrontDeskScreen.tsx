import type { CSSProperties } from 'react';
import {
  CalendarX,
  Moon,
  PhoneCall,
  PhoneMissed,
  PhoneForwarded,
  Repeat,
  Timer,
  Zap,
} from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { KpiTile, TILE_TONES } from '@/components/KpiTile';
import { useParams, useSearchParams } from 'react-router-dom';
import { StatusFilter } from '@/components/ui/StatusFilter';
import { useUrlFilter } from '@/lib/useUrlFilter';
import { DataStateBoundary } from '@/components/ui/DataStateBoundary';
import { Skeleton } from '@/components/ui/Skeleton';
import { MonthPicker, currentMonthKey } from '@/components/ui/MonthPicker';
import { useFrontDesk } from './useFrontDesk';
import { formatCount, formatPercent } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * Screen 4 - Front Desk.
 *
 * Phase 1 runs on synthetic call data behind a VoiceProvider abstraction; no live telephony is
 * connected. The point of the screen is the after-hours gap: the hours when nobody answers are
 * where the highest-intent enquiries land.
 */

// The database distinguishes more outcomes than the fixture did, so every one has a tone.
const outcomeTone: Record<string, 'neutral' | 'positive' | 'caution' | 'critical'> = {
  BOOKED: 'positive',
  ENQUIRY_ANSWERED: 'neutral',
  MESSAGE_TAKEN: 'neutral',
  TRANSFERRED: 'neutral',
  RESCHEDULED: 'caution',
  CANCELLED: 'caution',
  MISSED: 'critical',
  VOICEMAIL: 'critical',
};

/** Declaration order, matching CallOutcome on the server, so the pill row is stable. */
const OUTCOME_ORDER = [
  'BOOKED',
  'ENQUIRY_ANSWERED',
  'MESSAGE_TAKEN',
  'TRANSFERRED',
  'MISSED',
  'VOICEMAIL',
  'CANCELLED',
  'RESCHEDULED',
] as const;

type Outcome = (typeof OUTCOME_ORDER)[number];

/** Published hours: Mon-Thu 10-5, Fri 9-3. Everything else is the gap. */
const OPEN_FROM = 9;
const OPEN_TO = 17;

export function FrontDeskScreen() {
  const { orgId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const month = searchParams.get('month') ?? currentMonthKey();
  const [outcome, setOutcome] = useUrlFilter<Outcome>('outcome', OUTCOME_ORDER);
  const { data, isLoading, error, refetch } = useFrontDesk(orgId, month, outcome);

  return (
    <DataStateBoundary
      isLoading={isLoading}
      error={error}
      skeleton={<FrontDeskSkeleton />}
      onRetry={() => void refetch()}
    >
      {data && <FrontDeskView d={data} outcome={outcome} onOutcome={setOutcome} />}
    </DataStateBoundary>
  );
}

function FrontDeskSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-9 w-40" />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <Skeleton key={i} className="h-[104px]" />
        ))}
      </div>
      <Skeleton className="h-56" />
      <Skeleton className="h-72" />
    </div>
  );
}

type FrontDeskData = NonNullable<ReturnType<typeof useFrontDesk>['data']>;

function FrontDeskView({
  d,
  outcome,
  onOutcome,
}: {
  d: FrontDeskData;
  outcome: Outcome | null;
  onOutcome: (next: Outcome | null) => void;
}) {
  const peak = Math.max(...d.hourly, 1);

  // The server sends a list; the filter wants a lookup. Counts are the whole month either way.
  const outcomeCounts = Object.fromEntries(
    d.outcomeCounts.map((o) => [o.outcome, o.count]),
  ) as Partial<Record<Outcome, number>>;
  // A month with no calls is not a zero answer rate, it is no answer rate. The same is true of
  // the after-hours share, which did divide by zero and rendered the string "NaN% of all calls"
  // under the tile the moment the telephony provider was disconnected.
  const answerRate = d.totalCalls === 0 ? null : (d.answered / d.totalCalls) * 100;
  const afterHoursRate = d.totalCalls === 0 ? null : (d.afterHours / d.totalCalls) * 100;

  return (
    <div className="space-y-6">
      <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">Front Desk</h1>
          <p className="text-sm text-muted-foreground">Call performance and booked outcomes</p>
        </div>
        <div className="flex items-center gap-2">
          <MonthPicker />
          <Badge tone={d.dataSource === 'LIVE' ? 'positive' : 'pending'}>
            {d.dataSource === 'LIVE'
              ? 'Live provider'
              : d.dataSource === 'DEMO'
                ? 'Not connected'
                : d.dataSource === 'ERROR'
                  ? 'Provider error'
                  : 'Not connected'}
          </Badge>
        </div>
      </header>

      <section aria-label="Call volume" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          {
            label: 'Total calls',
            value: formatCount(d.totalCalls),
            icon: PhoneCall,
            hint: 'this month',
          },
          {
            label: 'Answered',
            value: formatCount(d.answered),
            icon: Zap,
            hint: answerRate === null ? 'no calls yet' : `${answerRate.toFixed(0)}% answer rate`,
          },
          {
            // "Unanswered", not "Missed": this counts every call nobody picked up, which is
            // MISSED plus VOICEMAIL. Calling it Missed put a different number under the same word
            // as the MISSED filter pill directly below it.
            label: 'Unanswered',
            value: formatCount(d.missed),
            icon: PhoneMissed,
            hint: 'missed or voicemail',
          },
          {
            label: 'After hours',
            value: formatCount(d.afterHours),
            icon: Moon,
            hint:
              afterHoursRate === null
                ? 'no calls yet'
                : `${afterHoursRate.toFixed(0)}% of all calls`,
          },
        ].map((t, i) => (
          <div key={t.label} className="reveal" style={{ '--i': i + 1 } as CSSProperties}>
            <KpiTile
              label={t.label}
              value={t.value}
              icon={t.icon}
              hint={t.hint}
              tone={TILE_TONES[i % TILE_TONES.length]}
            />
          </div>
        ))}
      </section>

      {/* The argument the whole service rests on, drawn rather than asserted. */}
      <Card className="reveal" style={{ '--i': 5 } as CSSProperties}>
        <CardHeader
          title="When calls arrive"
          action={
            <span className="flex items-center gap-3 text-xs text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-[hsl(var(--primary))]" aria-hidden />
                Opening hours
              </span>
              <span className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-critical" aria-hidden />
                Outside hours
              </span>
            </span>
          }
        />
        <CardBody>
          {/* Columns stretch to the full chart height and each bar is positioned absolutely inside
              its column. A percentage height only resolves against a parent with a definite
              height; with items-end the columns shrank to their content and every bar drew at
              zero height, leaving only the hour labels. */}
          <div
            className="flex h-44 gap-1"
            role="img"
            aria-label={`Call volume by hour of day. ${d.afterHours} of ${d.totalCalls} calls arrive outside published hours.`}
          >
            {d.hourly.map((count, hour) => {
              const open = hour >= OPEN_FROM && hour < OPEN_TO;
              return (
                <div
                  key={hour}
                  className="group/bar relative flex h-full flex-1 flex-col items-center gap-1"
                >
                  {/* A styled tooltip rather than the native title attribute, which waits a
                      second before appearing and cannot be themed. */}
                  <div className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 hidden -translate-x-1/2 whitespace-nowrap rounded-lg border border-border bg-card px-3 py-2 elev-lg group-hover/bar:block">
                    <p className="text-xs font-semibold tabular-nums">
                      {String(hour).padStart(2, '0')}:00
                    </p>
                    <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
                      <span
                        className={cn(
                          'h-2 w-2 rounded-full',
                          open ? 'bg-[hsl(var(--primary))]' : 'bg-critical',
                        )}
                        aria-hidden
                      />
                      {count} {count === 1 ? 'call' : 'calls'}
                      <span className="font-semibold text-foreground">
                        {/* Time of day, not outcome: open-hours calls can be missed, and the
                            AI Front Desk answers some that arrive outside them. */}
                        {open ? '· opening hours' : '· outside hours'}
                      </span>
                    </p>
                  </div>

                  <div className="relative w-full flex-1">
                    <div
                      className={cn(
                        'absolute inset-x-0 bottom-0 rounded-t-full transition-all duration-200 ease-out',
                        open
                          ? 'bg-gradient-to-t from-[hsl(var(--primary))] to-[hsl(199_89%_52%)]'
                          : 'bg-gradient-to-t from-critical/70 to-critical/40',
                        'group-hover/bar:brightness-110 group-hover/bar:saturate-150',
                      )}
                      style={{ height: `${Math.max((count / peak) * 100, 3)}%` }}
                    />
                  </div>
                  {/* Every column keeps a label row, so all bars share one baseline. */}
                  <span className="h-3.5 text-[10px] leading-none tabular-nums text-muted-foreground">
                    {hour % 4 === 0 ? hour : ''}
                  </span>
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
                {
                  label: 'Booked',
                  value: d.appointmentsBooked,
                  icon: Zap,
                  tone: 'text-positive-text',
                },
                { label: 'Transferred', value: d.transferred, icon: PhoneForwarded, tone: '' },
                {
                  label: 'Rescheduled',
                  value: d.rescheduled,
                  icon: Repeat,
                  tone: 'text-caution-text',
                },
                {
                  label: 'Cancelled',
                  value: d.cancelled,
                  icon: CalendarX,
                  tone: 'text-critical-text',
                },
              ].map(({ label, value, icon: Icon, tone }) => (
                <div
                  key={label}
                  className="rounded-lg bg-muted/50 p-4 ring-1 ring-inset ring-border/60"
                >
                  <Icon className="h-4 w-4 text-muted-foreground" aria-hidden />
                  <dt className="mt-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                    {label}
                  </dt>
                  <dd
                    className={cn(
                      'mt-1 text-2xl font-semibold tabular-nums tracking-[-0.02em]',
                      tone,
                    )}
                  >
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
              <span className="grid h-10 w-10 place-items-center rounded-lg bg-primary-soft text-primary-text">
                <Timer className="h-4 w-4" aria-hidden />
              </span>
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                  Average duration
                </p>
                <p className="text-lg font-semibold tabular-nums">
                  {d.averageDurationSeconds === null
                    ? '\u2014'
                    : `${Math.floor(d.averageDurationSeconds / 60)}m ${d.averageDurationSeconds % 60}s`}
                </p>
              </div>
            </div>
          </CardBody>
        </Card>
      </div>

      <Card className="reveal" style={{ '--i': 8 } as CSSProperties}>
        <CardHeader
          title="Recent calls"
          action={
            <span className="text-xs text-muted-foreground">
              The {d.recent.length} most recent · numbers masked
            </span>
          }
        />
        <CardBody className="px-0 py-0">
          <div className="px-5 pb-3 sm:px-6">
            <StatusFilter
              label="Filter recent calls by outcome"
              order={OUTCOME_ORDER}
              counts={outcomeCounts}
              tones={outcomeTone as Record<Outcome, 'neutral' | 'positive' | 'caution' | 'critical'>}
              total={d.totalCalls}
              selected={outcome}
              onSelect={onOutcome}
            />
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[620px] text-sm">
              <caption className="sr-only">Recent call activity with outcome and handler</caption>
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-muted-foreground">
                  <th scope="col" className="px-5 py-3 font-semibold sm:px-6">
                    Caller
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    Handled by
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    Outcome
                  </th>
                  <th scope="col" className="px-3 py-3 font-semibold">
                    When
                  </th>
                  <th scope="col" className="px-5 py-3 text-right font-semibold sm:px-6">
                    Duration
                  </th>
                </tr>
              </thead>
              <tbody>
                {d.recent.map((c, i) => (
                  <tr
                    key={c.id}
                    className="reveal border-b border-border last:border-0 transition-colors hover:bg-primary/[0.04]"
                    style={{ '--i': i } as CSSProperties}
                  >
                    <th
                      scope="row"
                      className="px-5 py-3 text-left font-mono text-xs font-medium sm:px-6"
                    >
                      {c.maskedNumber}
                    </th>
                    <td className="px-3 py-3">
                      <span
                        className={cn(
                          c.handledBy === 'AI Front Desk' && 'font-medium text-primary-text',
                        )}
                      >
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
                        <Moon
                          className="ml-1.5 inline h-3 w-3 text-caution-text"
                          aria-label="after hours"
                        />
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
        The badge above states whether a live provider is connected.
      </p>
    </div>
  );
}
