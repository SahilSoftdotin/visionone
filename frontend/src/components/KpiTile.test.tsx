import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { KpiTile } from './KpiTile';

describe('KpiTile', () => {
  it('shows a rise as positive for a growth metric', () => {
    render(<KpiTile label="New leads" value="60" current={60} prior={50} />);
    const delta = screen.getByText('+20%');
    expect(delta).toHaveClass('text-positive-text');
    // Direction must not depend on colour alone: the arrow is the second cue.
    expect(delta.querySelector('svg')).toBeInTheDocument();
  });

  it('shows a rise as negative for a cost metric', () => {
    render(
      <KpiTile label="Cost per lead" value="$40.00" current={40} prior={30} invertDirection />,
    );
    const delta = screen.getByText('+33%');
    expect(delta).toHaveClass('text-critical-text');
    expect(delta.querySelector('svg')).toBeInTheDocument();
  });

  it('omits the comparison row entirely when there is nothing to compare', () => {
    // An em dash here read as a rendering fault under every tile. The dash convention belongs to
    // an unanswerable *value* - cost per lead with no leads - not to an absent prior month.
    render(<KpiTile label="Growth budget" value="$5,000" />);
    expect(screen.getByText('$5,000')).toBeInTheDocument();
    expect(screen.queryByText('—')).not.toBeInTheDocument();
    expect(screen.queryByText('vs last month')).not.toBeInTheDocument();
  });

  it('still shows a hint when one is given without a comparison', () => {
    render(<KpiTile label="Growth budget" value="$5,000" hint="planned this month" />);
    expect(screen.getByText('planned this month')).toBeInTheDocument();
  });
});
