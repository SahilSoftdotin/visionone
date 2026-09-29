import type { ProblemDetail } from './types';

const BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ProblemDetail | null,
  ) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
  }

  /** 403 means the caller has no membership here - a different message from "nothing found". */
  get isForbidden(): boolean {
    return this.status === 403;
  }

  /**
   * 401 means the token has expired or was rejected. It is not a statement about the account, and
   * a screen that treats it as one tells the caller their practice is missing when they simply
   * need to sign in again.
   */
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }
}

let tokenProvider: () => string | undefined = () => undefined;

export function setTokenProvider(provider: () => string | undefined): void {
  tokenProvider = provider;
}

export async function apiGet<T>(
  path: string,
  params?: Record<string, string | undefined>,
): Promise<T> {
  const url = new URL(`${BASE}${path}`, window.location.origin);
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined) url.searchParams.set(key, value);
  });

  const token = tokenProvider();
  const response = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  if (!response.ok) {
    let problem: ProblemDetail | null = null;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      problem = null;
    }
    throw new ApiError(response.status, problem);
  }

  return (await response.json()) as T;
}

/**
 * Writes. An Idempotency-Key is sent for every mutation so a double-click cannot apply twice -
 * the backend honours it where a repeat is plausible.
 */
export async function apiSend<T>(
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  body?: unknown,
  params?: Record<string, string | undefined>,
): Promise<T> {
  const url = new URL(`${BASE}${path}`, window.location.origin);
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined) url.searchParams.set(key, value);
  });

  const token = tokenProvider();
  const response = await fetch(url.toString(), {
    method,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'Idempotency-Key': crypto.randomUUID(),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    let problem: ProblemDetail | null = null;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      problem = null;
    }
    throw new ApiError(response.status, problem);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const queryKeys = {
  session: ['session'] as const,
  overview: (orgId: string, month: string) => ['overview', orgId, month] as const,
  growth: (orgId: string, month: string) => ['growth', orgId, month] as const,
  leads: (orgId: string, month: string) => ['leads', orgId, month] as const,
  leadDetail: (orgId: string, leadId: string) => ['lead', orgId, leadId] as const,
  work: (orgId: string) => ['work', orgId] as const,
  content: (orgId: string) => ['content', orgId] as const,
  // outcome is part of the key: without it a filtered response would be served for the
  // unfiltered view, and the table would silently keep showing only missed calls.
  frontDesk: (orgId: string, month: string, outcome: string | null = null) =>
    ['frontdesk', orgId, month, outcome] as const,
  calendar: (orgId: string, month: string) => ['calendar', orgId, month] as const,
  reports: (orgId: string) => ['reports', orgId] as const,
  report: (orgId: string, reportId: string) => ['report', orgId, reportId] as const,
  latestReport: (orgId: string) => ['report', orgId, 'latest'] as const,
};
