import { describe, expect, it } from 'vitest';
import { layoutDay, minutesFromTime, PX_PER_MIN } from './layoutDay';
import type { CalendarAppointment } from './useCalendar';

/**
 * The day grid's arithmetic.
 *
 * Tested directly rather than through a rendered component because the only hard part of a day
 * view is deciding where overlapping blocks go, and a DOM assertion about pixel offsets proves
 * less about that than the numbers do.
 *
 * The density here is the real thing: Gary's calendar has seven appointments starting at 11:00.
 * Laid out naively they stack on top of each other and six of them are invisible.
 */

function appt(time: string, duration: number, id = time): CalendarAppointment {
  return {
    id,
    date: '2026-10-15',
    time,
    patient: 'In Person',
    channelId: null,
    serviceInterest: '',
    source: '',
    status: 'BOOKED',
    duration,
  };
}

describe('minutesFromTime', () => {
  it('reads HH:mm', () => {
    expect(minutesFromTime('09:45')).toBe(585);
    expect(minutesFromTime('00:00')).toBe(0);
    expect(minutesFromTime('23:59')).toBe(1439);
  });

  it('refuses anything else rather than placing it at midnight', () => {
    // A null is skipped by the caller. A zero would silently pile unparseable appointments at
    // the top of the grid, which looks like real data.
    expect(minutesFromTime('9:45 AM')).toBeNull();
    expect(minutesFromTime('25:00')).toBeNull();
    expect(minutesFromTime('09:99')).toBeNull();
    expect(minutesFromTime('')).toBeNull();
  });
});

describe('layoutDay', () => {
  it('places a single appointment at the right offset and height', () => {
    const layout = layoutDay([appt('10:00', 45)]);
    const block = layout.positioned[0];

    // The grid starts at 08:00 for a day this shape, so 10:00 is two hours down.
    expect(layout.startHour).toBe(8);
    expect(block.top).toBeCloseTo(120 * PX_PER_MIN);
    expect(block.height).toBeCloseTo(45 * PX_PER_MIN);
    expect(block.left).toBe(0);
    expect(block.width).toBe(1);
  });

  it('puts seven appointments at the same minute side by side', () => {
    // The real case. Stacked, six of these are hidden behind the seventh.
    const layout = layoutDay(
      Array.from({ length: 7 }, (_, i) => appt('11:00', 15, `a${i}`)),
    );

    expect(layout.positioned).toHaveLength(7);
    for (const block of layout.positioned) {
      expect(block.width).toBeCloseTo(1 / 7);
    }
    const lefts = layout.positioned.map((b) => Number(b.left.toFixed(4))).sort((a, b) => a - b);
    expect(new Set(lefts).size).toBe(7);
    expect(lefts[0]).toBe(0);
  });

  it('reuses a column once the earlier appointment has finished', () => {
    // 09:00-09:30 and 09:30-10:00 do not overlap, so they share a column and stay full width.
    const layout = layoutDay([appt('09:00', 30), appt('09:30', 30)]);

    expect(layout.positioned.every((b) => b.width === 1)).toBe(true);
    expect(layout.positioned.every((b) => b.left === 0)).toBe(true);
  });

  it('splits only the appointments that actually overlap', () => {
    const layout = layoutDay([appt('09:00', 60, 'long'), appt('09:30', 15, 'inside'), appt('14:00', 30, 'later')]);

    const byId = Object.fromEntries(layout.positioned.map((b) => [b.appointment.id, b]));
    expect(byId.long.width).toBeCloseTo(0.5);
    expect(byId.inside.width).toBeCloseTo(0.5);
    // The afternoon one is in its own cluster and keeps the full width.
    expect(byId.later.width).toBe(1);
  });

  it('treats touching appointments as not overlapping', () => {
    // 09:00-09:15 and 09:15-09:30 share an instant and nothing more. Splitting the width here
    // would halve every block on a back-to-back day for no reason.
    const layout = layoutDay([appt('09:00', 15), appt('09:15', 15)]);

    expect(layout.positioned.every((b) => b.width === 1)).toBe(true);
  });

  it('widens the grid for a practice that starts before eight', () => {
    const layout = layoutDay([appt('07:00', 30)]);

    expect(layout.startHour).toBe(7);
    expect(layout.positioned[0].top).toBe(0);
  });

  it('keeps a minimum grid for a day with nothing on it', () => {
    const layout = layoutDay([]);

    // An empty day still has to look like a day rather than a line.
    expect(layout.startHour).toBe(8);
    expect(layout.endHour).toBe(18);
    expect(layout.positioned).toEqual([]);
  });

  it('gives a five-minute appointment a clickable height', () => {
    // Gary's calendar has 1,085 five-minute slots. At 1.1px a minute that is five pixels, which
    // is a line rather than a block.
    const layout = layoutDay([appt('10:00', 5)]);

    expect(layout.positioned[0].height).toBeGreaterThanOrEqual(14);
  });

  it('skips an appointment whose time cannot be read, keeping the rest', () => {
    const layout = layoutDay([appt('not-a-time', 30, 'bad'), appt('10:00', 30, 'good')]);

    expect(layout.positioned.map((b) => b.appointment.id)).toEqual(['good']);
  });

  it('gives a zero-duration appointment a real block', () => {
    // A zero would render as nothing at all, which reads as a lost appointment.
    const layout = layoutDay([appt('10:00', 0)]);

    expect(layout.positioned[0].height).toBeGreaterThan(0);
  });
});
