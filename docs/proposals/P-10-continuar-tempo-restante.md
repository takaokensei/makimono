# P-10 — Continuar com tempo restante

- **ID:** P-10
- **Status:** Implementado (`PrimaryEpisodeSelection` + `SeriesDetailViewModel`)
- **Referências:** Netflix, Apple TV app ("Up Next")

## Objetivo

Exibir no botão primário da tela de Detalhe (`SeriesDetailFragment`) e no subtítulo da ação principal o episódio em andamento e o tempo restante calculado a partir de `watchedDuration` e `totalDuration` (`"Continuar E5 · faltam 12 min"` / `"Ep. 05 • 12:00 restante"`), mantendo a primeira prateleira da Home como `"Continuar assistindo"`.

## Implementação e Testes

- **`PrimaryEpisodeSelection.formatContinueCta(item, isMovie)`:** Formata o rótulo curto com episódio e minutos restantes quando o progresso está entre `1%` e `94%`.
- **`PrimaryEpisodeSelection.formatContinueSubtitle(item, isMovie)`:** Centraliza o subtítulo com `MM:SS restante` usado por `SeriesDetailViewModel`.
- **Testes unitários:** `PrimaryEpisodeSelectionTest` (`formatContinueCta shows remaining minutes for in-progress episode`, `formatContinueCta shows start label for unwatched episode and movie`, `formatContinueSubtitle formats remaining time MM SS for series and movies`).
