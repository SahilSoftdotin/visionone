import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FunnelChart } from './FunnelChart';

describe('FunnelChart', () => {
  it('shows every stage with its count', () => {
    render(<FunnelChart funnel={{ leads: 60, qualified: 30, appointmentRequested: 18, booked: 12 }} />);

    expect(screen.getByText('Lead')).toBeInTheDocument();
    expect(screen.getByText('Qualified')).toBeInTheDocument();
    expect(screen.getByText('Appointment requested')).toBeInTheDocument();
    expect(screen.getByText('Booked')).toBeInTheDocument();
    expect(screen.getByLabelText('Booked: 12 leads')).toBeInTheDocument();
  });

  it('survives an empty month without dividing by zero', () => {
    render(<FunnelChart funnel={{ leads: 0, qualified: 0, appointmentRequested: 0, booked: 0 }} />);
    expect(screen.getByLabelText('Lead: 0 leads')).toBeInTheDocument();
  });
});
