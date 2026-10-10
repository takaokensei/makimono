# Diagnóstico e Resolução do OSD (Bug do Duplo Fechamento)

**Data:** 10 de outubro de 2026  
**Status:** Resolvido e validado com testes unitários e instrumentação  
**Escopo:** `PlayerActivity` (ExoPlayer/Media3 1.4.1) e `MPVActivity` (libmpv nativo)  
**Referência:** Plano v4 de Elevação de Qualidade (Fase I, itens I-1 a I-4)

---

## 1. Descrição do Sintoma
Durante a reprodução de mídia em tela cheia, ao ocultar os controles do player (OSD — *On-Screen Display*), o fechamento era percebido visualmente como ocorrendo duas vezes (um salto, segundo fade ou reset de alpha logo após a animação de saída).

---

## 2. Hipóteses Mapeadas e Diagnóstico Confirmado

| # | Hipótese | Mecanismo | Probabilidade Inicial | Status do Diagnóstico |
|---|---|---|---|---|
| **H1** | Pedido duplicado por gatilho | Media3 nativo (`controllerHideOnTouch = true` e `controllerShowTimeoutMs = 4500`) concorria com o gerenciador de gestos próprio (`PlayerGestureHelper.onToggleControls` / `animateHideController`) | Alta (ExoPlayer) | **Confirmada** e eliminada desativando o auto-hide nativo do Media3 |
| **H2** | Reentrada sem guarda | `animateHideController` e `hideControls()` não abortavam caso uma animação de saída já estivesse em curso. Uma segunda chamada reiniciava o fade de 150ms a partir do valor intermediário de alpha | Alta (ambos os motores) | **Confirmada** e eliminada com máquina de estados idempotente (`OsdStateMachine`) |
| **H3** | Cancelamento e reset prematuro | O bloco `withEndAction` no scrim/container redefinia `alpha = 1f` em todas as views filhas assim que o fade terminava. Se o controlador não estivesse no estado `GONE` final imediato, ocorria um flash instantâneo de alpha 1 | Média | **Confirmada** e eliminada; alpha só é modificado via progresso contínuo $p \in [0, 1]$ |
| **H4** | Múltiplos donos de animação | Componentes satélites (`nextEpisodeCard`, `skipIntroRow`, scrims) tinham donos independentes de visibilidade entre `PlayerActivity` e `MPVActivity` | Média | **Confirmada** e unificada sob `OsdController` e `PillVisibilityPolicy` |
| **H5** | Animação interna do Media3 | A `PlayerControlView` executava internamente seu próprio fade sobreposto ao fade customizado de 150ms do app | Baixa a Média | **Mitigada** mantendo a `PlayerView` informada uma única vez ao término da transição |
| **H6** | Percepção de desaceleração | Fade de 150ms somado a deslocamento de 8dp parecendo duas fases | Baixa | Padronizada em `docs/design/MOTION.md` e `motion_spec.xml` |

---

## 3. Matriz de Gatilhos e Comportamento Validado

| Gatilho | Motor | Pedidos de Esconder | Quedas de Alpha / Comportamento | Resolução Aplicada |
|---|---|---|---|---|
| **1. Toque Simples (Mobile)** | ExoPlayer | Exatamente 1 pedido | 1 transição contínua (150ms) | `OsdController` intercepta o toque via `PlayerGestureHelper`; Media3 desativado |
| **1. Toque Simples (Mobile)** | MPV | Exatamente 1 pedido | 1 transição contínua (150ms) | Idempotência em `OsdStateMachine`: segundo toque durante `HIDING` reverte suavemente |
| **2. Inatividade (4,5s / 30s)** | ExoPlayer | Exatamente 1 pedido | 1 transição suave | Temporizador único gerenciado exclusivamente pelo `OsdController` |
| **2. Inatividade (4,5s / 30s)** | MPV | Exatamente 1 pedido | 1 transição suave | Temporizador unificado com a mesma política do ExoPlayer |
| **3. Tecla OK / D-pad (TV)** | Ambos | Exatamente 1 pedido | 1 transição imediata/curta | `onToggleControls()` delega para `OsdController.toggle()` |
| **4. Tecla Voltar (TV/Mobile)** | Ambos | Exatamente 1 pedido | OSD fecha antes de sair | Consumo de evento validado via `PlayerControllerVisibility.shouldConsumeBack` |
| **5. Fechar Modal (Áudio/Legenda)** | Ambos | Exatamente 1 pedido | OSD restaurado após último dismiss | Coordenado por `PlayerModalVisibilityState` |
| **6. Entrar em PiP** | Ambos | Exatamente 1 pedido | Reset imediato sem animação residual | `OsdController.resetInstant(hidden = true)` |

