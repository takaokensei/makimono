<USER_REQUEST>
/goal # Makimono: plano v4

A v4 é a v3 mais duas coisas: o bug do OSD do player que fecha duas vezes (Fase I, item I-1) e um padrão de movimento e interação para aproximar o app do nível Apple (Fase I, itens I-2 a I-8). O restante da v3 foi mantido.

Projeto privado, de uso pessoal. Este plano parte do estado verificado no remoto, não dos relatórios de fase. Onde um número ainda não foi medido, o plano manda medir antes de afirmar.

---

## 0. Estado de partida (verificado no remoto)

### 0.1 Git e releases
| Item | Estado |
|---|---|
| `origin/main` | `b0c4b20`. Ainda contém `docs/ui-audit/` (1479 arquivos, 527 MB) |
| `origin/fase-7-plataforma-revisao` | HEAD `4c872ed`. 46 commits à frente da `main`. `main` é ancestral, então o merge pode ser fast-forward |
| Branches `fase-0` a `fase-7` | Existem no remoto. As fases 5, 6 e 7 não estão na `main` |
| Tag `v1.6.5.0` | Aponta para `e95953a`, que é o HEAD antigo da `fase-7`. Não inclui o commit `4c872ed` |
| Release `v1.6.5.0` | Publicada, com 4 APKs de ABI, o universal e `.sha256` |
| Release `v1.6.4.9` | Publicada |
| Tag `v1.6.4.9` | Segundo o relatório do agente, foi recriada com `--force` após um primeiro push. Não verifiquei isso no remoto. Se for verdade, registrar como incidente de processo |
| Ramos remotos sem merge na `main` | 24, incluindo `dependabot/*`, `claude/design-system-proposal`, `fix/*`, `ui/pro-polish` |

### 0.2 Código (medido na `fase-7`)
| Métrica | Valor | Observação |
|---|---|---|
| `PlayerActivity.kt` | 3347 linhas | Era 3381 em `b0c4b20` e 3376 em `460ac1b`. Redução de 34 e 29 linhas |
| `MPVActivity.kt` | 2835 linhas | Era 2879 em `b0c4b20` e 2878 em `460ac1b`. Redução de 44 e 43 linhas |
| `HomeFragment.kt` | 551 linhas | Confirmado (era 1840 em `460ac1b`) |
| `SettingsFragment.kt` | 487 linhas | Confirmado (era 1100 em `460ac1b`) |
| `FilesFragment.kt` | 525 linhas | Confirmado (era 865 em `460ac1b`) |
| Arquivos com `UseCase` | 4 | Eram 0 antes do trabalho de domínio |
| `TvLoginServer.kt` | Removido | Confirmado |
| `backend/` | 14 arquivos, ainda presentes | Decisão D3 pendente |
| Declarações em `themes.xml` | 16 | Decisão D2 pendente |
| `layout_literal_*` em `res` e Java | 0 | Confirmado |
| `android.util.Log` fora de `AppLog.kt` | 0 | Confirmado |
| `catch (_: Exception) {}` | 3 | Confirmado |
| Paridade de `strings.xml` (pt/en) | 390 chaves em cada | Confirmado. A explicação de "387 + 3" no relatório não bate |
| Anotações `@Test` | 474 | O relatório diz 480 e 481 execuções. Sem log, não confirmado |
| `lint-baseline.xml` | 940 issues | Confirmado. Era 1038 |
| `TvWatchNextManager.kt` | Presente | `androidx.tvprovider:1.0.0`, esquema `makimono://` no manifesto |
| `DomainRulesArchitectureTest.kt` | Presente | ArchUnit 1.3.0 |
| Macrobenchmark ou Baseline Profile | Ausentes | Confirmado na `fase-7` |

### 0.3 Evidência que ainda falta
| Lacuna | Consequência |
|---|---|
| Nenhum teste em aparelho real | Foco, D-pad, Watch Next no launcher e reprodução não estão validados |
| Nenhuma medição de desempenho | Qualquer nota de desempenho é estimativa |
| Log de execução de testes e lint anexado | Contagens citadas em relatórios não são prova |
| Status do CI não verificável por mim | O token atual retorna 403 em Actions. "Lint success" e "TruffleHog success" são afirmações |
| `docs/ui-audit` ainda na `main` | Dívida de repositório. A `fase-7` já não tem a pasta |
| Relatórios de fase divergentes entre si | Números mudam de um relatório para outro (474, 480, 481; 387 e 390) |
| OSD do player parece fechar duas vezes | Relato do dono. Causa não confirmada. O código tem dois mecanismos de esconder o controlador e nenhuma guarda de reentrada (Fase I, item I-1) |
| Durações de animação literais no código | Observadas: 150, 200, 220 e 300 ms espalhadas em `PlayerActivity`, `MPVActivity` e `PlayerGestureHelper`, apesar de existir `motion_spec.xml` |

### 0.4 Nota de partida
Média de **5,5/10**, na régua usada desde a auditoria anterior. A nota de Desempenho passou de 6,0 para 5,5, porque a de 6,0 não tinha medição por trás. Área por área, a nota só sobe com evidência nova (seção 10).

---

## 1. Princípios

1. **Evidência antes de afirmação.** Um número só entra num relatório com o comando e a saída anexados.
2. **Nada entra na `main` sem pull request.** Inclusive o que já está na `fase-7`. O merge é do dono, depois de revisar o PR.
3. **Tag não é reescrita.** Uma tag publicada é imutável. Correção vira nova versão.
4. **Decisão de produto é do dono.** O agente recomenda e registra. Não escolhe motor, temas, backend nem visual sozinho.
5. **Aparelho é gate.** Fase de TV só fecha com teste feito em TV ou emulador de Android TV, com captura.
6. **Medir antes de otimizar.** Sem linha de base, nenhuma meta de desempenho é definida.
7. **Remover antes de adicionar.** Cada fase começa com a remoção do que não é usado.

---

