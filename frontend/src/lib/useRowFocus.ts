import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 * The row a notification pointed at, carried in history state rather than in the URL.
 *
 * Why not a query parameter. A URL is the least private thing a browser holds: it goes into
 * history, into every proxy and server access log along the way, and into the Referer header of
 * any request the page makes to another origin. A row id here identifies a lead or a call, which
 * in this product means a person at a clinic - so putting it in the address bar would scatter a
 * pseudo-identifier for a patient across logs nobody audits.
 *
 * History state travels with the navigation and none of that. It is also not shareable or
 * bookmarkable, which for a row about a person is the correct behaviour rather than a limitation:
 * a colleague should reach that row by looking it up under their own access, not by opening a
 * link. Category filters are the opposite case and stay in the URL, where they can be shared.
 *
 * The id is dropped from history state once used, so going back or refreshing does not re-focus a
 * row the reader has already dealt with, and the entry does not keep holding the id.
 *
 * @param ready pass false while the rows are still loading; the scroll needs a rendered target.
 */
export function useRowFocus(ready = true): string | null {
  const location = useLocation();
  const navigate = useNavigate();
  const pending = (location.state as { focus?: string } | null)?.focus ?? null;
  const [focused, setFocused] = useState<string | null>(null);

  useEffect(() => {
    if (!pending || !ready) return;

    setFocused(pending);

    const el = document.getElementById(`row-${pending}`);
    if (el) {
      const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      el.scrollIntoView({ behavior: still ? 'auto' : 'smooth', block: 'center' });
    }

    navigate(`${location.pathname}${location.search}`, { replace: true, state: null });
  }, [pending, ready, navigate, location.pathname, location.search]);

  return focused;
}
