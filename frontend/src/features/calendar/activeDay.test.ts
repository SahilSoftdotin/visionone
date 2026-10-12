import { describe, expect, it } from 'vitest';
import { activeDayFor } from './activeDay';

/**
 * Which day the Day view opens on.
 *
 * The Calendar loads one month at a time, so the day on screen has to belong to the month on
 * screen. Holding today's date instead meant browsing to September and pressing Day showed
 * "Nothing booked on this day" - the day was in October and only September had been fetched.
 */
describe('activeDayFor', () => {
  it('opens on today when today is in the month on screen', () => {
    expect(activeDayFor('2026-10', '2026-10-12', null)).toBe('2026-10-12');
  });

  it('opens on the first when the month on screen is not this one', () => {
    // The bug: this used to return 2026-10-12, a day with no loaded data, and the grid read
    // "Nothing booked on this day" for an entire busy month.
    expect(activeDayFor('2026-09', '2026-10-12', null)).toBe('2026-09-01');
    expect(activeDayFor('2027-01', '2026-10-12', null)).toBe('2027-01-01');
  });

  it('keeps a day the user picked with the arrows', () => {
    expect(activeDayFor('2026-09', '2026-10-12', '2026-09-18')).toBe('2026-09-18');
  });

  it('drops a picked day once the month changes under it', () => {
    // Pick 18 September, then change month with the picker. The picked day no longer has data.
    expect(activeDayFor('2026-10', '2026-10-12', '2026-09-18')).toBe('2026-10-12');
    expect(activeDayFor('2026-11', '2026-10-12', '2026-09-18')).toBe('2026-11-01');
  });

  it('does not mistake a month whose key is a prefix of another', () => {
    // "2026-1" is not a real key, but startsWith would match 2026-10 against it. Keys are
    // always YYYY-MM, so a full-length compare is what makes this safe.
    expect(activeDayFor('2026-01', '2026-10-12', null)).toBe('2026-01-01');
  });
});
