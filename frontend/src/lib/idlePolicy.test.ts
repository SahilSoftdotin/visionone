import { describe, expect, it } from 'vitest';
import { resolveIdlePolicy } from './idlePolicy';

/**
 * The policy arrives from the API now rather than from the bundle, so these cover the two things
 * that can go wrong with a served value: it has not arrived yet, or it cannot work.
 */
describe('resolveIdlePolicy', () => {
  it('uses what the API sent', () => {
    expect(resolveIdlePolicy({ idleTimeoutMinutes: 20, warningSeconds: 90 })).toEqual({
      idleMs: 20 * 60_000,
      warnMs: 90_000,
    });
  });

  it('falls back while /me is still in flight', () => {
    // undefined is the normal first render, not an error. Getting this wrong would either crash
    // the shell or start a timer with a NaN deadline.
    expect(resolveIdlePolicy(undefined)).toEqual({ idleMs: 15 * 60_000, warnMs: 60_000 });
  });

  it('falls back for an API that predates the policy', () => {
    expect(
      resolveIdlePolicy({} as unknown as { idleTimeoutMinutes: number; warningSeconds: number }),
    ).toEqual({ idleMs: 15 * 60_000, warnMs: 60_000 });
  });

  it('refuses values that would sign a client out on arrival', () => {
    // The server already rejects these. This is the second of two guards, because the browser
    // should not depend on the server having been careful. Zero is the dangerous one: a deadline
    // of now, every time the page loads.
    expect(resolveIdlePolicy({ idleTimeoutMinutes: 0, warningSeconds: 60 }).idleMs).toBe(
      15 * 60_000,
    );
    expect(resolveIdlePolicy({ idleTimeoutMinutes: -5, warningSeconds: 60 }).idleMs).toBe(
      15 * 60_000,
    );
    expect(
      resolveIdlePolicy({
        idleTimeoutMinutes: Number.NaN,
        warningSeconds: 60,
      }).idleMs,
    ).toBe(15 * 60_000);
  });

  it('never lets the warning cover more than half the window', () => {
    expect(resolveIdlePolicy({ idleTimeoutMinutes: 2, warningSeconds: 600 })).toEqual({
      idleMs: 120_000,
      warnMs: 60_000,
    });
  });
});
