import { useSearchParams } from 'react-router-dom';

/**
 * A filter kept in the URL, so a filtered view can be linked to and survives a refresh.
 *
 * Only ever a category - a status, an outcome. Row identifiers stay out of the address bar
 * and travel in history state instead; see useRowFocus for why.
 *
 * A value the list does not recognise reads as no filter rather than as an error, because
 * this arrives from a URL where a stale link or a typo is ordinary.
 */
export function useUrlFilter<S extends string>(param: string, allowed: readonly S[]) {
  const [searchParams, setSearchParams] = useSearchParams();
  const raw = searchParams.get(param);
  const value = raw && (allowed as readonly string[]).includes(raw) ? (raw as S) : null;
  const set = (next: S | null) => {
    const params = new URLSearchParams(searchParams);
    if (next) params.set(param, next);
    else params.delete(param);
    setSearchParams(params, { replace: true });
  };
  return [value, set] as const;
}
