import type { ReactNode } from 'react';
import { ApiError } from '@/lib/api';

interface Props {
  isLoading: boolean;
  error: unknown;
  isEmpty?: boolean;
  emptyMessage?: string;
  skeleton: ReactNode;
  onRetry?: () => void;
  children: ReactNode;
}

/**
 * Four states, handled explicitly, on every screen.
 *
 * The skeleton matches the final layout so the page does not jump. The Overview has to feel
 * instant even when it is not.
 */
export function DataStateBoundary({
  isLoading,
  error,
  isEmpty,
  emptyMessage,
  skeleton,
  onRetry,
  children,
}: Props) {
  if (isLoading) {
    return <div aria-busy="true">{skeleton}</div>;
  }

  if (error) {
    const forbidden = error instanceof ApiError && error.isForbidden;
    return (
      <div
        role="alert"
        className="rounded-lg border border-border bg-card px-5 py-8 text-center"
      >
        <p className="text-sm font-medium">
          {forbidden ? 'You do not have access to this practice' : 'We could not load this view'}
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {error instanceof Error ? error.message : 'An unexpected error occurred.'}
        </p>
        {onRetry && !forbidden && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-4 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90"
          >
            Try again
          </button>
        )}
      </div>
    );
  }

  if (isEmpty) {
    return (
      <div className="rounded-lg border border-dashed border-border px-5 py-10 text-center">
        <p className="text-sm text-muted-foreground">{emptyMessage ?? 'Nothing to show yet.'}</p>
      </div>
    );
  }

  return <>{children}</>;
}
