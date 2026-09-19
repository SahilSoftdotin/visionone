import type { Money } from './types';

/**
 * A missing number is an em dash, never a zero.
 *
 * Cost per lead with no leads is not $0.00 - showing zero would tell the client something
 * flattering and false. Every formatter here treats null and undefined the same way.
 */
export const EM_DASH = '—';

export function formatMoney(money: Money | null | undefined, options?: { compact?: boolean }): string {
  if (!money) return EM_DASH;
  const amount = money.amountMinor / 100;
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: money.currency,
    maximumFractionDigits: options?.compact && Math.abs(amount) >= 1000 ? 0 : 2,
    minimumFractionDigits: options?.compact && Math.abs(amount) >= 1000 ? 0 : 2,
  }).format(amount);
}

export function formatCount(value: number | null | undefined): string {
  if (value === null || value === undefined) return EM_DASH;
  return new Intl.NumberFormat('en-US').format(value);
}

export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) return EM_DASH;
  return `${value.toFixed(1)}%`;
}

export function formatMonth(isoDate: string): string {
  const [year, month] = isoDate.split('-');
  if (!year || !month) return isoDate;
  return new Date(Number(year), Number(month) - 1, 1).toLocaleDateString('en-US', {
    month: 'long',
    year: 'numeric',
  });
}

/** Direction of travel against the prior month, or null when there is nothing to compare. */
export function delta(current: number | null | undefined, prior: number | null | undefined) {
  if (current === null || current === undefined || prior === null || prior === undefined || prior === 0) {
    return null;
  }
  const change = ((current - prior) / prior) * 100;
  return { change, direction: change > 0.5 ? 'up' : change < -0.5 ? 'down' : 'flat' } as const;
}
