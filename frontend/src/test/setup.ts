import '@testing-library/jest-dom/vitest';

/**
 * A localStorage shim.
 *
 * This environment gives us `sessionStorage` but not `localStorage` - `'localStorage' in window` is
 * true while the value is `undefined`, so it is shadowed rather than absent. The cause is Node
 * itself, not jsdom: the run prints "localStorage is not available because --localstorage-file was
 * not provided", which is Node's own experimental localStorage taking the name and then declining
 * to implement it.
 *
 * It matters because the idle-timeout hook shares its last-activity timestamp between tabs through
 * localStorage. The hook is written to survive storage being missing - every access is wrapped, and
 * it degrades to per-tab timing - but a test environment without it silently skips the cross-tab
 * behaviour rather than covering it.
 *
 * Deliberately a real in-memory implementation rather than a `vi.fn()` stub, so tests exercise the
 * same read-write-read path the browser does.
 */
if (typeof window !== 'undefined' && !window.localStorage) {
  const store = new Map<string, string>();
  const shim: Storage = {
    get length() {
      return store.size;
    },
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, String(value)),
    removeItem: (key: string) => void store.delete(key),
    clear: () => store.clear(),
  };
  Object.defineProperty(window, 'localStorage', { value: shim, configurable: true });
}
