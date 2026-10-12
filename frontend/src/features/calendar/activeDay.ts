/**
 * Which day the Day view shows.
 *
 * The Calendar fetches one month at a time, so the day on screen has to belong to the month on
 * screen. Holding today's date instead meant that browsing to September and pressing Day showed
 * "Nothing booked on this day" - the day was in October, only September had been loaded, and the
 * screen had no way to say the difference.
 *
 * Its own module so the screen and its test share one copy. Written inline it was duplicated in
 * the test, which then proved only that the duplicate still worked.
 *
 * @param monthKey the month on screen, `YYYY-MM`
 * @param todayIso today in the practice's own local date, `YYYY-MM-DD`
 * @param pickedDay a day the arrows moved to, or null
 */
export function activeDayFor(
  monthKey: string,
  todayIso: string,
  pickedDay: string | null,
): string {
  if (pickedDay && pickedDay.startsWith(monthKey)) {
    return pickedDay;
  }
  return todayIso.startsWith(monthKey) ? todayIso : `${monthKey}-01`;
}
