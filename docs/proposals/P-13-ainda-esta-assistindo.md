# P-13 — "Ainda está assistindo?"

- **ID:** P-13
- **Status:** Implementado (`NextEpisodePolicy` + `PlaybackController` + `PlaybackCoordinator`)
- **Referências:** Netflix

## Objetivo

Evitar reprodução contínua infinita sem supervisão do usuário (ex.: usuário dormiu com a TV ligada). Após 3 episódios consecutivos avançados automaticamente sem interação humana (`onUserInteraction()`), pausar a transição automática e perguntar se o usuário continua assistindo.

## Implementação e Testes

- **`NextEpisodePolicy.shouldPromptStillWatching(consecutiveUnattendedEpisodes, threshold = 3)`:** Retorna `true` ao atingir 3 episódios consecutivos sem interação.
- **`PlaybackController`:** Mantém `consecutiveAutoPlayedEpisodes`, incrementa em `onAutoPlayNextEpisode()` e zera em `onUserInteraction()`.
- **Testes unitários:** `NextEpisodePolicyTest` e `PlaybackControllerTest` (`still watching prompt triggers after 3 consecutive unattended auto-plays and resets on interaction`).
