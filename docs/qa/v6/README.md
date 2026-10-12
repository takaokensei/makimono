# Plano de Execução v6 — Makimono (Concluído)

Este diretório reúne todas as auditorias, evidências e especificações produzidas durante a execução completa do plano v6 (derivado da auditoria base `ca26827`).

## Índice de Entregas por Fase

| Fase | Tarefas | Documentação / Código Principal | Status |
|---|---|---|---|
| **Fase 0 — Higiene e Verdade** | `T-001` a `T-008` | `README.md`, `tools/checks/check_readme_claims.py`, `docs/qa/README.md`, `docs/releases/POLICY.md`, `docs/proposals/README.md` | Concluída |
| **Fase 1.1 — Callbacks com Vida Útil** | `T-101` a `T-112` | `utils/LifecycleDelays.kt`, `LifecycleDelaysTest.kt` (12 telas/diálogos com cancelamento em `ON_DESTROY` / `dismiss`) | Concluída |
| **Fase 1.2 — Erro Visível** | `T-120` a `T-126` | `docs/qa/v6/catches.md` (33 blocos `catch` catalogados), `ui/common/UiError.kt`, `UiErrorTest.kt`, `StateView.kt`, `PlayerActivity.kt`, `MPVActivity.kt` | Concluída |
| **Fase 2 — Foco e Navegação TV** | `T-201` a `T-208` | `docs/qa/v6/foco-inicial.md` (19 telas + 18 diálogos), `QueueFragment.kt`, `MalAuthDialog.kt`, `PlaybackSettingsDelegate.kt`, `HomeActionHandler.kt` | Concluída |
| **Fase 3 — Núcleo de Reprodução Único** | `T-300` a `T-310` | `ui/player/BasePlayerActivity.kt`, `domain/player/PlaybackController.kt`, `NextEpisodePolicy.kt`, `TrackPreferencePolicy.kt`, `EngineSelector.kt`, `docs/qa/v6/cobertura.md` | Concluída |
| **Fase 4 — Experiência de Assistir** | `P-10` a `P-18` | `docs/proposals/P-10-continuar-tempo-restante.md` .. `P-18-previa-destaque-home.md`, `PrimaryEpisodeSelection.kt`, `ChapterMarkersPolicy.kt`, `MpvTechnicalInfoDisplay.kt` | Concluída |
| **Fase 5 — Biblioteca** | `T-501` a `T-507` | `domain/library/LibraryCatalogPolicy.kt`, `HomeShelvesBuilder.kt`, `SeriesDetailViewModel.kt`, `TvWatchNextManager.kt`, `docs/proposals/D-50-sincronizacao-progresso.md` | Concluída |
| **Fase 6 — Movimento (Apple HIG)** | `T-601` a `T-605` | `utils/MotionPolicy.kt`, `FocusEngine.kt`, `MotionPolicyTest.kt`, `docs/qa/v6/motion.md` (21 `withEndAction` auditados) | Concluída |
| **Fase 7 — Texto e Acessibilidade** | `T-701` a `T-724` | `tools/checks/check_kotlin_literals.py`, `EpisodeCardAccessibilityLabel.kt`, `tools/checks/check_contrast.py`, `docs/qa/v6/acessibilidade.md` | Concluída |
| **Fase 8 — Desempenho** | `T-801` a `T-806` | `app/src/main/baseline-prof.txt`, `MediaImageLoader.kt`, `ThisApp.kt` (`StrictMode`), redução de overdraw em 7 layouts, `docs/qa/v6/perf.md` | Concluída |
| **Fase 9 — Build e Qualidade Contínua** | `T-901` a `T-906` | `.editorconfig`, `config/ktlint-baseline.json`, `tools/checks/check_kotlin_style.py`, `tools/checks/check_immutable_tag.py`, `EncryptedSessionStoreTest.kt`, `docs/qa/v6/security-crypto.md` | Concluída |
