import { SELF, env, applyD1Migrations } from "cloudflare:test";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import migrations from "../migrations/meta.json";

let ipSequence = 1;

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/g, "");
}

function decodeBase64Url(value: string): Uint8Array {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/") + "=".repeat((4 - value.length % 4) % 4);
  return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
}

async function encryptForTest(value: string, aad: string, encodedKey: string): Promise<string> {
  const keyBytes = decodeBase64Url(encodedKey);
  const key = await crypto.subtle.importKey("raw", keyBytes.buffer.slice(keyBytes.byteOffset, keyBytes.byteOffset + keyBytes.byteLength) as ArrayBuffer, "AES-GCM", false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt({
    name: "AES-GCM",
    iv: iv.buffer.slice(iv.byteOffset, iv.byteOffset + iv.byteLength) as ArrayBuffer,
    additionalData: new TextEncoder().encode(aad).buffer as ArrayBuffer,
  }, key, new TextEncoder().encode(value).buffer as ArrayBuffer);
  return `v1.${base64Url(iv)}.${base64Url(new Uint8Array(ciphertext))}`;
}

function nextIp(): string {
  ipSequence += 1;
  return `192.0.2.${ipSequence}`;
}

function jsonRequest(body: unknown, headers: HeadersInit = {}): RequestInit {
  return {
    method: "POST",
    headers: { "content-type": "application/json", "cf-connecting-ip": nextIp(), ...headers },
    body: JSON.stringify(body),
  };
}

async function createPairing(deviceName = "Sala de estar") {
  const response = await SELF.fetch("https://makimono.test/v1/pairings", jsonRequest({
    device_name: deviceName,
    client_version: "1.6.4.1",
    protocol_version: 1,
  }));
  const body = await response.json() as { pairing_id: string; device_secret: string; confirm_code: string; qr_url: string; expires_in: number };
  expect(response.status).toBe(201);
  return { response, ...body };
}

async function authorizeForTest(pairingId: string, sub = "google-sub-test"): Promise<void> {
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare("INSERT OR IGNORE INTO accounts (google_sub, email, refresh_token_cipher, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
    .bind(sub, "test@example.invalid", await encryptForTest("test-refresh-token", sub, env.TOKEN_ENC_KEY), now, now).run();
  await env.DB.prepare("UPDATE pairings SET status = 'APPROVED', google_sub = ? WHERE pairing_id = ?")
    .bind(sub, pairingId).run();
}

beforeAll(async () => {
  const hmacKey = base64Url(crypto.getRandomValues(new Uint8Array(32)));
  const encryptionKey = base64Url(crypto.getRandomValues(new Uint8Array(32)));
  Object.assign(env as unknown as Record<string, string>, {
    HMAC_KEY: hmacKey,
    TOKEN_ENC_KEY: encryptionKey,
    GOOGLE_CLIENT_ID: "test-client-id",
    GOOGLE_CLIENT_SECRET: base64Url(crypto.getRandomValues(new Uint8Array(32))),
  });
  await applyD1Migrations(env.DB, migrations);
});

beforeEach(async () => {
  await env.DB.batch([
    env.DB.prepare("DELETE FROM sessions"),
    env.DB.prepare("DELETE FROM oauth_states"),
    env.DB.prepare("DELETE FROM pairings"),
    env.DB.prepare("DELETE FROM accounts"),
    env.DB.prepare("DELETE FROM rate_limits"),
  ]);
});

afterEach(() => vi.unstubAllGlobals());

describe("Makimono TV pairing", () => {
  it("creates a short-lived pairing with a secret that is not embedded in its QR URL", async () => {
    const { response, pairing_id, device_secret, confirm_code, qr_url, expires_in } = await createPairing();

    expect(pairing_id).toMatch(/^[A-Za-z0-9_-]{24}$/);
    expect(device_secret).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(confirm_code).toMatch(/^[2-9]{6}$/);
    expect(qr_url).toBe(`https://makimono.test/p/${pairing_id}`);
    expect(qr_url).not.toContain(device_secret);
    expect(expires_in).toBe(300);
    expect(response.headers.get("cache-control")).toBe("no-store");
    const saved = await env.DB.prepare("SELECT * FROM pairings WHERE pairing_id = ?").bind(pairing_id).first<Record<string, unknown>>();
    expect(saved?.device_secret_hash).not.toBe(device_secret);
    expect(saved?.confirm_code_hash).not.toBe(confirm_code);
  });

  it("requires the device secret to poll status", async () => {
    const pairing = await createPairing();
    const missing = await SELF.fetch(`https://makimono.test/v1/pairings/${pairing.pairing_id}`);
    const valid = await SELF.fetch(`https://makimono.test/v1/pairings/${pairing.pairing_id}`, { headers: { "x-device-secret": pairing.device_secret } });

    expect(missing.status).toBe(401);
    expect(valid.status).toBe(200);
    await expect(valid.json()).resolves.toMatchObject({ state: "pending", expires_in: 300 });
  });

  it("escapes the device name and issues a secure, same-site CSRF cookie for the confirmation form", async () => {
    const pairing = await createPairing("TV <script>alert(1)</script>");
    const response = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
    const body = await response.text();
    const setCookie = response.headers.get("set-cookie") ?? "";
    const csp = response.headers.get("content-security-policy") ?? "";

    expect(response.status).toBe(200);
    expect(body).toContain("TV &lt;script&gt;");
    expect(body).not.toContain("<script>");
    expect(setCookie).toContain("makimono_pairing_csrf=");
    expect(setCookie).toContain("Secure");
    expect(setCookie).toContain("HttpOnly");
    expect(setCookie).toContain("SameSite=Lax");
    expect(body).toContain(`action="https://makimono.test/p/${pairing.pairing_id}/confirm"`);
    expect(csp).toContain("form-action 'self' https://makimono.test https://accounts.google.com");
    expect(response.headers.get("referrer-policy")).toBe("no-referrer");
  });

  it("rejects cross-site confirmation without a valid CSRF proof without changing pairing state", async () => {
    const pairing = await createPairing();
    const formPage = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
    const csrfCookie = formPage.headers.get("set-cookie")?.split(";")[0] ?? "";
    const state = await env.DB.prepare("SELECT status FROM pairings WHERE pairing_id = ?").bind(pairing.pairing_id).first<{ status: string }>();
    const response = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST",
      headers: { origin: "https://attacker.test", cookie: csrfCookie, "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ code: pairing.confirm_code, csrf: "0".repeat(64) }),
    });

    expect(response.status).toBe(403);
    expect(state?.status).toBe("PENDING");
  });

  it("rejects oversized and unsupported confirmation bodies before changing pairing state", async () => {
    const pairing = await createPairing();
    const formPage = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
    const csrfCookie = formPage.headers.get("set-cookie")?.split(";")[0] ?? "";
    const proof = (await formPage.text()).match(/name="csrf" value="([0-9a-f]+)"/)?.[1] ?? "";
    const validFields = new URLSearchParams({ code: pairing.confirm_code, csrf: proof }).toString();
    const oversized = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST",
      headers: { origin: "https://makimono.test", cookie: csrfCookie, "content-type": "application/x-www-form-urlencoded", "cf-connecting-ip": nextIp() },
      body: `${validFields}&padding=${"x".repeat(5000)}`,
    });
    const unsupportedType = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST",
      headers: { origin: "https://makimono.test", cookie: csrfCookie, "content-type": "text/plain", "cf-connecting-ip": nextIp() },
      body: validFields,
    });
    const saved = await env.DB.prepare("SELECT status FROM pairings WHERE pairing_id = ?").bind(pairing.pairing_id).first<{ status: string }>();

    expect(oversized.status).toBe(413);
    expect(unsupportedType.status).toBe(415);
    expect(saved?.status).toBe("PENDING");
  });

  it("confirms a valid code when a same-site browser omits the Origin header", async () => {
    const pairing = await createPairing();
    const formPage = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
    const csrfCookie = formPage.headers.get("set-cookie")?.split(";")[0] ?? "";
    const proof = (await formPage.text()).match(/name="csrf" value="([0-9a-f]+)"/)?.[1] ?? "";
    const response = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST",
      headers: { cookie: csrfCookie, "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ code: pairing.confirm_code, csrf: proof }),
      redirect: "manual",
    });
    const saved = await env.DB.prepare("SELECT status FROM pairings WHERE pairing_id = ?").bind(pairing.pairing_id).first<{ status: string }>();

    expect(response.status, await response.clone().text()).toBe(303);
    expect(response.headers.get("location")).toBe(`/oauth/start?p=${pairing.pairing_id}`);
    expect(saved?.status).toBe("CONFIRMED");
  });

  it("resumes OAuth when a confirmed browser repeats the POST after the CSRF cookie was cleared", async () => {
    const pairing = await createPairing();
    const formPage = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
    const csrfCookie = formPage.headers.get("set-cookie")?.split(";")[0] ?? "";
    const proof = (await formPage.text()).match(/name="csrf" value="([0-9a-f]+)"/)?.[1] ?? "";
    const body = new URLSearchParams({ code: pairing.confirm_code, csrf: proof });
    const firstConfirmation = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST",
      headers: { cookie: csrfCookie, "content-type": "application/x-www-form-urlencoded" },
      body,
      redirect: "manual",
    });
    const browserCookie = firstConfirmation.headers.getSetCookie()
      .find((value) => value.startsWith("makimono_pairing_browser="))
      ?.split(";")[0] ?? "";
    const repeatedConfirmation = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST",
      headers: { cookie: browserCookie, "content-type": "application/x-www-form-urlencoded" },
      body,
      redirect: "manual",
    });
    const oauthStart = await SELF.fetch(`https://makimono.test${repeatedConfirmation.headers.get("location")}`, {
      headers: { cookie: browserCookie },
      redirect: "manual",
    });

    expect(firstConfirmation.status).toBe(303);
    expect(browserCookie).not.toBe("");
    expect(repeatedConfirmation.status).toBe(303);
    expect(repeatedConfirmation.headers.get("location")).toBe(`/oauth/start?p=${pairing.pairing_id}`);
    expect(oauthStart.status).toBe(302);
    expect(new URL(oauthStart.headers.get("location") ?? "https://invalid.test").origin).toBe("https://accounts.google.com");
  });

  it("allows no more than five incorrect confirmation codes", async () => {
    const pairing = await createPairing();
    for (let attempt = 0; attempt < 5; attempt += 1) {
      const formPage = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
      const csrfCookie = formPage.headers.get("set-cookie")?.split(";")[0] ?? "";
      const csrfToken = csrfCookie.slice(csrfCookie.indexOf("=") + 1);
      const page = await formPage.text();
      const proof = page.match(/name="csrf" value="([0-9a-f]+)"/)?.[1] ?? "";
      const response = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
        method: "POST",
        headers: { origin: "https://makimono.test", cookie: csrfCookie, "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ code: "000000", csrf: proof }),
      });
      expect(response.status).toBe(400);
      expect(response.headers.get("location")).toBeNull();
      expect(csrfToken).not.toBe("");
    }
    const status = await env.DB.prepare("SELECT status, failed_attempts FROM pairings WHERE pairing_id = ?").bind(pairing.pairing_id).first<{ status: string; failed_attempts: number }>();
    expect(status).toEqual({ status: "DENIED", failed_attempts: 5 });
  });

  it("requires the same browser cookie to start OAuth after a valid code", async () => {
    const pairing = await createPairing();
    const formPage = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
    const csrfCookie = formPage.headers.get("set-cookie")?.split(";")[0] ?? "";
    const proof = (await formPage.text()).match(/name="csrf" value="([0-9a-f]+)"/)?.[1] ?? "";
    const confirmation = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST",
      headers: { origin: "https://makimono.test", cookie: csrfCookie, "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ code: pairing.confirm_code, csrf: proof }),
      redirect: "manual",
    });
    const cookies = confirmation.headers.getSetCookie();
    const browserCookie = cookies.find((value) => value.startsWith("makimono_pairing_browser="))?.split(";")[0] ?? "";
    const startWithoutCookie = await SELF.fetch(`https://makimono.test/oauth/start?p=${pairing.pairing_id}`);
    const startWithMalformedCookie = await SELF.fetch(`https://makimono.test/oauth/start?p=${pairing.pairing_id}`, { headers: { cookie: "makimono_pairing_browser=%" } });
    const start = await SELF.fetch(`https://makimono.test/oauth/start?p=${pairing.pairing_id}`, { headers: { cookie: browserCookie }, redirect: "manual" });
    const oauthUrl = new URL(start.headers.get("location") ?? "https://invalid.test");

    expect(confirmation.status, await confirmation.clone().text()).toBe(303);
    expect(browserCookie).not.toBe("");
    expect(startWithoutCookie.status).toBe(403);
    expect(startWithMalformedCookie.status).toBe(403);
    expect(start.status).toBe(302);
    expect(oauthUrl.origin).toBe("https://accounts.google.com");
    expect(oauthUrl.searchParams.get("scope")).toContain("https://www.googleapis.com/auth/drive.readonly");
    expect(oauthUrl.searchParams.get("code_challenge_method")).toBe("S256");
    expect(start.headers.get("cache-control")).toBe("no-store");
    const persisted = await env.DB.prepare("SELECT browser_cookie_hash, state_hash, nonce_hash, verifier_cipher FROM oauth_states WHERE pairing_id = ?").bind(pairing.pairing_id).first<{ browser_cookie_hash: string; state_hash: string; nonce_hash: string; verifier_cipher: string }>();
    expect(persisted?.browser_cookie_hash).toHaveLength(64);
    expect(persisted?.state_hash).not.toBe(oauthUrl.searchParams.get("state"));
    expect(persisted?.verifier_cipher).toMatch(/^v1\./);
    expect(persisted?.verifier_cipher).not.toContain(oauthUrl.searchParams.get("code_challenge"));
  });

  it("rejects an OAuth callback without the browser that started it without consuming state", async () => {
    const pairing = await createPairing();
    const formPage = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}`);
    const csrfCookie = formPage.headers.get("set-cookie")?.split(";")[0] ?? "";
    const proof = (await formPage.text()).match(/name="csrf" value="([0-9a-f]+)"/)?.[1] ?? "";
    const confirmation = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}/confirm`, {
      method: "POST", headers: { origin: "https://makimono.test", cookie: csrfCookie, "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ code: pairing.confirm_code, csrf: proof }),
      redirect: "manual",
    });
    const browserCookie = confirmation.headers.getSetCookie().find((value) => value.startsWith("makimono_pairing_browser="))?.split(";")[0] ?? "";
    const start = await SELF.fetch(`https://makimono.test/oauth/start?p=${pairing.pairing_id}`, { headers: { cookie: browserCookie }, redirect: "manual" });
    const state = new URL(start.headers.get("location")!).searchParams.get("state")!;
    const callback = await SELF.fetch(`https://makimono.test/oauth/callback?code=placeholder&state=${encodeURIComponent(state)}`);

    expect(callback.status).toBe(403);
    await expect(callback.text()).resolves.toContain("Navegador não reconhecido");
    const consumed = await env.DB.prepare("SELECT consumed_at FROM oauth_states WHERE pairing_id = ?").bind(pairing.pairing_id).first<{ consumed_at: number | null }>();
    expect(consumed?.consumed_at).toBeNull();
  });

  it("returns the device session only once and reports consumed after a lost response", async () => {
    const pairing = await createPairing();
    await authorizeForTest(pairing.pairing_id);
    const poll = () => SELF.fetch(`https://makimono.test/v1/pairings/${pairing.pairing_id}`, { headers: { "x-device-secret": pairing.device_secret } });
    const first = await poll();
    const firstBody = await first.json() as Record<string, unknown>;
    const retry = await poll();
    const retryBody = await retry.json() as Record<string, unknown>;

    expect(firstBody.state).toBe("approved");
    expect(firstBody.session_token).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(retryBody).toMatchObject({ state: "consumed" });
    expect(retryBody).not.toHaveProperty("session_token");
    const sessions = await env.DB.prepare("SELECT COUNT(*) AS total FROM sessions WHERE pairing_id = ?").bind(pairing.pairing_id).first<{ total: number }>();
    expect(sessions?.total).toBe(1);
  });

  it("logs out only the current TV and preserves a second TV session on the same Google grant", async () => {
    const firstPairing = await createPairing("TV sala");
    const secondPairing = await createPairing("TV quarto");
    await authorizeForTest(firstPairing.pairing_id);
    await authorizeForTest(secondPairing.pairing_id);
    const claim = async (pairing: typeof firstPairing) => {
      const response = await SELF.fetch(`https://makimono.test/v1/pairings/${pairing.pairing_id}`, { headers: { "x-device-secret": pairing.device_secret } });
      return (await response.json() as { session_token: string }).session_token;
    };
    const firstToken = await claim(firstPairing);
    const secondToken = await claim(secondPairing);
    const loggedOut = await SELF.fetch("https://makimono.test/v1/logout", { method: "POST", headers: { authorization: `Bearer ${firstToken}` } });
    const remainingSession = await SELF.fetch("https://makimono.test/v1/logout", { method: "POST", headers: { authorization: `Bearer ${secondToken}` } });

    expect(loggedOut.status).toBe(204);
    expect(remainingSession.status).toBe(204);
    const account = await env.DB.prepare("SELECT refresh_token_cipher FROM accounts WHERE google_sub = ?").bind("google-sub-test").first<{ refresh_token_cipher: string | null }>();
    expect(account?.refresh_token_cipher).toMatch(/^v1\./);
  });

  it("keeps Google grant revocation behind the separate disconnect-all route", async () => {
    const firstPairing = await createPairing("TV sala");
    const secondPairing = await createPairing("TV quarto");
    await authorizeForTest(firstPairing.pairing_id);
    await authorizeForTest(secondPairing.pairing_id);
    const claim = async (pairing: typeof firstPairing) => {
      const response = await SELF.fetch(`https://makimono.test/v1/pairings/${pairing.pairing_id}`, { headers: { "x-device-secret": pairing.device_secret } });
      return (await response.json() as { session_token: string }).session_token;
    };
    const firstToken = await claim(firstPairing);
    const secondToken = await claim(secondPairing);
    const googleRevoke = vi.fn(async () => new Response(null, { status: 200 }));
    vi.stubGlobal("fetch", googleRevoke);
    const result = await SELF.fetch("https://makimono.test/v1/disconnect-all", { method: "POST", headers: { authorization: `Bearer ${firstToken}` } });

    expect(result.status).toBe(204);
    expect(googleRevoke).toHaveBeenCalledWith("https://oauth2.googleapis.com/revoke", expect.objectContaining({ method: "POST" }));
    const account = await env.DB.prepare("SELECT refresh_token_cipher FROM accounts WHERE google_sub = ?").bind("google-sub-test").first<{ refresh_token_cipher: string | null }>();
    const sessions = await env.DB.prepare("SELECT revoked_at FROM sessions WHERE google_sub = ?").bind("google-sub-test").all<{ revoked_at: number | null }>();
    const secondDeviceTokenResult = await SELF.fetch("https://makimono.test/v1/drive-token", { headers: { authorization: `Bearer ${secondToken}` } });
    expect(account?.refresh_token_cipher).toBeNull();
    expect(sessions.results).toHaveLength(2);
    expect(sessions.results.every((session) => session.revoked_at !== null)).toBe(true);
    expect(secondDeviceTokenResult.status).toBe(401);
  });

  it("does not revoke local account state when Google's revoke endpoint fails", async () => {
    const pairing = await createPairing();
    await authorizeForTest(pairing.pairing_id);
    const claim = await SELF.fetch(`https://makimono.test/v1/pairings/${pairing.pairing_id}`, { headers: { "x-device-secret": pairing.device_secret } });
    const token = (await claim.json() as { session_token: string }).session_token;
    vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 503 })));

    const result = await SELF.fetch("https://makimono.test/v1/disconnect-all", { method: "POST", headers: { authorization: `Bearer ${token}` } });

    expect(result.status).toBe(502);
    const account = await env.DB.prepare("SELECT refresh_token_cipher FROM accounts WHERE google_sub = ?").bind("google-sub-test").first<{ refresh_token_cipher: string | null }>();
    const session = await env.DB.prepare("SELECT revoked_at FROM sessions WHERE google_sub = ?").bind("google-sub-test").first<{ revoked_at: number | null }>();
    expect(account?.refresh_token_cipher).toMatch(/^v1\./);
    expect(session?.revoked_at).toBeNull();
  });

  it("rejects malformed and oversized pairing requests", async () => {
    const malformed = await SELF.fetch("https://makimono.test/v1/pairings", {
      method: "POST", headers: { "content-type": "application/json", "cf-connecting-ip": nextIp() }, body: "{",
    });
    const oversized = await SELF.fetch("https://makimono.test/v1/pairings", jsonRequest({ device_name: "x".repeat(49), client_version: "1", protocol_version: 1 }));

    expect(malformed.status).toBe(400);
    expect(oversized.status).toBe(400);
  });

  it("prefills confirmation code in the HTML form when provided via query parameter", async () => {
    const pairing = await createPairing();
    const withCode = await SELF.fetch(`https://makimono.test/p/${pairing.pairing_id}?code=${pairing.confirm_code}`);
    expect(withCode.status).toBe(200);
    const body = await withCode.text();
    expect(body).toContain(`value="${pairing.confirm_code}"`);
  });
});
