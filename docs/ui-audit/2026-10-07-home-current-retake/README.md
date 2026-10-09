# Reteste da Home no build atual

Data: 07/10/2026  
Dispositivo: Moto G34, Android 15; pacote isolado `zechs.drive.stream.visualreview`; paisagem 1600×720 px (display físico 720×1600 / 280 dpi).  
APK testado: Debug arm64, SHA-256 `2774FC7986E154A35DB265F38841B5F8EFBA3DBDB83110DBDA67A8C9226FE671`.

## Fluxo verificado

Abri `VisualReviewActivity` na tela Home com dados sintéticos. Depois de focar o CTA principal, percorri com DOWN as seis prateleiras até o catálogo e voltei com UP até o CTA. Os dumps de foco confirmam, nesta ordem: `rvContinueWatchingShelf`, `rvWatchQueueShelf`, `rvNewEpisodesShelf`, `rvStarredShelf`, `rvRecentlyAddedShelf`, `rvAnimeLibrary`. Um RIGHT selecionou o card seguinte em Continuar assistindo; UP retornou à ação principal.

- Home após carregar: `home-current-retake2.png` e `.xml`.
- Foco em cada uma das seis prateleiras: `home-current-retake-dpad-1.xml` até `home-current-retake-dpad-6.xml`.
- Estado com foco no Catálogo: `home-current-catalog.png`.
- Retorno ao CTA principal: `home-current-up-cta.png` e `.xml`.
- Card seguinte em Continuar assistindo: `home-current-preview.png` e `.xml` (captura feita após scroll, não usada como prova de atualização do hero).

## Resultado e limites

Os seis destinos recebem foco por D-pad e o retorno ao CTA funciona no build atual. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` havia concluído com 314 testes em 71 suítes, sem falhas, antes deste smoke; não houve alteração de código depois da compilação. A sessão permaneceu no pacote de QA e não foi acessado Drive real.

Este teste não confirma dados reais das prateleiras, preview do hero integrado ao ViewModel em runtime, tela de TV física, overscan, TalkBack ou experiência a 10 pés. Resolução simulada no telefone também não equivale a homologação em Android TV.
