# HIG conformance ledger — Makimono Android TV

F0 is a diagnostic baseline, not a passing conformance claim. “Gap” identifies an observed baseline issue; it is not an approved design deviation. Each affected rule must be rechecked in its implementing phase. Only the final gate may classify a rule `OK`, `D-xx` (one of the documented adaptations in `HIG_NOTES.md`) or `N/A-Android`.

**Evidence:** 12 user-provided screenshots in `docs/ui-audit/current-state/` and 12 fixture recaptures in `docs/ui-audit/after-v2/f0/`. Current capture dimensions: 1920×1080, 320 dpi (logical 960×540 dp). The fixture is debug-only and has the separate application ID `zechs.drive.stream.visualreview`; the signed install and its account data were left intact. **Scope note:** `AUDIT_REPORT.md` requested by the attached mission is absent; the existing review is `docs/ui-modernization/AUDIT.md`.

## F1 token verification

`python tools/check_contrast.py` measures primary and secondary text at ≥7:1 against each palette's base and elevated surfaces, and primary-action text at ≥4.5:1. All six palettes pass. Token and theme resources compile as part of `assembleDebug`; layout adoption, focused-state clearance, and reduced-motion behavior still require later phase verification.

| F1 area | Evidence | F1 status | Remaining phase |
|---|---|---|---|
| Color roles | `tools/check_contrast.py` output; six palette token definitions | Pass: checked primary/secondary text ≥7:1; action labels ≥4.5:1 | F2–F6 must replace remaining hardcoded view colors and validate on imagery |
| Safe margins | `values/dimens.xml`, `values-land/dimens.xml`, `values-sw600dp-land/dimens.xml` | Pass: shared 48dp horizontal / 30dp vertical tokens | F2–F5 visual checks for screens, clipping, and focus clearance |
| Type/shape/motion tokens | `values/typography.xml`, `values/dimens.xml`, `values/motion_spec.xml`, `ContinuousCornerOutline.kt` | Foundation added; runtime application not yet proven | F2/F4/F5/F6 |
| Theme compatibility | `AppThemeTest.kt`; original values 0–4 unchanged, Obsidian value 5 | Pass at model/resource mapping level | F5 theme selector and all surface variants |
| D-pad and theme picker | `after-v2/f1/02_home_details_focus.png`, `03_home_shelf_focus.png`, `04_theme_picker_obsidian.png` and hierarchy XML | RIGHT reaches Details; vertical navigation reaches shelf cards; five DOWN presses scroll to and focus Obsidian. The white ring/undersized action issue was addressed in F2; F3 verifies full visibility of a focused catalog card. | F4–F6 |

## F2 focus and control verification

Focus references re-opened for this phase: Buttons pp. 1–3, Collections pp. 1–2, Focus and selection pp. 1–6, Labels pp. 1–2, and Lockups pp. 1–2. Apple describes a 44×44 pt general button hit region; that unit is platform-specific. Makimono targets 48dp for the audited Android controls.

| F2 area | Evidence | F2 status | Remaining phase |
|---|---|---|---|
| Theme-aware focus | `utils/TvFocusRing.kt`; theme `colorAccentPrimary`/`colorFocusFill`; `after-v2/f2/02_home_focus_right.png`, `03_home_focus_play.png`, `06_home_focus_up.png`, `07_details_focus_primary.png` | Removed the fixed white double ring from hero/details actions; one 2dp theme-accent ring is drawn by the shared overlay. Compact controls get a subtle accent fill; poster cards stay clear. Card focus is drawn by the helper rather than an unreliable CardView foreground selector. | Verify dialogs/player/list focus in F4–F6 |
| Action target sizing | `fragment_home.xml` and its land/TV/sw720dp-land variants; `fragment_series_detail.xml` variants; both `item_series_season_tab.xml` variants | Home hero, nav/profile rail, details back/search/actions, and season tabs use 48dp bounds. Existing IDs and directional rules remain. | Audit remaining interactive targets and font-scale in F3–F6 |
| Focus vs selection | `drawable/tab_season_pill_bg.xml`, `drawable/detail_season_tab_bg.xml`; detail screenshot | Selected season uses a quiet surface/hairline. Transient focus is the accent ring, so selection does not reuse the focus outline. | Recheck favorite/watched semantics in F3/F5 |
| D-pad and touch routes | `after-v2/f2/14_home_focus_card_fixed.xml`, `15_details_focus_tab_final.xml`, `13_home_touch_details_fixed.xml` | Home D-pad focuses the individual `cardShelfItem`; touch no longer outlines the containing RecyclerView. Details DOWN reaches the first season tab. Production BACK/touch navigation remains for later route audits. | F3–F6 |

## F2 measured visual evidence

