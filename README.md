<p align="center">
  <img src="docs/logo.png" align="center" width="160" alt="Makimono Emblem" />
</p>
<h1 align="center">
  Makimono (巻物)
</h1>
<p align="center">
  <b>Sacred Anime &amp; Media Archive Client for Android &amp; Android TV</b><br>
  High-performance direct cloud streaming from Google Drive with native fansub subtitle styling, encrypted credentials, and fail-closed verified updates.
</p>

<p align="center">
  Powered by <a href="https://developers.google.com/drive/api">Google Drive API</a>, <a href="https://github.com/google/ExoPlayer">ExoPlayer</a> + FFmpeg Extension, and <a href="https://github.com/mpv-android">mpv-android</a>.
</p>

<div align="center">
  <a href="https://github.com/takaokensei/makimono/releases">
    <img alt="GitHub release" src="https://img.shields.io/github/v/release/takaokensei/makimono?style=for-the-badge">
  </a>
  <a href="https://github.com/takaokensei/makimono/blob/main/LICENSE">
    <img alt="License" src="https://img.shields.io/github/license/takaokensei/makimono?style=for-the-badge">
  </a>
</div>

<br>

## ✨ Highlights

- **Dual Playback Engines**: Common `PlayerEngine` contract and `PlaybackCoordinator` orchestrating both ExoPlayer (with FFmpeg decoders) and MPV.
- **Native Fansub Subtitle Rendering**: Preserves custom SSA/ASS font styles, colors, and dialogue formatting while automatically normalizing vertical placement right above the bezel across TV and mobile displays.
- **Hardened Security & Keystore Encryption**: Drive session tokens and credentials encrypted via Android Keystore (AES-256-GCM); zero raw tokens in logs with `RedactingLoggingInterceptor`.
- **Fail-Closed Secure In-App Updater**: Automatic detection of GitHub releases with mandatory SHA-256 checksum verification and cryptographic signing certificate fingerprint matching before installation.
- **Multi-Profile System**: Isolated watchlists, watch queues, play progress, and preferences per user profile.
- **Global Search & Offline-First Catalog**: Debounced global search across Drive files and local Room catalog caching for instant startup and offline browsing.
- **Background Playback & PiP**: Full `MediaSession` integration for remote controls/lockscreen and Picture-in-Picture (Android 8.0+) with dynamic aspect ratio.
- **Android TV & Leanback Support**: Native D-pad navigation, 10-foot rail layout, Leanback launcher banner, and overscan safe-insets.
- **Quality Assurance**: 122 automated unit tests passing (100% success rate), clean lint checks, and reproducible release builds.

---

## 📥 Download

Get the latest APK directly from [GitHub Releases](https://github.com/takaokensei/makimono/releases/latest).

---

## 🔑 Drive OAuth Setup

Makimono connects directly to your private Google Drive using standard OAuth 2.0 with least-privilege `drive.readonly` scope. To set up your credentials, create a Google Cloud Project with the Google Drive API enabled, and configure your client ID and client secret.

---

## 📜 License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.
