const DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
const OIDC_SCOPES = ["openid", "email", DRIVE_SCOPE].join(" ");
const PAIRING_TTL_SECONDS = 300;
const OAUTH_TTL_SECONDS = 600;
const SESSION_TOKEN_BYTES = 32;
const DEVICE_SECRET_BYTES = 32;
const CODE_ALPHABET = "23456789";
const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const GOOGLE_CERTS_URL = "https://www.googleapis.com/oauth2/v3/certs";

interface PairingEnv {
  DB: D1Database;
  GOOGLE_CLIENT_ID: string;
  GOOGLE_CLIENT_SECRET: string;
  TOKEN_ENC_KEY: string;
  HMAC_KEY: string;
}

type PairingStatus = "PENDING" | "CONFIRMED" | "APPROVED" | "CONSUMED" | "DENIED" | "CANCELLED" | "EXPIRED";

const utf8 = new TextEncoder();
const fromUtf8 = new TextDecoder();

function bufferSource(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

function json(body: unknown, status = 200, headers: HeadersInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers },
  });
}

function html(body: string, status = 200, headers: HeadersInit = {}): Response {
  return new Response(body, {
    status,
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
      ...headers,
    },
  });
}

function randomBytes(length: number): Uint8Array {
  return crypto.getRandomValues(new Uint8Array(length));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/g, "");
}

function decodeBase64Url(value: string): Uint8Array {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/") + "=".repeat((4 - value.length % 4) % 4);
  return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
}

function equalBytes(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) return false;
  let mismatch = 0;
  for (let index = 0; index < left.length; index += 1) mismatch |= left[index] ^ right[index];
  return mismatch === 0;
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", utf8.encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function hmac(value: string, env: PairingEnv): Promise<string> {
  const key = await crypto.subtle.importKey("raw", utf8.encode(env.HMAC_KEY), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, bufferSource(utf8.encode(value)));
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function constantTimeHexEqual(left: string, right: string): boolean {
  if (!/^[0-9a-f]{64}$/i.test(left) || !/^[0-9a-f]{64}$/i.test(right)) return false;
  return equalBytes(Uint8Array.from(left.match(/.{2}/g)!, (pair) => parseInt(pair, 16)), Uint8Array.from(right.match(/.{2}/g)!, (pair) => parseInt(pair, 16)));
}

function cookie(request: Request, name: string): string | null {
  const header = request.headers.get("cookie") ?? "";
  for (const item of header.split(";")) {
    const separator = item.indexOf("=");
    if (separator >= 0 && item.slice(0, separator).trim() === name) {
      try {
        return decodeURIComponent(item.slice(separator + 1).trim());
      } catch {
        return null;
      }
    }
  }
  return null;
}

function secureCookie(name: string, value: string, path = "/"): string {
  return `${name}=${encodeURIComponent(value)}; Path=${path}; Max-Age=600; Secure; HttpOnly; SameSite=Lax`;
}

function clearCookie(name: string, path = "/"): string {
  return `${name}=; Path=${path}; Max-Age=0; Secure; HttpOnly; SameSite=Lax`;
}

