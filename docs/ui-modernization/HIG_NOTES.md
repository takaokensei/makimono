# Apple HIG notes for Makimono TV

F0 reference register for the ten local Apple Developer Documentation PDFs. These are design references adapted to Android TV, not platform requirements or a claim of Apple-platform conformance. No PDF was copied into the repository. Page numbers below refer to the printed page number in each supplied PDF (not a web page or a platform not requested).

## F0 PDF review record

The supplied PDFs carry the print date 03/10/2026. General and tvOS material was reviewed; sections for other Apple platforms were excluded as directed. Relevant tables and illustrations were rasterized at 115 dpi and inspected where extraction omitted structure.

| PDF | Pages | General/tvOS pages reviewed | Raster pages inspected |
|---|---:|---|---|
| Focus and selection | 6 | 1–6 | 4–5 |
| Remotes | 3 | 1–3 | — |
| Lockups | 4 | 1–4 | — |
| Materials | 8 | 1–6, 8 | 2, 5 |
| Typography | 20 | 1–10, 15–16 | 2–10 (hierarchy, scaling and tvOS styles) |
| Layout | 9 | 1–6 | 6 (focus padding, 2-column grid values) |
| Motion | 4 | 1–4 | — |
| Color | 11 | 1–4, 8–9 | 2, 9 (contrast examples and system-color variants) |
| Dark Mode | 5 | 1–5 | — |
| Accessibility | 10 | 1–8 | 2, 5 (text/contrast table and tvOS control sizes) |

The Layout p. 6 two-column table and Typography pp. 9–10 tvOS styles were legible. The rasterized Typography pages also confirm that the style weights in those tables are not interchangeable with the other platforms' tables. Accessibility p. 2 confirms the tvOS 29/23 pt text sizes and its PDF contrast table; Color p. 9 shows the custom-color variants. The additional Layout grid-tab values for three through nine columns and tvOS alert anatomy remain unverified; no substitute numbers are fabricated.

The mission catalog also seeds rules from Buttons, Labels, Collections, Top Shelf, Menus, Lists and tables, Loading, Managing accounts, Settings, and other phase-specific guides. Their guide files are assigned to F2–F6; those rules are not recorded below as PDF-reconfirmed until those guides are read in their phase.

## Focus and selection

**Source:** `Focus and selection _ Apple Developer Documentation.pdf` (6 pages), general and tvOS sections, pp. 1–6.

- Keyboard/remote focus is a model for discrete navigation; it is not a pointer. Preserve the remote's familiar directional, select, menu/back and playback behavior.
- Visually separate unfocused, focused, highlighted/activated and selected states. A selected favorite, for example, must remain distinguishable from current D-pad focus.
- Focus should elevate and illuminate the target. Scale/animation are possible cues, but need padding so the enlarged target does not overlap content.
- On tvOS full-screen media, focus is not shown until the viewer interacts with controls. Focus groups/priorities should make the most likely action straightforward.

## Remotes

**Source:** `Remotes _ Apple Developer Documentation.pdf` (3 pages), pp. 1–3.

- Keep the standard remote's gestures and button meanings. Select activates one discrete focused item; don't replace that with pointer-like interaction.
- Back/Menu navigates to the parent level. Play/Pause toggles playback. Directional presses move among a predictable set of focus targets.
- Where a gesture is used, expose an onscreen alternative. The reference emphasizes standard tvOS remotes; Android TV should additionally follow Android's own remote conventions.

## Lockups

**Source:** `Lockups _ Apple Developer Documentation.pdf` (4 pages), pp. 1–4.

- Treat an image, title and supporting metadata as one coherent lockup. Keep spacing and relative sizing consistent across shelves.
- Reserve enough surrounding space for focused lockups to grow without obscuring their neighbors. Make focus-aware image assets sharp at the larger size.
- Profile imagery is circular in the monogram guidance; use a circular crop and initials fallback instead of a hard rectangular edge.

## Materials

**Source:** `Materials _ Apple Developer Documentation.pdf` (8 pages), general and tvOS sections, pp. 1–6 and 8. The supplied guide describes Liquid Glass as a tvOS navigation/control material; that visual system is not directly equivalent to Android blur.

