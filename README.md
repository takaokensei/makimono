<div align="center">

<img src="docs/logo.png" width="140" alt="Makimono (巻物) — Netflix alternative for your Google Drive on Android TV" />

# Makimono (巻物)

<samp>Google Drive Media Streaming · Android TV & Android · Native MPV</samp>

<br/>

<b>Your own "Netflix" powered by Google Drive.</b><br/>
An open-source alternative to Netflix, Plex and Jellyfin for anyone who keeps movies, TV shows and anime on Google Drive: no server, no subscription, native ASS/SSA fansub styling, D-pad navigation built for the living room and isolated profiles.

<br/><br/>

<a href="https://github.com/takaokensei/makimono/releases"><img alt="GitHub release" src="https://img.shields.io/github/v/release/takaokensei/makimono?style=for-the-badge"/></a>
<a href="https://github.com/takaokensei/makimono/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/github/license/takaokensei/makimono?style=for-the-badge"/></a>
<img alt="Platform" src="https://img.shields.io/badge/Android%20%7C%20Android%20TV-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>

<br/><br/>

<sub>
<b>Keywords:</b> Netflix alternative · Google Drive media player · Google Drive streaming · Android TV media player · Plex alternative · Jellyfin alternative · self-hosted streaming · anime player · ASS/SSA subtitles · MPV Android TV · open source media player · Google Drive video player
</sub>

</div>

<br/>

## `> overview`

**Makimono** (巻物, Japanese for "scroll") is a **streaming client for Android TV and Android that turns your Google Drive into a Netflix-style library**: artwork, episodes, "continue watching", profiles and playback straight from the cloud. Your files stay in your own Google account; the app only takes care of the watching experience.

```python
class Makimono:
    def __init__(self):
        self.name = "Makimono (巻物)"
        self.type = "Google Drive media streaming client"
        self.platforms = ["Android TV 11+ (1080p / 4K UHD)", "Android 5.0+ (phone / tablet)"]
        self.engines = ["AndroidX Media3 (ExoPlayer)", "libmpv (native)"]
        self.subtitles = "ASS/SSA with fansub styles preserved"
        self.language = "Kotlin"
        self.license = "Apache-2.0"

    def alternative_to(self):
        return ["Netflix", "Plex", "Jellyfin"]

    def does_not_need(self):
        return ["your own server", "a subscription", "cloud transcoding"]
```

<br/>

## `> features`

<table>
  <tr>
    <td width="33%" valign="top">
      <b>☁️ Drive-native streaming</b><br/>
      Plays straight from Google Drive. No server of your own, no cloud transcoding, no subscription.
    </td>
    <td width="33%" valign="top">
      <b>📺 Built for the TV</b><br/>
      100% remote-control (D-pad) navigation, predictable focus and an interface designed for the 10-foot experience.
    </td>
    <td width="33%" valign="top">
      <b>💬 Fansub-grade subtitles</b><br/>
      ASS/SSA with fonts, colors, positioning and effects preserved, rendered by the native <b>libmpv</b> engine.
    </td>
  </tr>
  <tr>
    <td width="33%" valign="top">
      <b>⚙️ Dual playback engines</b><br/>
      <b>AndroidX Media3 (ExoPlayer)</b> and <b>native MPV</b> behind a single <code>PlayerEngine</code> contract, covering the widest range of videos and codecs.
    </td>
    <td width="33%" valign="top">
      <b>👥 Isolated profiles</b><br/>
      Everyone in the household gets their own history and progress.
    </td>
    <td width="33%" valign="top">
      <b>🏠 Android TV integration</b><br/>
      <code>MediaSession</code> (system media controls) and <b>Watch Next</b> (continue watching from the TV home screen).
    </td>
  </tr>
  <tr>
    <td width="33%" valign="top">
      <b>⚡ Local-first</b><br/>
      Metadata cached locally (Room) so the app opens fast and doesn't depend on the network at every screen.
    </td>
    <td width="33%" valign="top">
      <b>🌐 Bilingual UI</b><br/>
      Full string parity between English and Portuguese.
    </td>
    <td width="33%" valign="top">
      <b>🎨 Obsidian Design System</b><br/>
      OLED dark theme, glassmorphism and an Electric Cyan accent, with WCAG-validated contrast.
    </td>
  </tr>
