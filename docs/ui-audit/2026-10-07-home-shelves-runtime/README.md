# Home shelves: reteste runtime (07/10/2026)

## Ambiente e isolamento

- Moto G34 5G (`0086442783`), Android 15, paisagem 1600×720 px / 280 dpi.
- APK debug `makimono-v1.6.4.3-arm64-v8a-debug.apk`, pacote exclusivamente QA `zechs.drive.stream.visualreview`.
- Activity `VisualReviewActivity`, fixture sintética marcada `PRÉVIA · DADOS DE TESTE`; nenhuma conta ou dado real do Drive foi usado.

## Percurso e observações

- Fixture Home exibiu hero, Continuar assistindo, Fila, Novos episódios/Seguidas, Favoritos, Adicionados recentemente e Catálogo.
- Percorri as seis zonas de prateleira/grid com DOWN; o foco atravessou os cards e chegou a `cardGridItem`. UP retornou ao card da fileira anterior (`cardStarredItem`), sem dead-end observado.
- Capturas/hierarquias por zona: `home`, `continuing`, `queue`, `followed`, `favorites`, `recent` e `catalog`.
- A inspeção de runtime revelou que “Em alta / Catálogo” ficava antes de “Adicionados recentemente” nos layouts landscape/TV. O título foi movido após a fileira recente e antes da grade nas variantes `layout-land`, `layout-television` e `layout-sw720dp-land`.
- Reteste depois do ajuste: `retest-catalog.png` mostra o título acima da grade; UIAutomator registrou foco em `cardGridItem` e o título em `[385,131][1516,170]`. `retest-recent.png` confirma foco de retorno na fileira anterior, com o título do catálogo abaixo dela.
- O texto do cabeçalho agora usa `@string/home_catalog_title` nas mesmas variantes, com recurso padrão e pt-BR.

## Build/teste

- `testDebugUnitTest`: 282 testes em 62 suítes, 0 falhas, erros ou skips.
- `assembleDebug`: concluído com `BUILD SUCCESSFUL`.
- As tarefas foram executadas separadamente depois de locks transitórios do Windows ao substituir arquivos de `app/build`; os retries sequenciais passaram. Não removi resultados manualmente.
- `git diff --check` passou nos XMLs alterados (apenas avisos de normalização LF/CRLF do checkout Windows).

## Limites ainda abertos

- Fixture prova renderização, ordem, foco e retorno em um telefone em paisagem; não prova o carregamento das prateleiras a partir de uma sessão Drive, paginação/datas reais, debounce/crossfade do hero ao focar cards, TV física, 1080p/720p de TV, TalkBack ou performance sustentada.
- A captura usa conteúdo demonstrativo; não representa verificação de metadados/posters de uma biblioteca real.
- Nenhum commit, push, release, troca de branch ou ação em dados pessoais.

## Arquivos principais

- `app/src/main/res/layout-land/fragment_home.xml`
- `app/src/main/res/layout-television/fragment_home.xml`
- `app/src/main/res/layout-sw720dp-land/fragment_home.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-pt/strings.xml`

## Tela de detalhes e perfis (mesmo APK/ambiente)

- `details-retest.png` e `details-episodes-retest.png`: backdrop limitado à região de hero; ao rolar/focar o primeiro episódio, a lista aparece sobre fundo escuro uniforme, sem o vazamento de artwork visto nas capturas antigas. A primeira mídia recebeu foco por D-pad (`cardEpisodeItem`).
- A seta no header não foi acionada neste reteste; este ciclo valida especificamente a contenção visual e o foco na lista.
- `details-more-menu.png`: abri o botão contextual usando RIGHT/RIGHT/CENTER; “Assistir trailer” recebeu foco ciano. DOWN/DOWN alcançou “Marcar temporada como assistida”; BACK fechou o diálogo e restaurou foco em `btnMoreActions`. Todos os callbacks da fixture são Toasts.
- `profiles-retest.png`: os avatares são circulares e sem tile cinza. Em reteste controlado, D-pad percorreu os três perfis até Adicionar perfil; Down alcançou Gerenciar perfis; Center alternou o modo de gerenciamento e a ação seguinte o encerrou. UP/LEFT retornaram à fileira; `profiles-active-focused.xml` registra o perfil em foco e a Activity permaneceu em `VisualReviewActivity`. Não confirmei Adicionar nem edição para evitar alterações de perfil. Evidências adicionais: `profiles-right-safe.png/xml`, `profiles-add-focused.png/xml`, `profiles-manage-focus.xml`, `profiles-manage-mode.png/xml` e `profiles-active-focused.png/xml`.


