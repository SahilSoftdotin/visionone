import type { SessionPolicy } from './types';

/**
 * How long a client may sit idle, and how much warning they get.
 *
 * The figures come from the API on `/me`, not from the bundle. They were build arguments first,
 * which put them in the web image and meant changing fifteen minutes to ten needed a rebuild and a
 * deploy. Now it is a restart of the API.
 *
 * The defaults below are what the browser uses if the API sends no policy at all - an older API,
 * or a `/me` that has not resolved yet. They match the server's own defaults deliberately, so the
 * two cannot disagree about what "unset" means.
 *
 * Fifteen minutes is what a healthcare security questionnaire expects. VisionOne holds no clinical
 * record - an appointment carries a first name and a last initial and nothing else - but it is
 * handled under a BAA, so HIPAA's automatic-logoff specification is the bar it gets measured
 * against. That specification is *addressable* and names no number; clinical systems sit at ten to
 * fifteen minutes, general dashboards at thirty to sixty.
 */

const DEFAULT_IDLE_MINUTES = 15;
const DEFAULT_WARNING_SECONDS = 60;

/**
 * A value that cannot work falls back to the default rather than being used.
 *
 * The server already refuses these, so this is the second of two guards rather than the only one.
 * It is here because the browser should not depend on the server having been careful: zero would
 * mean a deadline of *now*, signing a client out the instant they arrive, repeatedly, with no
 * obvious cause.
 */
function positiveOr(value: number | undefined, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback;
}

/**
 * Turns whatever `/me` sent into the two millisecond figures the idle hook takes.
 *
 * `undefined` is a normal input, not an error: it is what the first render sees while the session
 * query is still in flight.
 */
export function resolveIdlePolicy(policy: SessionPolicy | undefined): {
  idleMs: number;
  warnMs: number;
} {
  const idleMs = positiveOr(policy?.idleTimeoutMinutes, DEFAULT_IDLE_MINUTES) * 60_000;
  const requestedWarnMs = positiveOr(policy?.warningSeconds, DEFAULT_WARNING_SECONDS) * 1_000;
  // Half the window, at most. A warning that covers the whole timeout is not a warning - it is on
  // screen before the person has done anything.
  return { idleMs, warnMs: Math.min(requestedWarnMs, idleMs / 2) };
}