function safeText(value: string): string {
  return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

type PageTone = "info" | "success" | "error";

// Inline SVG only: the CSP is default-src 'none', so no external fonts, images or scripts.
const PAGE_ICONS: Record<PageTone, string> = {
  info: `<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2.5"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>`,
  success: `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m8 12.5 2.8 2.8L16.5 9.5"/></svg>`,
  error: `<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.4v.1"/></svg>`,
};

const PAGE_CSS = `
:root{color-scheme:dark;--bg:#070b12;--surface:#0f1722;--border:#1f2c3b;--text:#eef4fa;--muted:#94a7bb;--accent:#21c9df;--ok:#3ddc97;--err:#ff6b6b}
*{box-sizing:border-box}
html,body{margin:0}
body{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;color:var(--text);background:radial-gradient(900px 500px at 50% -10%,#10303b 0%,transparent 60%),var(--bg);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
.wrap{width:100%;max-width:440px}
.brand{display:flex;align-items:center;justify-content:center;gap:10px;margin-bottom:20px;font-weight:700;letter-spacing:.14em;text-transform:uppercase;font-size:13px;color:var(--muted)}
.brand i{display:block;width:10px;height:10px;border-radius:3px;background:var(--accent);box-shadow:0 0 14px var(--accent)}
.card{padding:36px 32px 32px;border:1px solid var(--border);border-radius:20px;background:linear-gradient(180deg,#111c29 0%,var(--surface) 100%);box-shadow:0 24px 60px rgba(0,0,0,.45);text-align:center}
.icon{width:56px;height:56px;margin:0 auto 20px;display:grid;place-items:center;border-radius:16px;background:rgba(33,201,223,.1);color:var(--accent)}
.icon svg{width:28px;height:28px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.success .icon{background:rgba(61,220,151,.12);color:var(--ok)}
.error .icon{background:rgba(255,107,107,.12);color:var(--err)}
h1{margin:0 0 10px;font-size:22px;line-height:1.25;font-weight:700;letter-spacing:-.01em}
p{margin:0;color:var(--muted)}
form{margin-top:28px;text-align:left}
label{display:block;margin-bottom:8px;font-size:13px;font-weight:600;color:var(--muted);letter-spacing:.02em}
input{width:100%;padding:16px;border-radius:12px;border:1px solid #33475c;background:#0a121c;color:var(--text);font:600 28px/1 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;letter-spacing:.5em;text-align:center;transition:border-color .15s,box-shadow .15s}
input:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px rgba(33,201,223,.25)}
button{width:100%;margin-top:16px;padding:15px 18px;border:0;border-radius:12px;background:var(--accent);color:#00141b;font:700 16px system-ui,sans-serif;cursor:pointer;transition:filter .15s,transform .05s}
button:hover{filter:brightness(1.08)}
button:active{transform:translateY(1px)}
button:focus-visible{outline:3px solid #fff;outline-offset:2px}
.foot{margin-top:20px;text-align:center;font-size:12px;color:#5f7287}
@media (max-width:420px){.card{padding:28px 20px 24px}input{font-size:24px;letter-spacing:.4em}}
`.replace(/\s*\n\s*/g, "");

function page(title: string, message: string, extra = "", tone: PageTone = extra ? "info" : "error"): string {
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>${safeText(title)} · Makimono</title><style>${PAGE_CSS}</style><body><div class="wrap"><div class="brand"><i></i>Makimono</div><main class="card ${tone}"><div class="icon">${PAGE_ICONS[tone]}</div><h1>${safeText(title)}</h1><p>${safeText(message)}</p>${extra}</main><div class="foot">Conexão segura · você pode fechar esta página após concluir</div></div></body></html>`;
}

async function encryptionKey(env: PairingEnv): Promise<CryptoKey> {
  const raw = decodeBase64Url(env.TOKEN_ENC_KEY);
  if (raw.length !== 32) throw new Error("TOKEN_ENC_KEY must be a base64url encoded 32-byte key");
  return crypto.subtle.importKey("raw", bufferSource(raw), "AES-GCM", false, ["encrypt", "decrypt"]);
}

async function encrypt(value: string, aad: string, env: PairingEnv): Promise<string> {
  const iv = randomBytes(12);
  const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv: bufferSource(iv), additionalData: bufferSource(utf8.encode(aad)), tagLength: 128 }, await encryptionKey(env), bufferSource(utf8.encode(value)));
  return `v1.${base64Url(iv)}.${base64Url(new Uint8Array(ciphertext))}`;
}

async function decrypt(value: string, aad: string, env: PairingEnv): Promise<string> {
  const [version, iv, ciphertext] = value.split(".");
  if (version !== "v1" || !iv || !ciphertext) throw new Error("Unsupported encrypted value");
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: bufferSource(decodeBase64Url(iv)), additionalData: bufferSource(utf8.encode(aad)), tagLength: 128 }, await encryptionKey(env), bufferSource(decodeBase64Url(ciphertext)));
  return fromUtf8.decode(plain);
}

function originOf(request: Request): string {
  const url = new URL(request.url);
  if (url.protocol !== "https:" && url.hostname !== "localhost" && url.hostname !== "127.0.0.1") throw new Error("HTTPS is required");
  return url.origin;
}

type FormBodyResult = { form: URLSearchParams } | { errorStatus: 400 | 413 | 415 };

async function formBody(request: Request): Promise<FormBodyResult> {
  const contentType = request.headers.get("content-type")?.split(";", 1)[0].trim().toLowerCase();
  if (contentType !== "application/x-www-form-urlencoded") return { errorStatus: 415 };

  const contentLength = request.headers.get("content-length");
  if (contentLength !== null) {
    if (!/^\d+$/.test(contentLength)) return { errorStatus: 400 };
    if (Number(contentLength) > 4096) return { errorStatus: 413 };
  }

  const reader = request.body?.getReader();
  if (!reader) return { form: new URLSearchParams() };
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      if (totalBytes > 4096) {
        await reader.cancel();
        return { errorStatus: 413 };
      }
      chunks.push(value);
    }
  } catch {
    return { errorStatus: 400 };
  } finally {
    reader.releaseLock();
  }

  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return { form: new URLSearchParams(new TextDecoder("utf-8", { fatal: true }).decode(bytes)) };
  } catch {
    return { errorStatus: 400 };
  }
}

async function rateLimit(request: Request, env: PairingEnv, action: string, limit: number, windowSeconds: number): Promise<boolean> {
  const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
  const key = await sha256(`${action}:${ip}`);
  const bucket = Math.floor(Date.now() / 1000 / windowSeconds) * windowSeconds;
  const result = await env.DB.prepare(`INSERT INTO rate_limits (rate_key, window_start, request_count)
      VALUES (?, ?, 1) ON CONFLICT(rate_key) DO UPDATE SET
      request_count = CASE WHEN window_start = excluded.window_start THEN request_count + 1 ELSE 1 END,
      window_start = excluded.window_start RETURNING request_count`).bind(key, bucket).first<{ request_count: number }>();
  return (result?.request_count ?? limit + 1) <= limit;
}

async function readPairing(env: PairingEnv, id: string) {
  return env.DB.prepare("SELECT * FROM pairings WHERE pairing_id = ?").bind(id).first<{
    pairing_id: string; device_secret_hash: string; confirm_code_hash: string; failed_attempts: number;
    device_name: string; expires_at: number; created_at: number; status: PairingStatus;
    browser_cookie_hash: string | null; google_sub: string | null;
  }>();
}

function isExpired(pairing: { expires_at: number }): boolean {
  return pairing.expires_at <= Math.floor(Date.now() / 1000);
}

async function expirePairing(env: PairingEnv, pairing: { pairing_id: string; expires_at: number; status: PairingStatus }): Promise<void> {
  if (pairing.expires_at <= Math.floor(Date.now() / 1000) && ["PENDING", "CONFIRMED", "APPROVED"].includes(pairing.status)) {
    await env.DB.prepare("UPDATE pairings SET status = 'EXPIRED' WHERE pairing_id = ? AND status = ?").bind(pairing.pairing_id, pairing.status).run();
  }
}

async function createPairing(request: Request, env: PairingEnv): Promise<Response> {
  if (!(await rateLimit(request, env, "pair-create", 10, 3600))) return json({ error: "rate_limited" }, 429, { "retry-after": "3600" });
  const contentLength = Number(request.headers.get("content-length") ?? 0);
  if (contentLength > 4096) return json({ error: "request_too_large" }, 413);
  let body: Record<string, unknown>;
  try { body = await request.json() as Record<string, unknown>; } catch { return json({ error: "invalid_json" }, 400); }
  const rawName = typeof body.device_name === "string" ? body.device_name.trim() : "Android TV";
  const rawVersion = typeof body.client_version === "string" ? body.client_version : "unknown";
  if (rawName.length > 48 || rawVersion.length > 32) return json({ error: "invalid_request" }, 400);
  const deviceName = rawName;
  const clientVersion = rawVersion;
  const protocolVersion = typeof body.protocol_version === "number" ? body.protocol_version : 1;
  if (!deviceName || !Number.isInteger(protocolVersion) || protocolVersion < 1) return json({ error: "invalid_request" }, 400);
  const id = base64Url(randomBytes(18));
  const deviceSecret = base64Url(randomBytes(DEVICE_SECRET_BYTES));
  const confirmCode = Array.from(randomBytes(6), (byte) => CODE_ALPHABET[byte % CODE_ALPHABET.length]).join("");
  const now = Math.floor(Date.now() / 1000);
  try {
    await env.DB.prepare(`INSERT INTO pairings
      (pairing_id, device_secret_hash, confirm_code_hash, failed_attempts, device_name, client_version, protocol_version, status, expires_at, created_at)
      VALUES (?, ?, ?, 0, ?, ?, ?, 'PENDING', ?, ?)`)
      .bind(id, await sha256(deviceSecret), await hmac(`${id}:${confirmCode}`, env), deviceName, clientVersion, protocolVersion, now + PAIRING_TTL_SECONDS, now).run();
  } catch {
    return json({ error: "pairing_unavailable" }, 503);
  }
  return json({ pairing_id: id, device_secret: deviceSecret, confirm_code: confirmCode, qr_url: `${originOf(request)}/p/${id}`, expires_in: PAIRING_TTL_SECONDS, poll_interval_seconds: 3 }, 201);
}

async function showPairingForm(request: Request, env: PairingEnv, id: string): Promise<Response> {
  const pairing = await readPairing(env, id);
  if (!pairing) return html(page("Pareamento indisponível", "Este código não existe ou já foi removido."), 404);
  await expirePairing(env, pairing);
  if (isExpired(pairing)) return html(page("Código expirado", "Gere um novo código na TV para continuar."), 410);
  if (pairing.status !== "PENDING") return html(page("Pareamento em andamento", "Este código já foi utilizado ou não está mais disponível."), 409);
  const csrf = base64Url(randomBytes(32));
  const csrfProof = await hmac(`csrf:${id}:${csrf}`, env);
  const origin = originOf(request);
  const formAction = `${origin}/p/${encodeURIComponent(id)}/confirm`;
  const form = `<form method="post" action="${formAction}"><label for="code">Código exibido na TV</label><input id="code" name="code" autocomplete="one-time-code" inputmode="numeric" maxlength="6" required><input type="hidden" name="csrf" value="${csrfProof}"><button type="submit">Continuar com Google</button></form>`;
  const csrfCookiePath = `/p/${id}`;
  // Chromium applies form-action to every hop of the redirect chain after the POST
  // (/confirm -> 303 /oauth/start -> 302 accounts.google.com), so Google must be allowed here.
  const csp = `default-src 'none'; style-src 'unsafe-inline'; form-action 'self' ${origin} https://accounts.google.com; base-uri 'none'; frame-ancestors 'none'`;
  return html(page("Conectar esta TV", `Digite o código exibido em ${pairing.device_name}. Ele expira em cinco minutos.`, form), 200, {
    "set-cookie": secureCookie("makimono_pairing_csrf", csrf, csrfCookiePath),
    "content-security-policy": csp,
  });
}

async function confirmPairing(request: Request, env: PairingEnv, id: string): Promise<Response> {
  // The signed synchronizer token is the CSRF boundary. A strict Origin check
  // is unreliable across mobile browsers and workers.dev host routing; the
  // HttpOnly SameSite cookie plus the HMAC-bound form proof remain mandatory.
  const csrf = cookie(request, "makimono_pairing_csrf");
  const parsedBody = await formBody(request);
  if ("errorStatus" in parsedBody) {
    const message = parsedBody.errorStatus === 413 ? "A solicitação excede o tamanho permitido." : "Atualize a página e envie o formulário novamente.";
    return html(page("Não foi possível confirmar", message), parsedBody.errorStatus);
  }
  const form = parsedBody.form;
  const proof = form.get("csrf") ?? "";
  if (!csrf || !constantTimeHexEqual(proof, await hmac(`csrf:${id}:${csrf}`, env))) {
    // A successful confirmation clears the one-time CSRF cookie. If the browser
    // repeats the POST after following the redirect (for example, Back/refresh),
    // resume OAuth only when its persistent browser cookie is already bound to
    // this confirmed pairing. All other CSRF failures remain rejected.
    const browserSecret = cookie(request, "makimono_pairing_browser");
    if (browserSecret) {
      const confirmedPairing = await readPairing(env, id);
      if (
        confirmedPairing?.status === "CONFIRMED" &&
        confirmedPairing.browser_cookie_hash &&
        constantTimeHexEqual(await sha256(browserSecret), confirmedPairing.browser_cookie_hash)
      ) {
        return new Response(null, {
          status: 303,
          headers: {
            location: `/oauth/start?p=${encodeURIComponent(id)}`,
            "cache-control": "no-store",
            "referrer-policy": "no-referrer",
          },
        });
      }
    }
    return html(page("Confirmação inválida", "Atualize a página e tente novamente."), 403);
  }
  const pairing = await readPairing(env, id);
  if (!pairing) return html(page("Pareamento indisponível", "Este código não existe ou já foi removido."), 404);
  await expirePairing(env, pairing);
  if (isExpired(pairing)) return html(page("Código expirado", "Gere um novo código na TV para continuar."), 410);
  if (pairing.status !== "PENDING") return html(page("Pareamento indisponível", "Este código já foi utilizado."), 409);
  if (!(await rateLimit(request, env, `pair-confirm:${id}`, 8, 300))) return html(page("Muitas tentativas", "Aguarde alguns minutos e gere outro código na TV."), 429);
  const code = (form.get("code") ?? "").replace(/\s/g, "");
  const codeHash = await hmac(`${id}:${code}`, env);
  if (!constantTimeHexEqual(codeHash, pairing.confirm_code_hash)) {
    const attempts = pairing.failed_attempts + 1;
    const nextStatus = attempts >= 5 ? "DENIED" : "PENDING";
    await env.DB.prepare("UPDATE pairings SET failed_attempts = ?, status = ? WHERE pairing_id = ? AND status = 'PENDING'").bind(attempts, nextStatus, id).run();
    return html(page("Código incorreto", attempts >= 5 ? "O limite de tentativas foi atingido. Gere um novo QR na TV." : "Confira o código na TV e tente novamente."), 400);
  }
  const browserSecret = base64Url(randomBytes(32));
  const browserHash = await sha256(browserSecret);
  const confirmedAt = Math.floor(Date.now() / 1000);
  const updated = await env.DB.prepare("UPDATE pairings SET status = 'CONFIRMED', browser_cookie_hash = ?, expires_at = ? WHERE pairing_id = ? AND status = 'PENDING' AND expires_at > ?")
    .bind(browserHash, confirmedAt + OAUTH_TTL_SECONDS, id, confirmedAt).run();
  if (!updated.meta.changes) return html(page("Pareamento indisponível", "Este código já foi confirmado em outra sessão."), 409);
  const headers = new Headers({
    location: `/oauth/start?p=${encodeURIComponent(id)}`,
    "cache-control": "no-store",
    "referrer-policy": "no-referrer",
  });
  headers.append("set-cookie", secureCookie("makimono_pairing_browser", browserSecret));
  headers.append("set-cookie", clearCookie("makimono_pairing_csrf", `/p/${id}`));
  return new Response(null, {
    status: 303,
    headers,
  });
}

async function startOAuth(request: Request, env: PairingEnv, id: string): Promise<Response> {
  const pairing = await readPairing(env, id);
  if (!pairing) return html(page("Pareamento indisponível", "Volte à TV e gere um novo QR."), 404);
  await expirePairing(env, pairing);
  if (isExpired(pairing)) return html(page("Código expirado", "Gere um novo código na TV."), 410);
  const browserSecret = cookie(request, "makimono_pairing_browser");
  if (pairing.status !== "CONFIRMED" || !browserSecret || !pairing.browser_cookie_hash || !constantTimeHexEqual(await sha256(browserSecret), pairing.browser_cookie_hash)) {
    return html(page("Confirme primeiro", "Use o código mostrado na TV e continue neste mesmo navegador."), 403);
  }
  const origin = originOf(request);
  const state = base64Url(randomBytes(32));
  const stateHash = await sha256(state);
  const nonce = base64Url(randomBytes(32));
  const nonceHash = await sha256(nonce);
  const verifier = base64Url(randomBytes(32));
  const challenge = base64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", bufferSource(utf8.encode(verifier)))));
  const redirectUri = `${origin}/oauth/callback`;
  const now = Math.floor(Date.now() / 1000);
  await env.DB.prepare("INSERT INTO oauth_states (state_hash, pairing_id, browser_cookie_hash, nonce_hash, verifier_cipher, redirect_uri, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
    .bind(stateHash, id, pairing.browser_cookie_hash, nonceHash, await encrypt(verifier, id, env), redirectUri, now + OAUTH_TTL_SECONDS, now).run();
  const google = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  google.search = new URLSearchParams({ client_id: env.GOOGLE_CLIENT_ID, redirect_uri: redirectUri, response_type: "code", scope: OIDC_SCOPES, access_type: "offline", prompt: "consent", include_granted_scopes: "true", state, nonce, code_challenge: challenge, code_challenge_method: "S256" }).toString();
  return new Response(null, { status: 302, headers: { location: google.toString(), "cache-control": "no-store", "referrer-policy": "no-referrer" } });
}

async function verifyGoogleIdToken(token: string, env: PairingEnv, expectedNonceHash: string): Promise<{ sub: string; email: string }> {
  const [headerPart, claimsPart, signaturePart] = token.split(".");
  if (!headerPart || !claimsPart || !signaturePart) throw new Error("invalid_id_token");
  const header = JSON.parse(fromUtf8.decode(decodeBase64Url(headerPart))) as { kid?: string; alg?: string };
  const claims = JSON.parse(fromUtf8.decode(decodeBase64Url(claimsPart))) as { iss?: string; aud?: string | string[]; azp?: string; exp?: number; iat?: number; sub?: string; email?: string; email_verified?: boolean; nonce?: string };
  if (header.alg !== "RS256" || !header.kid) throw new Error("invalid_id_token");
  const certResponse = await fetch(GOOGLE_CERTS_URL, { headers: { accept: "application/json" } });
  if (!certResponse.ok) throw new Error("google_keys_unavailable");
  const certs = await certResponse.json() as { keys: Array<JsonWebKey & { kid?: string; alg?: string; use?: string }> };
  const jwk = certs.keys.find((key) => key.kid === header.kid && (!key.alg || key.alg === "RS256") && (!key.use || key.use === "sig"));
  if (!jwk) throw new Error("unknown_id_token_key");
  const publicKey = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const signedData = utf8.encode(`${headerPart}.${claimsPart}`);
  if (!(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, bufferSource(decodeBase64Url(signaturePart)), bufferSource(signedData)))) throw new Error("invalid_id_token_signature");
  const now = Math.floor(Date.now() / 1000);
  const audienceValid = Array.isArray(claims.aud) ? claims.aud.includes(env.GOOGLE_CLIENT_ID) : claims.aud === env.GOOGLE_CLIENT_ID;
  const authorizedPartyValid = !Array.isArray(claims.aud) || claims.aud.length <= 1 || claims.azp === env.GOOGLE_CLIENT_ID;
  if (!audienceValid || !authorizedPartyValid || !claims.nonce || !constantTimeHexEqual(await sha256(claims.nonce), expectedNonceHash) || !["accounts.google.com", "https://accounts.google.com"].includes(claims.iss ?? "") || !claims.sub || !claims.email || claims.email_verified !== true || !claims.exp || claims.exp <= now || !claims.iat || claims.iat > now + 60) throw new Error("invalid_id_token_claims");
  return { sub: claims.sub, email: claims.email };
}

async function oauthCallback(request: Request, env: PairingEnv): Promise<Response> {
  const url = new URL(request.url);
  const state = url.searchParams.get("state") ?? "";
  const stateHash = await sha256(state);
  const pendingState = await env.DB.prepare("SELECT * FROM oauth_states WHERE state_hash = ? AND consumed_at IS NULL AND expires_at > ?")
    .bind(stateHash, Math.floor(Date.now() / 1000)).first<{
      state_hash: string; pairing_id: string; browser_cookie_hash: string; nonce_hash: string; verifier_cipher: string; redirect_uri: string; expires_at: number;
    }>();
  if (!pendingState) return html(page("Sessão expirada", "Volte à TV e gere um novo QR."), 410, { "set-cookie": clearCookie("makimono_pairing_browser") });
  const initialBrowserSecret = cookie(request, "makimono_pairing_browser");
  if (!initialBrowserSecret || !constantTimeHexEqual(await sha256(initialBrowserSecret), pendingState.browser_cookie_hash)) {
    return html(page("Navegador não reconhecido", "Retome o pareamento no mesmo navegador em que digitou o código."), 403);
  }
  const oauthState = await env.DB.prepare("UPDATE oauth_states SET consumed_at = ? WHERE state_hash = ? AND consumed_at IS NULL AND expires_at > ? RETURNING *")
    .bind(Math.floor(Date.now() / 1000), stateHash, Math.floor(Date.now() / 1000)).first<{
    state_hash: string; pairing_id: string; browser_cookie_hash: string; nonce_hash: string; verifier_cipher: string; redirect_uri: string; expires_at: number;
  }>();
  if (!oauthState || oauthState.expires_at <= Math.floor(Date.now() / 1000)) return html(page("Sessão expirada", "Volte à TV e gere um novo QR."), 410, { "set-cookie": clearCookie("makimono_pairing_browser") });
  const pairing = await readPairing(env, oauthState.pairing_id);
  const browserSecret = cookie(request, "makimono_pairing_browser");
  if (!pairing || pairing.status !== "CONFIRMED" || !browserSecret || !pairing.browser_cookie_hash || !constantTimeHexEqual(await sha256(browserSecret), oauthState.browser_cookie_hash) || !constantTimeHexEqual(pairing.browser_cookie_hash, oauthState.browser_cookie_hash)) {
    return html(page("Navegador não reconhecido", "Retome o pareamento no mesmo navegador em que digitou o código."), 403);
  }
  const oauthError = url.searchParams.get("error");
  if (oauthError === "access_denied") {
    await env.DB.prepare("UPDATE pairings SET status = 'DENIED' WHERE pairing_id = ? AND status = 'CONFIRMED'").bind(pairing.pairing_id).run();
    return html(page("Autorização cancelada", "A conta não foi conectada. Você pode fechar esta página."), 200, { "set-cookie": clearCookie("makimono_pairing_browser") });
  }
  const code = url.searchParams.get("code");
  if (!code || oauthError) return html(page("Não foi possível autorizar", "Volte à TV e tente gerar um novo QR."), 400, { "set-cookie": clearCookie("makimono_pairing_browser") });
  try {
    const verifier = await decrypt(oauthState.verifier_cipher, pairing.pairing_id, env);
    const tokenResponse = await fetch(GOOGLE_TOKEN_URL, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
      body: new URLSearchParams({ code, client_id: env.GOOGLE_CLIENT_ID, client_secret: env.GOOGLE_CLIENT_SECRET, redirect_uri: oauthState.redirect_uri, grant_type: "authorization_code", code_verifier: verifier }),
    });
    const token = await tokenResponse.json() as { access_token?: string; refresh_token?: string; id_token?: string; scope?: string; error?: string };
    if (!tokenResponse.ok || !token.access_token || !token.id_token) throw new Error("google_token_exchange_failed");
    const scopes = new Set((token.scope ?? "").split(/\s+/).filter(Boolean));
    if (!scopes.has(DRIVE_SCOPE)) throw new Error("drive_scope_missing");
    const identity = await verifyGoogleIdToken(token.id_token, env, oauthState.nonce_hash);
    const previous = await env.DB.prepare("SELECT refresh_token_cipher FROM accounts WHERE google_sub = ?").bind(identity.sub).first<{ refresh_token_cipher: string | null }>();
    if (!token.refresh_token && !previous?.refresh_token_cipher) throw new Error("refresh_consent_missing");
    const refreshCipher = token.refresh_token ? await encrypt(token.refresh_token, identity.sub, env) : previous!.refresh_token_cipher!;
    const now = Math.floor(Date.now() / 1000);
    await env.DB.batch([
      env.DB.prepare("UPDATE pairings SET status = 'APPROVED', google_sub = ? WHERE pairing_id = ? AND status = 'CONFIRMED'").bind(identity.sub, pairing.pairing_id),
      env.DB.prepare(`INSERT INTO accounts (google_sub, email, refresh_token_cipher, created_at, updated_at)
        SELECT ?, ?, ?, ?, ? WHERE EXISTS (
          SELECT 1 FROM pairings WHERE pairing_id = ? AND status = 'APPROVED' AND google_sub = ?
        ) ON CONFLICT(google_sub) DO UPDATE SET email = excluded.email, refresh_token_cipher = excluded.refresh_token_cipher, updated_at = excluded.updated_at`)
        .bind(identity.sub, identity.email, refreshCipher, now, now, pairing.pairing_id, identity.sub),
    ]);
    const approved = await env.DB.prepare("SELECT 1 AS approved FROM pairings WHERE pairing_id = ? AND status = 'APPROVED' AND google_sub = ?")
      .bind(pairing.pairing_id, identity.sub).first<{ approved: number }>();
    if (!approved) return html(page("Pareamento cancelado", "A TV cancelou este QR antes de concluir a autorização."), 409, { "set-cookie": clearCookie("makimono_pairing_browser") });
    return html(page("TV conectada", "A autorização foi concluída. Volte à TV; ela receberá uma sessão privada para este aparelho.", "", "success"), 200, { "set-cookie": clearCookie("makimono_pairing_browser") });
  } catch (error) {
    const reason = error instanceof Error ? error.message : "oauth_failed";
    await env.DB.prepare("UPDATE pairings SET status = 'DENIED' WHERE pairing_id = ? AND status = 'CONFIRMED'")
      .bind(pairing.pairing_id).run();
    const message = reason === "drive_scope_missing" ? "A permissão de leitura do Google Drive não foi concedida." : reason === "refresh_consent_missing" ? "O Google não emitiu uma autorização persistente. Revise o consentimento e tente novamente." : "O Google não concluiu a autorização. Gere um novo QR na TV e tente novamente.";
    return html(page("Autorização não concluída", message), 400, { "set-cookie": clearCookie("makimono_pairing_browser") });
  }
}

