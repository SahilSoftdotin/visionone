import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { KpiTile } from './KpiTile';

describe('KpiTile', () => {
  it('shows a rise as positive for a growth metric', () => {
    render(<KpiTile label="New leads" value="60" current={60} prior={50} />);
    expect(screen.getByText('+20%')).toHaveClass('text-positive');
  });

  it('shows a rise as negative for a cost metric', () => {
    render(<KpiTile label="Cost per lead" value="$40.00" current={40} prior={30} invertDirection />);
    expect(screen.getByText('+33%')).toHaveClass('text-critical');
  });

  it('renders an em dash when there is nothing to compare', () => {
    render(<KpiTile label="Growth budget" value="$5,000" />);
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});
