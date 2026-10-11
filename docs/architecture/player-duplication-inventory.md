# Inventário de Duplicação e Decisões de Arquitetura: PlayerActivity vs MPVActivity

---

## 1. Contexto e Problema

O Makimono mantém dois fluxos de reprodução de vídeo paralelos em atividades separadas:
1. `PlayerActivity.kt` (~3286 linhas): Baseado em Google Media3 / ExoPlayer.
2. `MPVActivity.kt` (~2794 linhas): Baseado em libmpv nativo via JNI.

Ambas as atividades desempenham funções análogas de controle de reprodução, gestão do ciclo de vida, persistência de progresso, renderização de overlays OSD, resolução de capítulos Matroska e sincronização de estado na experiência Android TV 10-foot.

---

## 2. Mapa Comparativo de Responsabilidades

| Subsistema / Recurso | Implementação no ExoPlayer (`PlayerActivity`) | Implementação no MPV (`MPVActivity`) | Grau de Duplicação |
|---|---|---|---|
| **OSD Controller & State Machine** | Integra `OsdStateMachine` e `PillVisibilityPolicy` via callbacks | Replica a mesma máquina de estados e listeners | **Alto (~90%)** |
| **Parsing de Capítulos MKV** | `MatroskaChapterParser.parse` | MPV nativo expõe capítulos via `MPVLib`, mas parsing local duplicado | **Médio (~60%)** |
| **Gerenciamento de HDR/Color Mode** | `HdrModeManager.applyHdrMode` | `HdrModeManager.applyHdrMode` | **Alto (~95%)** |
| **Auto Frame Rate (AFR)** | `AutoFrameRatePolicy.applyAutoFrameRate` | `AutoFrameRatePolicy.applyAutoFrameRate` | **Alto (~95%)** |
| **Sincronização com MyAnimeList / AniSkip** | `AniSkipRepository`, `MalRepository` chamados inline | Métodos equivalentes implementados inline | **Alto (~85%)** |
| **Watch Next do Android TV Leanback** | `TvWatchNextManager.updateProgress` | `TvWatchNextManager.updateProgress` | **Alto (~90%)** |
| **Persistência de Resumo e Histórico** | Gravação no Room (`WatchListRepository`) | Gravação no Room (`WatchListRepository`) | **Alto (~85%)** |
| **Renderização de Legendas Customizadas** | `SubtitleView` com `SubtitleAppearance` / ASS-to-SRT | Configuração via propriedades libmpv (`sub-text-color`, etc.) | **Baixo (~30%)** (específico do motor) |

---

## 3. Riscos de Divergência Identificados

1. **Inconsistência de UX no OSD:** Qualquer ajuste em atalhos de D-pad, timing de auto-hide ou animações em um player precisa ser replicado manualmente no outro.
2. **Atualização de Progresso ASS/SRT:** Lógicas de compensação de delay de legenda divergem entre ExoPlayer e MPV.
3. **Complexidade de Testes:** Requer duplicar testes instrumentados para ambas as Activities, consumindo tempo de execução em emulador/hardware.

---

## 4. Plano Recomendado de Unificação Sob `PlayerEngine`

Conforme formalizado na proposta P-01 e no ADR-002:
1. Manter uma única `PlayerActivity` contendo todo o layout OSD, controllers de foco, bindings de TV, gerenciamento de ciclo de vida e navegação.
2. Isolar o motor de decodificação sob a interface de domínio `PlayerEngine`:
   - `ExoPlayerEngine : PlayerEngine`
   - `MpvPlayerEngine : PlayerEngine`
3. A Activity apenas observa o `PlayerEngine` e despacha comandos abstratos (`play`, `pause`, `seek`, `selectTrack`, `setSpeed`), eliminando ~2.200 linhas de código duplicado.
