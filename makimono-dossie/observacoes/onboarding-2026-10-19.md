# Onboarding references — 2026-10-19

Scope: session entry, preparation/loading, profile creation. Static observations only; no authentication, DRM, or playback bypass. No proprietary implementation incorporated in Makimono.

## Artifact identity
- Crunchyroll Android TV 3.75.0 (22365), package com.crunchyroll.crunchyroid. Pulled base APK from emulator-5554; SHA-256 901ecdaade4720c44763bf655409bcf69977a6b9ab203102f66738981378976e. REA evidence ev_fec0c7c9548c33e15a58317953108e89a3552f7901297afab4c5d73f133d4d3a.
- Netflix 9.88.0 build 5 74722, package com.netflix.mediaclient. Extracted base.apk from the existing APKMirror APKM in Downloads. SHA-256 5ea13d4c71f0032d0ce4dad3a6bb2da5ef085877a65549f9e28960554aca6183. This is the mobile variant, not a Netflix TV runtime observation. REA evidence ev_221e8cb1a0143d89dc78a643bd11f5bdf21adbd3b7c37526707d4194d6cb76c5. Signature provenance was not revalidated in this inspection.

## Findings
[IDEIA] Crunchyroll declares separate splash/startup Activities. StartupActivity uses a startup ViewModel and lifecycle-bound asynchronous work before its Compose rendering. Observed in class inventory ev_442a6734f76bc7d951b47e8934cac6fddf5ed1ec549b307beb686da03da658ea and method onCreate ev_f0b12250a9167ee32b97d2e4c03d79c83e0666a4b49ca707eb41997b60c3e447. This supports a dedicated initialization stage; it does not establish that all media is preloaded or prove visible ordering.

[IDEIA] Netflix exposes acquisition, loading, onboarding and profile-validation components. SignupNativeActivity.launchProfilesGate prevents duplicate login work, displays progress, checks session cookies and uses lifecycle-bound asynchronous validation before proceeding. Evidence ev_3344470244b6bdcdc455cfb8b5cef0aaea00825e87a55ecb8af3b7f8d8bddb8f; method ev_6bc9b7ad8b40a88a209c751308fcf666fb884f58ee3763a01fc0b0593d7f302b. Search for Onboarding returned specific loading and profile-onboarding components (ev_1a8ab7556609f72640a94c9a328c8ed25ce50ee51069643a9bdfbc0c9a583e80). No claim about Netflix TV QR UI follows from this mobile artifact.

[EXPRESSÃO] Decompiled method text stays only in local REA evidence JSON; none is used in application source.

## Makimono design decisions
- No local session: enter pairing directly, without the intro or automatic profiles.
- Newly paired: bounded preparation with the existing intro, initial metadata/art cache warming, remaining media in background.
- New installation: ask for a real name and optional art; persist only the profile explicitly saved by the user. The first profile becomes administrator and inherits the detected library root.
- Saved session: return to the existing profiles without a replayed intro. Existing names, art, libraries and downloaded files remain intact.
- Notification permission follows onboarding, when entering Home.

## Tool limitations
The connected REA MCP lacked REA_JADX_MCP_JAR. Used the installed Node 24 and the existing official jadx-headless-mcp 0.7.1 JAR via rea-agents 6.4.0 CLI, Java 21.0.7, one worker per operation. No tool installation or global MCP configuration changes.
A focused Crunchyroll login class search failed because the provider returned a duplicated/inconsistent class inventory. No repeated inventory was attempted; manifest-declared exact startup classes were inspected instead.
Context7 was not exposed as a callable MCP and its documentation endpoints were unavailable. Existing library APIs were reused and checked against official Glide v4 and Android Room migration documentation; compilation and migration checks validate configured Glide 4.13.2, Room 2.6.1 and Media3 1.4.1 integration.
