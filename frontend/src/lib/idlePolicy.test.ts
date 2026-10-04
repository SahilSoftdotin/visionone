import { describe, expect, it } from 'vitest';
import { positiveNumberOr, resolveIdlePolicy } from './idlePolicy';

/**
 * The parsing matters more than it looks. These values arrive as strings from a Docker build arg,
 * which is the easiest place in the stack to fat-finger, and the consequence of accepting a bad
 * one is a client being signed out the instant they arrive.
 */
describe('positiveNumberOr', () => {
  it('takes a usable number', () => {
    expect(positiveNumberOr('20', 15)).toBe(20);
    expect(positiveNumberOr(' 7 ', 15)).toBe(7);
  });

  it('falls back when the build arg was never set or came through empty', () => {
    expect(positiveNumberOr(undefined, 15)).toBe(15);
    expect(positiveNumberOr('', 15)).toBe(15);
    expect(positiveNumberOr('   ', 15)).toBe(15);
  });

  it('refuses values that would disable or invert the timeout', () => {
    // 0 is the dangerous one: a deadline of "now", so every client is signed out on arrival.
    expect(positiveNumberOr('0', 15)).toBe(15);
    expect(positiveNumberOr('-5', 15)).toBe(15);
    expect(positiveNumberOr('fifteen', 15)).toBe(15);
    expect(positiveNumberOr('15 minutes', 15)).toBe(15);
    expect(positiveNumberOr('Infinity', 15)).toBe(15);
  });
});

describe('resolveIdlePolicy', () => {
  it('converts minutes and seconds into milliseconds', () => {
    expect(resolveIdlePolicy('20', '90')).toEqual({ idleMs: 20 * 60_000, warnMs: 90_000 });
  });

  it('defaults to fifteen minutes with a minute of warning', () => {
    expect(resolveIdlePolicy(undefined, undefined)).toEqual({
      idleMs: 15 * 60_000,
      warnMs: 60_000,
    });
  });

  it('never lets the warning cover more than half the window', () => {
    // Otherwise the warning is on screen from the moment the page loads, telling someone they are
    // about to be signed out before they have done anything.
    expect(resolveIdlePolicy('2', '600')).toEqual({ idleMs: 120_000, warnMs: 60_000 });
  });
});
