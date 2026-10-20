# Makimono pairing API — local architecture proof

This directory is an isolated Kotlin/Ktor sub-build. It does not change the Android Gradle build. Run it with the repository wrapper:

```powershell
./gradlew.bat -p backend test
./gradlew.bat -p backend run
```

By default the development server binds only to `127.0.0.1`, uses an in-memory, single-process store, and advertises a deliberately non-routable `.invalid` URL. For PostgreSQL persistence testing, set `MAKIMONO_DATABASE_URL`, `MAKIMONO_DATABASE_USER`, and `MAKIMONO_DATABASE_PASSWORD` together before `run`; Flyway applies the versioned migration at startup and the PostgreSQL store uses row locks for authenticated state transitions. The database user should have only the permissions required for the service and its migrations.

The PostgreSQL implementation is still a local architecture slice, not a deployable service. It implements challenge creation, status polling, cancellation, expiry, and persisted metadata/hash. `GET /pair/{challengeId}` serves a self-contained, responsive HTML preview for the QR locator; it escapes the device name, applies a restrictive CSP/referrer/no-store policy, and never approves the challenge. The page accepts manual-code lookup through a bounded POST form at `/pair/lookup`. The JSON endpoint `POST /v1/pairing/lookup` remains available to clients and returns only the active TV's challenge locator, display name, and expiry; it normalizes spaces/hyphens/case, returns a generic not-found result for unknown or inactive codes, and never approves a challenge or returns its poll secret. The HTTP routes cap challenge-creation JSON at 8 KiB and lookup JSON at 1 KiB (including requests without a declared content length) and have a bounded per-process rate limiter (create: 5/minute, status: 20/minute, cancel: 10/minute, code lookup: 5/minute, QR preview: 20/minute per socket peer) with `413`/`429`, typed errors, `Retry-After`, and `no-store`; these are local abuse guards, neither shared across instances nor proxy-aware. It does not implement distributed rate limits, the authenticated mobile approval page, real Google OAuth, encrypted grants, revocable Makimono sessions, claim/refresh, nor Drive/media APIs. The verification URL remains `.invalid`; do not expose it to the internet or point the Android app at it.

## Free-first hosting direction: Cloudflare Workers + D1

Because the owner requires a strict no-charge option, `cloudflare-worker/` is the current deployment direction. It ports the pairing challenge endpoints to a TypeScript Worker backed by D1, whose free plan has explicit limits that fail closed instead of billing overages. This costs more implementation work than deploying the existing Kotlin/Ktor container to Cloud Run, but avoids attaching a paid Google Cloud billing account and a separately billed PostgreSQL server. See `cloudflare-worker/README.md` for setup and limitations.

The Kotlin/Ktor backend remains a local architecture prototype. Its container files are an optional path if a paid-billing provider is chosen later. The Cloudflare Worker is also only a challenge API prototype; **do not publish either preview as a functioning login service**. Google OAuth, mobile approval, encrypted grant storage, revocable Makimono sessions, production abuse controls, and Drive proxy endpoints remain unimplemented.

Cloudflare's `workers.dev` URL can be used for initial technical testing without registering a custom domain. Public Google OAuth verification may require a domain the owner controls and can verify. Do not place OAuth client secrets in Wrangler variables or repository files; use Worker secrets after the OAuth callback exists.

Before network deployment, complete the OAuth/mobile-approval flow, token encryption and revocation, TV session issuance, Drive gateway, distributed abuse controls, retention/cleanup policy, operational metrics, backup/restore strategy, and production OAuth/domain review. No credentials belong in this repository or chat.
