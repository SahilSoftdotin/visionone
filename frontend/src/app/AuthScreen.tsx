import type { ReactNode } from 'react';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { VisionDigitalLabMark, VisionOneMark } from '@/components/ui/Brand';

/**
 * The first thing anyone sees, so it carries the brand rather than a spinner on white.
 *
 * There is no password field here by design. Authentication is a redirect to the identity
 * provider; VisionOne never handles a credential. The button starts that redirect.
 */
export function AuthScreen({
  state,
  message,
  onSignIn,
}: {
  state: 'idle' | 'working' | 'error';
  message?: string;
  onSignIn: () => void;
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <BrandPanel />

      <div className="flex items-center justify-center bg-background px-6 py-12 sm:px-10">
        <div className="w-full max-w-sm">
          <div className="lg:hidden">
            <VisionOneMark className="text-lg" />
          </div>

          <h1 className="mt-6 text-2xl font-semibold tracking-[-0.02em] lg:mt-0">
            Sign in to your practice
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            Your growth operations, in one place — calls, campaigns, reviews and bookings.
          </p>

          {state === 'error' && (
            <div
              role="alert"
              className="mt-5 rounded-lg border border-critical/25 bg-critical/5 px-3.5 py-3"
            >
              <p className="text-sm font-semibold text-critical">Sign-in failed</p>
              {message && <p className="mt-1 text-xs text-muted-foreground">{message}</p>}
            </div>
          )}

          <button
            type="button"
            onClick={onSignIn}
            disabled={state === 'working'}
            className="group mt-6 flex h-12 w-full items-center justify-center gap-2 rounded-full bg-primary px-5 text-sm font-semibold text-primary-foreground elev-sm transition-all duration-200 hover:-translate-y-px hover:elev-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:translate-y-0 disabled:opacity-70"
          >
            {state === 'working' ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground/30 border-t-primary-foreground" />
                Signing you in…
              </>
            ) : (
              <>
                {state === 'error' ? 'Try again' : 'Continue to secure sign-in'}
                <ArrowRight className="h-4 w-4 transition-transform duration-200 group-hover:translate-x-0.5" />
              </>
            )}
          </button>

          <p className="mt-4 flex items-start gap-2 text-xs leading-relaxed text-muted-foreground">
            <ShieldCheck className="mt-px h-3.5 w-3.5 shrink-0 text-positive" aria-hidden />
            You are handed to your identity provider to sign in. VisionOne never sees your
            password.
          </p>

          <p className="mt-8 border-t border-border pt-5 text-xs text-muted-foreground">
            No account yet? Practices are onboarded by Vision Digital Lab — speak to your account
            contact.
          </p>
        </div>
      </div>
    </div>
  );
}

/** The blue half: who made this, for whom, and what it promises. */
function BrandPanel() {
  return (
    <div className="relative hidden overflow-hidden bg-primary px-12 py-12 text-primary-foreground lg:flex lg:flex-col">
      {/* Depth without imagery - a light source behind the panel. */}
      <span
        aria-hidden
        className="pointer-events-none absolute -left-32 -top-40 h-[34rem] w-[34rem] rounded-full bg-white/15 blur-3xl"
      />
      <span
        aria-hidden
        className="pointer-events-none absolute -bottom-48 -right-24 h-[30rem] w-[30rem] rounded-full bg-white/10 blur-3xl"
      />

      <div className="relative flex items-center gap-2.5">
        <VisionDigitalLabMark className="h-7 w-7" />
        <span className="text-sm font-semibold tracking-[-0.01em]">Vision Digital Lab</span>
      </div>

      <div className="relative my-auto max-w-md py-10">
        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-primary-foreground/70">
          Practice Growth Operating System
        </p>
        <p className="mt-4 text-4xl font-semibold leading-[1.12] tracking-[-0.025em]">
          Vision<span className="text-white/60">One</span>
        </p>
        <p className="mt-6 text-lg leading-snug text-primary-foreground/90">
          You run the practice.
          <br />
          VisionOne shows you how Vision is growing it.
        </p>

        <ul className="mt-9 space-y-3 text-sm text-primary-foreground/80">
          {[
            'Every enquiry labelled with where it came from',
            'Spend, leads and bookings on one screen',
            'Every panel states how fresh its data is',
          ].map((line) => (
            <li key={line} className="flex items-start gap-2.5">
              <span
                aria-hidden
                className="mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full bg-primary-foreground/60"
              />
              {line}
            </li>
          ))}
        </ul>
      </div>

      <p className="relative text-xs text-primary-foreground/60">
        A Vision Digital Lab product · visiondigitallab.com
      </p>
    </div>
  );
}

/** Shared frame for the brief moment before the session is known. */
export function AuthSplash({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="max-w-sm text-center">
        <VisionOneMark className="text-lg" />
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}