## 2. Fase A: Integrar e fechar a rodada

**Objetivo:** a `main` passa a representar o que está publicado, e a dívida de histórico fica registrada.

**Tarefas**
1. Abrir PR de `fase-7-plataforma-revisao` para `main`. Descrição com:
   - lista dos 46 commits;
   - saída de `git diff --stat origin/main..origin/fase-7-plataforma-revisao`;
   - nota de que o PR remove `docs/ui-audit/` da árvore (ver Fase G).
2. Anexar ao PR o log de `./gradlew testDebugUnitTest lintDebug assembleDebug` executado do zero, com a contagem de testes lida dos XML em `app/build/test-results/`.
3. Revisar o diff de `app/build.gradle` e do `CHANGELOG.md` para garantir que `versionCode 62` e `1.6.5.0` batem com a tag.
4. Merge após aprovação do dono, por merge commit ou squash, a critério do dono. Sem force-push.
5. Registrar o commit `4c872ed` (recalibração da rubrica) na `main`, pois a tag `v1.6.5.0` não o inclui.
6. Não recriar nem mover tags já publicadas. A correção do incidente de `v1.6.4.9` vai para o `docs/releases/` como nota de processo.

**Critérios de saída**
- `origin/main` contém os 46 commits, ou o dono decidiu explicitamente não integrar.
- PR com log de build anexado e aprovado pelo dono.
- Nenhuma tag reescrita a partir daqui.

---

## 3. Fase B: Evidência de build e CI

**Objetivo:** todo número citado tem uma origem reproduzível.

**Tarefas**
1. Reconciliar as contagens de teste. Gerar, a partir dos XML, duas métricas separadas: anotações `@Test` e execuções (`tests=` somado). Publicar as duas no `README` via script, não à mão.
2. Documentar a diferença entre execuções e anotações. Explicar o que gera execuções extras (ArchUnit e testes parametrizados).
3. Verificar o status do CI com um token que tenha permissão de leitura de Actions. Se o token atual não tiver, rotacionar o PAT e criar um com escopo mínimo: leitura de conteúdo e de Actions, apenas neste repositório.
4. Anexar o link de um run verde de `ci.yml` e de `release.yml` para o commit do tag `v1.6.5.0`.
5. Documentar as categorias restantes do `lint-baseline.xml` (940). Atacar por categoria nas fases seguintes.

**Critérios de saída**
- README com duas contagens geradas, e não escritas à mão.
- Link de run verde do CI para o commit de release.
- Tabela de categorias do lint com contagem por categoria.

---

## 3A. Fase I: bug do OSD e movimento nível Apple

**Objetivo:** corrigir o fechamento duplicado do OSD do player e levar toda a animação do app a um padrão único: breve, interrompível, opcional e consistente.

**Ordem:** o item I-1 pode começar antes da Fase C. Para confirmar a causa e para fechar o critério de aceite é preciso gravar a tela em um aparelho ou emulador.

**O que "nível Apple" significa aqui.** Não é um estado certificável. O plano usa quatro substitutos que dá para medir: consistência (a mesma função tem o mesmo movimento em todas as telas), contenção (sem animação sem propósito), precisão (sem jank, sem salto, sem flash) e acessibilidade (movimento opcional). A base é o texto do HIG sobre movimento: adicionar movimento com propósito, torná-lo opcional, preferir feedback breve e preciso, evitar movimento em interações muito frequentes e não obrigar a pessoa a esperar a animação terminar. Li esse texto em espelhos da página `developer.apple.com/design/human-interface-guidelines/motion`. Confirmar na página oficial antes de usar como norma.

### I-1. OSD do player fecha duas vezes

**Sintoma relatado:** ao fechar a interface do player (título, barra de controles e escurecimento), a animação parece acontecer duas vezes. Eu não reproduzi o sintoma em aparelho. O que segue é leitura do código.

**Evidência no código** (branch `fase-7`, commit `4c872ed`) [F]:

1. **Dois mecanismos esconderam o mesmo controlador no ExoPlayer.** A `PlayerView` está configurada com `controllerShowTimeoutMs = 4500` e `controllerHideOnTouch = true` (`PlayerActivity.kt:1654-1658`), então ela esconde o controlador sozinha por inatividade e ao toque. Em paralelo, o código próprio `animateHideController()` (`PlayerActivity.kt:1429`) é chamado por gesto (`:544`), por tecla ou Voltar (`:1382`, `:1418`) e por modais (`:1418`). O caminho nativo não passa pela animação própria e não marca `isHidingControls`.
2. **Um toque pode acionar os dois.** `PlayerGestureHelper.onSingleTapConfirmed` chama `onToggleControls` (`PlayerGestureHelper.kt:109-112`). Se o `PlayerView` também tratar o mesmo toque por `controllerHideOnTouch`, são dois pedidos para esconder. Não sei se o helper consome o evento antes do `PlayerView`. Isso decide a hipótese e só se confirma em aparelho.
3. **Não há guarda de reentrada.** `animateHideController` marca `isHidingControls = true` mas nunca retorna cedo se já estava `true`. No MPV, `hideControls()` só retorna se `!controller.root.isVisible` (`MPVActivity.kt:1013`), e a view continua visível durante todo o fade. `hideControls()` tem pelo menos cinco chamadores (`MPVActivity.kt:516, 567, 652, 928, 1078`). Uma segunda chamada cancela os animadores e recomeça 150 ms a partir do valor atual. O resultado é um fade mais longo ou um segundo "fechamento".
4. **Cinco animadores independentes, com ação final em um só.** Título, barra de controles, controles principais, faixa de pular abertura e escurecimento animam separados. Só o escurecimento tem `withEndAction`, que restaura alpha e deslocamento (`PlayerActivity.kt:1464-1479`, `MPVActivity.kt:1045-1059`). Pela documentação do Android, a ação final só roda se a animação termina normalmente. Se o escurecimento for cancelado por uma segunda chamada, o reset dos outros pode não rodar ou rodar fora de ordem (confirmar em aparelho).
5. **A lógica está duplicada em dois arquivos e já divergiu.** Mostrar e esconder existem em `PlayerActivity.kt:1429-1525` e em `MPVActivity.kt:944-1065`. O ExoPlayer chama `playerView.hideController()`, o MPV usa `controller.root.isVisible = false`. Só o ExoPlayer tem o `ControllerVisibilityListener` (`PlayerActivity.kt:1087`), que anima o card do próximo episódio e chama `handleLockingControls()`. No MPV, o card é animado dentro de `hideControls()` (`MPVActivity.kt:1061`). O mesmo elemento tem donos diferentes em cada motor.
6. **A animação interna da `PlayerControlView` provavelmente pesa pouco.** O layout customizado só declara cinco ids `exo_*`: `exo_ffwd`, `exo_rew`, `exo_duration`, `exo_position` e `exo_progress`. A versão do Media3 é a 1.4.1 (`app/build.gradle:186`). Se a animação interna do Media3 mexer em views que o layout não tem, o efeito é pequeno. Tratar como hipótese de baixa probabilidade e verificar se a 1.4.1 permite desligá-la.

