# Typography token cleanup — 2026-10-07

- Replaced the remaining 14 literal `android:textSize` values across player, dialog, season tab, theme preview, profile landscape, and portrait Home layouts with the existing Makimono typography tokens.
- Preserved existing weights, colors, and text-specific font choices; TV token variants keep their distance-readable scale.
- `python tools/check_typography.py --max 0`: passed (0 literal sizes; 421 token references).
- `testDebugUnitTest assembleDebug`: passed. The JVM test task was already up-to-date; its latest full run recorded 288 tests across 63 suites with no failures. The latest resource changes were compiled by `assembleDebug`.
- Installed only `zechs.drive.stream.visualreview` on Moto G34 Android 15 and smoke-checked the circular-profile selection screen. Screenshot and UI hierarchy are in this directory.
- Limits: this device smoke check did not exercise the affected TV-only layouts or every dialog/player state; no TV hardware, Google Drive account, or production package was used.

### Profile editing smoke test

Opened the profile edit dialog in the isolated review build, scrolled to the circular avatar gallery, selected an image in the unsaved form, and cancelled. The profile picker remained open and no edit was saved. Evidence: profile-edit-dialog.png, profile-avatar-gallery.png, profile-edit-cancelled.png, and profile-selection-after-cancel.xml. D-pad moved through the fields into the image gallery, RIGHT advanced across avatar choices, the focus ring remained visible, and BACK dismissed the dialog. The wallpaper tab, a real TV remote, and large-text layouts remain unverified.


### Home hero visual smoke

Opened the production Home XML in the explicitly watermarked .visualreview fixture on Moto G34 Android 15. Backdrop stayed within the hero; title, metadata, synopsis, and both CTAs rendered. Evidence: home-hero-smoke.png/xml. This fixture assigns static sample content and therefore does not validate the real focus-triggered debounce, network metadata replacement, or crossfade. At this handset's short landscape viewport, the next shelf is below the fold and requires scrolling; no change was made because this is not the plan's 960×540dp TV acceptance viewport. TV hardware/1080p and Drive-backed dynamic preview remain pending.
