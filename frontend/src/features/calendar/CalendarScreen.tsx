import { useMemo, type CSSProperties } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarCheck, CalendarX, Clock, Repeat } from 'lucide-react';
import { Card, CardBody } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { KpiTile, TILE_TONES } from '@/components/KpiTile';
import { StatusPill } from '@/components/ui/Chips';
import { MonthPicker, currentMonthKey } from '@/components/ui/MonthPicker';
import {
  appointmentsDemo,
  appointmentsOn,
  isoDate,
  type AppointmentStatus,
} from '@/lib/demoCalendar';
import { formatCount, formatMonth } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * Calendar - booked appointments by day.
 *
 * Reads `appointment_reference`, which holds a time, a broad service category and the channel that
 * produced the booking. Names are synthetic and there is no clinical detail: this shows that a
 * booking happened and where it came from, not what it was for.
 */

const statusTone: Record<AppointmentStatus, 'primary' | 'positive' | 'caution' | 'critical'> = {
  BOOKED: 'primary',
  ATTENDED: 'positive',
  RESCHEDULED: 'caution',
  CANCELLED: 'critical',
};

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export function CalendarScreen() {
  const [searchParams] = useSearchParams();
  const requested = searchParams.get('month') ?? currentMonthKey();
  // A hand-edited ?month= can be anything. Fall back rather than rendering NaN cells.
  const valid = /^\d{4}-(0[1-9]|1[0-2])$/.test(requested);
  const monthKey = valid ? requested : currentMonthKey();
  const year = Number(monthKey.slice(0, 4));
  const monthIndex = Number(monthKey.slice(5, 7)) - 1;

  const cells = useMemo(() => {
    const first = new Date(year, monthIndex, 1);
    // Monday-first: getDay() is Sunday-first, so Sunday becomes the seventh column.
    const lead = (first.getDay() + 6) % 7;
    const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();
    const out: (number | null)[] = Array.from({ length: lead }, () => null);
    for (let d = 1; d <= daysInMonth; d += 1) out.push(d);
    while (out.length % 7 !== 0) out.push(null);
    return out;
  }, [year, monthIndex]);

  const inMonth = appointmentsDemo.filter((a) => a.date.startsWith(monthKey));
  const counts = {
    booked: inMonth.filter((a) => a.status === 'BOOKED').length,
    attended: inMonth.filter((a) => a.status === 'ATTENDED').length,
    rescheduled: inMonth.filter((a) => a.status === 'RESCHEDULED').length,
    cancelled: inMonth.filter((a) => a.status === 'CANCELLED').length,
  };

  const todayIso = new Date().toISOString().slice(0, 10);

  return (
    <div className="space-y-5">
      <header className="reveal flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold tracking-[-0.01em]">Calendar</h1>
          <p className="mt-0.5 text-sm text-muted-foreground">
            Appointments booked in {formatMonth(`${monthKey}-01`)}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <MonthPicker />
          <Badge tone="demo">Demo data</Badge>
        </div>
      </header>

      <section aria-label="Appointment summary" className="grid grid-cols-2 gap-5 lg:grid-cols-4">
        {[
          { label: 'Booked', value: formatCount(counts.booked), icon: CalendarCheck },
          { label: 'Attended', value: formatCount(counts.attended), icon: Clock },
          { label: 'Rescheduled', value: formatCount(counts.rescheduled), icon: Repeat },
          { label: 'Cancelled', value: formatCount(counts.cancelled), icon: CalendarX },
        ].map((t, i) => (
          <div key={t.label} className="reveal" style={{ '--i': i } as CSSProperties}>
            <KpiTile
              label={t.label}
              value={t.value}
              icon={t.icon}
              tone={TILE_TONES[i % TILE_TONES.length]}
            />
          </div>
        ))}
      </section>

      <Card className="reveal" style={{ '--i': 4 } as CSSProperties}>
        <CardBody className="space-y-4">
          <div className="grid grid-cols-7 gap-2">
            {WEEKDAYS.map((w) => (
              <div
                key={w}
                className="pb-1 text-center text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground"
              >
                {w}
              </div>
            ))}

            {cells.map((day, i) => {
              if (day === null) {
                return <div key={`pad-${i}`} className="min-h-[92px] rounded-lg" />;
              }

              const iso = isoDate(year, monthIndex, day);
              const items = appointmentsOn(iso);
              const isToday = iso === todayIso;
              const has = items.length > 0;

              return (
                <div
                  key={iso}
                  className={cn(
                    'group relative min-h-[92px] rounded-lg border p-2 transition-all duration-150',
                    has
                      ? 'border-primary/20 bg-primary-soft/60 hover:-translate-y-0.5 hover:border-primary/40 hover:elev-md'
                      : 'border-border bg-card',
                    isToday && 'ring-2 ring-primary ring-offset-1',
                  )}
                >
                  <div className="flex items-center justify-between">
                    <span
                      className={cn(
                        'text-sm font-semibold tabular-nums',
                        has ? 'text-primary-text' : 'text-muted-foreground',
                      )}
                    >
                      {day}
                    </span>
                    {has && (
                      <span className="rounded-full bg-primary px-1.5 py-0.5 text-[10px] font-bold text-primary-foreground tabular-nums">
                        {items.length}
                      </span>
                    )}
                  </div>

                  {/* Two names fit in the cell; the rest are in the hover card. */}
                  {has && (
                    <ul className="mt-1.5 space-y-1">
                      {items.slice(0, 2).map((a) => (
                        <li
                          key={a.id}
                          className="truncate text-[11px] leading-tight text-muted-foreground"
                        >
                          <span className="font-semibold text-foreground">{a.time}</span>{' '}
                          {a.patient.split(' ')[0]}
                        </li>
                      ))}
                      {items.length > 2 && (
                        <li className="text-[11px] font-semibold text-primary-text">
                          +{items.length - 2} more
                        </li>
                      )}
                    </ul>
                  )}

                  {/* Hover card: who, when, what category, and which channel produced it. */}
                  {has && (
                    <div className="pointer-events-none absolute left-1/2 top-full z-30 mt-1 hidden w-64 -translate-x-1/2 rounded-lg border border-border bg-card p-3 elev-lg group-hover:block">
                      <p className="text-xs font-bold">
                        {new Date(year, monthIndex, day).toLocaleDateString(undefined, {
                          weekday: 'long',
                          day: 'numeric',
                          month: 'long',
                        })}
                      </p>
                      <ul className="mt-2 space-y-2">
                        {items.map((a) => (
                          <li
                            key={a.id}
                            className="border-t border-border pt-2 first:border-0 first:pt-0"
                          >
                            <div className="flex items-baseline justify-between gap-2">
                              <span className="text-xs font-semibold tabular-nums">{a.time}</span>
                              <StatusPill tone={statusTone[a.status]}>
                                {a.status.toLowerCase()}
                              </StatusPill>
                            </div>
                            <p className="mt-0.5 text-xs font-semibold">{a.patient}</p>
                            <p className="text-[11px] text-muted-foreground">
                              {a.serviceInterest} · {a.duration} min
                            </p>
                            <p className="text-[11px] text-muted-foreground">via {a.source}</p>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {inMonth.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">
              No appointments booked in this month.
            </p>
          )}
        </CardBody>
      </Card>

      <p className="reveal text-xs text-muted-foreground" style={{ '--i': 5 } as CSSProperties}>
        Names are synthetic and no clinical information is stored. The calendar records that a
        booking happened and which channel produced it, not what it was for. A live scheduling
        connection arrives in Phase 2 behind the SchedulingProvider interface.
      </p>
    </div>
  );
}