**Hipóteses, em ordem de probabilidade (estimativa minha):**

| # | Hipótese | Evidência | Como confirmar | Probabilidade |
|---|---|---|---|---|
| H1 | Pedido duplicado por gatilho: nativo e próprio atuam no mesmo toque ou no mesmo timeout | Itens 1 e 2 | Log de debug: contar pedidos de esconder por ação | Alta (ExoPlayer) |
| H2 | Reentrada: segunda chamada reinicia o fade | Item 3 | Log + gravação: a queda de alpha recomeça no meio | Alta (os dois motores) |
| H3 | Cancelamento deixa o reset de alpha incompleto | Item 4 | Gravação de tela e `adb shell dumpsys gfxinfo` | Média |
| H4 | Dois donos para a animação do card | Item 5 | Log da animação do card ao esconder | Média |
| H5 | Animação interna do Media3 sobreposta | Item 6 | Desligar a animação interna e comparar | Baixa |
| H6 | Percepção: fade de 150 ms mais deslocamento de 8 dp mais escurecimento separado parecem duas etapas | Código mostra animações paralelas, não encadeadas | Se há 1 pedido e 1 queda de alpha, ajustar movimento (I-2) | Baixa a média |

**Diagnóstico (fazer antes de corrigir):**

1. **Instrumentação só de debug.** Em `animateHideController`, `animateShowController`, `hideControls`, `showControlsWithFocus`, `playerView.hideController()` e `playerView.showController()`, registrar com `AppLog.d` (atrás de `BuildConfig.DEBUG`): gatilho, estado atual, instante e uma pilha curta (`Throwable().stackTrace`, três quadros). Remover depois da correção ou manter atrás de flag.
2. **Matriz de gatilhos**, nos dois motores:
   1. tecla OK ou D-pad com o OSD visível;
   2. Voltar com o OSD visível;
   3. toque simples (celular);
   4. inatividade por 4,5 s;
   5. abrir e fechar o menu de áudio ou de legenda (`onPlayerModalVisibilityChanged`);
   6. bloquear controles e entrar em PiP.
3. **Gravação:** `adb shell screenrecord --time-limit 15 /sdcard/osd-<gatilho>.mp4`, depois `adb pull`. Contar quedas de alpha visíveis. Complemento: `adb shell dumpsys gfxinfo zechs.drive.stream framestats`.
4. **Registro:** `docs/qa/osd-bug.md`, com uma linha por gatilho e motor: pedidos de esconder, quedas de alpha, observação.
5. **Regra de decisão:**
   - 2 pedidos por 1 ação: causa é chamada duplicada (H1 ou H2).
   - 1 pedido e 2 quedas: causa é animação sobreposta ou cancelamento (H3, H4 ou H5).
   - 1 pedido e 1 queda, mas ainda parece duplo: problema de percepção (H6). Corrigir pelo item I-2.

**Correção (desenho): um `OsdController` compartilhado.**

- **Estados:** `HIDDEN`, `SHOWING`, `SHOWN`, `HIDING`.
- **Um só animador.** Uma variável de progresso `p` de 0 a 1 aplicada a todas as partes: título em `translationY = -8dp x (1 - p)`, barra de controles em `+8dp x (1 - p)`, `alpha = p` para título, controles, faixa de pular abertura e escurecimento. Um único `ValueAnimator`.
- **Interrompível.** `show()` durante `HIDING` reverte a partir do progresso atual, sem saltar para alpha 0. `hide()` durante `SHOWING` faz o mesmo. Nunca recomeça do zero.
- **Idempotente.** `hide()` em `HIDING` ou `HIDDEN` não faz nada. `show()` em `SHOWING` ou `SHOWN` só reinicia o temporizador de inatividade.
- **Um só dono do temporizador.** No ExoPlayer, desativar o nativo (`controllerShowTimeoutMs = 0` e `controllerHideOnTouch = false`) e deixar o `OsdController` esconder por inatividade (4,5 s; 30 s no modo de revisão, como hoje).
- **Media3.** A `PlayerView` continua gerenciando a visibilidade do controlador. Chamar `showController()` no começo do show e `hideController()` uma única vez no fim do hide, sem animação própria concorrente.
- **Peças puras e testáveis.** O estado e as transições ficam em `playback/osd/OsdStateMachine.kt`, sem Android. A parte de view fica em `OsdController`, que recebe as views por interface e é usada por `PlayerActivity` e `MPVActivity`.
- **Movimento reduzido.** Sem deslocamento. Fade curto ou troca imediata (decisão D9).
- **Um dono para o card do próximo episódio e para a faixa de pular abertura**, acompanhando o mesmo `p`.
- `PlayerControllerVisibility` (já existe) passa a derivar do estado do `OsdController`.

**Critérios de aceite:**

