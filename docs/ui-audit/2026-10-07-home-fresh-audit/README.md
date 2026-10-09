# Home TV — rail, alinhamento editorial e foco entre fileiras (2026-10-07)

## Achados e correções

1. A rail de 128 dp quebrava “Catálogo”/“Favoritos” e cortava “Configurações”. Nas variantes `layout-television` e `layout-sw720dp-land`, a largura passou para 176 dp; o cálculo de colunas da Home usa a largura efetiva da rail.
2. “Novos episódios” e “Adicionados recentemente” começavam fora da linha dos cards. Ambos os títulos agora usam `@dimen/home_content_margin` em `layout`, `layout-land`, `layout-television` e `layout-sw720dp-land`.

## Validação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: build concluído; 309 testes Android, 0 falhas, 0 erros, 0 ignorados.
- Instalei somente o pacote de QA `zechs.drive.stream.visualreview` no Moto G34 Android 15. O viewport foi simulado em 1920×1080 a 240 dpi (1280×720 dp), selecionando a variante ampla; isso não substitui hardware/firmware de Android TV.
- Hierarquia e screenshot mostram “Catálogo”, “Favoritos” e “Configurações” por inteiro; os títulos de “Minha lista” e “Adicionados recentemente” começam em x=264 px, alinhados ao início dos cards na captura 1920×1080.
- D-pad desde o CTA: `rvContinueWatchingShelf` → `rvWatchQueueShelf` → `rvNewEpisodesShelf` → `rvStarredShelf` → `rvRecentlyAddedShelf`; RIGHT avança para o segundo card de Adicionados recentemente e UP retorna para Minha lista e Novos episódios. A borda ciano permanece visível no card focado.
- Evidências: `home-shelf-alignment-final.png` (alinhamento), `home-dpad-path.png` (foco na prateleira Novos episódios) e dumps XML próximos.
- Restaurei o celular para 720×1600, 280 dpi e encerrei a fixture.

## Limites

A biblioteca e callbacks são sintéticos; não acessam conta/Drive nem persistem reprodução/favoritos. A matriz prova interação no layout amplo com D-pad virtual no Moto G34, não controle remoto físico, TV real, overscan a 10 pés, TalkBack, modo de fonte ampliada ou conteúdo real. O foco entre prateleiras da Home integrada com Drive continua exigindo homologação final.