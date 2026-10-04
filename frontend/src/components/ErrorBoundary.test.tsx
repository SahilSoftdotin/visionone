import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ErrorBoundary } from './ErrorBoundary';

/**
 * A boundary that has never caught anything is a claim, not a protection.
 *
 * React writes the caught error to console.error itself, on top of our own logging, so these would
 * otherwise bury the suite in red stack traces that look like failures. Silenced and asserted on
 * instead - the logging is part of what this component is for, since there is no error-tracking
 * service to catch it anywhere else.
 */

function Boom({ message = 'exploded' }: { message?: string }): never {
  throw new Error(message);
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a usable message instead of a blank page', () => {
    render(
      <ErrorBoundary>
        <Boom message="cannot read properties of undefined" />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Something went wrong on this page')).toBeInTheDocument();
    // The message is shown rather than swallowed: with no error reporting wired up, what the
    // person can read back to us is the only diagnostic that exists.
    expect(screen.getByText('cannot read properties of undefined')).toBeInTheDocument();
  });

  it('renders children untouched when nothing throws', () => {
    render(
      <ErrorBoundary>
        <p>the dashboard</p>
      </ErrorBoundary>,
    );

    expect(screen.getByText('the dashboard')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('logs the failure, because nothing else will', () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );

    expect(console.error).toHaveBeenCalledWith(
      '[VisionOne] a screen failed to render',
      expect.any(Error),
      expect.anything(),
    );
  });

  it('clears when the route changes, so the error is not sticky', () => {
    // Without this the fallback outlives the screen that caused it: navigating to a working page
    // would still show the error, because the boundary's state survives the route change.
    const { rerender } = render(
      <ErrorBoundary resetKey="/overview">
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();

    rerender(
      <ErrorBoundary resetKey="/leads">
        <p>leads</p>
      </ErrorBoundary>,
    );

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByText('leads')).toBeInTheDocument();
  });

  it('offers to reload at page level and to retry inside the shell', () => {
    const { unmount } = render(
      <ErrorBoundary variant="page">
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument();
    unmount();

    render(
      <ErrorBoundary variant="screen">
        <Boom />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('recovers when Try again is pressed and the cause has gone', () => {
    function Flaky({ fail }: { fail: boolean }) {
      if (fail) throw new Error('transient');
      return <p>recovered</p>;
    }

    // A single boundary instance: fail, press Try again, and the retry renders a child that no
    // longer throws - which is the transient case the button exists for.
    const { rerender } = render(
      <ErrorBoundary variant="screen">
        <Flaky fail />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toBeInTheDocument();

    rerender(
      <ErrorBoundary variant="screen">
        <Flaky fail={false} />
      </ErrorBoundary>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(screen.getByText('recovered')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
