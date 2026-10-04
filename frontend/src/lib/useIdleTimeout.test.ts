import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useIdleTimeout } from './useIdleTimeout';

/**
 * The first tests in this repo to use fake timers, because the thing under test is a clock.
 *
 * `shouldAdvanceTime` is off: every advance here is deliberate, so the deadline cannot be crossed
 * by the suite taking a moment to run.
 */

const IDLE_MS = 15 * 60_000;
const WARN_MS = 60_000;

function setup(overrides: Partial<Parameters<typeof useIdleTimeout>[0]> = {}) {
  const onWarn = vi.fn();
  const onExpire = vi.fn();
  const onReprieve = vi.fn();
  const view = renderHook(() =>
    useIdleTimeout({
      enabled: true,
      idleMs: IDLE_MS,
      warnMs: WARN_MS,
      onWarn,
      onExpire,
      onReprieve,
      ...overrides,
    }),
  );
  return { onWarn, onExpire, onReprieve, ...view };
}

/** Moves both the fake clock and `Date.now`, which is what the hook actually reads. */
function advance(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
}

describe('useIdleTimeout', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('warns one minute out and signs out at the deadline', () => {
    const { onWarn, onExpire } = setup();

    advance(13 * 60_000);
    expect(onWarn).not.toHaveBeenCalled();

    advance(60_000 + 5_000);
    expect(onWarn).toHaveBeenCalledTimes(1);
    expect(onExpire).not.toHaveBeenCalled();

    // The warning carries the real deadline so a countdown can be rendered against it rather than
    // against the tick that happened to notice.
    const deadline = onWarn.mock.calls[0]?.[0] as number;
    expect(deadline).toBeGreaterThan(Date.now());
    expect(deadline - Date.now()).toBeLessThanOrEqual(WARN_MS);

    advance(60_000);
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it('a keystroke resets the clock', () => {
    const { onWarn, onExpire } = setup();

    advance(14 * 60_000 + 30_000);
    expect(onWarn).toHaveBeenCalledTimes(1);

    act(() => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'a' }));
    });

    // Reprieved, and the full allowance starts again.
    advance(14 * 60_000);
    expect(onExpire).not.toHaveBeenCalled();
    expect(onWarn).toHaveBeenCalledTimes(2);
  });

  it('does not sign out twice', () => {
    const { onExpire } = setup();
    advance(IDLE_MS + 60_000);
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it('does nothing at all when disabled, which is every Vision Admin session', () => {
    const { onWarn, onExpire } = setup({ enabled: false });
    advance(IDLE_MS * 2);
    expect(onWarn).not.toHaveBeenCalled();
    expect(onExpire).not.toHaveBeenCalled();
  });

  it('activity in another tab keeps this one signed in', () => {
    const { onExpire } = setup();

    advance(14 * 60_000);

    // What a second tab's reset looks like from here: a newer timestamp in shared storage, and a
    // storage event. Without this the forgotten tab signs the user out of the one they are using.
    act(() => {
      const at = String(Date.now());
      window.localStorage.setItem('visionone.lastActivity', at);
      window.dispatchEvent(
        new StorageEvent('storage', { key: 'visionone.lastActivity', newValue: at }),
      );
    });

    advance(14 * 60_000);
    expect(onExpire).not.toHaveBeenCalled();
  });

  it('signs out after a sleep, where a countdown timer would not have fired', () => {
    const { onExpire } = setup();

    // A closed laptop: wall-clock time passes but no interval callback runs. Simulated by moving
    // Date.now past the deadline while the timer queue stays where it was.
    const wakeAt = Date.now() + IDLE_MS + 60_000;
    vi.spyOn(Date, 'now').mockReturnValue(wakeAt);

    // One tick - or, in the real browser, the visibilitychange on returning to the tab.
    advance(5_000);
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it('stops listening once unmounted', () => {
    const { onExpire, unmount } = setup();
    unmount();
    advance(IDLE_MS * 2);
    expect(onExpire).not.toHaveBeenCalled();
  });
});
