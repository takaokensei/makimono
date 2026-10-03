# Makimono TV Design System — Obsidian

This document records the shared presentation contract introduced in F1. XML resources are the source of truth; existing Tokio Night, Dracula, Nord, Catppuccin Mocha, and Kodi Estuary preferences remain valid and Obsidian is added as the new default for profiles without a saved preference.

## Foundations

- Spacing follows a 4dp scale: 4, 8, 12, 16, 24, 32, and 48dp. TV safe-content margins are 48dp horizontal and 30dp vertical across landscape qualifiers.
- Inter is the bundled family. TV text styles live in `res/values/typography.xml` and retain Android font scaling.
- Motion durations are semantic: instant 100ms, fast 150ms, normal 220ms, gentle 300ms, slow 450ms. Legacy resource IDs remain available with shorter TV timings.
- Cards use continuous-corner outlines where Android supports path outlines; older devices fall back to native round-rect clipping. Shared radii are 16dp cards, 28dp dialogs, and 32dp player HUD.
- Elevation tokens define 2dp resting cards, 12dp focused surfaces, and 24dp overlays. A focus ring remains necessary even when elevation is present.

## Semantic color roles

Obsidian uses `#070B11` base, `#111925` elevated surface, `#F5F7FA` primary text, `#B0B8C4` secondary text, and cyan `#22D3EE` for primary actions and active progress. `#7C8696` tertiary text is reserved for unavailable or disabled information. Borders and focus fills are subtle white overlays; they do not use the brand accent at rest. Dialog and media scrims are dedicated opaque/alpha resources.

Each selectable theme defines the same roles in `res/values/colors.xml` and binds them through theme attributes. The Estuary Kotlin preview metadata now matches the XML palette. Legacy aliases resolve to Obsidian's default tokens. Existing layout literals were centralized as `layout_literal_*` resources to remove inline hex; these compatibility resources are migration staging, and screen phases should replace them with `?attr` roles so saved palettes remain consistent.

## Contrast

Run `python tools/check_contrast.py` and `python tools/check_layout_colors.py` from the repository root. The contrast checker measures primary and secondary text against base/elevated surfaces, plus text on every theme's primary action color. Primary and secondary text target at least 7:1; primary-action text targets at least 4.5:1. Tertiary is restricted to disabled/unavailable states and is intentionally excluded. The layout checker prevents new inline hex values. Focus is conveyed by a paired treatment (outline/fill plus scale/elevation), never color alone.

## Component guidance

- Backdrop art is full-bleed and receives dedicated start/bottom scrims; content surfaces do not introduce an unrelated second background.
- Glass is a dark translucent tint without a border by default. A thin hairline is for separation only; avoid stacking multiple outlines.
- Player controls preserve functional contrast over moving video, use the player-specific opaque glass treatment, and keep the feature hierarchy documented in the player phase.
- Focus animation changes transforms/alpha/elevation, not layout measurements; motion preferences must avoid bounce and large zoom.
- Scrollable rows leave room for focus lift/scale and must not clip their focused children.