async function pairingStatus(request: Request, env: PairingEnv, id: string): Promise<Response> {
  const pairing = await readPairing(env, id);
  if (!pairing) return json({ error: "pairing_not_found" }, 404);
  const suppliedSecret = request.headers.get("x-device-secret") ?? "";
  if (!constantTimeHexEqual(await sha256(suppliedSecret), pairing.device_secret_hash)) return json({ error: "device_secret_invalid" }, 401);
  await expirePairing(env, pairing);
  if (pairing.status === "APPROVED" && pairing.google_sub) {
    const sessionToken = base64Url(randomBytes(SESSION_TOKEN_BYTES));
    const now = Math.floor(Date.now() / 1000);
    try {
      await env.DB.batch([
        env.DB.prepare("UPDATE pairings SET status = 'CONSUMED' WHERE pairing_id = ? AND status = 'APPROVED'").bind(id),
        env.DB.prepare("INSERT INTO sessions (session_hash, pairing_id, google_sub, created_at, revoked_at) VALUES (?, ?, ?, ?, NULL)").bind(await sha256(sessionToken), id, pairing.google_sub, now),
      ]);
      return json({ state: "approved", session_token: sessionToken, expires_in: 0 });
    } catch {
      const current = await readPairing(env, id);
      if (current?.status === "CONSUMED") return json({ state: "consumed" });
      return json({ error: "pairing_claim_failed" }, 503);
    }
  }
  const current = await readPairing(env, id);
  if (!current) return json({ error: "pairing_not_found" }, 404);
  const states: Record<PairingStatus, string> = { PENDING: "pending", CONFIRMED: "confirmed", APPROVED: "approved", CONSUMED: "consumed", DENIED: "denied", CANCELLED: "cancelled", EXPIRED: "expired" };
  return json({ state: states[current.status], expires_in: Math.max(0, current.expires_at - Math.floor(Date.now() / 1000)) });
}

