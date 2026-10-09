# Q18.1 — seleção de faixas e propriedade modal do D-pad (05/10/2026)

**Estado do subincremento:** RUNTIME_VALIDATED com fixture local nos dois engines; Q18 segue aberto.

Ao testar um MKV de 18 s com duas faixas AAC nomeadas (Japanese Original/jpn e Português BR/por) e uma legenda SRT pt-BR, encontrei que o `dispatchKeyEvent` dos players consumia BACK e direções antes de o diálogo modal recebê-los. No MPV, o primeiro DOWN abria uma notificação de alteração de legenda por trás do seletor; no Moto, o seletor também abria sem foco inicial quando acionado por toque, pois o dispositivo permanecia em touch mode.

A política `PlayerModalVisibilityState` agora expõe `isVisible`; ExoPlayer e MPV delegam os eventos ao Android enquanto um modal está aberto. O seletor solicita foco pelo caminho touch-aware (`requestFocusFromTouch`) caso `requestFocus` não consiga focar o item inicial. Assim, o modal mantém os eventos, a lista começa na faixa atual, DOWN move o anel para a próxima faixa, OK aplica a escolha e BACK fecha apenas o diálogo.

**Evidência:** `exo-audio-initial-focus.png`, `exo-audio-dpad-down.png`, `exo-audio-selected-ptbr.png`, `mpv-audio-initial.png`, `mpv-audio-dpad-down.png`, `mpv-audio-selected-ptbr.png` e `mpv-back-dismissed-dialog.png`, nesta pasta. Em ambos os engines o diálogo voltou a abrir com Português selecionado após a troca. `uiautomator` confirmou itens clicáveis/focáveis no Exo; no MPV, screenshot mostra o foco ciano indo do áudio atual a Português. BACK manteve a Activity de player em primeiro plano.

**Verificação:** `testDebugUnitTest assembleDebug` e a execução separada de `testDebugUnitTest` passaram; relatório agregado: 200 testes JVM, 0 falhas/erros/skips. Instalei somente `zechs.drive.stream.visualreview` no Moto G34 conectado (`0086442783`), sem tocar no app principal. O arquivo de revisão permaneceu na sandbox privada do pacote. `git diff --check` passou.

**Limites:** arquivo sintético local, um formato de contêiner e aparelho Android móvel; ainda falta medir D-pad com controle remoto e foco a 10 pés numa TV física. Legenda foi listada como SRT no fixture, mas esta rodada focou troca de áudio; ASS/SSA, legenda externa/Drive, idiomas indisponíveis, offsets e política de preferência por perfil seguem pendentes conforme Q18. Sem alteração de release, commit ou push.