- Separate the functional/navigation layer from the content layer. Use the material only where controls must remain legible over changing imagery.
- Choose material thickness by the amount of content that must remain visible. The tvOS table distinguishes ultra-thin, thin, regular and thick materials (p. 5); dark overlays that cover content call for a dark-scheme material.
- Test translucency against bright and dark artwork. Apply a dimming treatment when bright imagery weakens labels; don't use glass as decoration on every card.

## Typography

**Source:** `Typography _ Apple Developer Documentation.pdf` (20 pages), general and tvOS sections, pp. 1–10 and 15–16.

- The tvOS reference uses 29 pt as its standard text size and 23 pt as the smallest style (p. 9); avoid thin weights. Its style table on p. 10 ranges from Title 1 (76/96 pt) through Body (29/36 pt) and Caption 2 (23/30 pt), with paired weights/line heights.
- Preserve hierarchy, limit type families, and adapt layout when text grows. Do not truncate important labels or metadata merely to keep a fixed composition.
- Makimono uses Android `sp`/Inter; values need platform-specific conversion and device testing, not literal copying of Apple point sizes.

## Layout

**Source:** `Layout _ Apple Developer Documentation.pdf` (9 pages), general and tvOS sections, pp. 1–6.

- Group related content and establish hierarchy. Disclose secondary controls progressively; visually distinguish controls from browsable content.
- tvOS safe areas are 80 pt at left/right and 60 pt at top/bottom (p. 5). Map these conservatively to Android TV and account for overscan/device variance.
- Leave padding for focused elements that scale. The two-column grid reference specifies 860 pt unfocused content width, 40 pt horizontal spacing and 100 pt minimum vertical spacing (p. 6). Those dimensions are not directly transferable to a denser Android layout, but the emphasis on generous focus-safe spacing is applicable.
- Keep partially offscreen items symmetrical and use consistent spacing. Background art can extend behind navigation; preserve its aspect ratio using scale/crop.

## Motion

**Source:** `Motion _ Apple Developer Documentation.pdf` (4 pages), pp. 1–4.

- Motion should communicate a meaningful state change, remain brief and precise, and be cancelable where appropriate.
- Avoid gratuitous or repetitive animations on common remote interactions. Use focus transitions to reinforce, not delay, directional navigation.
- Check reduced-motion preferences and provide a restrained alternative to large scale or parallax effects.

## Color

**Source:** `Color _ Apple Developer Documentation.pdf` (11 pages), general and tvOS sections, pp. 1–4 and 8–9.

- Keep the palette purposeful and semantic; use accent colors sparingly. Never encode focus, rating, watched or warning state by color alone.
- Maintain legible foreground/background combinations over changing artwork and translucent surfaces. Color and translucency can change the appearance of nearby artwork (p. 2); validate states on varied backgrounds.
- The guide encourages testing default and increased-contrast variants (p. 2) and checking color across displays and viewing conditions.

## Dark Mode

**Source:** `Dark Mode _ Apple Developer Documentation.pdf` (5 pages), general and tvOS-relevant sections, pp. 1–5.

- Apps should generally respect system appearance; a dedicated dark-only appearance is defensible for an immersive media app, but must remain readable and consistent.
- The guide calls for at least 4.5:1 contrast for text and greater contrast for small text; the project briefing adopts 4.5:1 as a minimum audit target for all text and 7:1 as a target for small text.
- Avoid pure-white expanses in dark surfaces; keep icons distinguishable. Support increased contrast and reduced transparency where feasible.

## Accessibility

**Source:** `Accessibility _ Apple Developer Documentation.pdf` (10 pages), general and tvOS sections, pp. 1–8.

- Support intuitive, perceivable and adaptable experiences. Text should adapt to larger sizes; tvOS reference styles use 29 pt standard and 23 pt minimum (p. 3).
- Contrast guidance varies by size/weight: 4.5:1 below 18 pt regular (or below 14 pt bold), and 3:1 for larger text (p. 4). Makimono uses the stricter project-wide 4.5:1 floor.
- Don't use color alone for state. Make remote controls sufficiently large: 66×66 pt default and 56×56 pt minimum for tvOS. Allow 12 pt around beveled controls or about 24 pt for borderless controls (p. 5).
- Prefer simple gestures and expose alternative controls. Avoid time-limited interactions where possible; let viewers pause/skip autoplay; captions and audio descriptions need usable settings. Respect Reduce Motion.