async function bearerSession(request: Request, env: PairingEnv) {
  const authorization = request.headers.get("authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return null;
  const token = authorization.slice(7).trim();
  if (!token) return null;
  const tokenHash = await sha256(token);
  return env.DB.prepare(`SELECT sessions.session_hash, sessions.pairing_id, sessions.google_sub, accounts.refresh_token_cipher
    FROM sessions JOIN accounts ON accounts.google_sub = sessions.google_sub
    WHERE sessions.session_hash = ? AND sessions.revoked_at IS NULL AND accounts.refresh_token_cipher IS NOT NULL`)
    .bind(tokenHash).first<{ session_hash: string; pairing_id: string; google_sub: string; refresh_token_cipher: string }>();
}

async function driveToken(request: Request, env: PairingEnv): Promise<Response> {
  const session = await bearerSession(request, env);
  if (!session) return json({ error: "session_invalid" }, 401);
  const refreshToken = await decrypt(session.refresh_token_cipher, session.google_sub, env);
  const response = await fetch(GOOGLE_TOKEN_URL, {
    method: "POST", headers: { "content-type": "application/x-www-form-urlencoded", accept: "application/json" },
    body: new URLSearchParams({ client_id: env.GOOGLE_CLIENT_ID, client_secret: env.GOOGLE_CLIENT_SECRET, refresh_token: refreshToken, grant_type: "refresh_token" }),
  });
  const body = await response.json() as { access_token?: string; expires_in?: number; error?: string };
  if (body.error === "invalid_grant") {
    const now = Math.floor(Date.now() / 1000);
    await env.DB.batch([
      env.DB.prepare("UPDATE accounts SET refresh_token_cipher = NULL, updated_at = ? WHERE google_sub = ?").bind(now, session.google_sub),
      env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE google_sub = ? AND revoked_at IS NULL").bind(now, session.google_sub),
    ]);
    return json({ error: "reauth_required" }, 409);
  }
  if (!response.ok || !body.access_token || !body.expires_in) return json({ error: "drive_token_unavailable" }, 502);
  await env.DB.prepare("UPDATE sessions SET last_seen_at = ? WHERE session_hash = ? AND revoked_at IS NULL").bind(Math.floor(Date.now() / 1000), session.session_hash).run();
  return json({ access_token: body.access_token, expires_in: body.expires_in });
}

async function logout(request: Request, env: PairingEnv): Promise<Response> {
  const session = await bearerSession(request, env);
  if (!session) return json({ error: "session_invalid" }, 401);
  await env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE session_hash = ? AND revoked_at IS NULL").bind(Math.floor(Date.now() / 1000), session.session_hash).run();
  return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
}

async function disconnectAll(request: Request, env: PairingEnv): Promise<Response> {
  const session = await bearerSession(request, env);
  if (!session) return json({ error: "session_invalid" }, 401);
  const refreshToken = await decrypt(session.refresh_token_cipher, session.google_sub, env);
  let revokeResponse: Response;
  try {
    revokeResponse = await fetch("https://oauth2.googleapis.com/revoke", {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ token: refreshToken }),
    });
  } catch {
    return json({ error: "google_revoke_unavailable" }, 502);
  }
  if (!revokeResponse.ok) return json({ error: "google_revoke_unavailable" }, 502);
  const now = Math.floor(Date.now() / 1000);
  await env.DB.batch([
    env.DB.prepare("UPDATE accounts SET refresh_token_cipher = NULL, updated_at = ? WHERE google_sub = ?").bind(now, session.google_sub),
    env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE google_sub = ? AND revoked_at IS NULL").bind(now, session.google_sub),
  ]);
  return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
}