1. Para cada um dos 6 gatilhos, o log de debug mostra exatamente 1 pedido de esconder por ação do usuário, nos dois motores.
2. A gravação de tela mostra uma só queda de alpha, sempre decrescente, em até 150 ms (mais ou menos um frame), sem reaparecer.
3. Testes unitários da máquina de estados cobrem: esconder duas vezes; mostrar durante o esconder; esconder durante o mostrar; fim do temporizador durante o esconder; abrir e fechar modal; bloqueio de controles.
4. Teste instrumentado (UiAutomator) com D-pad e toque: depois de OK, de Voltar, de um toque e de uma espera de 5 s, o estado final é o esperado e o contador de animações iniciadas é no máximo 1 por ação.
5. O código de mostrar e esconder existe em um lugar só. As linhas de `PlayerActivity` e `MPVActivity` são medidas antes e depois.
6. Com as animações do sistema desligadas, nenhuma animação roda.

**Regressões a verificar:** o foco volta ao botão play/pause ao mostrar; o foco não fica em view escondida; Voltar fecha o OSD antes de sair do player (`PlayerControllerVisibility.shouldConsumeBack`); PiP; modais de áudio e legenda escondem e restauram o OSD; bloqueio de controles; a legenda sobe e desce junto com o OSD (`SubtitleElevationPolicy`).

**Se a causa não for confirmada:** não aplicar a correção às cegas. Registrar a tabela do diagnóstico e perguntar ao dono.

### I-2. Especificação de movimento (`docs/design/MOTION.md`)

- **Situação [F]:** existe `motion_spec.xml` com as curvas como strings, mas as durações estão literais no código (150, 200, 220 e 300 ms observados).
- **Tabela de tokens (proposta a validar em TV):**

| Token | Uso | Duração | Curva |
|---|---|---|---|
| `motion_osd_show` | OSD aparece | 220 ms (valor atual) | desaceleração |
| `motion_osd_hide` | OSD some | 150 ms (valor atual) | aceleração |
| `motion_focus` | anel e escala de foco | 150 ms | padrão (acelera e desacelera) |
| `motion_card` | card do próximo episódio | 220 ms | padrão |
| `motion_pill` | pílulas de gesto e avisos | 200 ms entrada, 150 ms saída | padrão |
| `motion_crossfade` | esqueleto para conteúdo | 200 ms | linear |

- **Regras:**
  1. A saída nunca é mais lenta que a entrada.
  2. Interações muito frequentes (mostrar e esconder o OSD, mover o foco) usam o mínimo: fade curto e, no máximo, 8 dp de deslocamento.
  3. Nenhuma animação bloqueia a entrada do usuário.
  4. Toda animação é cancelável.
  5. Toda animação respeita movimento reduzido.
- **Curvas no Android:** usar as classes `FastOutSlowInInterpolator`, `LinearOutSlowInInterpolator` e `FastOutLinearInInterpolator` do AndroidX. Confirmar que a dependência está disponível no projeto antes de usar.
- **Aceite:** o documento existe, é aprovado pelo dono (D9) e os tokens estão em `motion_spec.xml`.

### I-3. Inventário e consolidação das animações
1. Listar todas as animações: `grep -rnE "animate\(\)|ObjectAnimator|ValueAnimator|TransitionManager|setDuration" app/src/main/java`. Registrar a contagem como linha de base.
2. Exemplos já vistos [F]: pílulas de gesto em `PlayerGestureHelper.kt:68-94`, `:282`, `:320`, `:348-353`; card em `PlayerActivity.kt:1097`, `:2098`; pílula de pular abertura em `:2198`; `TransitionManager` em `:455`, `:934` e `:1539`.
3. Classificar cada uma: necessária, redundante ou frequente demais. Substituir as durações literais pelos tokens.
- **Aceite:** zero durações literais em código de animação, verificado por busca no CI.

### I-4. Interrupção e ordem das animações
- Toda `withEndAction { visibility = GONE }` precisa de um `cancel()` correspondente no caminho de mostrar. Sem isso, a ação final de um esconder anterior pode rodar depois de um mostrar e apagar a view.
- Auditar as cinco pílulas de `PlayerGestureHelper` e o card do próximo episódio.
- **Aceite:** teste unitário ou instrumentado que dispara esconder e mostrar em sequência rápida e confirma a view visível no fim.

### I-5. Transições entre telas e abertura do player [I]
- Não tenho evidência de problema aqui. Medir antes de mexer: flash preto ao abrir o player, tempo até o primeiro quadro (Fase D) e continuidade visual de Home para Detalhes e Detalhes para Player.
- Só adicionar transição se a medição mostrar quebra de continuidade. Regra do HIG: não adicionar movimento por adicionar.

### I-6. Carregamento progressivo e imagens
- Esqueleto, depois conteúdo com crossfade de `motion_crossfade`, sem salto de layout.
- Marcador de posição com o gradiente Obsidian, nunca um quadro preto vazio.
- Decodificar no tamanho exato da view, fora da thread principal.
- **Aceite:** captura de cada estado (carregando, vazio, erro, pronto) em aparelho.

### I-7. Resposta imediata ao controle remoto
- O foco precisa aparecer no mesmo quadro da tecla.
- Medir com `FrameTimingMetric` do Macrobenchmark durante navegação por D-pad (Fase D) e com `gfxinfo` durante a abertura e o fechamento do OSD.
- Sem meta numérica antes da linha de base.

### I-8. Revisão por tela (Apêndice D)
- Cada tela de TV recebe o checklist do Apêndice D, com evidência por item: captura, log ou gravação.

**Critérios de saída da Fase I:**
- Item I-1 com os seis critérios de aceite cumpridos, ou diagnóstico registrado e decisão do dono se a causa não for confirmada.
- `docs/design/MOTION.md` aprovado, com tokens em uso.
- Inventário de animações feito e durações literais removidas.
- Checklist do Apêndice D preenchido para Home, Detalhes, Player, Configurações e Pastas, marcando o que não foi testado.