</table>

<br/>

## `> tech_stack`

<table align="center">
  <tr>
    <td align="center" width="33%">
      <strong>📱 App</strong>
      <br/><br/>
      <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white"/>
      <img src="https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white"/>
      <img src="https://img.shields.io/badge/Android_TV-22D3EE?style=flat-square&logoColor=black"/>
      <img src="https://img.shields.io/badge/Room-4285F4?style=flat-square"/>
    </td>
    <td align="center" width="33%">
      <strong>🎬 Playback</strong>
      <br/><br/>
      <img src="https://img.shields.io/badge/AndroidX_Media3-3DDC84?style=flat-square"/>
      <img src="https://img.shields.io/badge/libmpv-691F69?style=flat-square"/>
      <img src="https://img.shields.io/badge/ASS%2FSSA-22D3EE?style=flat-square"/>
    </td>
    <td align="center" width="33%">
      <strong>🔧 Tooling</strong>
      <br/><br/>
      <img src="https://img.shields.io/badge/Google_Drive_API-4285F4?style=flat-square&logo=googledrive&logoColor=white"/>
      <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white"/>
      <img src="https://img.shields.io/badge/Python_checks-3776AB?style=flat-square&logo=python&logoColor=white"/>
      <img src="https://img.shields.io/badge/Git-F05032?style=flat-square&logo=git&logoColor=white"/>
    </td>
  </tr>
</table>

<br/>

## `> who_is_it_for`

| If you... | Makimono helps because... |
| --- | --- |
| Keep movies, TV shows or anime on Google Drive | It turns your Drive folders into a browsable library with artwork and episodes |
| Want a Netflix alternative with no subscription and no server to host | It plays directly from your Drive, with no extra infrastructure |
| Watch anime with fansub subtitles | It renders ASS/SSA with the original styles through MPV |
| Use Android TV, a TV box or Google TV | It was designed D-pad-first for big screens, with a mobile version too |
| Share the TV with your family | Isolated profiles keep history and progress separate |

<details>
<summary><b>❓ FAQ</b></summary>

<br/>

**Is Makimono a Netflix alternative?**
It doesn't ship its own catalog: it's the player and library for **your** content on Google Drive, with an experience similar to a streaming service.

**Do I need a server, like with Plex or Jellyfin?**
No. The app talks directly to the Google Drive API; there is nothing to install or maintain on a server.

**Does it work on Android TV, Google TV and TV boxes?**
Yes, on Android TV 11+ (1080p / 4K UHD), and also on phones and tablets running Android 5.0+.

**Does it support ASS/SSA fansub subtitles?**
Yes. Styles are preserved by the native libmpv engine.

**Can several people use it on the same TV?**
Yes, with isolated profiles.

</details>

<br/>

## `> architecture`

The project follows a layered architecture focused on separation of concerns and local-first design:

| Layer | Responsibility |
|---|---|
| `domain` | Models and pure business rules (no Android framework dependencies). |
| `data` | Repositories, Google Drive client, local Room database and metadata cache. |
| `playback` | Decoupled playback engines under a single contract (`PlayerEngine`), managing Media3 and native MPV. |
| `platform` | Android TV platform integrations (`MediaSession`, Watch Next). |
| `ui` | Presentation for Android TV (D-pad 10-foot experience, 80dp rail) and mobile. |

For full details and design decisions:
- [Canonical Architecture Document](docs/architecture/ARCHITECTURE.md)
- [Architecture Decision Records (ADRs)](docs/architecture/adr/)
- [Native MPV Guide](docs/architecture/native-mpv.md)

<br/>

## `> quickstart`

### Requirements

| | |
|---|---|
| **JDK** | OpenJDK 17 or later (`JAVA_HOME` configured) |
| **Android SDK** | `compileSdk 34`, `targetSdk 33`, `minSdk 21` |
| **Build Tools** | Android SDK Build-Tools 34.0.0 |
| **Device** | Android 5.0+ (Lollipop, API 21+) for phone/tablet, or Android TV 11+ (1080p / 4K UHD) |

### Build from scratch

1. **Clone the repository:**
   ```bash
   git clone https://github.com/takaokensei/makimono.git
   cd makimono
   ```

