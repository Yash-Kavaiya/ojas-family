import { GoogleAuth } from "google-auth-library";
import type { PlayApi } from "./billing";

export const PACKAGE_NAME = "com.ojashealing.family";
const BASE = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases`;

/**
 * Google Play Developer API over REST, authorised as the Functions service account. That account must be
 * invited in Play Console (Users and permissions) with "View financial data" and "Manage orders".
 */
export function playApi(): PlayApi {
  const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });

  async function call(method: "GET" | "POST", url: string): Promise<Record<string, unknown>> {
    const client = await auth.getClient();
    const res = await client.request<Record<string, unknown>>({ url, method, data: method === "POST" ? {} : undefined });
    return res.data ?? {};
  }

  return {
    async getSubscription(token) {
      const s = await call("GET", `${BASE}/subscriptionsv2/tokens/${encodeURIComponent(token)}`);
      const items = (s.lineItems ?? []) as Array<{ productId?: string; expiryTime?: string }>;
      const latest = items.map((i) => ({ productId: i.productId ?? "", expiry: new Date(i.expiryTime ?? 0) }))
        .sort((a, b) => b.expiry.getTime() - a.expiry.getTime())[0];
      const state = String(s.subscriptionState ?? "");
      // Grace period and cancelled-but-not-yet-expired still have paid time left
      const active = ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"].includes(state)
        && !!latest && latest.expiry.getTime() > Date.now();
      return {
        productId: latest?.productId ?? "",
        expiry: latest?.expiry ?? new Date(0),
        active,
        acknowledged: s.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
      };
    },
    async getProduct(productId, token) {
      const p = await call("GET", `${BASE}/products/${productId}/tokens/${encodeURIComponent(token)}`);
      return { purchased: p.purchaseState === 0, consumed: p.consumptionState === 1 };
    },
    async acknowledgeSubscription(productId, token) {
      await call("POST", `${BASE}/subscriptions/${productId}/tokens/${encodeURIComponent(token)}:acknowledge`);
    },
    async consumeProduct(productId, token) {
      await call("POST", `${BASE}/products/${productId}/tokens/${encodeURIComponent(token)}:consume`);
    },
  };
}