---

## 4. Fase C: Validação em aparelho

**Objetivo:** gerar a primeira evidência real de foco, navegação e reprodução na TV.

**Pré-requisito:** matriz de dispositivos definida em `docs/qa/device-matrix.md`. Mínimo: emulador de Android TV 1080p a 320dpi. Desejável: TV 1080p real com densidade 213dpi.

**Tarefas**
1. Registrar em `docs/qa/device-matrix.md` para cada dispositivo: modelo, versão do sistema, `adb shell wm size`, `adb shell wm density`.
2. Criar a suíte instrumentada de foco com UiAutomator, a partir de `docs/qa/focus-checklist.md`. Hoje não há testes instrumentados com UiAutomator. Cobrir:
   - foco inicial em Home, Detalhes, Configurações, Pastas e Player;
   - DPAD em quatro direções a partir de cada região;
   - Voltar em cada nível, incluindo a volta do conteúdo para o rail;
   - memória de foco ao voltar de tela filha.
3. Executar a suíte e anexar o relatório (`adb` ou `gradlew connectedDebugAndroidTest`) com a contagem.
4. Validar o Watch Next no launcher:
   - reproduzir um episódio por alguns minutos, sair, verificar o item no launcher;
   - abrir o item e confirmar que o `makimono://watch/{videoId}` leva ao episódio certo;
   - concluir um episódio e confirmar a remoção do item.
5. Validar a media session: controles do controle remoto (play, pause, avançar, voltar) atuam sobre o player, nos dois motores.
6. Capturar telas de Home, Detalhes, Configurações, Pastas e Player em cada dispositivo. Máximo de 20 capturas curadas em `docs/qa/audits/<data>/`. Não versionar dumps de hierarquia.
7. Verificar a legibilidade de texto a três metros numa TV real (inferência a validar: 12sp de piso e 16sp de corpo).

**Critérios de saída**
- Suíte UiAutomator passando nos dispositivos da matriz, com o relatório anexado.
- Watch Next validado no launcher de pelo menos um dispositivo real ou emulador de Android TV, com captura.
- Capturas curadas e registradas.
- Checklist de foco preenchido, marcando explicitamente o que não foi testado.

---

## 5. Fase D: Desempenho medido

**Objetivo:** ter números, e só então metas.

**Tarefas**
1. Criar o módulo `benchmark/` com Macrobenchmark. Fluxos:
   1. inicialização a frio até primeiro frame (TTID) e Home completa (TTFD);
   2. abertura de série com metadados em cache;
   3. abertura de série sem cache;
   4. primeiro frame do player após escolher episódio;
   5. navegação ida e volta entre abas do rail;
   6. rolagem de estante com 100 itens (jank).
2. Gerar Baseline Profile para os fluxos 1, 2, 4 e 5. Medir antes e depois.
3. Rodar em emulador e, se houver, em TV real. Não misturar os números: cada tabela declara o dispositivo.
4. Registrar em `docs/qa/perf-baseline.md`: dispositivo, versão, modo de compilação, repetições, mediana e variação.
5. Adicionar JaCoCo e registrar a cobertura atual, por camada (domínio, playback, UI). Sem meta antes do número.
6. Só depois da linha de base, definir metas. Regra inicial: não regredir a linha de base. Metas numéricas são decisão do dono, com base na tabela.

**Critérios de saída**
- Seis fluxos medidos, com repetições e dispositivo declarados.
- Baseline Profile medido, mantido só se houver ganho.
- Cobertura por camada registrada.

---

## 6. Fase E: Motor de reprodução

**Objetivo:** uma sessão, um contrato, motores escolhidos por regra, e duas Activities finas.

**Pré-requisito:** decisão D1 registrada no ADR-002. A recomendação é Media3 como padrão e MPV apenas para legendas ASS que precisem do estilo original, ou como fallback explícito.

**Tarefas**
1. Confirmar que `selectEngine(media, device)` existe, com testes para cada caminho. Qualquer Activity que escolha motor por conta própria é removida.
2. Mapear, em tabela, cada responsabilidade e sua localização em `PlayerActivity` e `MPVActivity` (arquivo e linha): OSD, controles, progresso, capítulos, pulo de abertura, trilhas, atraso de áudio e legenda, modo de exibição, liberação de recursos.
3. Para cada responsabilidade duplicada, mover para `playback/` como classe pura, com teste. A lista mínima: `OsdController` (criado na Fase I, item I-1), `PlaybackSession`, `ProgressRecorder` (Room, `watch_list`), `SkipController`, `TrackSelection`, `DisplayModeController`. `TrackSelection` ainda não existe no código: é para criar. As demais já existem e precisam receber a lógica que continua nas Activities.
4. Migrar uma Activity por vez, começando pelo Media3. Ao fim de cada PR, medir linhas das duas Activities e registrar.
5. Testes de contrato do `PlayerEngine` rodando contra Media3 e MPV com os mesmos casos.
6. Verificar a liberação de recursos com LeakCanary: dez ciclos de abrir e fechar o player, sem vazamento de Activity.
7. Verificar a troca de taxa de quadros pela API oficial, com a implementação própria mantida só como fallback até validação em TV real. Registrar o efeito de piscar em aparelho, se houver.

**Critérios de saída**
- Responsabilidades duplicadas listadas na tabela e com destino definido. Duplicações restantes justificadas.
- Soma das linhas das duas Activities medida antes e depois. A meta é definida pelo dono depois da tabela.
- Testes de contrato passando para os dois motores.
- LeakCanary sem vazamento após dez ciclos.
- Checklist de foco do player passando em aparelho (Fase C).

---

## 7. Fase F: Domínio, estado e telas de TV

**Objetivo:** regras fora da UI e um estado por tela, sem reescrever o que já funciona.

