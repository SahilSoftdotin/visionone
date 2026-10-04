/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
  readonly VITE_OIDC_AUTHORITY: string;
  readonly VITE_OIDC_CLIENT_ID: string;
  readonly VITE_OIDC_REDIRECT_URI: string;
  /** Optional. Minutes a client may sit idle before being signed out. Defaults to 15. */
  readonly VITE_IDLE_TIMEOUT_MINUTES?: string;
  /** Optional. Seconds of warning before that sign-out. Defaults to 60. */
  readonly VITE_IDLE_WARNING_SECONDS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
