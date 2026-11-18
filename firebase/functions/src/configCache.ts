/**
 * Small per-instance cache for documents that change rarely (config/*, the goals list). A warm
 * function instance serves many calls; without this every call re-reads the same documents.
 * An admin's change reaches every instance within the TTL (60 seconds by default).
 *
 * Under Jest the cache is off by default so tests that change a config doc see it at once;
 * the cache's own test switches it on with setConfigCacheEnabled(true).
 */
interface Entry { value: unknown; at: number; }

const entries = new Map<string, Entry>();
const pending = new Map<string, Promise<unknown>>();
let enabled = process.env.JEST_WORKER_ID === undefined;

export const CONFIG_TTL_MS = 60_000;

export async function cached<T>(key: string, loader: () => Promise<T>, ttlMs: number = CONFIG_TTL_MS, now: () => number = Date.now): Promise<T> {
  if (!enabled) return loader();
  const hit = entries.get(key);
  if (hit && now() - hit.at < ttlMs) return hit.value as T;
  // Calls that arrive together share one read
  const inFlight = pending.get(key);
  if (inFlight) return inFlight as Promise<T>;
  const p = loader()
    .then((value) => { entries.set(key, { value, at: now() }); return value; })
    .finally(() => pending.delete(key));
  pending.set(key, p);
  return p;
}

/** Drops one key (after this instance writes that doc) or everything. */
export function clearConfigCache(key?: string): void {
  if (key === undefined) entries.clear();
  else entries.delete(key);
}

export function setConfigCacheEnabled(on: boolean): void {
  enabled = on;
  entries.clear();
}
