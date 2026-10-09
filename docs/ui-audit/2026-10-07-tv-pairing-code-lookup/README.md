# TV pairing: QR preview and manual code

## What changed

The loopback-only pairing prototype now serves `GET /pair/{challengeId}` as a self-contained mobile page for the QR locator. It shows the active TV name and expiry, and includes a manual-code form posting to `/pair/lookup`. The page clearly says that Google authentication is unavailable in this development environment; it has no approval action and does not imply that scanning a QR connects the account.

The HTML is generated without external scripts, fonts, images, analytics, or JavaScript. Device text is HTML-escaped. Responses use `no-store`, a restrictive Content Security Policy, `no-referrer`, `nosniff`, and `X-Frame-Options: DENY`. The manual browser form accepts only a bounded, single `userCode` field, normalizes the code through the pairing protocol, and shows the same generic unavailable state for invalid/inactive codes. JSON clients may still use `POST /v1/pairing/lookup`.

## Verification

- `cmd /c "cd /d X:\ & set TEMP=C:\Temp& set TMP=C:\Temp& set GRADLE_USER_HOME=C:\Temp\.gradle& gradlew.bat -p X:\backend test --no-daemon --max-workers=1"`: passed, **25 tests**, no failures/errors/skips. The PostgreSQL Testcontainers class ran 5 tests with 0 skips.
- Tests cover active QR preview, XSS escaping, no poll secret, pending state unchanged, form lookup/normalization, indistinguishable unavailable responses, body bounds, and PostgreSQL locator lookup after cancellation.
- Root Android verification from this work session remains valid: `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1` passed **309 tests** and built Debug APK arm64 SHA-256 `F4405D9D42D11E2ECDEBFACB538A4070AC41A597B96F8B42DB0BE8ADA36363B0`.
- No APK was installed because the Moto G34 already had a QA MPV session in the foreground. No real Google/Drive data was read or changed.
- Rendered the local page in the isolated Codex browser at the default desktop viewport and at 390×844. Both showed the device/expiry preview, OAuth-unavailable notice, and usable manual-code form without horizontal overflow. The form itself was not submitted; route tests cover its POST behavior.

## Not implemented or accepted

The page is a preview only. Authenticated mobile approval, Google OAuth, browser state/CSRF, owner authorization, grant storage, approve/deny, claim, refresh, Android pairing UI, and public HTTPS deployment remain absent. The verification base URL remains `.invalid`, and the service binds only to loopback; do not expose it or configure the Android app against it.
