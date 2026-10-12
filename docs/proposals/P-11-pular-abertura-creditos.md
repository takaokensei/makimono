# P-11 — Pular abertura, recapitulação e créditos

- **ID:** P-11
- **Status:** Implementado (`SkipController` + `PlaybackController` + `PillVisibilityPolicy`)
- **Referências:** Netflix, Crunchyroll

## Objetivo

Exibir a pílula de pulo ("Pular Abertura", "Pular Recap", "Pular Encerramento") quando a posição atual entra no intervalo detectado via AniSkip ou capítulos MKV, transferindo foco sem deslocar o OSD principal e ocultando automaticamente após janela de exposição caso não haja interação.

## Implementação e Testes

- **`SkipController` (`ui/player/SkipController.kt`):** Avalia capítulos MKV e intervalos AniSkip em ambos os motores (`PlayerActivity` e `MPVActivity`), respeitando `suppressedUntilMs` após pulo manual.
- **`PillVisibilityPolicy` (`ui/player/PillVisibilityPolicy.kt`):** Garante cancelamento de animação no caminho oposto e devolução de foco previsível em TV.
- **Testes unitários:** `SkipControllerTest` e `PlaybackControllerTest`.
