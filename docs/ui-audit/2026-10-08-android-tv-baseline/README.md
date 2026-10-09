# Baseline e sidebar no Android TV AVD — 08/10/2026

## Ambiente e artefato

- Checkout: HEAD `0295a6a`; alterações locais existentes preservadas.
- AVD `MakimonoAndroidTV1080p`, Android 16/API 36, 1920×1080, 320 dpi.
- Build: `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`, executado com diretório atual `X:\` e `TEMP`, `TMP` e `GRADLE_USER_HOME` no caminho ASCII `C:\Temp`.
- Resultado: 335 testes JVM em 75 suítes; 0 falhas, erros ou skips. `BUILD SUCCESSFUL`.
- APK instalado: `makimono-v1.6.4.4-x86_64-debug.apk`, package `zechs.drive.stream.visualreview`.
- SHA-256: `5448E53E8C99E8FD96264F6DFD044E45EA6CA1D0753A02ED0985E46A40EA1D32`.

## Percurso D-pad reproduzido

1. Abrir o seletor de perfil e selecionar o perfil principal.
2. Home recebeu foco inicial em `btnFeaturedPlay`; RIGHT chegou a `btnFeaturedInfo`; LEFT retornou ao CTA principal e outro LEFT alcançou `btnNavInicio`.
3. DOWN percorreu `btnNavAnimes` → `btnNavPastas` → `btnNavFavoritos` → `userProfilePill` → `btnNavConfig`.
4. Os rótulos `Início`, `Catálogo`, `Pastas`, `Favoritos` e `Configurações` permaneceram em uma linha. Bounds medidos a 1920×1080: rail `[16,164][320,260]`, `[16,264][320,360]`, `[16,364][320,460]`, `[16,464][320,560]` e `[16,968][320,1064]`, respectivamente.
5. CENTER em Configurações abriu `Perfil e biblioteca`. BACK retornou à Home; a Activity permaneceu `MainActivity` e o foco retornou a um card.
6. Nenhuma fatal exception foi encontrada no log consultado.

## Configuração de biblioteca e privacidade

Em Settings, o perfil principal exibiu a raiz `oneblacki`. O usuário confirmou que `1oneblacki` é outra pasta irmã. Não alterei favoritos, progresso, arquivos ou pastas do Drive. A validação foi de navegação da interface e não percorreu arquivos remotos. Não guardei screenshot nem UI dump porque a tela da Home mostra dados da biblioteca pessoal; os dumps temporários foram removidos.

## Player Exo/Media3 e MPV — fixture sintética

- O Exo/Media3 abriu `review_multitrack.mkv` pelo `VisualReviewActivity`. Com OSD ativo, `↑` levou o foco de Play/Pause para Voltar; `↓` retornou para Play/Pause; OK em Voltar retornou ao harness. BACK ocultou o OSD e a pressão seguinte retornou ao harness.
- O MPV abriu a mesma fixture. A primeira tecla durante a entrada/abertura do OSD estabilizou o foco; após Play/Pause receber foco, `↑` levou a Voltar e `↓` retornou a Play/Pause. OK em Voltar saiu do vídeo. BACK ocultou o OSD e a pressão seguinte retornou ao harness.
- A fonte de verdade da visibilidade no Exo/Media3 agora usa `PlayerView.isControllerFullyVisible`, evitando depender apenas do estado espelhado pelo callback. Build final instalado no AVD após a alteração.
- Fixture sintética; isso não valida streams do Drive, decoder/codec de mídia real, legendas/áudio em conteúdo remoto, menus completos ou TVs físicas. O MPV ainda merece uma retestagem de foco inicial após a animação do OSD.

## Limites

Este smoke valida a rail, as ações do hero, Settings, navegação básica dos dois players e retorno por BACK em fixture sintética no AVD. Não representa validação de TV física/overscan, TalkBack, fonte ampliada, fluxos reais do Drive/OAuth, Files com erros de rede, detalhes reais, playback remoto, pareamento QR ou atualização. A build release não foi executada. Nenhum commit, push, merge ou release foi feito.
