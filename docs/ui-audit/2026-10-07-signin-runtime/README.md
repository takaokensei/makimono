# Sign-in runtime check — 2026-10-07

## Scope and setup

- Device: Moto G34 5G, Android 15. TV-like viewport simulated at 1920×1080 px / 320 dpi (960×540 dp); original handset size 720×1600 px / 280 dpi was restored after each run.
- Installed only `zechs.drive.stream.visualreview`, build `1.6.4.3`; no production package was replaced.
- Opened `VisualReviewActivity` with its explicit `signin` fixture. No OAuth action, authorization code, Google account or Drive data was used.

## Changes

- The sign-in scroll content used `layout_gravity="center"`. When its content exceeded the viewport, vertical centering clipped the logo and title. It now uses `center_horizontal`, keeping the start of the flow visible.
- Focus on each sign-in action explicitly scrolls the focused control into the NestedScrollView safe area, so the last action is not covered by the bottom edge.
- OAuth URL is no longer written to Logcat. Missing configuration and browser are handled with localized messages. Sign-in and help share a guarded external-URI launcher. Raw auth/HTTP error text is replaced with a generic localized message; missing code has its own localized message.
- The TV path requests initial focus on the primary Drive action. Advanced connection settings expand/collapse and update their accessible label.

## Runtime observations

- At the 1080p-equivalent viewport, logo, title, description and primary action are visible together: `signin-final-1080.png`.
- D-pad DOWN focused “Conectar ao Google Drive” with a cyan ring: `signin-dpad-1080.png` and `signin-dpad-1080.xml`.
- Four further DOWN presses reached “Configurações avançadas”; its full focus bounds were `[677,930][1243,1026]`, inside the safe viewport. Evidence: `signin-advanced-final.png` and `signin-advanced-final.xml`.
- At the handset landscape viewport, the logo/title remain visible and the CTA receives focus; lower actions are reachable by scrolling. Evidence: `signin-final-handset.png` and `signin-final-handset.xml`.
- The initial-focus branch keyed by the Android TV/Leanback device feature was not directly exercised on physical TV hardware. The D-pad and 960×540 dp viewport were exercised on the Moto fixture.

## Build and tests

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: successful; 288 tests in 63 suites, 0 failures, 0 errors, 0 skipped.
- APK installed only to `.visualreview`; SHA-256 arm64: `84C044CCCEDF8758ADC8332E3EE5EFE612200AAF819EC646F0D4A62E4450D2A3`.
- No release was generated or published. `git diff --check` passed (only existing CRLF conversion warnings).

## Not validated

- OAuth browser round-trip, authorization-code submission, real network errors, Google/Drive account, physical Android TV, TalkBack or the integrated QR pairing flow. Pairing still depends on mobile approval/OAuth/session backend work; no QR code was presented as functional.