**Tarefas**
1. Ampliar os casos de uso. Hoje há 4 arquivos com `UseCase`. Criar, no mínimo, para: montar biblioteca a partir do Drive, escolher próximo episódio, calcular progresso e conclusão, decidir filme ou série (`isMovie`). Cada um com teste unitário.
2. Substituir a lógica equivalente em `HomeViewModel`, `SeriesDetailViewModel` e nas Activities de player pelas chamadas aos casos de uso.
3. `UiState` selado (`Loading`, `Content`, `Empty`, `Error`) em Home, Detalhes, Pastas e Configurações, com `StateView` como único componente de estado.
4. Estado de foco no ViewModel, não no Fragment, para sobreviver a rotação e à volta de tela filha.
5. Dividir `HomeFragment` só no que for necessário para o foco. A redução de linhas não é meta por si.
6. Verificar cada tela com a suíte da Fase C. Qualquer regressão de foco volta antes do merge.
7. Rail de TV: confirmar em aparelho a largura de 80dp, o indicador de item ativo e a volta do conteúdo para o rail.
8. Hero: confirmar que o anel de foco aparece completo nas quatro bordas em 1080p e 4K, em aparelho.

**Critérios de saída**
- Quatro casos de uso mínimos, com teste, usados pelas telas.
- `UiState` selado nas quatro telas, sem variável solta de estado no Fragment.
- Suíte de foco da Fase C passando nessas telas.

---

## 8. Fase G: Repositório, documentação e dívida de lint

**Objetivo:** cada assunto tem um documento, o repositório pesa o que precisa, e o lint não esconde problema.

**Tarefas**
1. Remover `docs/ui-audit/` da árvore pelo PR da Fase A. Guardar as capturas-chave em `docs/qa/audits/`, no máximo 20.
2. Decidir D6: reescrever o histórico para tirar `docs/ui-audit` (`git filter-repo`), ou manter. Se reescrever, avisar que clones antigos precisam ser refeitos. Não é obrigatório.
3. Decidir D3 (`backend/` e pareamento):
   - se for remover, abrir PR removendo a pasta e atualizar `ADR-001` como descartado;
   - se for manter, mover para `services/pairing/` com README e CI próprios.
4. Decidir D2 (temas). Se só Obsidian for mantido, reduzir `themes.xml` de 16 declarações para o tema base e, se necessário, uma variante. Verificar que nenhuma tela quebra.
5. Lint: zerar uma categoria por PR, começando pela de maior volume. Medir a contagem depois de cada PR. Não regenerar baseline para esconder problema novo.
6. Triagem dos 24 ramos remotos sem merge: para cada um, decidir merge, descarte ou arquivo. Dependabot: revisar as propostas e fechar as obsoletas. Há branches de atualização que parecem ser de versão maior (por exemplo `actions/checkout-7`, `okhttp-bom-5.5.0`, `hilt-android-gradle-plugin-2.60.1`). Aplicar uma por PR, com a suíte completa, e nunca em lote.
7. `README.md`: reescrever com passos de compilação do zero, comandos de teste e lint, contagens geradas, e links para `docs/architecture/`, `docs/design/`, `docs/qa/` e `docs/releases/`.
8. Mover `GOAL.md`, `docs/FRONTEND_LOG.md`, `docs/ANTIGRAVITY_IMPLEMENTATION_PLAN.md` e `docs/CORRECTION_PLAN_TRACKER.md` para `docs/archive/`. Os relatórios de fase do `docs/qa/` ficam como histórico, com aviso de que não são fonte de verdade.
9. Remover as cópias de `docs/ui-modernization/` que foram substituídas por `docs/design/DESIGN.md` e ADRs. Extrair decisões antes de remover.
10. Ativar `check_strings.py`, `check_contrast.py`, `check_layout_colors.py` e `check_typography.py` no CI, e não só no script local.

**Critérios de saída**
- `docs/ui-audit/` fora da `main`.
- D2, D3 e D6 decididos e registrados em ADR.
- Lint com categorias zeradas e contagem registrada.
- Ramos remotos triados, com lista do que foi feito.
- README verificado a partir de um clone limpo.

---

## 9. Fase H: Release e governança

**Objetivo:** cada release tem origem, notas e verificação, e nenhuma tag muda depois de publicada.

**Tarefas**
1. Processo de release escrito em `docs/releases/README.md`:
   - release sai da `main`, depois do merge;
   - `versionCode` e `versionName` batem com a tag;
   - notas em `docs/releases/vX.Y.Z.md`, geradas a partir de commits com convenção;
   - `CHANGELOG.md` atualizado no mesmo PR.
2. Tags anotadas, nunca recriadas. Correção vai para nova versão.
3. Não declarar build reproduzível sem prova (dois builds limpos com o mesmo hash, anexados). Na `fase-7` o README já não contém essa frase. Conferir que ela continua ausente na `main` depois do merge.
4. Governança do token: PAT com escopo mínimo, rotacionado depois desta conversa. O token atual circulou em várias conversas e precisa ser revogado.
5. Actions: fixar por SHA todas as ações de `ci.yml` e `release.yml`. Já feito no tag `v1.6.5.0`. Manter a regra: nenhuma ação nova sem SHA.
6. Segredos no `BuildConfig`: aceite de risco registrado em ADR-003, fora de tarefa. Lembrar ao dono que as releases já publicadas contêm esses valores enquanto existirem.

**Critérios de saída**
- Processo de release escrito e aplicado à próxima versão.
- Token rotacionado, com escopo registrado.
- Nenhum claim de reprodutibilidade sem prova.

---

## 10. Decisões pendentes do dono

