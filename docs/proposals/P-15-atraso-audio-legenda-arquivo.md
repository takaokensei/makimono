# P-15 — Atraso de áudio e legenda por arquivo

- **ID:** P-15
- **Status:** Implementado (`TrackPreferencePolicy` + `PlaybackPreferences`)
- **Referências:** Kodi

## Objetivo

Permitir ajuste fino de atraso de áudio e de legenda em passos de 50 ms / 100 ms (limitado a `±30 000 ms`) e persistir o atraso por arquivo (`fileId`) para que seja reaplicado ao reabrir o mesmo vídeo.

## Implementação e Testes

- **`TrackPreferencePolicy.clampDelayMs(delayMs)` e `formatDelayOffset(delayMs)`:** Limita o atraso ao intervalo seguro `[-30_000, +30_000]` ms e formata o deslocamento em segundos (`+0.25s`, `-0.50s`).
- **`PlaybackPreferences`:** Persiste os atrasos ajustados no escopo do arquivo/preferências do player.
- **Testes unitários:** `TrackPreferencePolicyTest` (`clampDelayMs and formatDelayOffset handle bounds and signs`).
