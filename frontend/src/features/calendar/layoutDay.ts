import type { CalendarAppointment } from './useCalendar';

/**
 * Works out where each appointment sits on a day's time grid.
 *
 * Kept separate from the component and free of React, because the only hard part of a day view is
 * this arithmetic and it deserves to be tested directly rather than through a rendered DOM.
 *
 * The problem is overlap. A single provider's calendar routinely has several appointments
 * starting at the same minute - Gary's has seven at 11:00 - and stacking them on top of each
 * other hides all but the last. So overlapping appointments are laid out side by side, which
 * means deciding how many columns a given moment needs and which column each appointment takes.
 */

/** Pixels per minute. Forty-five minutes is a comfortable block at this value. */
export const PX_PER_MIN = 1.1;

/** Shown even when the day is emptier than this, so an empty day still looks like a day. */
const MIN_HOUR = 8;
const MAX_HOUR = 18;

export interface PositionedAppointment {
  appointment: CalendarAppointment;
  /** Pixels from the top of the grid. */
  top: number;
  /** Pixels. Floored, so a five-minute appointment is still clickable. */
  height: number;
  /** 0-1 fraction of the grid's width. */
  left: number;
  width: number;
}

export interface DayLayout {
  startHour: number;
  endHour: number;
  hours: number[];
  /** Total grid height in pixels. */
  height: number;
  positioned: PositionedAppointment[];
}

/** "09:45" -> 585. Returns null for anything unparseable rather than placing it at midnight. */
export function minutesFromTime(time: string): number | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(time.trim());
  if (!match) {
    return null;
  }
  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (hours > 23 || minutes > 59) {
    return null;
  }
  return hours * 60 + minutes;
}

/**
 * Groups appointments into runs that overlap, then packs each run into columns.
 *
 * Greedy and left-to-right: each appointment takes the first column free at its start time. That
 * is not the minimum possible number of columns in every case, but it is stable - an appointment
 * does not jump sideways when an unrelated one later in the day changes - which matters more on a
 * screen somebody reads repeatedly than optimal packing does.
 */
function packOverlapping(
  items: { appointment: CalendarAppointment; start: number; end: number }[],
): PositionedAppointment[] {
  const sorted = [...items].sort((a, b) => a.start - b.start || a.end - b.end);
  const out: PositionedAppointment[] = [];

  let cluster: typeof sorted = [];
  let clusterEnd = -1;

  const flush = () => {
    if (cluster.length === 0) {
      return;
    }
    // columnEnds[i] is when the last appointment in column i finishes.
    const columnEnds: number[] = [];
    const assigned = cluster.map((item) => {
      let column = columnEnds.findIndex((end) => end <= item.start);
      if (column === -1) {
        column = columnEnds.length;
      }
      columnEnds[column] = item.end;
      return { item, column };
    });
    const columns = columnEnds.length;
    for (const { item, column } of assigned) {
      out.push({
        appointment: item.appointment,
        top: item.start,
        height: Math.max(item.end - item.start, 14),
        left: column / columns,
        width: 1 / columns,
      });
    }
    cluster = [];
    clusterEnd = -1;
  };

  for (const item of sorted) {
    // A new cluster starts where nothing currently open still covers this moment.
    if (cluster.length > 0 && item.start >= clusterEnd) {
      flush();
    }
    cluster.push(item);
    clusterEnd = Math.max(clusterEnd, item.end);
  }
  flush();
  return out;
}

/**
 * The grid's hour range plus every appointment's position within it.
 *
 * The range follows the data rather than being fixed at nine to five, because a practice that
 * starts at seven should not have its first appointment clipped off the top. It is widened to a
 * sensible minimum so that a day with one appointment does not render as a single sliver.
 */
export function layoutDay(appointments: CalendarAppointment[]): DayLayout {
  const measured = appointments
    .map((appointment) => {
      const start = minutesFromTime(appointment.time);
      if (start === null) {
        return null;
      }
      // A zero or missing duration would be an invisible block, which reads as a lost
      // appointment rather than a short one.
      const duration = appointment.duration > 0 ? appointment.duration : 15;
      return { appointment, start, end: start + duration };
    })
    .filter((x): x is { appointment: CalendarAppointment; start: number; end: number } => x !== null);

  const earliest = measured.reduce((min, m) => Math.min(min, m.start), MIN_HOUR * 60);
  const latest = measured.reduce((max, m) => Math.max(max, m.end), MAX_HOUR * 60);

  const startHour = Math.floor(earliest / 60);
  const endHour = Math.min(24, Math.ceil(latest / 60));
  const gridStart = startHour * 60;

  const hours: number[] = [];
  for (let h = startHour; h <= endHour; h += 1) {
    hours.push(h);
  }

  const packed = packOverlapping(measured).map((p) => ({
    ...p,
    top: (p.top - gridStart) * PX_PER_MIN,
    height: p.height * PX_PER_MIN,
  }));

  return {
    startHour,
    endHour,
    hours,
    height: (endHour - startHour) * 60 * PX_PER_MIN,
    positioned: packed,
  };
}