| # | Decisão | Opções | Recomendação | Antes da fase |
|---|---|---|---|---|
| D1 | Motor de reprodução | A: Media3 padrão e MPV só para ASS. B: só Media3. C: só MPV | A | E |
| D2 | Temas | A: só Obsidian. B: manter todos | A | G |
| D3 | `backend/` e pareamento | A: remover. B: separar em `services/pairing/` | A, se o pareamento não for usado | G |
| D4 | Views ou Compose for TV | A: manter Views. B: migrar por tela | A até haver medição de desempenho | F |
| D5 | Canal de preview no launcher | Implementar ou não | Não implementar | C |
| D6 | Reescrita do histórico | Reescrever ou não | Não, por ser privado | G |
| D7 | Ambient mode | Implementar ou delegar ao sistema | Delegar | C |
| D8 | Segredos no `BuildConfig` | Aceito por você, para uso pessoal | Registrar em ADR-003. Rotacionar se o APK for compartilhado | H |
| D9 | Estilo de movimento do OSD | A: fade com 8 dp de deslocamento (como hoje). B: só fade. C: sem animação | A, com movimento reduzido caindo em fade curto ou troca imediata | I |

Decisões D4, D5 e D7 foram propostas nos relatórios da Fase 7. Sem registro formal, continuam pendentes.

---

## 11. Rubrica e critérios de nota

A nota de cada área só sobe com a evidência listada. Sem ela, fica onde estava.

| Área | Nota de partida | Evidência que faz subir | Teto sem aparelho |
|---|---|---|---|
| Arquitetura | 5,0 | Casos de uso usados pelas telas; `PlaybackSession` e contrato de motor com testes; arquivos de player com linhas medidas | 6,0 |
| Segurança (uso pessoal) | 3,5 | Token rotacionado com escopo registrado; ADR-003 escrito; sem tokens reais em fixtures | 4,0 |
| Android TV e navegação | 7,0 | Suíte UiAutomator passando em aparelho; Watch Next validado; capturas curadas | 7,0 |
| Desempenho e reprodução | 5,5 | Macrobenchmark com linha de base; Baseline Profile medido; LeakCanary limpo | 5,5 |
| Qualidade de código | 6,5 | Lint com categorias zeradas; contagens geradas; cobertura por camada | 7,0 |
| Build e release | 6,5 | CI com status verificado; release a partir da `main`; processo escrito | 7,5 |
| Design e UX | 7,0 | Captura de estados em aparelho; contraste verificado; paridade de texto; OSD com 1 animação por ação (log e gravação); `MOTION.md` em uso; checklist do Apêndice D preenchido | 7,0 |
| Documentação | 3,5 | Repositório sem dumps; README verificado; um documento por assunto | 7,0 |

**Leitura realista:** sem aparelho e sem medição de desempenho, a média não passa de cerca de 6,4 (média dos tetos da tabela acima). Com a Fase C e a Fase D concluídas, a média pode chegar a 7,0 ou um pouco mais, dependendo do que a validação mostrar. Nota acima de 8 exige uso prolongado do app na TV, sem regressões em várias releases, e isso não é avaliável nesta rodada.

---

## 12. Regras para o agente

1. Nenhuma alteração entra na `main` sem PR aprovado pelo dono.
2. Nenhuma tag é recriada, movida ou forçada. Versão nova sempre.
3. Todo número citado vem com o comando e a saída. Número de relatório anterior não conta como prova.
4. Contagens de teste: informar anotações e execuções separadamente, lidas dos XML.
5. Fase de TV só fecha com captura ou log de aparelho. Quando não houver aparelho, marcar explicitamente como "não testado".
6. Quando um item depender de decisão de produto (D1 a D8), parar e perguntar. Não escolher sozinho.
7. Não remover código sem a lista de referências no PR (`grep` com resultado anexado).
8. Não regenerar a baseline de lint para esconder problema novo. A baseline só diminui.
9. Não afirmar CI verde sem link do run. Se não houver permissão de leitura, dizer isso.
10. Não reescrever histórico sem autorização explícita.
11. Não colocar tokens, segredos ou dumps com dados de biblioteca em nenhum arquivo versionado.
12. Quando um relatório anterior tiver um número que não bate com a medição, corrigir o relatório e dizer por quê.
13. Bug visual ou de animação: reproduzir e registrar (log e gravação de tela) antes de corrigir. Se a causa não se confirmar, parar e perguntar. Nenhuma correção por palpite.

---

## 13. Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|
| Merge da `fase-7` traz regressão de foco não vista na JVM | Média | Alto | Suíte UiAutomator antes do merge, ou PR com teste em aparelho anexado |
| Fusão dos players quebra reprodução de ASS ou de HDR | Média | Alto | Testes de contrato, teste com arquivo ASS real, LeakCanary |
| Benchmark feito só em emulador vira meta para TV real | Alta | Médio | Declarar dispositivo em cada tabela. Metas só com TV real |
| Lint zerado com supressões mascaradas | Média | Médio | Proibir `@SuppressLint` novo sem justificativa no PR |
| Token atual usado por terceiros | Baixa (uso pessoal) | Alto | Rotacionar agora, com escopo mínimo |
| Reescrita do histórico quebra clones | Certa, se feita | Baixo | Só com autorização (D6) |
| Relatórios divergentes voltam a aparecer | Alta | Médio | Regra 3 e 12 da seção 12 |
| Corrigir o OSD sem reproduzir e esconder a causa real | Média | Médio | Diagnóstico da Fase I antes da correção; regra 13 |
| Novo `OsdController` regride foco, PiP ou legenda | Média | Alto | Lista de regressões do item I-1 e suíte de foco da Fase C |

---

## Apêndice A. Comandos de verificação

```bash
# Estado do remoto
git fetch origin --prune
git log --oneline -1 origin/main
git log --oneline origin/main..origin/fase-7-plataforma-revisao | wc -l
git merge-base --is-ancestor origin/main origin/fase-7-plataforma-revisao && echo "fast-forward possível"

# Tags
git rev-parse --short v1.6.5.0^{commit}

# Contagens (na branch a verificar)
git grep -h "@Test" <branch> -- app/src/test | wc -l
git show <branch>:app/lint-baseline.xml | grep -c "<issue"
git grep -h "layout_literal_" <branch> -- app/src/main | wc -l
git grep -h "android.util.Log" <branch> -- app/src/main | grep -v AppLog | wc -l
git grep -h "catch (_: Exception) {}" <branch> -- app/src/main | wc -l

# Tamanho das Activities
git show <branch>:app/src/main/java/zechs/drive/stream/ui/player/PlayerActivity.kt | wc -l
git show <branch>:app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt | wc -l

# Execuções reais (no ambiente de build)
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -c "import glob,xml.etree.ElementTree as E; print(sum(int(E.parse(f).getroot().attrib.get('tests',0)) for f in glob.glob('app/build/test-results/testDebugUnitTest/*.xml')))"

# Aparelho
adb devices
adb shell wm size
adb shell wm density
./gradlew connectedDebugAndroidTest

# Ramos não integrados
git branch -r --no-merged origin/main
```

