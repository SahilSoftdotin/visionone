import type { ReactNode } from 'react';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { VisionOneMark } from '@/components/ui/Brand';

/**
 * Sign-in: a form column on the left behind a wide gutter, an illustration mosaic filling the
 * right and bleeding off the edge.
 *
 * The reference layout puts email and password fields in that column. VisionOne cannot:
 * authentication is a redirect to the identity provider, and the product never handles a
 * credential. The column keeps the same rhythm - heading, subtitle, primary action, footer line -
 * with the action standing where the fields would be, on the same 330px measure behind the same
 * 7rem gutter as the Keycloak page this button leads to.
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
    // Form column left, illustration mosaic right - the same shape as the provider page this
    // hands over to, so the redirect does not feel like changing product.
    <div className="grid min-h-screen bg-card lg:grid-cols-[minmax(450px,40fr)_60fr]">
      <div className="relative flex flex-col justify-center px-6 py-10 sm:px-10 lg:py-12 lg:pl-[126px] lg:pr-0">
        <div className="w-full max-w-[324px]">
          {/* In flow, not absolutely positioned in the corner. It used to sit at left-4 top-4
              while everything else began at the 126px gutter, so the brand lined up with nothing
              on the page - the same fault the Keycloak sign-in page had. One measure, one left
              edge. It is also the heading now that "Welcome back" is gone, so it is a size
              larger to carry that. */}
          <div className="flex items-center gap-2.5">
            <img
              src="/brand/vision-digital-lab.svg"
              alt=""
              aria-hidden
              className="h-9 w-9 rounded-lg"
            />
            <VisionOneMark className="text-2xl font-extrabold" />
          </div>

          <p className="mt-7 text-base leading-[1.5]">Log in to your VisionOne account</p>

          {state === 'error' && (
            <div role="alert" className="mt-6 rounded-lg bg-critical-soft px-4 py-3 text-sm">
              <p className="font-semibold text-critical-text">Sign-in failed</p>
              {message && <p className="mt-0.5 text-xs text-muted-foreground">{message}</p>}
            </div>
          )}

          <div className="mt-6 flex gap-3 rounded-lg bg-primary-soft px-4 py-3.5">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary-text" aria-hidden />
            <p className="text-[13px] leading-relaxed text-muted-foreground">
              You are handed to your identity provider to sign in.{' '}
              <span className="font-semibold text-primary-text">
                VisionOne never sees your password.
              </span>
            </p>
          </div>

          <button
            type="button"
            onClick={onSignIn}
            disabled={state === 'working'}
            className="group mt-6 flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-primary text-base font-bold text-primary-foreground transition-all duration-150 hover:brightness-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-70"
          >
            {state === 'working' ? (
              <>
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground/30 border-t-primary-foreground" />
                Signing you in…
              </>
            ) : (
              <>
                {state === 'error' ? 'Try again' : 'Log In'}
                <ArrowRight className="h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5" />
              </>
            )}
          </button>

          <p className="mt-7 border-t border-border pt-5 text-sm text-muted-foreground">
            New to VisionOne?{' '}
            <span className="font-semibold text-foreground">
              Practices are onboarded by Vision Digital Lab
            </span>{' '}
            —{' '}
            {/* There is no self-registration: the realm has it disabled and an account is created
                with a practice, not by one. So the only honest destination is a conversation. */}
            <a
              href="https://visiondigitallab.com/contact"
              className="font-semibold text-primary-text underline underline-offset-2 hover:opacity-80"
            >
              talk to our team
            </a>
            .
          </p>
        </div>
      </div>

      {/* Bleeds off the right edge on purpose: a mosaic that stops neatly reads as a picture, one
          that runs off reads as a wall. */}
      <div
        aria-hidden
        className="hidden bg-white bg-[url('/brand/auth-mosaic.svg')] bg-[length:auto_104%] bg-left-top bg-no-repeat lg:block"
      />
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
