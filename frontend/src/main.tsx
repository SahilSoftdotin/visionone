import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './styles/index.css';

/**
 * Deliberately not wrapped in StrictMode.
 *
 * react-oidc-context 3.x guards its initialisation with a ref. StrictMode mounts, unmounts and
 * remounts in development, but the ref survives that cycle, so the second mount sees itself as
 * already initialised and skips the sign-in callback entirely. The authorization code is then
 * never exchanged: the URL keeps its ?code, the session never resolves, and the app bounces back
 * to the provider forever.
 *
 * It fails only in development, which is where every demo is given, so this is not a cost worth
 * paying for the extra warnings. Revisit if the library gains a StrictMode-safe initialisation.
 */
const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element not found');
}

createRoot(container).render(<App />);