## Apêndice B. Ordem sugerida

1. **A** (integração) e **B** (evidência de build). São rápidas e destravam o resto.
2. **I-1** (bug do OSD). Diagnóstico primeiro, correção depois. Pode começar antes da C, mas a confirmação exige gravação de tela.
3. **C** (aparelho). Sem ela, a nota de TV não sobe.
4. **D** (desempenho). Precisa de C para ter o dispositivo definido.
5. **E** (motor). Depende de D1 e de D (linha de base). O `OsdController` do I-1 é a primeira peça compartilhada a extrair.
6. **I-2 a I-8** (movimento nível Apple). Depois do I-1, com D9 decidida.
7. **F** (domínio e telas). Depende de C para não regredir foco.
8. **G** (repositório e lint). Pode rodar em paralelo com E e F, desde que D2, D3 e D6 estejam decididas.
9. **H** (release e governança). Fecha a rodada.

---

## Apêndice C. Registro de validação do plano

Validação feita contra o remoto depois da primeira versão do plano.

**Conferido e correto:** `main` em `b0c4b20`; `fase-7` à frente em 46 commits, com `main` como ancestral; tag `v1.6.5.0` em `e95953a`; releases `v1.6.4.9` e `v1.6.5.0` publicadas; `TvLoginServer` removido; `backend/` com 14 arquivos; 16 declarações de tema; 24 ramos sem merge; 4 arquivos com `UseCase`; ausência de Macrobenchmark e de UiAutomator; arquivos de documentação e verificadores citados pelo plano existem; os verificadores `check_*.py` não rodam no CI.

**Corrigido nesta versão:**
1. Linhas de `PlayerActivity` e `MPVActivity` estavam atribuídas ao commit errado (`b0c4b20` em vez de `460ac1b`).
2. Medidas marcadas como "relatado" foram confirmadas.
3. A afirmação sobre a tag recriada com `--force` vinha de um relatório e não foi verificada.
4. A nota de Desempenho era maior que o teto da própria tabela. A média dos tetos é 6,4, e o texto dizia 6,0.
5. `TrackSelection` estava descrito como algo a mover, mas não existe no código.
6. O item sobre o README já estava cumprido na `fase-7`.
7. Faltava o aviso sobre atualizações de versão maior nas branches do Dependabot.

**Limites que permanecem:** o status do CI não foi verificado (o token atual não lê Actions); nenhuma medição de aparelho; os números de execução de testes (480 e 481 nos relatórios) continuam sem log.

---

## Apêndice D. Checklist de qualidade de interação (por tela)

Cada item é aprovado, reprovado ou "não testado", sempre com evidência (captura, log ou gravação).

1. Cada ação do usuário produz exatamente um feedback visual.
2. O feedback aparece no mesmo quadro da entrada, sem espera perceptível.
3. Nenhuma animação precisa terminar para o usuário poder agir.
4. A saída é mais rápida que a entrada.
5. A mesma função usa a mesma duração e a mesma curva em todas as telas.
6. Movimento reduzido é respeitado.
7. O foco é sempre visível e previsível.
8. Nenhum quadro preto, salto de layout ou flash entre telas.
9. Texto dentro da zona segura, com contraste de pelo menos 4,5:1.
10. Estados de carregamento, vazio e erro têm ação e foco inicial.
11. Nenhum texto técnico cru na interface.
12. Sem jank perceptível, com a porcentagem de quadros lentos medida.

---

## Apêndice E. Registro do diagnóstico do OSD (o que foi lido)

**Lido no código** (`fase-7`, `4c872ed`): `PlayerActivity.kt` linhas 1425-1525 (esconder e mostrar), 1087-1110 (listener de visibilidade), 1360-1422 (Voltar e modais), 1646-1660 (configuração da `PlayerView`), 535-550 (toque); `MPVActivity.kt` linhas 940-1070 (mostrar e esconder); `PlayerGestureHelper.kt` (toque simples e animações de pílulas); ids `exo_*` do layout do controlador; versão do Media3 em `app/build.gradle`.

**Não verificado:**
- Se `PlayerGestureHelper` consome o toque antes de a `PlayerView` aplicar `controllerHideOnTouch`.
- Se a `PlayerControlView` do Media3 1.4.1 permite desligar a animação interna e se ela atua com este layout.
- O comportamento da `withEndAction` em cancelamentos neste código (leitura da documentação do Android, sem teste).
- O sintoma em aparelho. Só tenho o relato do dono.
- Em qual dos dois motores o bug aparece com mais frequência.

**Texto do HIG usado:** princípios de movimento lidos em espelhos da página oficial. Não consultei a página diretamente.
</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-10-10T00:28:02-03:00.

The user has mentioned some items in the form @[ITEM]. Here is extra information about the items that were mentioned by the user, in the order that they appear:

/goal is a [Slash Command]:
The user has marked this task with /goal, indicating that this task is intended to run for a long time without user input, e.g. overnight. You should be extra thorough and only stop when you are confident the goal has been completely fulfilled. The system will force you to continue execution, prompting you to audit your work until completion. Once complete, include <!-- GOAL_COMPLETE --> in your response. If the user explicitly asked to stop or cancel this goal, include <!-- GOAL_CANCELLED --> in your response to cancel the goal.
</ADDITIONAL_METADATA>