| Capture and hierarchy | Measured value | Interpretation |
|---|---|---|
| `after-v2/f2/06_home_focus_up.png` + `home-up.xml` | `cardShelfItem` focused at `[742,267][1148,496]` (203×114.5dp). | Single cyan perimeter is visible without a white inner ring; D-pad returns from catalog to the shelf. |
| `after-v2/f2/09_details_final.png` + `details-final.xml` | Final `btnPrimaryAction` bounds `[336,469][658,553]` (322×84 px); XML target is 48dp high. | Primary action has the final target size and uses the shared theme-accent focus overlay. |
| `after-v2/f2/15_details_focus_tab_final.png` + `15_details_focus_tab_final.xml` | DOWN from the primary action focuses `tabPillContainer` `[336,588][506,672]` (170×84 px); XML target is 48dp high. | Cyan ring identifies transient focus; the selected tab also has a quiet filled surface. Episode navigation continues through the next focus step. |
| `after-v2/f2/13_home_touch_details_fixed.png` | Tap at `[700,528]`; no focused item or collection outline appears. | Touch on the hero background does not decorate the entire shelf. |
| `after-v2/f2/14_home_focus_card_fixed.png` + `14_home_focus_card_fixed.xml` | `cardShelfItem` focused at `[308,759][714,988]` (406×229 px). | The individual card retains a single cyan outline after collection containers were excluded. |

## F3 viewport and surface verification

| F3 area | Evidence | F3 status | Remaining phase |
|---|---|---|---|
| Collection clipping | Home `contentScrollView` in base/land/TV/sw720dp-land and Details `scrollView` in base/land set `clipChildren`/`clipToPadding` true; shelf/grid/episode rows retain local clipping behavior. | Scroll content is contained inside its viewport while card-level focus treatment remains visible. | Audit other screens and orientation changes in F4–F6 |
| Focused Home item visibility | `after-v2/f3/05_home_focus_scrolled.png` + `05_home_focus_scrolled.xml` | `cardGridItem` fully visible at `[314,162][520,604]`; after DOWN from hero through the shelf, the page scrolls the complete focused tile into view. | Continue checking end-of-list, search and async updates in F4/F6 |
| Details backdrop boundary | `after-v2/f3/02_details_after_clip.png` + `02_details_after_clip.xml` | In the fixture, `ivHeroBackdrop` ends at y=574 px and `rvSeasonTabs` begins at y=581 px. The user screenshot's art bleed did not reproduce in this layout; scroll viewport clipping is now explicit. | Recheck on production metadata and other themes in F4/F5 |

## F1 measured visual evidence

| Capture and hierarchy | Measured value | Interpretation |
|---|---|---|
| `after-v2/f1/home-focus-hierarchy.xml` | Focused `btnFeaturedInfo` bounds `[605,486][823,570]` = 109×42dp. | Historical F1 baseline: the target was below the 48dp standard control target and had a double white ring; both were addressed in F2. |
| `after-v2/f1/home-down-hierarchy.xml` | Focused `cardShelfItem` bounds `[308,759][714,988]` = 203×114.5dp. `tvHomeCatalogTitle` is at `[308,1077][1836,1080]` (3 px visible). | Historical baseline showed the catalog below the initial fold; F3 now scrolls the full focused grid card into the viewport. |
| `after-v2/f1/theme-picker-focus-hierarchy.xml` | Focused `cardTheme` bounds `[516,839][1403,1003]`; its label is Obsidian. | The new sixth theme can be reached by D-pad scrolling; the original five remain in place. |

