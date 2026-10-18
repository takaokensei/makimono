---
name: android-tv-emulator
description: Start and verify the local Android TV AVD on this Windows development machine, including its D-pad and host keyboard setup. Use when the user asks to open, restart, or prepare the Makimono Android TV emulator; do not use for physical Android devices or app UI testing.
---

# Android TV Emulator

Use the bundled `scripts/open-android-tv.ps1` to start or verify the local TV AVD. It uses the installed Android Emulator and ADB, preserves the existing AVD, enables the host keyboard and D-pad in the AVD config while it is shut down, and waits for the Android boot-complete property. It does not install or launch an app.

Default setup on this machine:

- SDK: `C:\Android\Sdk`
- AVD: `MakimonoTVAscii`
- AVD home: `C:\Temp\MakimonoAVD`
- Graphics backend: `swangle` (the backend that booted this AVD successfully here)

Run from PowerShell:

```powershell
& "$env:USERPROFILE\.codex\skills\android-tv-emulator\scripts\open-android-tv.ps1"
```

For another SDK or AVD, pass `-SdkRoot`, `-AvdHome`, and `-AvdName`. The script does not modify a running emulator; if its keyboard configuration needs changing, close that AVD and rerun the script. It creates a timestamped backup before changing `config.ini`. Never interact with Google sign-in fields or handle credentials; leave authentication to the user.

After it starts, use the returned emulator serial explicitly for ADB (for example, `adb -s emulator-5554 ...`) and avoid targeting a connected physical phone.
