# C1.1 — saída do player em mídia local

Device: Moto G34 5G, Android 15, display paisagem 1600×720; APK isolado `zechs.drive.stream.visualreview`. Fixture: `review_multitrack.mkv`, sem sessão Google Drive.

## MPV

- A fixture iniciou com faixa de legenda sintética, posição 00:05/00:10 e OSD visível (`c11-mpv-start.png`).
- Toque no botão Voltar terminou `MPVActivity` e retornou à `VisualReviewActivity` (`c11-mpv-touch-exit.png`; confirmado por `dumpsys activity`).
- D-pad UP focou `btnBack` com anel ciano (`c11-mpv-dpad-back-focus.png`); CENTER terminou a Activity (`c11-mpv-dpad-exit.png`; `dumpsys activity` mostrou a fixture no topo).
- BACK com HUD visível ocultou a HUD e mostrou “Pressione Voltar novamente para sair do vídeo”, mantendo MPV em primeiro plano (`c11-mpv-back1.png`). BACK seguinte retornou à fixture (`c11-mpv-back2.png`).

## Exo / Media3

- A fixture local foi aberta pelo caminho `reviewMediaUri`. O decoder H.264 e AAC foram inicializados; o fluxo especial da fixture pausa intencionalmente quando chega a `STATE_READY`, então o teste não afirma playback contínuo. O frame sintético é azul uniforme.
- Toque em Voltar fechou `PlayerActivity` e retornou à fixture (`c11-exo-before-tap.xml`, `c11-exo-after-tap.png`; confirmado por `dumpsys activity`).
- D-pad UP focou `btnBack` (`c11-exo-arrow-focused.xml` / `c11-exo-dpad-back.png`); CENTER terminou `PlayerActivity` e voltou à fixture (`c11-exo-after-center.xml`; confirmado por `dumpsys activity`).
- BACK com a HUD visível a ocultou e apresentou a instrução de saída mantendo `PlayerActivity` em primeiro plano (`c11-exo-back1.png`). A pressão seguinte retornou à fixture (`c11-exo-back2.png`).

## Resultado e limites

C1.1 está RUNTIME_VALIDATED para os caminhos de saída por toque, D-pad e BACK nos dois engines, usando fixture local no dispositivo de QA. Isso não aceita C1 integral: Exo não foi exercitado em reprodução contínua nesta fixture, e streaming do Drive, formatos/decoders, áudio/legendas em uso, AniSkip, autoplay, buffering/rede e Android TV físico continuam em aberto.