## Configurações — percurso de foco (mesmo APK/ambiente)

- A fixture `.visualreview` iniciou com foco em Perfil. DOWN percorreu Perfil, Pasta raiz, Aparência, Reduzir movimento, Player, Legendas, Cache, Atualizações, conta MAL, sincronização, AniSkip, gestos, Exportar backup, Restaurar backup e Sair da conta Google. O foco chegou ao último item, e UP retornou a Restaurar backup (`settings-logout-focus.xml`, `settings-up-focus.xml`).
- Não usei CENTER em nenhum ajuste, restauração, logout, atualização ou limpeza. Captura do trecho final: `settings-up.png`.
- BACK encerrou a Activity de revisão e trouxe para o primeiro plano uma Activity existente do pacote de produção (`zechs.drive.stream`). Não acionei controles nessa tela; parei a interação do dispositivo. Isso evidencia que esse launch de fixture não é um percurso isolado de navegação entre Activities.
- O teste cobre apenas foco/scroll do layout estático da fixture, não callbacks/persistência, categorias dinâmicas da tela Settings real, nem login ou conta.

## Cabeçalho da prateleira acompanha a seleção (07/10/2026)

- Identifiquei que a rolagem automática podia deixar o título da seção colado à barra fixa. A Home agora alinha o cabeçalho da seção ativa 12dp abaixo da barra e limita o deslocamento para não empurrar o card focado para fora da área visível.
- A regra é usada nos callbacks de foco da Home e da fixture. Testes puros cobrem alinhamento, caso já alinhado e limite de preservação do item focado.
- No Moto G34, o D-pad DOWN percorreu Continuar assistindo, Minha fila, Novos episódios, Minha lista, Adicionados recentemente e Catálogo; cada título ficou em y=119px, com a barra terminando em y=98px. UP voltou por todas as prateleiras e terminou no CTA principal. Capturas/XML: `home-shelf-aligned-down-01..06.*` e `home-shelf-aligned-up-01..06.*`.
- Build final: `testDebugUnitTest assembleDebug`, 294 testes em 65 suítes, zero falhas/erros/skips. APK arm64 `.visualreview` SHA-256 `36DE6E9424CE0FEE35D187C64C07E05232FC50F523391851C54B7B24A8E18C18`.
- Após estender o espaçamento a `layout-port`, reinstalei o mesmo APK final e repeti a entrada nas prateleiras; `home-final-runtime.png/xml` confirma `cardShelfItem` focado e o título “Minha fila” em `[385,119][557,158]`.
- Limite: runtime da fixture sintética no Moto, não Android TV física nem prateleiras carregadas de Drive real. Nenhuma ação de reprodução ou persistência foi ativada.

## Confirmação no APK atual (07/10/2026, pós-build 1.6.4.3)

- Build limpo atual: `testDebugUnitTest --rerun-tasks` executou **287 testes / 63 suítes**, zero falhas, erros ou skips. `assembleDebug` passou em comando separado; APK arm64 SHA-256 `53FEDC56720057D93FE4236416FC1D84CE84BFADEDCA40A114334B547A27428B`.
- Instalei somente `makimono-v1.6.4.3-arm64-v8a-debug.apk` com `adb install -r` no pacote `.visualreview`, preservando dados. A Home fixture abriu em `VisualReviewActivity`; D-pad RIGHT/DOWN levou ao primeiro card de Continuar assistindo (`cardShelfItem` focado). Capturas atuais: `home-current-build.png/xml` e `home-current-focus.png/xml`.
- A captura confirma contenção do hero, leitura da CTA e foco chegando à prateleira. A fixture mantém o hero Frieren estático; não confirma debounce/crossfade da biblioteca real. Esse comportamento continua comprovado pelo código (delay 250 ms + Glide crossfade 400 ms), mas sem validação dinâmica de runtime autenticada.
- A interface segue com rail fixa no layout de TV; a proposta de recolher rótulos ao sair de foco e expandir/realçar a lateral ao retornar ainda é refinamento, não comportamento validado. A MainActivity de produção não foi aberta nesta rodada.
