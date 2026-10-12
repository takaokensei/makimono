# Auditoria de Movimento e `withEndAction` (Fase 6 — T-601 a T-604)

Este documento cataloga todas as 21 ocorrências de `withEndAction` em `app/src/main/java` identificadas no achado **Q-007** do relatório de auditoria (`ca26827`), verificando o cancelamento explícito no caminho oposto (`animate().cancel()` / `PillVisibilityPolicy`) e a regra de durações `entrada > saída` em `res/values/motion_spec.xml`.

## 1. Tabela das 21 Ocorrências de `withEndAction` (T-603)

| # | Arquivo | Linha | Elemento / Transição | Cancelamento no caminho oposto | Guarda de estado (`PillVisibilityPolicy` / Flag) | Status |
|---|---|---|---|---|---|---|
| 1 | `playback/osd/PillVisibilityPolicy.kt` | 9 | KDoc de contrato da política de visibilidade | Contrato exige `animate().cancel()` | `PillVisibilityPolicy.requestShow()` | Documentação |
| 2 | `playback/osd/PillVisibilityPolicy.kt` | 53 | KDoc de `onHideAnimationEnded()` | Contrato exige checagem de retorno booleano | `state == State.HIDING` | Documentação |
| 3 | `ui/home/controller/RailController.kt` | 395 | `binding.navRail` (`expandSidebar`) | `binding.navRail.animate().cancel()` (linha 390 e 420) | `isSidebarExpanded` | Seguro |
| 4 | `ui/home/controller/RailController.kt` | 415 | `binding.sidebarDimOverlay` (`collapseSidebar`) | `animate().cancel()` em `expandSidebar` (linha 378) e `collapseSidebar` (linha 410) | `isSidebarExpanded` | Seguro |
| 5 | `ui/home/controller/RailController.kt` | 425 | `binding.navRail` (`collapseSidebar`) | `binding.navRail.animate().cancel()` (linha 390 e 420) | `if (!isSidebarExpanded)` | Seguro |
| 6 | `ui/main/MainActivity.kt` | 140 | `splashScreenView` (saída única da Splash Screen do sistema) | Transição única de saída (`splashScreenView.remove()`) | Ciclo de vida da Splash | Seguro |
| 7 | `ui/main/MainActivity.kt` | 771 | `binding.vinhetaContainer` (`dismissVinheta`) | Transição única de saída (`isVinhetaDismissed = true`) | `isVinhetaDismissed` | Seguro |
| 8 | `ui/player/PlayerActivity.kt` | 2036 | `binding.nextEpisodeCard.root` (`dismissNextEpisodeCard`) | `card.root.animate().cancel()` em `showNextEpisodeCard` e `dismissNextEpisodeCard` | `if (!isNextEpisodeCardShowing)` | Corrigido (T-603) |
| 9 | `ui/player/PlayerActivity.kt` | 2137 | `aniskipPill` (`showAniSkipPill`) | `aniskipPill.animate().cancel()` + `aniskipPillJob?.cancel()` ao reexibir | `aniskipPillJob` | Seguro |
| 10 | `ui/player/PlayerGestureHelper.kt` | 85 | `hudBinding.gestureBrightnessPill` (`hideBrightnessRunnable`) | `gestureBrightnessPill.animate().cancel()` + `removeCallbacks` | `brightnessPolicy.onHideAnimationEnded()` | Seguro |
| 11 | `ui/player/PlayerGestureHelper.kt` | 99 | `hudBinding.gestureVolumePill` (`hideVolumeRunnable`) | `gestureVolumePill.animate().cancel()` + `removeCallbacks` | `volumePolicy.onHideAnimationEnded()` | Seguro |
| 12 | `ui/player/PlayerGestureHelper.kt` | 113 | `hudBinding.gestureSeekCard` (`hideSeekRunnable`) | `gestureSeekCard.animate().cancel()` + `removeCallbacks` | `seekPolicy.onHideAnimationEnded()` | Seguro |
| 13 | `ui/player/PlayerGestureHelper.kt` | 127 | `hudBinding.gestureNotificationPill` (`hideNotificationRunnable`) | `gestureNotificationPill.animate().cancel()` + `removeCallbacks` | `notificationPolicy.onHideAnimationEnded()` | Seguro |
| 14 | `ui/player/PlayerGestureHelper.kt` | 326 | `hudBinding.gestureSpeedPill` (`ACTION_UP` long press) | `gestureSpeedPill.animate().cancel()` em `showSpeedPill()` | `speedPolicy.onHideAnimationEnded()` | Seguro |
| 15 | `ui/player/PlayerGestureHelper.kt` | 377 | `hudBinding.gestureSpeedPill` (`hideSpeedPill`) | `gestureSpeedPill.animate().cancel()` em `showSpeedPill()` | `speedPolicy.onHideAnimationEnded()` | Seguro |
| 16 | `ui/player/PlayerGestureHelper.kt` | 408 | `doubleTapBubble` (entrada → encadeia saída) | `bubble.animate().cancel()` no início de `showDoubleTapBubble` | `policy.requestShow()` / `onHideAnimationStarted()` | Seguro |
| 17 | `ui/player/PlayerGestureHelper.kt` | 414 | `doubleTapBubble` (saída encadeada) | `bubble.animate().cancel()` no início de `showDoubleTapBubble` | `policy.onHideAnimationEnded()` | Seguro |
| 18 | `ui/player2/MPVActivity.kt` | 1128 | `binding.netflixSkipRow` (`SkipButtonUi.Hidden`) | `binding.netflixSkipRow.animate().cancel()` em `Shown` e `Hidden` | `SkipButtonUi` | Seguro |
| 19 | `ui/player2/MPVActivity.kt` | 1134 | `controller.skipIntroRow` (`SkipButtonUi.Hidden`) | `controller.skipIntroRow.animate().cancel()` em `Shown` e `Hidden` | `SkipButtonUi` | Seguro |
| 20 | `ui/player2/MPVActivity.kt` | 2370 | `binding.nextEpisodeCard.root` (`dismissNextEpisodeCard`) | `card.root.animate().cancel()` em `showNextEpisodeCard` e `dismissNextEpisodeCard` | `isNextEpisodeCardShowing` | Seguro |
| 21 | `ui/player2/MPVActivity.kt` | 2568 | `controller.skipIntroRow` (`updateIntroButtonVisibility`) | `row.animate().cancel()` nos ramos `shouldShow` e `!shouldShow` | `shouldShow` | Seguro |

## 2. Tokens de Movimento (`res/values/motion_spec.xml`) — Regra Entrada > Saída (T-604)

| Par de Tokens | Entrada (`ms`) | Saída (`ms`) | Regra `entrada > saída` |
|---|---|---|---|
| OSD (`motion_osd_show` / `motion_osd_hide`) | `220 ms` | `150 ms` | `220 > 150` (OK) |
| Pílulas (`motion_pill_enter` / `motion_pill_exit`) | `200 ms` | `150 ms` | `200 > 150` (OK) |
