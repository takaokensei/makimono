# Retorno do player à Home na fixture visual (07/10/2026)

## Ambiente e privacidade

APK Debug arm64 `zechs.drive.stream.visualreview` instalado no Moto G34 (Android 15). Vídeos locais empacotados na fixture; a Home mostra a marca `PRÉVIA · DADOS DE TESTE`. Nenhuma conta, biblioteca, progresso ou dado do Google Drive foi usado.

## Percurso observado

- **Media3/ExoPlayer:** com OSD visível, BACK ocultou os controles e mostrou “Pressione Voltar novamente para sair do vídeo”, mantendo a `PlayerActivity` ativa. O BACK seguinte saiu do episódio e retornou à Home da fixture.
- **MPV:** com OSD visível, BACK ocultou os controles e manteve `MPVActivity` em primeiro plano; um segundo BACK encerrou o vídeo e retornou à mesma Home.
- A fixture hospedeira agora mantém uma Home real de revisão sob o player. Isso corrige o harness, que antes substituía a tela por uma View vazia, e torna o retorno verificável.

## Evidências

- Capturas `exoplayer-osd-visible.png`, `exoplayer-after-back-osd-hidden.png`, `home-after-exoplayer-exit.png`, `mpv-osd-visible.png`, `mpv-after-back-osd-hidden.png` e `home-after-mpv-exit.png`.
- XML `exoplayer-after-back.xml`, `home-after-exoplayer.xml`, `mpv-osd-hidden.xml` e `home-after-mpv.xml`.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 309 testes JVM, 0 falhas/erros/skips. APK SHA-256 `23F683E397FD6BB48366E6D0C23753CE87FB8C661C0FAD6E646D44453F53C65B`.

## Limites

O vídeo sintético valida transições de Activity e OSD, não streaming, seek, áudio/legendas, AniSkip, progresso ou retomada do Drive. Moto G34 com teclas ADB não substitui controle remoto nem TV física. Esta evidência cobre BACK do sistema; a seta touch, múltiplas camadas (menu/aviso/próximo episódio) e sequências longas ainda precisam da matriz Q17/Q26.
