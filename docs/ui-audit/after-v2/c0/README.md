# C0.1 — Home visual-review fixture

These files document a debug-only fixture run on MuMu x86_64 at 1920×1080 on 2026-10-04.

- `home-before.png` / `home-before.xml`: fixture before the binder correction.
- `home-after.png` / `home-after.xml`: fresh fixture after install.
- `home-focus-after.png` / `home-focus-after.xml`: focused primary action after D-pad navigation.
- `home-focus-after.xml` reports focus on `btnFeaturedPlay`; `home-return-after.xml` captures the preceding return from the shelf to that CTA.

The fixture now labels shelf cards with series title, episode badge, episode number, and remaining time. It uses synthetic data and must not be interpreted as a production Home, Drive-session, account, or playback test.

`assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild --max-workers=1` succeeded (171 tests; 0 failures, errors, or skips). On this host, temporary ASCII drive aliases for the checkout and existing Gradle cache plus `C:\temp` for temporary files avoided Windows path/loopback issues. The x86_64 APK was installed only as `zechs.drive.stream.visualreview`; the main package and its data were left installed and untouched.
