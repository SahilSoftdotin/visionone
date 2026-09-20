import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

/**
 * Puts every screen back at the top when the route changes.
 *
 * A single-page app keeps the scroll position across navigations, so arriving at Reports from
 * halfway down Leads drops you into the middle of a screen you have not read. Only the path
 * matters: changing the month on a screen should not throw the reader back to the top.
 */
export function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [pathname]);

  return null;
}
