# P-12 — Pós-play com contagem

- **ID:** P-12
- **Status:** Implementado (`NextEpisodePolicy` + `PlaybackCoordinator` + `PlayerActivity`/`MPVActivity`)
- **Referências:** Netflix, Crunchyroll, add-on "Up Next" do Kodi

## Objetivo

Durante os créditos finais (ou últimos segundos do episódio), exibir o cartão do próximo episódio com contagem regressiva de 10 segundos, foco inicial no botão de reprodução imediata e cancelamento explícito ou por navegação.

## Implementação e Testes

- **`NextEpisodePolicy` (`ui/player/NextEpisodePolicy.kt`):** Calcula `evaluateCountdown(positionMs, durationMs, creditsStartMs, hasNextEpisode, autoPlayEnabled, dismissedForCurrentFile)` e expõe `remainingSeconds` (janela de 10 s).
- **`PlaybackCoordinator` (`ui/player/PlaybackCoordinator.kt`):** Determina transição ao fim do arquivo (`EndOfFileAction.AutoAdvance` vs `PromptStillWatching` vs `DismissOrStop`).
- **Testes unitários:** `NextEpisodePolicyTest` e `PlaybackCoordinatorTest`.