2. **Configure local properties:**
   Copy the properties template to create your own `local.properties`:
   ```bash
   cp local.properties.example local.properties
   ```
   Set the Android SDK path (`sdk.dir`) and the required credentials as described in the example file.

   > [!NOTE]
   > **Windows environment:** To avoid Gradle test-class loading failures caused by paths containing non-ASCII characters (e.g. `C:\Users\Name With Accent\...`), run the build and tests from a strictly ASCII path or mount a temporary virtual drive (e.g. `subst T: .` and work from `T:\`).

3. **Build the debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The generated binary will be at `app/build/outputs/apk/debug/app-debug.apk`.

<br/>

## `> quality`

Run the validation and integrity-check tasks:

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run code quality checks (Lint with active baseline)
./gradlew lintDebug

# Run the full verification in a single command
./gradlew testDebugUnitTest lintDebug assembleDebug
```

### Code integrity metrics (v1.9.0)

| Metric | Result |
|---|---|
| **Unit tests** | 683 `@Test` annotations · **691 executions** · 0 failures · 0 errors |
| **Android Lint** | 681 issues in baseline · **0 blocking errors** · **0 errors filtered by the baseline** |
| **i18n parity** | `MissingTranslation`: 0 · `MissingQuantity`: 0 · `Untranslatable`: 0 · `ContentDescription`: 0 |

- **Unit Tests:** 683 `@Test` annotations in the source code and **691 executions** recorded in the JUnit XML report (0 failures, 0 errors). The static count and the execution count are distinct measures; inherited tests are reconciled by `python tools/checks/count_tests.py`.
- **Android Lint (Baseline):** 681 issues cataloged in `app/lint-baseline.xml` with **0 blocking errors** and **0 errors filtered by the baseline**, plus full internationalization parity (`MissingTranslation`: 0, `MissingQuantity`: 0, `Untranslatable`: 0, `ContentDescription`: 0). Main categories: `HardcodedText` (286), `UnusedResources` (158), `SetTextI18n` (102), `InconsistentLayout` (18), `UnusedAttribute` (15). Verification: `python tools/checks/count_lint_categories.py`.

<details>
<summary><b>🧰 Continuous verification tools (<code>tools/checks/</code>)</b></summary>

<br/>

- `check_strings.py`: Strict string parity validation (551 `<string>` tags in total in `values/strings.xml`, of which 548 are translatable and validated against `values-pt/strings.xml`, and 3 are technical non-translatable strings marked `translatable="false"`).
- `check_kotlin_literals.py`: Guard against hardcoded UI literals (`Toast.makeText`, `setTitle`, `setMessage`, `setText`) in Kotlin files.
- `check_kotlin_style.py`: Static Kotlin style and hygiene check with a versioned baseline (`config/ktlint-baseline.json`).
- `check_immutable_tag.py`: Release tag immutability guard with a self-test (`--self-test`).
- `check_contrast.py`: WCAG AAA/AA compliance validation for all color palettes and gradients over imagery.
- `check_layout_colors.py` & `check_typography.py`: Audit of semantic token and typography usage.
- `check_no_direct_log.py`: Guard against direct `android.util.Log` calls outside `AppLog.kt`.
- `check_readme_claims.py`: Continuous parity validation between README claims and the actual values in the code.
- `count_tests.py` & `count_lint_categories.py`: Automated audit of tests and the Lint baseline.

</details>

<br/>

## `> docs`

| Area | Links |
|---|---|
| **Design System & TV UX** | [Obsidian Design System](docs/design/DESIGN.md) · [HIG References](docs/design/hig-references.md) |
| **Quality Assurance** | [Device Matrix](docs/qa/device-matrix.md) · [TV Focus Checklist](docs/qa/focus-checklist.md) · [Diagnostic Baseline](docs/qa/baseline-0.md) |
| **History & Releases** | [Release Guidelines](docs/releases/README.md) · [Full Changelog](CHANGELOG.md) |

<br/>

---

<div align="center">
  <samp>
    Your library. Your Drive. Your screen.<br/>
    Distributed under the Apache 2.0 license. See <a href="LICENSE">LICENSE</a> for more information.
  </samp>
</div>