async function cancelPairing(request: Request, env: PairingEnv, id: string): Promise<Response> {
  const pairing = await readPairing(env, id);
  if (!pairing) return json({ error: "pairing_not_found" }, 404);
  if (!constantTimeHexEqual(await sha256(request.headers.get("x-device-secret") ?? ""), pairing.device_secret_hash)) return json({ error: "device_secret_invalid" }, 401);
  await env.DB.prepare("UPDATE pairings SET status = 'CANCELLED' WHERE pairing_id = ? AND status IN ('PENDING', 'CONFIRMED', 'APPROVED')").bind(id).run();
  await env.DB.prepare("DELETE FROM oauth_states WHERE pairing_id = ?").bind(id).run();
  return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
}

async function handle(request: Request, env: PairingEnv): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname;
  if (path === "/health") return json({ status: "ok" });
  const authRoute = path.startsWith("/v1/") || path.startsWith("/p/") || path.startsWith("/oauth/");
  if (authRoute && (!env.GOOGLE_CLIENT_ID || !env.GOOGLE_CLIENT_SECRET || !env.HMAC_KEY || env.HMAC_KEY.length < 32 || !env.TOKEN_ENC_KEY)) {
    return json({ error: "service_not_configured" }, 503);
  }
  if (request.method === "POST" && path === "/v1/pairings") return createPairing(request, env);
  if (request.method === "GET" && path === "/oauth/callback") return oauthCallback(request, env);
  const pairPage = path.match(/^\/p\/([A-Za-z0-9_-]+)$/);
  const confirm = path.match(/^\/p\/([A-Za-z0-9_-]+)\/confirm$/);
  const oauthStart = path === "/oauth/start" ? url.searchParams.get("p") : null;
  const status = path.match(/^\/v1\/pairings\/([A-Za-z0-9_-]+)$/);
  const cancel = path.match(/^\/v1\/pairings\/([A-Za-z0-9_-]+)\/cancel$/);
  if (request.method === "GET" && pairPage) return showPairingForm(request, env, pairPage[1]);
  if (request.method === "POST" && confirm) return confirmPairing(request, env, confirm[1]);
  if (request.method === "GET" && oauthStart) return startOAuth(request, env, oauthStart);
  if (request.method === "GET" && status) return pairingStatus(request, env, status[1]);
  if (request.method === "DELETE" && cancel) return cancelPairing(request, env, cancel[1]);
  if (request.method === "GET" && path === "/v1/drive-token") return driveToken(request, env);
  if (request.method === "POST" && path === "/v1/logout") return logout(request, env);
  if (request.method === "POST" && path === "/v1/disconnect-all") return disconnectAll(request, env);
  return json({ error: "not_found" }, 404);
}

export default {
  async fetch(request: Request, env: PairingEnv): Promise<Response> {
    try {
      return await handle(request, env);
    } catch {
      return json({ error: "internal_error" }, 500);
    }
  },
};
