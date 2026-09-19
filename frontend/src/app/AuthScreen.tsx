import type { ReactNode } from 'react';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { VisionOneMark } from '@/components/ui/Brand';

/**
 * Sign-in, on the Modernize auth layout: a tinted illustration panel on the left and a white
 * form column on the right.
 *
 * Modernize puts email and password fields in that column. VisionOne cannot: authentication is a
 * redirect to the identity provider, and the product never handles a credential. The column keeps
 * the same rhythm - heading, subtitle, divider, primary action, footer line - with the action
 * standing where the fields would be. The real form, styled to match, lives on the Keycloak page
 * this button leads to.
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
    <div className="min-h-screen bg-card lg:flex">
      {/* Left: brand and illustration on the tinted ground. */}
      <div className="relative hidden w-[58%] flex-col bg-[hsl(220_60%_97%)] px-12 py-10 lg:flex">
        <div className="flex items-center gap-3">
          <img
            src="/brand/vision-digital-lab.svg"
            alt="Vision Digital Lab"
            className="h-10 w-10 rounded-lg"
          />
          <span className="text-2xl font-extrabold tracking-[-0.02em]">
            Vision<span className="text-primary">One</span>
          </span>
        </div>

        <div className="flex flex-1 items-center justify-center">
          <img
            src="/brand/login-illustration.svg"
            alt=""
            aria-hidden
            className="w-full max-w-[460px]"
          />
        </div>

        <p className="text-sm text-muted-foreground">
          Practice Growth Operating System · a Vision Digital Lab product
        </p>
      </div>

      {/* Right: the form column. */}
      <div className="flex flex-1 items-center justify-center px-6 py-12 sm:px-10">
        <div className="w-full max-w-[400px]">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <img src="/brand/vision-digital-lab.svg" alt="" aria-hidden className="h-8 w-8 rounded-lg" />
            <VisionOneMark className="text-lg" />
          </div>

          <h1 className="text-2xl font-bold tracking-[-0.02em]">Welcome to VisionOne</h1>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Your growth operations, in one place.
          </p>

          {state === 'error' && (
            <div
              role="alert"
              className="mt-6 rounded-lg bg-critical-soft px-4 py-3 text-sm"
            >
              <p className="font-semibold text-critical-text">Sign-in failed</p>
              {message && <p className="mt-0.5 text-xs text-muted-foreground">{message}</p>}
            </div>
          )}

          {/* Modernize's tinted information panel, carrying what a caller actually needs. */}
          <div className="mt-6 flex gap-3 rounded-lg bg-primary-soft px-4 py-3.5">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary-text" aria-hidden />
            <p className="text-[13px] leading-relaxed text-muted-foreground">
              You are handed to your identity provider to sign in.{' '}
              <span className="font-semibold text-primary-text">
                VisionOne never sees your password.
              </span>
            </p>
          </div>

          <div className="my-7 flex items-center gap-3">
            <span className="h-px flex-1 bg-border" aria-hidden />
            <span className="text-sm text-muted-foreground">continue with</span>
            <span className="h-px flex-1 bg-border" aria-hidden />
          </div>

          <button
            type="button"
            onClick={onSignIn}
            disabled={state === 'working'}
            className="group flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-primary text-[15px] font-semibold text-primary-foreground transition-all duration-150 hover:brightness-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-70"
          >
            {state === 'working' ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground/30 border-t-primary-foreground" />
                Signing you in…
              </>
            ) : (
              <>
                {state === 'error' ? 'Try again' : 'Secure sign-in'}
                <ArrowRight className="h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5" />
              </>
            )}
          </button>

          <p className="mt-7 text-sm text-muted-foreground">
            New to VisionOne?{' '}
            <span className="font-semibold text-foreground">
              Practices are onboarded by Vision Digital Lab
            </span>{' '}
            — speak to your account contact.
          </p>
        </div>
      </div>
    </div>
  );
}

/** Shared frame for the brief moment before the session is known. */
export function AuthSplash({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-card px-4">
      <div className="max-w-sm text-center">
        <VisionOneMark className="text-lg" />
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}
