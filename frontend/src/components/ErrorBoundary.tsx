import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * The last thing between a thrown render and a blank page.
 *
 * Every screen already handles its queries failing, through DataStateBoundary - loading, error,
 * empty, loaded. What none of them handled is a component *throwing while rendering*: a shape the
 * code did not expect, a null where an object was assumed. React's answer to an uncaught render
 * error is to unmount the whole tree, so without this the practice gets a white page with nothing
 * on it and no way forward. That is the worst failure this product can show a client, and it was
 * one unexpected null away at any time.
 *
 * A class, because `getDerivedStateFromError` has no hook equivalent. This is the one place in the
 * app that cannot be a function component.
 *
 * It does not report anywhere. There is no error-tracking service wired up, so the console and the
 * person's description are what exist - which is a reason to show the message rather than hide it.
 */

interface Props {
  children: ReactNode;
  /**
   * Changing this clears a caught error. Without it the fallback is sticky: once a screen throws,
   * navigating away leaves the error on screen, because the boundary's state outlives the route.
   */
  resetKey?: string;
  /**
   * `screen` sits inside the shell, so the navigation survives and the practice can move to a
   * different page. `page` is for a failure outside it, where there is nothing left to navigate
   * with and reloading is the only move.
   */
  variant?: 'screen' | 'page';
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidUpdate(previous: Props) {
    if (this.state.error && previous.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // The component stack is the useful half - it names the screen that threw, which the message
    // alone usually does not.
    console.error('[VisionOne] a screen failed to render', error, info.componentStack);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    const page = this.props.variant === 'page';

    const body = (
      // Same shape as DataStateBoundary's error state, so a render failure and a failed request
      // look like the same kind of problem to the person reading them - because they are.
      <div
        role="alert"
        className="rounded-lg border border-border bg-card px-5 py-8 text-center"
      >
        <p className="text-sm font-medium">Something went wrong on this page</p>
        <p className="mt-1 text-sm text-muted-foreground">
          {error.message || 'An unexpected error occurred.'}
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {page
            ? 'Reloading usually clears it. If it keeps happening, contact Vision Digital Lab.'
            : 'The rest of the portal still works - use the menu to go elsewhere, or try again.'}
        </p>
        <button
          type="button"
          onClick={() => {
            if (page) {
              window.location.reload();
            } else {
              this.setState({ error: null });
            }
          }}
          className="mt-4 inline-flex min-h-10 items-center justify-center rounded-lg bg-primary px-4 text-sm font-semibold text-primary-foreground transition-all duration-150 hover:brightness-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        >
          {page ? 'Reload' : 'Try again'}
        </button>
      </div>
    );

    if (!page) return body;

    return (
      <div className="flex min-h-screen items-center justify-center bg-card px-4">
        <div className="w-full max-w-md">{body}</div>
      </div>
    );
  }
}

/**
 * The boundary with the current route as its reset key, which is how navigating away clears it.
 *
 * Separate because the reset key comes from `useLocation`, and a class component cannot call a
 * hook. This is the wrapper that should be used; the class is exported only for tests.
 */
export function RouteErrorBoundary({
  children,
  variant,
}: {
  children: ReactNode;
  variant?: 'screen' | 'page';
}) {
  const location = useLocation();
  return (
    <ErrorBoundary resetKey={location.pathname} variant={variant}>
      {children}
    </ErrorBoundary>
  );
}