## F0 rule register (traceable IDs)

IDs below are from the mission briefing's confirmed catalog. Each record is a paraphrase of the cited general/tvOS section; applicability is Android TV adaptation, not tvOS code reuse.

| ID | Source and PDF page(s) | Recorded rule |
|---|---|---|
| H-L1 | Layout, p. 5 | Keep primary content inside 80 pt horizontal / 60 pt vertical safe areas; artwork may bleed to screen edges. |
| H-L2 | Layout, p. 6; Focus and selection, p. 4 | Allow focused enlargement without covering neighbors or important information. |
| H-L3 | Layout, p. 6 | Use consistent grid spacing; 2-column reference is 860/40/100 pt and partial offscreen items should be symmetric. |
| H-L4 | Layout, pp. 1–4 | Establish hierarchy, align/group related items, and disclose secondary actions progressively. |
| H-L5 | Layout, pp. 2, 5 | Distinguish controls from content; full-screen background may extend under navigation; crop without changing aspect ratio. |
| H-K1 | Lockups, pp. 1–3 | Artwork, title and supporting content form a consistent unit and grow/contract coherently on focus. |
| H-K2 | Lockups, pp. 3–4 | Profile monograms use circular images and names; use initials as a fallback. |
| H-A1 | Accessibility, p. 3; Typography, pp. 9–10 | TvOS reference text is 29 pt standard and 23 pt minimum; avoid thin weights. |
| H-A2 | Accessibility, p. 4; Dark Mode, pp. 2–3 | Contrast threshold depends on size/weight; this project applies its stricter 4.5:1 floor and 7:1 small-text target. |
| H-A3 | Accessibility, pp. 3–4; Color, pp. 1–3 | Never encode state only through color. |
| H-A4 | Accessibility, p. 5 | TvOS controls are 66×66 pt default / 56×56 pt minimum, with padding around them. |
| H-A5 | Accessibility, p. 3; Typography, pp. 8–10 | Adapt useful text to larger sizes without overlap or loss of essential content. |
| H-A6 | Accessibility, p. 7; Motion, pp. 1–4 | Minimize timed dismissal and make necessary timed UI understandable and controllable. |
| H-A7 | Accessibility, pp. 7–8 | Playback and autoplay must remain user-controllable. |
| H-A8 | Accessibility, pp. 7–8; Motion, pp. 2–4 | Respect reduced-motion settings and avoid motion as the only state signal. |
| H-A9 | Accessibility, pp. 6–8 | Preserve legible, adjustable captions/subtitles and audio accessibility. |
| H-A10 | Accessibility, pp. 2, 6 | Expose meaningful interface descriptions to assistive technologies. |
| H-D1–D5 | Dark Mode, pp. 1–5 | A dark-only immersive media UI can be appropriate; maintain contrast, distinguish surfaces, and support increased contrast/reduced transparency. |
| H-CO1–CO6 | Color, pp. 1–4, 8–9 | Use a restrained semantic palette, don't rely on color for focus, test contrast and validate across displays. |
| H-Y1–Y8 | Typography, pp. 8–10, 15–16 | Preserve readable tvOS hierarchy, weight, tracking and leading; adapt layouts to larger text. |
| H-F1 | Focus and selection, pp. 1–5 | Keep unfocused/focused/activated/selected/disabled states distinct; focus elevates and illuminates. |
| H-F2–F4 | Focus and selection, pp. 2–5 | Make all actions reachable, don't steal focus, and reserve space for focused assets. |
| H-F5–F7 | Focus and selection, pp. 3–5; Remotes, pp. 1–3 | Hide focus when full-screen playback controls are hidden; use directional focus and set a sensible initial target. |
| H-F8–F9 | Focus and selection, pp. 4–5; Lockups, pp. 1–4 | Keep hierarchical selection persistent and ensure focused items don't crowd neighbors. |
| H-R1–R5 | Remotes, pp. 1–3 | Preserve standard remote meanings, parent/back behavior, directional navigation, select-vs-touch distinction and familiar playback toggles. |
| H-M1–M5 | Materials, pp. 2, 5–6 | Reserve glass-like material for navigation/control layers, select opacity semantically and protect text over rich media. |
| H-O1–O5 | Motion, pp. 1–4 | Motion should be purposeful, brief, coherent, cancelable and subordinate to input. |

