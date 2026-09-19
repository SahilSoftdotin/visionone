import { describe, expect, it } from 'vitest';
import { delta, EM_DASH, formatCount, formatMoney, formatPercent } from './format';

describe('formatters', () => {
  it('renders a missing money value as an em dash, never as zero', () => {
    expect(formatMoney(null)).toBe(EM_DASH);
    expect(formatMoney(undefined)).toBe(EM_DASH);
  });

  it('renders minor units as currency', () => {
    expect(formatMoney({ amountMinor: 500000, currency: 'USD' })).toBe('$5,000.00');
    expect(formatMoney({ amountMinor: 3333, currency: 'USD' })).toBe('$33.33');
  });

  it('drops cents for large compact values', () => {
    expect(formatMoney({ amountMinor: 500000, currency: 'USD' }, { compact: true })).toBe('$5,000');
  });

  it('renders a missing percentage as an em dash', () => {
    expect(formatPercent(null)).toBe(EM_DASH);
    expect(formatPercent(12.34)).toBe('12.3%');
  });

  it('renders a missing count as an em dash but keeps a real zero', () => {
    expect(formatCount(null)).toBe(EM_DASH);
    expect(formatCount(0)).toBe('0');
  });
});

describe('delta', () => {
  it('returns null when there is nothing to compare against', () => {
    expect(delta(10, 0)).toBeNull();
    expect(delta(10, null)).toBeNull();
    expect(delta(null, 10)).toBeNull();
  });

  it('reports direction of travel', () => {
    expect(delta(120, 100)?.direction).toBe('up');
    expect(delta(80, 100)?.direction).toBe('down');
    expect(delta(100, 100)?.direction).toBe('flat');
  });
});
