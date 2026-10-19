# Makimono pairing Worker

Cloudflare Worker + D1 backend for authorizing a single Makimono TV through a phone browser. The Worker stores Google refresh tokens encrypted with AES-256-GCM; Android receives only a revocable Makimono session and short-lived Google access tokens. Video bytes continue to come directly from Google Drive.

## Local setup

Use Node.js 22.19+ or 24.11+ (the verified local runtime is Node 24). From this directory:

```powershell
npm ci
npm run d1:migrate:local
npm run dev
```

Local `.dev.vars` is ignored by Git. Set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `HMAC_KEY` (at least 32 random bytes), and `TOKEN_ENC_KEY` (32 random bytes encoded as unpadded base64url). Generate local-only values with a cryptographic random source; never reuse them in production. `TOKEN_ENC_KEY` encrypts grants and PKCE verifiers with AES-256-GCM and row-specific additional authenticated data.

Checks:

```powershell
npm run typecheck
npm test
npm exec wrangler -- deploy --dry-run
```

The test suite uses an isolated local D1 database and generated test keys. It does not call Google or deploy a Worker.

## API and security behavior

- `POST /v1/pairings` creates a five-minute QR/code challenge. The QR URL contains only an opaque pairing ID; the TV polling secret is returned only to the TV.
- `GET /p/:id` and `POST /p/:id/confirm` verify the six-digit code. The form uses an HttpOnly, Secure, SameSite=Lax CSRF cookie and an origin check. On success the Worker creates a separate HttpOnly, Secure, SameSite=Lax browser-binding cookie.
- `/oauth/start` and `/oauth/callback` require that same browser cookie. OAuth uses one-time state, OIDC nonce, PKCE S256, offline consent, an exact stored callback URI, and server-side code exchange. The callback validates the Google ID-token signature and claims before saving a grant.
- `GET /v1/pairings/:id` requires `X-Device-Secret`. It returns a Makimono session token once. After that, polling reports `consumed` without a token; if the TV missed the one-time response, it must show the recoverable message and create a new QR.
- `GET /v1/drive-token` exchanges the server-held grant for a short-lived Google access token. The TV uses that token directly with Drive; the Worker does not proxy media.
- `POST /v1/logout` revokes only the calling TV session. `POST /v1/disconnect-all` is the separate, explicit account-wide action that revokes the Google grant and all Makimono sessions.

Pairing creation and confirmation attempts are rate-limited in D1. The Worker does not log authorization codes, cookies, bearer tokens, refresh tokens, or request bodies. Responses containing authentication state are `no-store`; verification pages have a restrictive CSP and `no-referrer` policy.

## Cloudflare and Google setup (not performed by tests)

Create a D1 database named `makimono-pairing`, set its real ID in `wrangler.jsonc`, apply the reviewed migrations, and configure the four Worker secrets with Wrangler. `wrangler.jsonc` intentionally has a placeholder D1 ID until the owner selects the production database. No remote database, secret, OAuth client, or Worker is modified by local tests or dry-run.

After the first Worker deploy, use its HTTPS `workers.dev` origin to configure the Android `PAIRING_API_BASE_URL` Gradle property/environment variable and register this exact Google OAuth redirect URI:

```text
https://<worker-host>/oauth/callback
```

The production API base URL must end with `/`. Register the redirect URI exactly as shown; the callback derives its origin from the HTTPS request URL. Google OAuth consent configuration must include `openid`, `email`, and `https://www.googleapis.com/auth/drive.readonly`. The Drive scope is restricted; public release may require Google verification and a security assessment. A successful local mock test does not prove the live Google OAuth configuration works.

Never run `wrangler deploy`, `wrangler d1 migrations apply --remote`, or `wrangler secret put` without the owner's explicit request for the corresponding remote change.
