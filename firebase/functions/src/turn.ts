import type { Firestore } from "firebase-admin/firestore";

/**
 * TURN relays let a call through networks that block direct (peer-to-peer) connections, such as some
 * office Wi-Fi and mobile carriers. Credentials come from Cloudflare Calls TURN and expire after a day,
 * so a leaked one is short-lived.
 *
 * The Cloudflare key lives in serverConfig/turn ({ keyId, apiToken }). No rule allows clients to read
 * serverConfig, so only this function sees it. Without it, calls fall back to STUN only (as before).
 */
export interface IceServer { urls: string[]; username?: string; credential?: string }

type FetchFn = (url: string, init: { method: string; headers: Record<string, string>; body: string }) =>
  Promise<{ ok: boolean; status: number; json(): Promise<unknown> }>;

export const TURN_TTL_SECONDS = 24 * 3600;

export async function turnCredentialsFor(db: Firestore, fetchFn: FetchFn): Promise<{ iceServers: IceServer[] }> {
  const cfg = (await db.doc("serverConfig/turn").get()).data();
  const keyId = typeof cfg?.keyId === "string" ? cfg.keyId : "";
  const apiToken = typeof cfg?.apiToken === "string" ? cfg.apiToken : "";
  if (!keyId || !apiToken) return { iceServers: [] };
  try {
    const res = await fetchFn(`https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(keyId)}/credentials/generate-ice-servers`, {
      method: "POST",
      headers: { Authorization: `Bearer ${apiToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ ttl: TURN_TTL_SECONDS }),
    });
    if (!res.ok) {
      console.warn(`TURN credentials failed: ${res.status}`);
      return { iceServers: [] };
    }
    const body = (await res.json()) as { iceServers?: unknown };
    // Cloudflare returns one object or a list; urls may be a string or a list
    const raw = Array.isArray(body.iceServers) ? body.iceServers : body.iceServers ? [body.iceServers] : [];
    const servers = raw.map((s) => {
      const o = s as { urls?: unknown; username?: unknown; credential?: unknown };
      const urls = Array.isArray(o.urls) ? o.urls.filter((u): u is string => typeof u === "string") : typeof o.urls === "string" ? [o.urls] : [];
      return {
        urls,
        ...(typeof o.username === "string" ? { username: o.username } : {}),
        ...(typeof o.credential === "string" ? { credential: o.credential } : {}),
      };
    }).filter((s) => s.urls.length > 0);
    return { iceServers: servers };
  } catch (e) {
    console.warn("TURN credentials failed", e);
    return { iceServers: [] };
  }
}