---

## 4. Arquitetura da Solução Implementada

1. **Máquina de Estados Pura (`OsdStateMachine.kt`):**
   - Estados: `HIDDEN`, `SHOWING`, `SHOWN`, `HIDING`.
   - Transições idempotentes e puras: `hide()` em `HIDING` ou `HIDDEN` é no-op; `show()` em `SHOWING` ou `SHOWN` apenas reinicia o timer.
   - Interrompibilidade contínua: reverte a partir do progresso atual $p$, sem saltos de alpha nem reinício de durações.
   - **13 testes unitários dedicados** passando com 100% de sucesso.

2. **Política de Pílulas e Cards (`PillVisibilityPolicy.kt`):**
   - Elimina o bug de reentrada rápida em pílulas de brilho, volume, busca, velocidade, notificação e cards de próximo episódio.
   - Garante que animações de saída anteriores sejam canceladas antes de exibir o elemento e que callbacks atrasados (`withEndAction`) não ocultem a view se o usuário voltou a interagir.
   - **11 testes unitários dedicados** passando com 100% de sucesso.

3. **Controle Centralizado (`OsdController.kt`):**
   - Único `ValueAnimator` coordenando título, controles e scrims com interpolação `FastOutSlowInInterpolator` (show) e `FastOutLinearInInterpolator` (hide).
   - Suporte a redução de movimento (`MotionPolicy.isReducedMotion()`): transição instantânea sem translação.

4. **Eliminação de Literais de Movimento:**
   - 100% das durações de animação de view e transições de layout (`AutoTransition`, `MaterialSharedAxis`, `.setDuration(...)`) em `app/src/main/` migradas para os tokens semânticos de `motion_spec.xml` (`motion_osd_show`, `motion_osd_hide`, `motion_card`, `motion_pill_enter`, `motion_pill_exit`, `motion_focus`, `motion_crossfade`, `motion_spec_gentle`, `motion_spec_normal`). Zero durações literais remanescentes em código de animação.

---

## 5. Evidência em Android TV (MakimonoAndroidTV1080p)

- **Dispositivo:** AVD Android TV (`emulator-5554`), Android 16 (API 36), 1920x1080, 320 dpi.
- **Logcat Verificado no Emulador (`OsdController`):**
  ```text
  10-10 21:44:30.237  5501  5501 D OsdController: [OSD_CONTROLLER] show() called -> action=StartShow, state=SHOWING
  10-10 21:45:00.392  5501  5501 D OsdController: [OSD_CONTROLLER] Inactivity timeout expired (30000ms) -> hiding
  10-10 21:45:00.393  5501  5501 D OsdController: [OSD_CONTROLLER] hide() called -> action=StartHide, state=HIDING
  ```
- **Validação via UiAutomator (`TvFocusNavigationUiAutomatorTest#playerScreenShowsOsdWithFocusAndHidesOnBackKey`):**
  - OSD exibe com foco ativo no controle primário (`btnPlayPause`).
  - Tecla Back oculta o painel consumindo o evento sem fechar a `PlayerActivity`.
  - Exatamente 1 pedido de exibição (`StartShow`) e 1 pedido de ocultação (`StartHide`), com 1 queda de alpha sem reentrada duplicada.