**Catalog-only pending reconfirmation:** H-C1, H-F10, H-T1, H-B1–B6, H-Q1–Q5, H-N1, H-S1, H-G1, H-U1, H-NV1–NV8, H-V1, H-PV1–PV6, H-SE1, H-PG1, H-RV1–RV2, and H-CO/Y extensions not covered by the selected page ranges. These remain in the ledger as pending source review, not as verified HIG statements.

## Conflitos (C-01–C-08)

| ID | Reconciliation |
|---|---|
| C-01 | Earlier 3:1 floor is overridden by Dark Mode's 4.5:1 floor: apply H-A2. |
| C-02 | `text.tertiary` is reserved for unavailable content; ordinary metadata/eyebrows use secondary label treatment (H-T1). |
| C-03 | Earlier SemiBold-only title guidance conflicts with tvOS table's Medium base / Bold emphasis (H-Y1); project uses Inter, validated visually. |
| C-04 | Earlier 1.06 focus scale is superseded by reference ratios 1.09/1.14 where the component type applies (H-F10). |
| C-05 | “Never indicate focus only by color” is grounded in Color/Accessibility/Toggles, not Focus and selection. |
| C-06 | Top Shelf poster's printed unfocused 333×570 size conflicts with the 2:3 poster ratio; use 333×500 for that calculation. |
| C-07 | Some PDF tab/table visuals are absent from text extraction; rasterized Layout p. 6 confirms only the two-column measurements. Other 3–9 column measurements and alert anatomy remain unverified and are not invented. |
| C-08 | Tracking values in Typography are SF Pro metrics; Inter values require visual validation (D-06). |

## Desvios pré-aprovados (D-01–D-08)

- **D-01 — H-M2 / motivo (c):** MPV `SurfaceView` cannot blur. Alternative: ≥88% opaque dark surface + hairline, and a 35% dim layer over bright artwork. Evidence to record in F6.
- **D-02 — Dark Mode / motivo (b):** Preserve the five existing dark themes; don't add a light theme or another appearance picker. Evidence: existing theme list and theme dialog.
- **D-03 — H-K1 / motivo (b):** The v1.5.8 invariant keeps poster titles visible at rest. Alternative: secondary label at rest, primary emphasis on focus, title grows with card. Evidence: catalog/details captures.
- **D-04 — Focus / motivo (a):** tvOS parallax/tilt assumes Siri remote swipe and layered image assets, with no Android D-pad equivalent. Alternative: restrained scale, elevation and fill; don't add parallax.
- **D-05 — H-B5/H-N1/H-Q2 / motivo (a):** English title-style capitalization has no PT-BR equivalent. Alternative: sentence case consistently across UI.
- **D-06 — H-Y3/H-Y5 / motivo (b):** Inter is the established project identity. Alternative: preserve Inter, validate its tracking/readability, and don't copy SF Pro metrics verbatim.
- **D-07 — H-PG1 / motivo (a):** tvOS has no equivalent seek slider. Alternative: retain Android `SeekBar` with strong focus treatment and test remote access.
- **D-08 — H-NV3 / motivo (b):** Season changes can't steal focus from asynchronous metadata/episode updates (v1.4.23 invariant). Alternative: keep explicit OK confirmation if focus-driven debounce would violate the invariant.

Apple HIG pages describe Apple platforms. The tvOS sizing and interaction metrics above are used as the user's chosen adaptation reference; they are not statements about Android's native APIs. Exact 3–9 column grids and alert anatomy not legible in the supplied source remain explicitly unverified.

