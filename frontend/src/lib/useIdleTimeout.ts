import { useCallback, useEffect, useRef } from 'react';

/**
 * Signs someone out after a period with no deliberate input.
 *
 * Headless on purpose: it takes callbacks and owns no auth and no UI, so it can be tested without
 * an AuthProvider, a QueryClientProvider or a router.
 *
 * Three decisions in here are not obvious.
 *
 * **It compares wall-clock timestamps rather than counting down a timer.** A `setTimeout` chain is
 * the usual way to write this and it is wrong for the case that matters most: a laptop closed on
 * an open dashboard. Timers do not run while a machine sleeps, so a countdown resumes where it
 * left off and someone who walked away for two hours is still signed in. Comparing `Date.now()`
 * on a tick notices the gap. It also means activity handlers only write a number instead of
 * tearing down and rebuilding a timer on every mouse-down.
 *
 * **Activity means deliberate input, not network traffic.** Background query refetches, token
 * renewals and polling must never count: the app talks to the server constantly on its own, so
 * counting that as activity gives a timer that can never fire.
 *
 * **Activity is shared between tabs through localStorage.** The OIDC store is sessionStorage, so
 * each tab holds its own copy of the session, but signing out is an RP-initiated logout that ends
 * the session for all of them. Without sharing, a forgotten second tab would sign someone out of
 * the tab they were working in.
 */

/** Shared between tabs. Deliberately not sessionStorage, which is per-tab and would defeat this. */
const ACTIVITY_KEY = 'visionone.lastActivity';

/**
 * How often the deadline is checked. Five seconds rather than a minute because it bounds how late
 * the warning appears; the sign-out moment itself is the stored deadline, not the tick, so the
 * countdown the caller renders stays accurate regardless.
 */
const TICK_MS = 5_000;

/**
 * Pointer, key, scroll and touch. No `mousemove`: a trackpad nudge from a sleeve or a cat is not
 * someone reading their dashboard, and on a shared front desk that is the exact case this exists
 * to catch.
 */
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'scroll', 'touchstart'] as const;

export interface IdleTimeoutOptions {
  /** False disables everything, including the listeners. */
  enabled: boolean;
  /** Total inactivity allowed before `onExpire`. */
  idleMs: number;
  /** How long before the deadline `onWarn` fires. */
  warnMs: number;
  /** Given the exact moment sign-out is due, so a countdown can be rendered against it. */
  onWarn: (deadline: number) => void;
  /** Called once. The caller is expected to navigate away. */
  onExpire: () => void;
  /** Activity arrived after a warning: the caller should dismiss it. */
  onReprieve?: () => void;
}

/** Storage is unavailable in some privacy modes, and a sign-out timer is not worth throwing over. */
function readSharedActivity(): number {
  try {
    const raw = window.localStorage.getItem(ACTIVITY_KEY);
    const parsed = raw === null ? Number.NaN : Number(raw);
    return Number.isFinite(parsed) ? parsed : 0;
  } catch {
    return 0;
  }
}

function writeSharedActivity(at: number): void {
  try {
    window.localStorage.setItem(ACTIVITY_KEY, String(at));
  } catch {
    /* no cross-tab sync, but the tab's own timer still works */
  }
}

export function useIdleTimeout({
  enabled,
  idleMs,
  warnMs,
  onWarn,
  onExpire,
  onReprieve,
}: IdleTimeoutOptions): { reset: () => void } {
  const lastActivityRef = useRef(Date.now());
  const warnedRef = useRef(false);
  // Fire-once latch, the same shape as the redirect guard in OrganizationRouter: expiry navigates
  // away, and a re-render must not be able to fire a second sign-out.
  const expiredRef = useRef(false);

  // Held in refs so the effect below does not need them as dependencies. Without this, an inline
  // arrow passed by the caller would tear down and rebuild every listener on each render.
  const handlers = useRef({ onWarn, onExpire, onReprieve });
  handlers.current = { onWarn, onExpire, onReprieve };

  const reset = useCallback(() => {
    const now = Date.now();
    lastActivityRef.current = now;
    writeSharedActivity(now);
    if (warnedRef.current) {
      warnedRef.current = false;
      handlers.current.onReprieve?.();
    }
  }, []);

  useEffect(() => {
    if (!enabled) return;

    // Start the clock from this moment rather than from whatever another tab last wrote, so
    // arriving on the page is itself activity.
    reset();

    const check = () => {
      if (expiredRef.current) return;
      // The newest of this tab and any other. Reading storage each tick rather than trusting the
      // `storage` event alone, because that event does not fire in the tab that wrote it and can
      // be missed entirely if a tab was asleep.
      const lastActivity = Math.max(lastActivityRef.current, readSharedActivity());
      lastActivityRef.current = lastActivity;

      const deadline = lastActivity + idleMs;
      const now = Date.now();

      if (now >= deadline) {
        expiredRef.current = true;
        handlers.current.onExpire();
        return;
      }
      if (now >= deadline - warnMs) {
        if (!warnedRef.current) {
          warnedRef.current = true;
          handlers.current.onWarn(deadline);
        }
      } else if (warnedRef.current) {
        // Another tab reset the clock while this one was showing a warning.
        warnedRef.current = false;
        handlers.current.onReprieve?.();
      }
    };

    const onActivity = () => reset();
    const onVisibility = () => {
      // Returning to a tab is the moment a sleep gap becomes visible, so check before waiting for
      // the next tick. Not treated as activity: coming back to a tab that has already timed out
      // should sign out, not grant another fifteen minutes.
      if (document.visibilityState === 'visible') check();
    };
    const onStorage = (event: StorageEvent) => {
      if (event.key !== ACTIVITY_KEY) return;
      check();
    };

    for (const name of ACTIVITY_EVENTS) {
      document.addEventListener(name, onActivity, { passive: true, capture: true });
    }
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('storage', onStorage);
    const interval = window.setInterval(check, TICK_MS);

    return () => {
      for (const name of ACTIVITY_EVENTS) {
        document.removeEventListener(name, onActivity, { capture: true });
      }
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('storage', onStorage);
      window.clearInterval(interval);
    };
  }, [enabled, idleMs, warnMs, reset]);

  return { reset };
}
