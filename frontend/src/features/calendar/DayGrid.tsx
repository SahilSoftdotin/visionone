import { ChevronLeft, ChevronRight } from 'lucide-react';
import { StatusPill } from '@/components/ui/Chips';
import { cn } from '@/lib/utils';
import { layoutDay } from './layoutDay';
import type { CalendarAppointment } from './useCalendar';

/**
 * One day on a time axis, the way a scheduling system shows it.
 *
 * The month grid answers "how busy was September and where did it come from". It is the wrong
 * shape for "what does Thursday look like": at eighteen appointments a day average and
 * thirty-four at peak, a month cell can only show a count and two lines.
 *
 * Deliberately not a replacement for the month view. The two answer different questions and the
 * month one is what carries the attribution story this product exists for.
 *
 * <b>What it cannot show.</b> Healthie's own day view puts a patient name on every block.
 * VisionOne's cannot, because the .ics export carries no patient data at all - across 5,257 real
 * events its SUMMARY only ever reads "In Person", "Phone Call" or "Healthie Video Call". So the
 * blocks here are labelled by contact type and time. The shape of the day is honest; who is in it
 * is not something this feed knows.
 */

const statusTone: Record<string, 'primary' | 'positive' | 'caution' | 'critical'> = {
  BOOKED: 'primary',
  ATTENDED: 'positive',
  RESCHEDULED: 'caution',
  CANCELLED: 'critical',
  NO_SHOW: 'critical',
};

function hourLabel(hour: number): string {
  const suffix = hour < 12 ? 'AM' : 'PM';
  const twelve = hour % 12 === 0 ? 12 : hour % 12;
  return `${twelve}:00 ${suffix}`;
}

export interface DayGridProps {
  /** ISO date, used for the heading. */
  date: string;
  appointments: CalendarAppointment[];
  onPrev: () => void;
  onNext: () => void;
  onToday: () => void;
  /** The practice's own timezone, shown because the question always gets asked. */
  timezoneLabel?: string;
}

export function DayGrid({
  date,
  appointments,
  onPrev,
  onNext,
  onToday,
  timezoneLabel,
}: DayGridProps) {
  const layout = layoutDay(appointments);
  const heading = new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onPrev}
            aria-label="Previous day"
            className="grid h-8 w-8 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
          >
            <ChevronLeft className="h-4 w-4" aria-hidden />
          </button>
          <button
            type="button"
            onClick={onToday}
            className="min-h-8 rounded-md border border-border px-3 text-xs font-semibold transition-colors hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
          >
            Today
          </button>
          <button
            type="button"
            onClick={onNext}
            aria-label="Next day"
            className="grid h-8 w-8 place-items-center rounded-md text-muted-foreground transition-colors hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
          >
            <ChevronRight className="h-4 w-4" aria-hidden />
          </button>
          <p className="ml-2 text-sm font-semibold">{heading}</p>
        </div>
        <p className="text-xs text-muted-foreground">
          {appointments.length} {appointments.length === 1 ? 'appointment' : 'appointments'}
          {timezoneLabel ? ` · ${timezoneLabel}` : ''}
        </p>
      </div>

      {appointments.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border px-4 py-10 text-center text-sm text-muted-foreground">
          Nothing booked on this day.
        </p>
      ) : (
        // Scrolls rather than growing: a clinic running seven to six is over 600px of grid, and
        // the page already has a summary row above it.
        <div className="max-h-[560px] overflow-y-auto rounded-lg border border-border">
          <div className="relative flex">
            <div className="w-20 shrink-0 border-r border-border bg-muted/30">
              {layout.hours.map((hour) => (
                <div
                  key={hour}
                  style={{ height: 60 * 1.1 }}
                  className="relative -top-2 pr-2 text-right text-[11px] tabular-nums text-muted-foreground"
                >
                  {hourLabel(hour)}
                </div>
              ))}
            </div>

            <div className="relative flex-1" style={{ height: layout.height }}>
              {layout.hours.map((hour, index) => (
                <div
                  key={hour}
                  className="absolute inset-x-0 border-t border-border/60"
                  style={{ top: index * 60 * 1.1 }}
                  aria-hidden
                />
              ))}

              {layout.positioned.map(({ appointment, top, height, left, width }) => (
                <div
                  key={appointment.id}
                  title={`${appointment.time} · ${appointment.patient} · ${appointment.duration} min`}
                  className={cn(
                    'absolute overflow-hidden rounded-md border px-1.5 py-0.5 text-[11px] leading-tight',
                    appointment.status === 'CANCELLED' || appointment.status === 'NO_SHOW'
                      ? 'border-critical/30 bg-critical-soft/70 text-critical-text line-through'
                      : 'border-primary/30 bg-primary-soft text-primary-text',
                  )}
                  style={{
                    top,
                    height,
                    // Inset by a sliver so adjacent columns read as separate blocks.
                    left: `calc(${left * 100}% + 2px)`,
                    width: `calc(${width * 100}% - 4px)`,
                  }}
                >
                  <span className="font-semibold tabular-nums">{appointment.time}</span>{' '}
                  <span className="opacity-90">{appointment.patient}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
        <StatusPill tone={statusTone.BOOKED}>booked</StatusPill>
        <StatusPill tone={statusTone.CANCELLED}>cancelled</StatusPill>
        <span>
          Blocks are labelled by contact type. The scheduling export carries no patient names.
        </span>
      </div>
    </div>
  );
}
