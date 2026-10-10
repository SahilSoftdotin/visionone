import { useEffect, useState } from 'react';
import { Clock } from 'lucide-react';

/**
 * "You are about to be signed out", with a live countdown.
 *
 * Hand-rolled, because there is no dialog library in this app and no Modal component to reuse. The
 * overlay and the stop-propagation shape come from GlobalSearch, the `role="alertdialog"` and the
 * two-button layout from the share confirmation in ReportAdminPanel - the only other place that
 * asks before doing something.
 *
 * Deliberately not dismissible by clicking away or pressing Escape, which is the one place it
 * departs from GlobalSearch. Every other overlay in this app closes on a stray click because the
 * cost of closing it is nothing. Here a stray click would silently buy another fifteen minutes on
 * an unattended screen, which is the thing being prevented. The choice has to be made on purpose.
 *
 * The countdown runs against the real deadline rather than a local count, so a slow tick or a
 * backgrounded tab cannot leave it displaying time that is no longer there.
 */
export function IdleWarningDialog({
  deadline,
  onStaySignedIn,
  onSignOutNow,
}: {
  /** Epoch milliseconds at which sign-out happens. */
  deadline: number;
  onStaySignedIn: () => void;
  onSignOutNow: () => void;
}) {
  const [secondsLeft, setSecondsLeft] = useState(() =>
    Math.max(0, Math.ceil((deadline - Date.now()) / 1000)),
  );

  useEffect(() => {
    const tick = () => setSecondsLeft(Math.max(0, Math.ceil((deadline - Date.now()) / 1000)));
    tick();
    const interval = window.setInterval(tick, 1000);
    return () => window.clearInterval(interval);
  }, [deadline]);

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-foreground/25 p-4"
      role="presentation"
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="idle-warning-title"
        aria-describedby="idle-warning-body"
        className="w-full max-w-md rounded-lg border border-border bg-card p-6 elev-lg"
      >
        <div className="flex items-start gap-3">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-caution-soft">
            <Clock className="h-5 w-5 text-caution-text" aria-hidden />
          </span>
          <div className="min-w-0">
            <h2 id="idle-warning-title" className="text-base font-semibold">
              Still there?
            </h2>
            <p id="idle-warning-body" className="mt-1 text-sm text-muted-foreground">
              You have been inactive for a while. For your practice&rsquo;s security we will sign
              you out in{' '}
              {/* aria-live so a screen reader is told the time is running down, polite so it does
                  not interrupt whatever is being read. */}
              <span className="font-semibold tabular-nums text-foreground" aria-live="polite">
                {secondsLeft}s
              </span>
              .
            </p>
          </div>
        </div>

        <div className="mt-6 flex flex-col gap-2 sm:flex-row-reverse">
          <button
            type="button"
            autoFocus
            onClick={onStaySignedIn}
            className="inline-flex min-h-10 flex-1 items-center justify-center rounded-lg bg-primary px-4 text-sm font-semibold text-primary-foreground transition-all duration-150 hover:brightness-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            Stay signed in
          </button>
          <button
            type="button"
            onClick={onSignOutNow}
            className="inline-flex min-h-10 flex-1 items-center justify-center rounded-lg border border-border px-4 text-sm font-semibold text-muted-foreground transition-colors duration-150 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          >
            Sign out now
          </button>
        </div>
      </div>
    </div>
  );
}
