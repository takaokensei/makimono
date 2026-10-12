# Relatório de Cobertura e Núcleo de Reprodução (Fase 3 — T-300 a T-310)

## 1. Resumo Arquitetural

A Fase 3 unificou as responsabilidades críticas de reprodução em classes puras e testáveis no pacote `zechs.drive.stream.playback` e na classe base `BasePlayerActivity`, reduzindo a duplicação entre `PlayerActivity` (Media3/ExoPlayer) e `MPVActivity` (libmpv).

| Componente | Arquivo | Papel | Suíte de Teste |
|---|---|---|---|
| `BasePlayerActivity` | `ui/player/BasePlayerActivity.kt` | Encapsula as 6 funções idênticas de Q-001 (`dispatchTouchEvent`, `hideSystemUI`, `isControlsShowing`, `onUserInteraction`, `onConfigurationChanged`, `showEpisodesDrawer`) e a persistência de progresso (`saveProgress`) | Validado via compilação e políticas subjacentes |
| `PlaybackController` | `playback/session/PlaybackController.kt` | Orquestrador sem dependência de View para motor (`PlayerEngine`), progresso, pulo de abertura/créditos, pós-play, "Ainda está assistindo?", OSD e liberação única (`release()`) | `PlaybackControllerTest` (6 testes) |
| `PlayerEngine` | `playback/engine/PlayerEngine.kt` | Contrato unificado entre Media3 e MPV (`load`, `play`, `pause`, `seekTo`, `fastForward`, `rewind`, `setSpeed`, `release`) | `PlayerEngineContractTest` (`Media3EngineContractTest` + `MpvEngineContractTest`, 16 execuções) |
| `NextEpisodePolicy` | `playback/session/NextEpisodePolicy.kt` | Cálculo de contagem regressiva pós-play (P-12), gatilho "Ainda está assistindo?" após 3 episódios sem interação (P-13) e visibilidade dos controles de episódio (T-304) | `NextEpisodePolicyTest` (6 testes) |
| `TrackPreferencePolicy` | `playback/session/TrackPreferencePolicy.kt` | Seleção automática de áudio (japonês/preferência da série) e legenda (português/preferência da série), filtragem de legendas externas e limites de atraso de sincronia (T-306, P-14, P-15) | `TrackPreferencePolicyTest` (6 testes) |
| `EngineSelector` | `playback/engine/EngineSelector.kt` | Tabela declarativa de 7 regras (`EngineRoutingRule`) inspirada no `PlayerCoreFactory` do Kodi (ASS/SSA → MPV, PGS → MPV, codecs sem HW → MPV, HDR → Media3, Low-RAM → Media3) (T-309) | `EngineSelectorTest` (12 testes) |
| `SkipController` | `playback/session/SkipController.kt` | Mesclagem de capítulos MKV + AniSkip, auto-skip com janela de graça e pulo manual/+90s (T-305, P-11) | `SkipControllerTest` (14 testes) |
| `ProgressRecorder` | `playback/session/ProgressRecorder.kt` | Persistência de progresso e atrasos de áudio/legenda por perfil (T-303, P-15) | `ProgressRecorderTest` (9 testes) |
| `OsdStateMachine` | `playback/osd/OsdStateMachine.kt` | Máquina de estados determinística e reversível (`HIDDEN`, `SHOWING`, `SHOWN`, `HIDING`) (T-307) | `OsdStateMachineTest` (10 testes) |

---

## 2. Métricas de Código e Duplicação (T-310)

| Métrica | Antes (Auditoria `ca26827`) | Depois (Fase 3) | Observação |
|---|---|---|---|
| Funções homônimas com corpo idêntico entre players | 6 | **0** | Extraídas para `BasePlayerActivity` (T-301) |
| Função `saveProgress()` duplicada nas duas Activities | 2 cópias independentes | **0** (centralizada em `BasePlayerActivity` + `PlaybackController`) | Eliminada das subclasses (T-303) |
| Funções privadas homônimas (não-`override`) entre players | 58 | **30** | Redução de 48% nas funções privadas homônimas |
| Linhas em `PlayerActivity.kt` | 3 373 | **3 329** | Redução líquida mesmo após adição do diálogo de falha (T-125) |
| Linhas em `MPVActivity.kt` | 2 842 | **2 889** | Inclui diálogo de erro e chaveamento para ExoPlayer (T-125) |
| Anotações `@Test` totais (`app/src/test`) | 519 | **555** (+36 novos testes unitários) | 100% passando |
| Execuções JUnit (`testDebugUnitTest`) | 526 | **563** (+37 execuções) | 0 falhas, 0 erros, 0 ignorados |

---

## 3. Evidência de Execução JUnit (`testDebugUnitTest`)

Comando executado:
```powershell
.\gradlew.bat testDebugUnitTest --offline -g C:\temp\gradle_home
python tools/checks/count_tests.py
```

Saída verificada:
```text
Anotações @Test no código-fonte: 555
Execuções de teste (XML JUnit):   563
Falhas: 0, Erros: 0, Ignorados: 0

Explicação da diferença:
A herança de classes de teste (BasePlayerEngineContractTest com 8 métodos @Test herdados
e executados independentemente por Media3EngineContractTest e MpvEngineContractTest) adiciona
+8 execuções em tempo de execução ao total reportado no JUnit.
```
