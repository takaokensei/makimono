# Home shelf ordering during refresh — 2026-10-07

## Change

When the user is focused inside the Home catalog or Recently Added shelf, an asynchronous data refresh now keeps the relative order of items already present. Their latest metadata is still applied, entries removed from the refreshed result disappear, and newly arrived entries are appended. Once focus leaves the shelf, normal Drive/catalog ordering applies again. Explicit A–Z/Z–A catalog sorting remains unchanged.

## Verification

- Added `HomeShelfOrderPolicyTest` cases for stable order with refreshed item content and appended new items, and for removing entries absent from the refreshed result.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: **300 tests / 67 suites, 0 failures, 0 errors, 0 skipped; BUILD SUCCESSFUL**.
- Added a debug-only `home_refresh_order` fixture using the production `StarredShelfAdapter` and the shared policy. On a Moto G34 (Android 15, 720×1600 physical, 280 dpi; app in landscape), RIGHT focused Jujutsu Kaisen, then a delayed synthetic snapshot arrived in reverse order. The result remained `Sousou no Frieren atualizado → Jujutsu Kaisen atualizado → Chainsaw Man atualizado → Bakemono...`; Jujutsu Kaisen remained focused with its new title. Screenshot and UI hierarchy: `focused-refresh.png` and `focused-refresh.xml`.
- Installed the debug APK alongside production as `zechs.drive.stream.visualreview`; version `1.6.4.3`, arm64 SHA-256 `E3A7C07013B30810F3D8F0F7C48F1A671C2C9D9185D62D3EC466E0C2DF4310D3`.
- `git diff --check` passed for the implementation and test files.

## Limits

The runtime fixture verifies the shared ordering helper and `ListAdapter` focus continuity, but it does not drive `HomeFragment`'s real Drive/Flow collectors. No authenticated Drive listing or persisted user data was used for verification. TV hardware, TalkBack, and production-network refresh remain pending. The debug APK uses the local debug signing key and is not a release artifact.