| Rule | Source | Diagnostic evidence / baseline finding | F0 status | Planned phase |
|---|---|---|---|---|
| H-F1–F2 | Focus and selection, pp. 2–5 | Cards have prominent rings, but selected/favorite and focused states are not consistently distinguishable and every action path is not proven. | Gap | F1 focus |
| H-F3, H-F7 | Focus and selection, pp. 2–5 | Home/details choose initial targets; async list updates and full screen need interaction verification to ensure focus is not stolen. | Not verified | F1 |
| H-F4, H-L2, H-K1, H-F10 | Layout, p. 6; Focus and selection, p. 4; Lockups, pp. 1–4 | Some cards visibly clip/collide under focus, and shelf card treatments differ. F0 episode-focus route now reproduces an episode card state. | Gap | F1/F2 |
| H-F5, H-R1–R5 | Focus and selection, p. 4; Remotes, pp. 1–3 | User report confirms BACK can strand player playback. D-pad direction, central-key single activation, player parity and return hierarchy need real key testing. | Gap | F1 player |
| H-L1, H-L3–L6, H-C1 | Layout, pp. 1–6; Collections, tvOS pages | Details episode region leaks hero art through its content surface; home bottom rows and settings rows crop; grid rhythm and responsive variants need comparison. | Gap | F2/F3 |
| H-Y1–Y8, H-A1, H-A5 | Typography, pp. 5–10; Accessibility, pp. 3–4 | Labels truncate, small captions/metadata are difficult at viewing distance; no font-scale testing has been run. | Gap / not verified | F2 |
| H-A2, H-A3, H-CO1–CO6, H-D2–D4 | Accessibility, p. 4; Color, pp. 1–4, 8–9; Dark Mode, pp. 2–5 | Gray metadata overlays art without consistent scrim; oversized favorites stars make semantic focus/selection ambiguous; cyan is overused. Contrast across themes is not yet measured. | Gap | F2 |
| H-A4 | Accessibility, p. 5 | Icon controls and action affordances vary in focus/hit bounds; details action group is visually dense. | Gap | F1/F2 |
| H-A6–A10, H-G1, H-U1 | Accessibility, pp. 2, 6–8; Loading/Managing accounts, tvOS pages | Current captures cannot verify timers, reduced motion, accessibility descriptions, captions, loading states or account flows. | Not verified | F3/F6 |
| H-D1, H-D5 | Dark Mode, pp. 1–5 | Dark-only shell is appropriate to media, but reduced-transparency/high-contrast behavior is not demonstrated. | D-02 / not verified | F2/F5 |
| H-M1–M5, H-O1–O5 | Materials, pp. 2, 5–6; Motion, pp. 1–4 | Image leaks through detail shelf and player menu; focus-motion timing/cancelability is not evidenced by still captures. | Gap / not verified | F1/F2/F6 |
| H-Q1–Q5, H-N1 | Alerts/Action sheets/Modality/Menus PDFs assigned to F6; not yet source-checked | Theme dialog nearly fills the display; player menu layers expose artwork through a content-heavy panel; Back/dismiss behavior needs validation. | Pending source review | F6 |
| H-V1, H-PV1–PV6 | Going full screen/Playing video PDFs assigned to F6; not yet source-checked | User report identifies failed player exit path. OSD and playback parity, aspect fill, loading, resume and overlay timing need runtime validation. | Pending source review | F6 |
| H-S1, H-NV1–NV8, H-SE1, H-PG1 | Lists/Search/Settings/Sidebars/Collections/Loading PDFs assigned to F3–F5; not yet source-checked | File list/search empty states, rail behavior, settings organization and progress semantics need an interactive audit. | Pending source review | F3–F5 |
| H-RV1–RV2 | Ratings and reviews/Rating indicators PDFs assigned to F6; not yet source-checked | Rating prompt is shown by fixture; engagement timing, dismissal and rating representation need checking. | Pending source review | F6 |

## F0 measured evidence

| Capture and hierarchy | Measured value | Interpretation |
|---|---|---|
| `after-v2/f0/02_home_shelf_focus.png` + `after-v2/f0/home-hierarchy.xml` | Focused `cardShelfItem` bounds `[308,759][714,988]` = 406×229 px. `tvHomeCatalogTitle` bounds `[308,1077][1836,1080]` = 3 px visible at bottom edge. | Historical first-fold view showed only the top of the next section; F3 confirms D-pad scrolling reveals the focused catalog tile. |
| `after-v2/f0/06_details_episode_focus.png` + `after-v2/f0/details-episode-hierarchy.xml` | Focused `cardEpisodeItem` bounds `[336,718][651,988]` = 315×270 px (157.5×135 dp); hierarchy confirms `focused=true`. | Confirms the previously mislabeled state now actually focuses episode 1; used as reproducibility evidence, not a claim that details layout conforms. |

## F0 rubric (0–10)

Ratings judge current screenshots and code evidence, not future quality. They are not averaged into a single score because that obscures critical TV navigation failures.

| Area | Score | Evidence |
|---|---:|---|
| Home visual hierarchy | 5.5 | Hero consumes most of the viewport; shelf begins close to the fold; multiple prominent button shapes and cropped lower row in provided capture. |
| Catalog/card system | 4.5 | Cards are dense and inconsistent; favorite stars dominate artwork; labels clip; category pills compete with titles. |
| Files and folder navigation | 5.0 | Breadcrumb/search affordances exist, but sparse content has weak empty/error hierarchy and the root session error is easy to confuse with a transient failure. |
| Media details | 4.5 | The supplied screenshot shows image bleed through the episode region, overly dense outlined actions and clipped episode titles/metadata. |
| Player behavior | 3.0 | User-reported Back behavior can strand full-screen playback; this is a functional blocker until D-pad and touch paths are reproduced and fixed. |
| Settings/dialogs | 5.0 | Large setting rows are readable, but screenshots show header/content cropping and dialog backdrop/shape consistency problems. |
| Focus and accessibility | 4.0 | Focus rings exist, yet card/action hit area, contrast, focus-vs-selected semantics and 10-foot movement are unverified or visibly inconsistent. |

## F0 checks and limitations

- The repository has 152 JVM unit tests in the current checkout; all 152 passed with `--rerun-tasks`. This is the observed suite count; older project notes reference 145 or 122 tests.
- `assembleDebug` passed before the fixture-only route adjustment. F0's final gate must rebuild after that adjustment.
- The user-provided `current-state/` screenshots remain byte-for-byte preserved. They are evidence supplied with the task; don't overwrite them with emulator output.
- OAuth/Drive login, expired-token behavior, network failure recovery, MPV/ExoPlayer key behavior, and subfolder traversal require runtime fixtures/accounts beyond the current visual fixture. Do not infer that these paths pass from screenshots or JVM tests.

