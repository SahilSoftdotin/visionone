/**
 * How long a client may sit idle, and how much warning they get.
 *
 * Read from the build environment rather than written into the component, so the figure can be
 * changed per environment without editing a screen. **It is still a build-time value**: Vite
 * inlines `import.meta.env` into the bundle, so changing it means rebuilding the web image and
 * deploying - exactly like the OIDC authority next to it in `Dockerfile`. There is no runtime
 * configuration here and this file does not pretend otherwise.
 *
 * Fifteen minutes is the default because that is what a healthcare security questionnaire expects.
 * VisionOne holds no clinical record - an appointment carries a first name and a last initial and
 * nothing else - but it is handled under a BAA, so HIPAA's automatic-logoff specification is the
 * bar it gets measured against. That specification is *addressable* and names no number; clinical
 * systems sit at ten to fifteen minutes, general dashboards at thirty to sixty.
 */

const DEFAULT_IDLE_MINUTES = 15;
const DEFAULT_WARNING_SECONDS = 60;

/**
 * A misconfigured value falls back to the default rather than being used.
 *
 * This matters more than it looks. An empty or misspelled build arg parses to `NaN`, and a `0`
 * from someone trying to disable the feature would mean a deadline of *now* - signing a client out
 * the instant they arrive, over and over, with no obvious cause. Refusing the value is the only
 * safe reading, and the default is a working policy rather than an absence of one.
 */
export function positiveNumberOr(raw: string | undefined, fallback: number): number {
  if (raw === undefined || raw.trim() === '') return fallback;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

/**
 * The warning has to fit inside the timeout, or it fires the moment the page loads and the person
 * is told they are about to be signed out before they have done anything.
 */
export function resolveIdlePolicy(
  minutesRaw: string | undefined,
  warningSecondsRaw: string | undefined,
): { idleMs: number; warnMs: number } {
  const idleMs = positiveNumberOr(minutesRaw, DEFAULT_IDLE_MINUTES) * 60_000;
  const requestedWarnMs = positiveNumberOr(warningSecondsRaw, DEFAULT_WARNING_SECONDS) * 1_000;
  // Half the window, at most. A warning that covers the whole timeout is not a warning.
  return { idleMs, warnMs: Math.min(requestedWarnMs, idleMs / 2) };
}

export const { idleMs: IDLE_MS, warnMs: IDLE_WARN_MS } = resolveIdlePolicy(
  import.meta.env.VITE_IDLE_TIMEOUT_MINUTES,
  import.meta.env.VITE_IDLE_WARNING_SECONDS,
);
