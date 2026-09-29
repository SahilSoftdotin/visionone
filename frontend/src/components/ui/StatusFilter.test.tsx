import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { StatusFilter, type StatusTone } from './StatusFilter';

type S = 'PLANNED' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED';

const ORDER: readonly S[] = ['PLANNED', 'BLOCKED', 'COMPLETED', 'CANCELLED'];

const TONES: Record<S, StatusTone> = {
  PLANNED: 'neutral',
  BLOCKED: 'critical',
  COMPLETED: 'positive',
  CANCELLED: 'neutral',
};

function renderFilter(selected: S | null = null, counts: Partial<Record<S, number>> = {}) {
  const onSelect = vi.fn();
  render(
    <StatusFilter
      label="Filter work by status"
      order={ORDER}
      counts={counts}
      tones={TONES}
      total={Object.values(counts).reduce((a: number, b) => a + (b as number), 0)}
      selected={selected}
      onSelect={onSelect}
    />,
  );
  return onSelect;
}

describe('StatusFilter', () => {
  it('gives each status the colour its row badge carries', () => {
    renderFilter(null, { PLANNED: 2, BLOCKED: 3, COMPLETED: 9 });

    // The whole point of the change: blocked reads as critical in the filter, not as generic grey.
    expect(screen.getByRole('button', { name: /Blocked/ }).className).toContain('critical');
    expect(screen.getByRole('button', { name: /Completed/ }).className).toContain('positive');
    expect(screen.getByRole('button', { name: /Planned/ }).className).toContain('muted');
  });

  it('marks the selected pill with a ring rather than recolouring it', () => {
    renderFilter('BLOCKED', { BLOCKED: 3, COMPLETED: 9 });

    const blocked = screen.getByRole('button', { name: /Blocked/ });
    expect(blocked).toHaveAttribute('aria-pressed', 'true');
    expect(blocked.className).toContain('ring-2');
    // Still critical-toned: selection must not change what the status means.
    expect(blocked.className).toContain('critical');

    expect(screen.getByRole('button', { name: /Completed/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('offers only statuses that have items, so no pill filters to nothing', () => {
    renderFilter(null, { BLOCKED: 3, COMPLETED: 9 });

    expect(screen.queryByRole('button', { name: /Planned/ })).toBeNull();
    expect(screen.getByRole('button', { name: /Blocked/ })).toBeTruthy();
  });

  it('keeps the selected status visible even once nothing matches it, so it can be cleared', () => {
    renderFilter('CANCELLED', { BLOCKED: 3 });

    const cancelled = screen.getByRole('button', { name: /Cancelled/ });
    expect(cancelled).toHaveAttribute('aria-pressed', 'true');
  });

  it('shows the total against All', () => {
    renderFilter(null, { BLOCKED: 3, COMPLETED: 9 });
    expect(screen.getByRole('button', { name: /All 12/ })).toBeTruthy();
  });
});
