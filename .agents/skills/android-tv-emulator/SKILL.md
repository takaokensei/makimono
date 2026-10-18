---
name: android-tv-emulator
description: Start and verify the local Android TV AVD on this Windows development machine, including its D-pad and host keyboard setup. Use when the user asks to open, restart, or prepare the Makimono Android TV emulator; do not use for physical Android devices or app UI testing.
---

# Android TV Emulator

Use the bundled `scripts/open-android-tv.ps1` to start or verify the local TV AVD in a single command. It guarantees that the emulator window appears directly on the user's interactive desktop (`WinSta0\Default`), enables the physical host keyboard and D-pad, cleans stale lock files, preserves the AVD state, waits for the boot-complete signal, and brings Makimono to the foreground.

If the emulator is already running and booted, the script detects it immediately and returns in 1–2 seconds.

Default setup on this machine:

- SDK: `C:\Android\Sdk`
- AVD: `MakimonoTVAscii`
- AVD home: `C:\Temp\MakimonoAVD`
- Graphics backend: `swangle`
- Interactive Desktop: `WinSta0\Default` (via Task Scheduler `/it` launcher to prevent sandbox window hiding)

Run from PowerShell:

```powershell
& ".\.agents\skills\android-tv-emulator\scripts\open-android-tv.ps1"
```

or via user profile path:

```powershell
& "$env:USERPROFILE\.codex\skills\android-tv-emulator\scripts\open-android-tv.ps1"
```

Key features handled automatically by the script:
1. **Interactive Screen Window:** Always launches on the user's visible session (`WinSta0\Default`) so the window never starts hidden or headless.
2. **Process Detachment:** Detaches emulator from any ephemeral agent job object so the window stays open indefinitely.
3. **Lock Cleanup:** Removes stale `multiinstance.lock` and `hardware-qemu.ini.lock` if a previous session crashed.
4. **AVD Pointer Mirroring:** Ensures `MakimonoTVAscii.ini` is synced to both `C:\Temp\MakimonoAVD` and `$env:USERPROFILE\.android\avd\`.
5. **D-pad & Keyboard:** Enforces `hw.keyboard=yes` and `hw.dPad=yes` in `config.ini`.
6. **Fast Reuse:** Detects an already booted instance and exits immediately with serial confirmation.
7. **Auto-Launch:** Automatically focuses or launches Makimono `MainActivity` (`zechs.drive.stream.visualreview`) upon boot.
