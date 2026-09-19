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
}

let tokenProvider: () => string | undefined = () => undefined;

export function setTokenProvider(provider: () => string | undefined): void {
  tokenProvider = provider;
}

export async function apiGet<T>(path: string, params?: Record<string, string | undefined>): Promise<T> {
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

export const queryKeys = {
  session: ['session'] as const,
  overview: (orgId: string, month: string) => ['overview', orgId, month] as const,
};
