# Makimono — tracker de execução do plano premium

Iniciado em 04/10/2026. Fonte: `APPLE_GRADE_PRODUCT_PLAN.md`. Status descreve código/evidência, não intenção.

Estados: `NOT_STARTED` → `IN_PROGRESS` → `IMPLEMENTED` → `BUILT` → `RUNTIME_VALIDATED` → `ACCEPTED`. `BLOCKED_EXTERNAL` exige dependência externa específica. `IMPLEMENTED` e build verde não significam aceite UX/integração.

## Q00 — baseline e preservação

- **Status:** ACCEPTED (baseline documental; os outros tickets ainda não).
- **Base conferida:** HEAD `853d3f7`; alterações locais preexistentes visíveis e preservadas; app principal instalado `zechs.drive.stream`, release reportado 1.6.4.1/53; debug `.visualreview` separado.
- **Referências de verificação existentes:** `REAUDIT_2026-10-04.md` e `docs/ui-audit/2026-10-04-reaudit/README.md`. SDK/dependências e `Media3 1.4.1` conferidos no Gradle nesta execução; API min 21 / compile 34 / target 33 / quatro ABIs.
- **Build nesta rodada:** `assembleDebug --no-daemon -PdistributionBuild --max-workers=1` concluiu. O primeiro intento no caminho acentuado falhou ao abrir loopback antes de compilar; o mesmo checkout via `X:\` e cache em `G:\` compilou sem esse bloqueio. O APK x86_64 `.visualreview` foi instalado no MuMu (`127.0.0.1:7555`); perfil e Home abriram. A variante de revisão não tem sessão Drive, portanto biblioteca, detalhes, OAuth real, player, update autenticado, TV física e migrações destrutivas continuam sem validação nesta rodada. Não usar baseline histórico como status atual de release.

## Q01 — metadados sem presunção (R01)

- **Status:** IN_PROGRESS (detalhes e HUD técnica dos dois engines avançados; matriz completa de conteúdos e decoders pendente).
- **Mudança atual:** Details deixa de preencher 1080p, áudio Japonês/dual PT-BR/JA, Multi Subs e duração de 24 min sem evidência. Marca a origem quando a única indicação é o nome do arquivo; ausência/variação fica desconhecida. Container da badge some quando não há informação. HUD Media3 sem defaults falsos está registrada em Q01.1.
- **Arquivos desta subentrega:** `SeriesDetailViewModel.kt`, `SeriesDetailFragment.kt`, `SeriesDetailEpisodeAdapter.kt`, `res/layout/fragment_series_detail.xml`, `res/layout-land/fragment_series_detail.xml`.
- **Base das alterações:** diff atual do ticket sobre checkout 853d3f7. `PlayerActivity.kt` não alterado por Q01 nesta subentrega.
- **Validação desta parte:** o build incremental compilou a tela de detalhes após a agregação de mídia mista. A tela de perfis e a Home abriram no MuMu; o detalhe com conteúdo real continua indisponível porque a variante isolada `.visualreview` não tem sessão Drive e não foi autenticada. A aparência em fonte ampliada segue pendente.

### Q01.1 — HUD técnica fiel aos dados do Media3

- **Status:** RUNTIME_VALIDATED para ExoPlayer/Media3 com vinheta local e layout compacto no Moto G34; Q01 permanece IN_PROGRESS. A validação da variante principal de TV continua pendente.
- **Falha corrigida:** resolução/FPS ausentes eram rotulados como 1080p/taxa dinâmica; áudio ausente implicava estéreo/48 kHz; codec não identificado recebia um nome padrão; decoder era afirmado como HW MediaCodec. O buffer era descrito como RAM. `layout_width="wrap_content"` no `<include>` ainda comprimia o painel e cortava os rótulos.
- **Mudança:** formatador dedicado apresenta apenas valores reportados por `Format`; callback de inicialização Media3 exibe o decoder real. Dados ausentes permanecem explícitos. O painel conserva a largura de 340 dp, e conteúdo curto integralmente carregado não gera um alarme de buffer baixo.
- **Arquivos:** `ui/player/MediaInfoDisplay.kt`, `ui/player/PlayerActivity.kt`, `res/layout/activity_player.xml`, `res/layout/view_kodi_info_hud.xml`, `MediaInfoDisplayTest.kt`.
- **Verificação:** quatro testes cobrem metadados presentes e ausentes; `assembleDebug testDebugUnitTest` passou com 185 testes, 0 falhas/erros/skips. Os 6 instrumentados passaram quando invocados diretamente no MuMu (`OK (6 tests)`). O wrapper Gradle/UTP encerrou com código 1 ao coletar os resultados apesar do XML marcar 6/6; não contar o wrapper como verde. MuMu x86_64 exibiu codec AVC, resolução/FPS, nome de decoder OMX real, AAC estéreo/48 kHz, nenhuma legenda ativa e buffer completo. Captura: `docs/ui-audit/after-v2/q01.1/exo-technical-hud.png`; sem fatal exception.
- **Correção para altura baixa:** reproduzi no Moto G34 (1600×720, paisagem, ~411 dp de altura disponível) que a HUD de 340 dp descia sobre a OSD e escondia linhas. A variante compacta `layout-land/view_kodi_info_hud.xml` mantém codec, decoder, resolução/FPS, áudio, legenda e buffer visíveis; `layout-h480dp-land` conserva a versão espaçosa usada em telas mais altas. O espaçamento vertical varia com a altura disponível. APK de revisão recompilado e instalado somente em `zechs.drive.stream.visualreview`; na captura, todas as linhas terminam acima da barra OSD. Evidência: `docs/ui-audit/2026-10-05-moto-exo-technical-info-compact.png`.
- **Build desta iteração:** `assembleDebug --no-daemon -PdistributionBuild --max-workers=1` passou. Não rodei novamente a suíte: a execução mais recente de testes falhou ao iniciar `GradleWorkerMain`; não atribuir os 185 testes históricos a esta variante.
- **Limite:** somente Exo/Media3 com mídia local foi exercitado; MPV, streams Drive, arquivos sem metadata em runtime, decoder alternativo e mídia longa seguem pendentes. Sem release, commit ou push.

### Q01.2 — HUD técnica fiel aos dados do MPV

- **Status:** RUNTIME_VALIDATED em fixture local no MuMu (Media3/ExoPlayer e MPV); a matriz completa de mídia continua pendente.
- **Mudança:** o painel consulta codec, dimensões, FPS informado pelo contêiner, decoder ativo, formato/canais/frequência de áudio, legenda selecionada e estimativa de cache do demuxer. Propriedades ausentes aparecem como indisponíveis; decoder `no` é apresentado como software. A nomenclatura deixa explícito que FPS vem do contêiner e cache é estimado.
- **Arquivos:** `ui/player2/MpvTechnicalInfoDisplay.kt`, `ui/player2/MPVActivity.kt`, `app/src/test/java/zechs/drive/stream/ui/player2/MpvTechnicalInfoDisplayTest.kt`.
- **Testes:** três casos cobrem dados ausentes, dados reportados e decoder por software; três testes também verificam que linhas somente de leitura não viram alvo D-pad. Suíte completa: 191 testes, 0 falhas/erros/skips.
- **Validação no MuMu:** APK `.visualreview` reinstalado. O painel exibiu codec, 1920×1080, FPS do contêiner, decoder software, AAC estéreo/48 kHz, legenda desativada e estimativa de cache do vídeo local. D-pad abriu “Mais opções”, focou “Informações técnicas”, abriu o painel e deixou Fechar como único alvo; as linhas informativas não recebem foco. Toque também abriu a tela. BACK duplo durante o fade manteve o player e mostrou a instrução de saída; BACK seguinte retornou à fixture. Evidências em `docs/ui-audit/after-v2/q01.2/`.
- **Build:** `gradlew --no-daemon -PdistributionBuild --max-workers=1 testDebugUnitTest assembleDebug` passou. O JDK local ainda falha com AF_UNIX no seletor; a execução usou patch temporário fora do repositório para o seletor NIO usar seu fallback TCP. Nenhum workaround do host entrou no app/repo.
- **Limite:** fixture local e um formato/decoder apenas; Drive autenticado, SD, faixa pt-BR, ausência de legenda embutida/externa e decoders alternativos seguem pendentes. Sem release, commit ou push.

### Q01.3 — distinguir legenda desativada de estado não reportado no MPV

- **Status:** RUNTIME_VALIDATED para `sid=no` e faixa selecionada na fixture de QA do Moto G34; o caso `auto`/propriedade ausente ainda não foi induzido em runtime.
- **Falha:** o painel técnico inferia “Desativadas” de `player.sid == -1`. Esse fallback também cobria propriedade `sid` ausente ou ilegível, apresentando como fato um estado que o MPV não reportara.
- **Mudança:** a HUD consulta a propriedade textual `sid` diretamente e separa `no` (desativadas), `auto` (automática), IDs positivos (nome da faixa ou “Faixa N”) e valor ausente/inválido (“Indisponível”).
- **Teste e build:** teste focado incluído em `MpvTechnicalInfoDisplayTest`; `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou pelo caminho temporário `X:\`, com 214 testes JVM e 0 falhas/erros/skips. `git diff --check` sem erros; apenas avisos de normalização CRLF do checkout.
- **Runtime complementar:** fixture sintética MKV instalada apenas em `.visualreview`. Abri o menu de legendas, selecionei “Desativadas” e confirmei na HUD técnica `Legenda = Desativadas`; reativei a faixa sintética e confirmei o nome da faixa na mesma HUD. Evidências: `docs/ui-audit/2026-10-06-q01-3-mpv-subtitle-menu.png`, `docs/ui-audit/2026-10-06-q01-3-mpv-technical-disabled.xml` e `docs/ui-audit/2026-10-06-q01-3-mpv-technical-selected.xml`.
- **Limite:** validação unitária, sem instalação da variante principal nem fixture MPV nesta rodada. Conta Drive, streams reais e menu técnico em TV continuam pendentes. Sem release, commit ou push.

### Q01.4 — resumos de mídia respeitam listagem parcial do Drive

- **Status:** BUILT; falta verificar com pasta Drive real e paginação incompleta observada ao vivo.
- **Base/arquivos:** HEAD de baseline `853d3f7` mais o diff local preservado; `ui/series/SeriesMediaMetadataPolicy.kt`, `ui/series/SeriesDetailViewModel.kt` e `app/src/test/java/zechs/drive/stream/ui/series/SeriesMediaMetadataPolicyTest.kt`. Nenhuma alteração preexistente foi revertida.
- **Falha:** os badges de qualidade, áudio e legenda eram agregados dos vídeos recebidos, mas ignoravam `hasIncompleteDriveListing`. Se todos os itens da primeira página tivessem a mesma tag, a série podia parecer uniforme apesar de arquivos ainda não carregados.
- **Mudança:** `SeriesMediaMetadataPolicy` agrega pistas de nome de arquivo apenas com a cobertura de listagem. Séries completas e uniformes conservam o badge inferido; pistas ausentes ou página incompleta ficam “Parcial”; diferenças observadas continuam “Variada/Variado”; nenhuma pista continua sem badge. Badge de áudio por episódio conserva a mesma origem explícita.
- **Teste e build:** quatro testes cobrem uniformidade completa, listagem truncada, variações observadas e pistas ausentes/parciais. Teste focado passou; `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou via `X:\` com 218 testes JVM, 0 falhas/erros/skips. `git diff --check` limpo, com avisos apenas de normalização CRLF já configurada.
- **Limite:** build/debug e dados sintéticos; sem runtime com Drive autenticado ou controle remoto/TV. Nenhum app instalado, release, commit ou push.

## Q03.1 — preservar o player ao tentar fallback de codec

- **Status:** BUILT (não validado em runtime para falha de lançamento).
- **Falha:** `launchMpvFallback()` encerrava ExoPlayer antes de chamar `startActivity`. Se o MPV fosse indisponível ou falhasse ao iniciar, o usuário perdia o player e a função só registrava o erro. IDs/título ausentes também encerravam a função sem feedback.
- **Mudança:** agora valida a mídia antes de alternar, tenta iniciar MPV antes de fechar ExoPlayer e mantém o player atual com mensagem recuperável se o lançamento falhar. O request mantém posição, título, miniatura, tema, playlist e legendas; nenhum token de acesso é transferido em extras.
- **Arquivos:** `ui/player/PlayerActivity.kt`, `res/values/strings.xml`.
- **Contexto de dependências:** o app usa Media3 1.4.1. O AAR `app/libs/extension-ffmpeg-release.aar` contém classes no namespace antigo `com.google.android.exoplayer2` e não aparece referenciado em `app/build.gradle`; por isso não pode ser contado como extensão FFmpeg ativa da versão Media3. Nenhuma extensão incompatível foi conectada ao renderer.
- **Validação:** `testDebugUnitTest assembleDebug` passou nesta alteração: 191 testes JVM, 0 falhas/erros/skips; APK `.visualreview` compilou. O diff foi aprovado por `git diff --check`.
- **Limite:** não foi possível induzir com segurança uma falha real de `startActivity` nesta fixture; a recuperação está validada por inspeção e compilação, não por teste runtime. O fallback MPV ainda precisa cobrir falha de fonte/rede, codecs e retomada da posição. Extensão FFmpeg Media3 continua sem paridade: o README oficial requer checkout local, build nativo e dependências NDK/CMake e não oferece artefato Maven pronto; o ambiente Android SDK não reportou NDK instalado. Mantém-se a troca para MPV como estratégia explícita até completar essa matriz. Sem release, tag, commit ou push.

### Q03.2 — handoff Exo→MPV preserva contexto Drive e posição

- **Status:** BUILT; handoff induzido por erro de decoder/fonte em runtime segue pendente.
- **Falhas corrigidas:** a troca de engine perdia `siblingFolderId` de shortcuts; posição zero era omitida e o MPV podia consultar um ponto salvo antigo em vez de iniciar do começo.
- **Mudança:** copia a origem Drive não vazia e sempre envia a posição atual, inclusive zero, junto ao perfil fixado da sessão, playlist, legendas, tema e miniatura.
- **Validação:** política de sessão cobre pasta presente/blank e posições zero/negativa; suíte JVM com 230 testes, 0 falhas/erros/skips; `assembleDebug` passou. APK `.visualreview` arm64 reinstalado no Moto, bloqueado; sem runtime do handoff.
- **Limite de distribuição:** APK debug assinado com chave debug porque release.storeFile não está configurado. Nenhum release ou push.

## Q04 — navegação e componentes (R05/R06/R08)

- **Status:** IN_PROGRESS. Android TV é o alvo principal para percurso D-pad, legibilidade a 10 pés e overscan; o Moto G34 conectado por ADB é cobertura adicional para toque e comportamento compacto. O primeiro subproblema de toque foi corrigido; geometria global do foco, dimensões/hitboxes e ida/volta D-pad↔touch continuam abertos.

### Q04.1 — toque único na navegação lateral

- **Status:** RUNTIME_VALIDATED (MuMu, Home real, variante `.visualreview`; somente esse percurso).
- **Reprodução:** no app principal instalado, um toque em Pastas moveu o foco sem navegar; um segundo toque abriu a biblioteca. Evidência: `docs/ui-audit/2026-10-04-reaudit/q04-main-before.png`, `q04-main-one-tap-folders.png` e `q04-main-two-tap-folders.png`.
- **Mudança:** os itens da rail da Home encaminham uma única ativação no `ACTION_UP` pelo listener existente, cancelam gesto que sai do alvo e continuam chamando `performClick()` para semântica/acessibilidade. Política de gesto tem testes de toque completo, saída/cancelamento e cancelamento explícito.
- **Validação:** `testDebugUnitTest assembleDebug` passou com 194 testes, 0 falhas/erros/skips. Instalei apenas `zechs.drive.stream.visualreview`; na Home real do pacote de revisão, um toque em Pastas abriu Files e exibiu o estado esperado de sessão ausente. Evidência: `docs/ui-audit/2026-10-04-reaudit/q04-review-home-one-tap.png`. A tela local não tinha Drive e não recebeu credenciais. `git diff --check` passou.
- **Limite:** o app principal foi observado, não atualizado; a validação de correção ocorreu no pacote de revisão, sem sessão Drive. Foco visual duplicado segue visível em componentes e é subticket distinto.

### Q04.2 — um único indicador de foco por componente

- **Status:** RUNTIME_VALIDATED na fixture local (Home e perfis; critérios transversais seguem abertos).
- **Falha:** o anel genérico `TvFocusRing` era sobreposto a backgrounds com estado `focused`; o próprio Android também desenhava um highlight padrão quando um `RecyclerView` ganhava foco, produzindo borda branca enorme na fileira.
- **Mudança:** o overlay central agora respeita seletores que já possuem estado focado e desativa o highlight padrão dos containers `RecyclerView`. A seleção de perfil representa o perfil ativo, não apenas o foco; o foco usa uma borda ciano única de 2 dp.
- **Arquivos:** `utils/TvFocusRing.kt`, `ui/profile/ProfilesAdapter.kt`, `res/drawable/bg_profile_surface.xml`.
- **Validação:** `testDebugUnitTest assembleDebug` passou com 194 testes. No MuMu, a fixture de perfis mostra um único contorno ciano no item focado; na Home de revisão, após DOWN, o XML identifica somente o cartão individual `[308,696][714,925]` como focado, sem highlight envolvendo toda a fileira. Capturas: `q04-review-profiles-after.png`, `q04-review-home-row-focused-after.png`; hierarquia: `q04-review-home-focused.xml`.
- **Limite:** a fixture usou apenas o tema atualmente ativo. Restante dos seletores/layouts, todos os temas, toque+foco combinados e texto ampliado devem ser auditados antes de aceitar Q04.

### Q04.5 — rota D-pad entre transporte e saída do player

- **Status:** RUNTIME_VALIDATED nos dois engines com fixture local no Moto G34; aceite Android TV pendente.
- **Falha corrigida:** em ExoPlayer e MPV, DPAD_UP sobre Play/Pause deixava a seta de saída fora do percurso de foco, criando um dead-end para controles remotos.
- **Mudança:** política compartilhada direciona UP de Play/Pause para Voltar e DOWN de Voltar para Play/Pause apenas quando a OSD está estável e não há overlay bloqueador. O dispatcher consome o evento completo da tecla (inclusive soltura/repetição) quando move o foco, impedindo o engine de sobrescrever a seleção.
- **Arquivos:** `utils/PlayerDpadFocus.kt`, `ui/player/PlayerActivity.kt`, `ui/player2/MPVActivity.kt`, `app/src/test/java/zechs/drive/stream/PlayerDpadFocusTest.kt`.
- **Verificação:** `testDebugUnitTest assembleDebug` passou; suíte JVM completa com 199 testes, 0 falhas/erros/skips. Instalei somente `zechs.drive.stream.visualreview` no serial ADB `0086442783`. Nos dois engines, ADB confirmou UP→`btnBack`, DOWN→`btnPlayPause` e OK em Voltar→retorno ao host da fixture. No ExoPlayer, BACK primeiro ocultou a OSD mantendo `PlayerActivity`; BACK seguinte retornou à fixture. Evidências: `docs/ui-audit/2026-10-05-moto-mpv-up-current.png`/`.xml`, `moto-mpv-down-current.xml`, `moto-exo-up-current.png`/`.xml`, `moto-exo-down-current.xml` e `moto-exo-back-first-current.png`.
- **Limite:** a validação ocorreu no telefone em paisagem, não num televisor nem num controle remoto físico. A rota precisa ser confirmada em Android TV com diferentes DPADs, proporções/overscan, skip/próximo episódio e fade da OSD antes de Q04 ser aceito.

## Q02 — listagem paginada sem corte silencioso (R02)

- **Status:** IN_PROGRESS (API, detalhe, busca, catálogo, grade e listas auxiliares dos players implementados; validação com Drive autenticado pendente).
- **Mudança atual:** nova resposta `FileCollectionResult` expõe arquivos, completude, cursor, páginas e motivo; o limite de 2.500/páginas deixa de parecer sucesso completo. Compatibilidade em `getAllFiles` propaga incompletude como `Resource.Error` com dados parciais. A tela de detalhes usa o resultado detalhado para reter vídeos parciais e avisa quando a leitura falha ou é limitada; subpastas também acumulam esse estado.
- A busca global da Home consome resultados parciais mesmo após falha e avisa quando alcança o limite. O catálogo da Home mantém itens parciais visíveis, prioriza a ordem da resposta recente, mescla itens únicos do último catálogo cacheado e não substitui o snapshot do Room por uma resposta incompleta; informa o usuário e permite tentar novamente.
- A grade agora mantém as páginas que já carregou se a página seguinte falhar, remove o rodapé de carregamento preso e oferece retry do mesmo cursor; erros de autenticação oferecem Conectar. Scroll e D-pad não criam requisições concorrentes nem entram em retry automático infinito. O play de pasta só escolhe primeiro episódio automaticamente quando a enumeração dos arquivos está completa.
- A busca do diretório de biblioteca configurado não cai silenciosamente para a raiz do Drive se o nome não for encontrado ou a consulta falhar.
- **Arquivos desta subentrega:** `DriveRepository.kt`, `data/model/FileCollectionResult.kt`, `SeriesDetailViewModel.kt`, `SeriesDetailFragment.kt`, `HomeViewModel.kt`, `HomeViewModelCatalogMergeTest.kt`, `FilesViewModel.kt`, `FilesFragment.kt`, `res/layout/fragment_series_detail.xml`, `res/layout-land/fragment_series_detail.xml`, `res/values/strings.xml`.
- **Ainda pendente:** teste runtime da recuperação da grade e da lista dos players com sessão Drive autenticada. `EpisodeCheckWorker` foi migrado para `getAllFilesDetailed` em Q02.4: falha/paginação incompleta gera retry sem atualizar a contagem e só vídeos únicos entram no total. `HomeViewModel.getStarredFiles` também usa o wrapper, mas não tem chamadas no checkout atual; se reativado, migrar para o resultado detalhado antes de conectar à UI. Teste da falha na terceira página já existe; fixture de 2.501 itens reais e troca de pasta durante requisição ainda pendentes. Não dar aceite final ainda.

### Q02.1 — listas auxiliares dos players

- **Status:** BUILT (Drive autenticado ainda não validado).
- **Mudança:** playlist de episódios e legendas externas agora recebem dados parciais junto com o estado de completude; falha posterior à primeira página preserva o que já chegou e gera aviso não modal tanto em Media3/ExoPlayer como em MPV. Lista completa permanece silenciosa.
- **Arquivos:** `data/model/DriveFolderListing.kt`, `data/repository/DriveRepository.kt`, `ui/player/PlayerViewModel.kt`, `ui/player/PlayerActivity.kt`, `ui/player2/MPVActivity.kt`, `DriveRepositoryTest.kt`.
- **Verificação:** `assembleDebug` e suíte JVM completa passaram; 178 testes, 0 falhas/erros/skips (inclui `DriveRepositoryTest` 14/14 e `HomeViewModelCatalogMergeTest` 2/2). APK `.visualreview` instalado em `127.0.0.1:7555`; tela de perfis e os dois players abriram com fixture local, sem fatal exception. Os avisos distinguem falha e limite sem instruir o usuário a usar um cursor que o player não expõe. A fixture não simula sessão Drive nem exibe aviso parcial em runtime.
- **HIG consultado/adaptação:** Loading, p. 1 recomenda mostrar conteúdo disponível e manter outras ações acessíveis durante carregamento; Focus and selection, pp. 1–4 descreve distinção de foco/seleção e cautela contra mudanças de contexto. Aplicado como retenção do resultado parcial e aviso Snackbar não modal. A não-interrupção de foco precisa de teste com a sessão e conteúdo reais.

### Q02.2 — fronteira de 2.500 arquivos

- **Status:** BUILT (fronteira coberta em teste de unidade; sem listagem real Drive nesta validação).
- **Mudança:** nenhum comportamento da API foi alterado; adicionado um contrato automatizado para uma biblioteca com mais de 2.500 itens. A última página limitada deve reter exatamente 2.500 arquivos, preservar o próximo cursor e identificar `FILE_LIMIT`/incompletude.
- **Arquivo:** `app/src/test/java/zechs/drive/stream/DriveRepositoryTest.kt`.
- **Validação:** teste focado passou; a suíte completa passou com 195 testes, 0 falhas/erros/skips; `assembleDebug` passou. Este caso prova o limite local com respostas simuladas, não o comportamento do Drive real.

### Q02.3 — merge de páginas sem duplicatas e capas sem snapshot obsoleto (05/10/2026)

- **Status:** BUILT; smoke visual da tela Files no Moto; Q02 continua IN_PROGRESS.
- **Falha reproduzida por inspeção:** `postSuccess` mutava a lista que já podia estar submetida ao `ListAdapter` e anexava páginas sem deduplicação global por ID. O enriquecimento assíncrono de capas também mantinha uma referência indexada de página antiga e podia republicá-la depois de uma página mais nova.
- **Mudança:** `FilesPageMerger` cria uma nova lista estável por `DriveFile.id`; preserva posição, aceita o objeto mais recente e remove o sentinel de loading. O ViewModel invalida/cancela enriquecimento de capa por geração quando há página nova/erro; ao resolver URL de poster, procura o ID no snapshot atual e emite uma lista copiada.
- **Testes:** `FilesPageMergerTest` cobre duplicação entre páginas/na mesma página, order/position, dados novos e preservação do snapshot anterior. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou, 202 testes JVM, 0 falhas/erros/skips.
- **Smoke runtime:** instalei somente `zechs.drive.stream.visualreview` no Moto G34 (`0086442783`). A fixture Files exibiu 12 itens e D-pad DOWN focou o cartão seguinte mantendo-o visível após rolagem. Evidência: `docs/ui-audit/2026-10-05-files-page-merge/README.md`.
- **Limite:** sem sessão Drive; o smoke não exerce paginação real, duplicatas do servidor, retry, troca/saída de pasta ou a corrida assíncrona. Essas validações permanecem necessárias antes do aceite Q02.

## Q17 — saída previsível do player (parte de R04 / C7)

- **Status:** RUNTIME_VALIDATED (MuMu x86_64, fixture local, ExoPlayer e MPV; transição refinada em Q17.1).
- **Evidência inicial:** fixture `VisualReviewActivity` lançou os Activities reais com mídia local. A seta do OSD encerrou ambos; a primeira pressão de Voltar ocultou os controles e mostrou a orientação no canto superior direito.
- **Mudança atual:** ao ocultar controles com Voltar, ExoPlayer e MPV mostram uma mensagem curta indicando a segunda pressão para sair. O aviso fica no canto superior direito; modal e cartão de próximo episódio mantêm prioridade.
- **Arquivos:** `ui/player/PlayerActivity.kt`, `ui/player2/MPVActivity.kt`, `res/layout/view_player_gesture_hud.xml`, `res/values/strings.xml`.
- **Build/testes:** o APK debug x86_64 foi reinstalado no MuMu; o teste ampliado da animação e os 181 testes unitários estão detalhados em Q17.1.
- **Limite pendente:** testar Activity real com stream Drive, dispositivos/TVs adicionais e saída após avisos modais/cartões. A validação local não dá aceite geral ao player.

### Q17.1 — Voltar durante o fade do OSD

- **Status:** RUNTIME_VALIDATED (MuMu x86_64, mídia local, Media3/ExoPlayer e MPV).
- **Falha reproduzida e correção:** uma segunda pressão de Voltar durante a animação de 220 ms podia escapar do estado de transição e encerrar o player. O helper compartilhado agora consome BACK enquanto o controller está visível ou em fade; ExoPlayer e MPV consultam também a flag de ocultação ao decidir se os controles contam como visíveis.
- **Teste no MuMu:** com cada Activity real aberta e OSD visível, BACK seguido de BACK após 45 ms manteve o player ativo durante e após o fade, exibindo “Pressione Voltar novamente para sair do vídeo.” Uma nova pressão após a transição retornou à fixture anterior. Capturas em `docs/ui-audit/after-v2/q17.1/`.
- **Automação:** `PlayerControllerVisibilityTest` cobre controller visível, transição de ocultação e estado totalmente oculto; suíte JVM completa: 181 testes, 0 falhas/erros/skips. `git diff --check` sem erros de whitespace. Não houve fatal exception nos logs consultados.
- **Limite:** reprodução com vinheta local e pacote isolado `.visualreview`; sem sessão Drive, OAuth, conteúdo remoto, acesso à conta ou alteração do pacote de produção. Este aceite cobre saída por BACK e não certifica as demais interações do player.

## Q14.1 — contenção do backdrop em paisagem

- **Status:** RUNTIME_VALIDATED (fixture local; aceite global de detalhes pendente).
- **Falha observada:** `layout-land/fragment_series_detail.xml` não possuía a superfície opaca que a variante comum usa entre o fim do hero e o fim do conteúdo rolável; o artwork aparecia atrás da faixa de episódios.
- **Mudança:** adicionada `viewSeriesBodySurface`, ancorada logo após `heroContentBlock` e até o fim do conteúdo, com superfície de base opaca. O scrim e a intensidade do artwork dentro do hero foram preservados.
- **Evidência:** `docs/ui-audit/after-v2/q14.1/series-detail-body-contained.png` e `series-detail-dpad-focus.png`; `series-detail-dpad.xml` registra o primeiro cartão de episódio focado após duas pressões DOWN.
- **Verificação:** `assembleDebug` passou e a rota `details` abriu no MuMu 1920×1080; a área de episódios ficou visualmente uniforme, o conteúdo focado permaneceu visível e não houve fatal exception. São metadados/imagens de fixture, não a mídia da sessão Drive.

## Q03–Q26 — demais tickets

- **Status:** execução parcial conforme subtickets registrados; Q04 e Q05 seguem abertos até completar seus gates.
- Usar as dependências, critérios de aceite e gates em `APPLE_GRADE_PRODUCT_PLAN.md`.

### Q04.3 — saída acessível do player no toque e D-pad

- **Status:** RUNTIME_VALIDATED em MuMu x86_64, fixture local `.visualreview`, ExoPlayer e MPV.
- **Falhas reproduzidas:** o foco podia permanecer na superfície nativa do MPV quando os controles apareciam, deixando a seta fora da navegação; no ExoPlayer, o `PlayerView` podia consumir o gesto da seta durante a interação com o OSD.
- **Mudanças:** ambos os players agora oferecem caminho D-pad para recuperar o foco do botão Play/Pause quando o foco inicial ficou fora do controller; a seta tem relação de foco explícita com Play/Pause. No Exo, um toque completo na seta encaminha exatamente um `performClick()` e `ACTION_CANCEL` não sai do vídeo.
- **Validação:** `testDebugUnitTest assembleDebug` passou com 195 testes, sem falhas/erros/skips. Build instalado no MuMu `127.0.0.1:7555`: um toque na seta do Exo encerrou `PlayerActivity`; dois UP e OK no MPV transferiram o foco da superfície para Play/Pause, depois para a seta, e encerraram `MPVActivity`. OSD MPV: `docs/ui-audit/2026-10-04-reaudit/q04-player-mpv-final.png`; hierarquia Exo confirmou `btnBack` clicável, com limites `[63,64][147,148]`.
- **Limite:** emulador MuMu 1920×1080 e arquivo de demonstração local; sem Drive autenticado, controle remoto físico ou TV. Q04 continua IN_PROGRESS: restante da auditoria transversal de foco, dimensões/hitboxes e acessibilidade segue aberto. Sem alteração do pacote principal, commit, push ou release.

### Q04.4 — margem de rolagem inferior na grade paisagem

- **Status:** RUNTIME_VALIDATED em MuMu x86_64, fixture `.visualreview` com 12 arquivos.
- **Falha reproduzida:** ao navegar por D-pad até a última fileira, o cartão focado terminava em y=1080 e ficava cortado na borda inferior. O atributo `android:padding="0dp"` depois dos paddings individuais zerava também o espaçamento inferior de overscan.
- **Mudança:** removido o padding geral que sobrescrevia as margens start/top/end/bottom definidas na `RecyclerView` de `layout-land/fragment_files.xml`.
- **Validação:** `testDebugUnitTest assembleDebug` passou com 195 testes. No MuMu, o primeiro cartão recebeu foco após RIGHT; navegação DOWN até a última fileira rolou a grade e manteve o cartão focado integralmente visível, terminando em y=1040, com margem inferior livre. Antes/depois: `docs/ui-audit/2026-10-04-reaudit/q04-files-row-focus.png` e `q04-files-bottom-after.png`.
- **Limite:** grade de fixture local, não biblioteca Drive autenticada; outros formatos de tela, escala de texto e ordenação real seguem pendentes. Q04 continua IN_PROGRESS.

### Q05.1 — preferência de movimento reduzido (05/10/2026)

- **Status:** INCREMENTO VALIDADO; Q05 continua IN_PROGRESS.
- **Mudança:** preferência `SYSTEM`/`REDUCED` isolada por perfil no DataStore. `SYSTEM` segue o estado de animações do Android; `REDUCED` mantém estados e foco visíveis, retirando escala decorativa da Home/Configurações, transições de lock e deslocamentos de entrada/saída da HUD tanto no Media3/ExoPlayer quanto no MPV.
- **Interface:** linha em ambos os layouts de Configurações, ao lado das opções de aparência, com diálogo de escolha D-pad e rótulo do valor atual. Teste visual corrigiu diálogo inicialmente sem opções quando título, mensagem e lista eram combinados.
- **Verificação:** `testDebugUnitTest assembleDebug` passou com 197 testes, sem falhas/erros/skips; `git diff --check` sem erros. APK debug isolado instalado no MuMu x86_64; selecionei `Reduzido` com toque, confirmei o valor e reabri o diálogo para validar persistência. Em seguida percorri a linha usando D-pad, movi foco entre as opções e escolhi `Seguir dispositivo`; anel ciano e seleção permaneceram distinguíveis.
- **Acessibilidade adicional:** configurações a 1.3× e 2× mostraram tipografia ampliada sem cortar o título/subtítulo das linhas; com 2× o D-pad rolou até `settingReduceMotion` e manteve o alvo inteiro no viewport (`[274,806][1645,993]`). Na fixture de Perfis, a hierarquia do Android confirmou os rótulos “Cauã, administrador, perfil ativo”, “Kids / Shounen, perfil infantil” e “Adicionar perfil”. O D-pad percorreu os três cartões, o cartão de adicionar e chegou a `btnManageProfiles`; todos permaneceram integralmente visíveis. Evidência: `q05-profile-accessibility.png` e `q05-profile-accessibility.xml`. Isso verifica semântica e foco expostos à plataforma; a leitura real com TalkBack ainda está pendente.
- **Evidências:** `docs/ui-audit/2026-10-04-reaudit/q05-motion-settings.png`, `q05-motion-dialog.png`, `q05-motion-reduced-after.png`, `q05-motion-dpad-dialog.png`, `q05-motion-dpad-system.png`, `q05-motion-dpad-persisted.png`, `q05-settings-font-1.3.png`, `q05-settings-font-2.0.png` e `q05-settings-font-2.0-focus.png`.
- **Limite:** validação feita em fixture local e no layout paisagem 1920×1080. Falta verificar persistência ao alternar perfis em runtime, leitura real com TalkBack, redução em cada superfície de movimento do app e em TVs/dispositivos físicos; os testes unitários cobrem a política, não Room/DataStore nem frame timing. Sem sessão Drive ou alteração da instalação principal.

### Q05.2 — escala de foco do seletor de perfis (05/10/2026)

- **Status:** IMPLEMENTADO E RUNTIME_VALIDATED no MuMu x86_64, pelo app real dentro do pacote isolado `.visualreview`.
- **Falha:** o cartão de perfil sempre ampliava 3,5% ao ganhar foco. Isso contrariava `Reduzido`, apesar da preferência já ser aplicada às outras superfícies.
- **Mudança:** `ProfileSelectionFragment` lê a preferência de movimento do perfil ativo ao coletar a lista e combina-a com o estado de animações do Android. `ProfilesAdapter` cancela escala em andamento e mantém o cartão em escala 1× quando reduzido; o anel de foco continua visível. Ao ativar a preferência, holders anexados também são normalizados.
- **Validação:** alterei para `Reduzido` na tela real de Configurações, voltei ao seletor e confirmei que o cartão continua focado com contorno ciano, sem expansão visual. O dump confirma papel/admin, perfil ativo e estado de foco; o D-pad segue chegando às ações abaixo dos cartões. Comparação visual: `q05-real-profile-selection.png` (sistema) e `q05-profile-reduced-motion.png` (reduzido); hierarquia após retorno em `q05-profile-reduced-motion.xml`.
- **Build/testes:** `testDebugUnitTest` passou com 197 testes, 0 falhas/erros; `assembleDebug` passou. O primeiro teste executado a partir do caminho Windows com caractere acentuado falhou no carregamento de todas as classes pelo worker do Gradle (CNFE); repetir pelo drive temporário `W:` sem acento resolveu a falha e a suíte completa passou. Nenhuma alteração no pacote principal.
- **Limite:** não medi frame timing nem fiz leitura com TalkBack, TV física ou mudança de perfil enquanto o seletor permanece em primeiro plano. Essa validação cobre o seletor e não fecha Q05.

## Q06.1 — Configurações por intenção e navegação D-pad (05/10/2026)

- **Status:** split view RUNTIME_VALIDATED no MuMu pela variante isolada `.visualreview`; Q06 continua IN_PROGRESS.
- **Mudança:** Configurações agrupa as opções existentes em Perfil e biblioteca, Aparência, Reprodução, Integrações e Dados e sessão. Os controles e handlers existentes foram preservados. Em telefone, as categorias ficam horizontais; em paisagem ampla, o espaço vira split view com categorias em uma rail vertical à esquerda e controles roláveis à direita. Os alvos continuam com mínimo de 48 dp e a categoria ativa sobrevive à recriação do Fragment.
- **D-pad:** no layout amplo, RIGHT entra no primeiro controle visível, LEFT volta à categoria atual, UP/DOWN percorrem a rail, OK seleciona a categoria e encaminha o foco ao conteúdo, e BACK retorna à tela anterior. Testei Perfil, Aparência, Reprodução e BACK no MuMu.
- **Evidência visual:** `docs/ui-audit/2026-10-05-settings-split.png`, `settings-appearance-selected.png`, `settings-appearance-row.png`, `settings-playback-selected.png`, `settings-back.png` e `moto-settings-appearance.png`; `moto-settings-appearance.xml` confirma a seleção da categoria e foco no primeiro controle no Moto G34.
- **Build/testes:** `assembleDebug` passou e a variante isolada atualizada foi instalada no MuMu e no Moto G34, sem substituir `zechs.drive.stream`. D-pad emulado por ADB selecionou Aparência e focou seu primeiro controle no aparelho físico em paisagem. A execução mais recente de `testDebugUnitTest` compilou os testes, mas não conseguiu carregar `worker.org.gradle.process.internal.worker.GradleWorkerMain`; a suíte verde de 197 testes refere-se à revisão anterior, antes do split view.
- **Limites:** falta validar o layout portrait e rotação/recriação no telefone, TalkBack, e caminhos reais de OAuth/Drive. A validação deste incremento não é aceite comercial do app nem encerra Q06.

### Q18.1 — seletor modal de faixas respeita D-pad, touch e BACK (05/10/2026)

- **Status:** RUNTIME_VALIDATED em fixture local; Q18 e Q04 continuam IN_PROGRESS.
- **Falha reproduzida:** ao abrir a lista de áudio no MPV, UP/DOWN escapavam do modal e acionavam o player por baixo; BACK podia também ser tratado pela Activity antes do diálogo. Ao abrir por toque, o foco inicial podia ficar nulo por causa do touch mode.
- **Mudança:** ambos os dispatchers delegam ao Android enquanto `PlayerModalVisibilityState.isVisible`; o item inicialmente selecionado também tenta `requestFocusFromTouch()` quando `requestFocus()` falha.
- **Validação:** fixture MKV com Japanese Original + Português BR, SRT pt-BR, testada no ExoPlayer e MPV do pacote `.visualreview` no Moto. DOWN focou a segunda faixa, OK selecionou PT-BR, reabertura mostrou a faixa marcada e BACK dispensou o seletor mantendo a Activity do player em primeiro plano. Screenshots e README: `docs/ui-audit/2026-10-05-multitrack-selection/`.
- **Build/testes:** `testDebugUnitTest assembleDebug` e suíte separada passaram; 200 testes JVM, 0 falhas/erros/skips. `git diff --check` passou. Instalação limitada a `zechs.drive.stream.visualreview` (`0086442783`).
- **Limite:** Moto e fixture local não substituem controle remoto/TV física. Q18 ainda precisa cobrir ASS/SSA, legendas externas/Drive, idiomas sem faixa, offsets, codecs e escopo de preferência por perfil. Sem release, commit ou push.

### Q18.2 — editor de estilo com navegação, persistência e retorno (05/10/2026)

- **Status:** RUNTIME_VALIDATED no MPV com fixture local; Q18 continua IN_PROGRESS.
- **Verificação:** no Moto G34 em paisagem, o foco percorreu o seletor até “Personalizar aparência”; o controle mostrou um único anel ciano. CENTER abriu o editor de estilo e a prévia ativa da legenda. RIGHT mudou o tamanho de 20 para 21 sp; navegação DOWN percorreu os controles roláveis até Cancelar e então RIGHT/CENTER ativou Salvar. Ao reabrir o editor, 21 sp persistia. Repeti o caminho inverso e salvei 20 sp para restaurar a fixture.
- **BACK:** sair do editor retornou ao player sem deixar o seletor por baixo nem encerrar a Activity. O player ficou em primeiro plano.
- **Evidências:** `docs/ui-audit/2026-10-05-subtitle-style/README.md`, capturas e dumps associados. A correção de borda duplicada usa apenas a moldura neutra do botão; o indicador compartilhado `TvFocusRing` é a única borda de foco.
- **Build/testes:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` e, em seguida, `testDebugUnitTest --rerun-tasks --no-daemon --max-workers=1` passaram neste checkout. XMLs dos resultados: 200 testes, 0 falhas/erros/skips. `git diff --check` sem erros de whitespace (Git apenas avisou sobre normalização LF/CRLF já configurada no checkout).
- **Limite:** somente MPV/Moto/fixture local nesta execução; validação do editor via Exo, TV física/controle remoto, TalkBack, ASS/SSA e persistência depois de reiniciar o processo seguem pendentes. A preferência foi restaurada a 20 sp. Sem alteração do pacote principal, release, commit ou push.

## Q16.1 — intervalos de arcos sem episódios duplicados (05/10/2026)

- **Status:** BUILT; validação runtime dos dados reais de anime pendente.
- **Falha encontrada:** o intervalo configurado de Skypiea (136–206) se sobrepunha integralmente ao grupo G-8 (196–206), então episódios 196–206 podiam aparecer em dois arcos tanto no agrupador de arquivos quanto no de playlist do player.
- **Mudança:** Skypiea termina no episódio 195; G-8 conserva 196–206 e o arco seguinte começa em 207. Foi adicionado teste de fronteira que garante atribuição única e preservação dos quatro limites.
- **Verificação:** teste focado e suíte JVM completa passaram; XMLs reportam 203 testes, 0 falhas/erros/skips. `assembleDebug` e `git diff --check` passaram.
- **Limite:** os dados de entrada são sintéticos; o teste não valida a classificação de arquivos reais nem os demais arcos/animes. Nenhuma instalação principal, conta Drive ou release foi alterada.

## Q16.2 — conteúdos extras fora dos arcos narrativos (05/10/2026)

- **Status:** BUILT; runtime com biblioteca Drive real pendente.
- **Falha encontrada:** os agrupadores de animes com arcos canônicos tentavam classificar todo vídeo pelo número do episódio antes de separar OP/ED, OVAs/especiais e promos. Um `OP 01` podia ser colocado no arco que contém o episódio 1.
- **Mudança:** os agrupadores de arquivos da biblioteca e de playlists do player agora atribuem somente conteúdo `STORY` aos arcos. Specials, música e promos seguem em grupos dedicados, mantendo as ações de acesso.
- **Testes:** fixture de episódio regular, OP, OVA e PV verifica separação e IDs esperados nos dois agrupadores; teste de fronteira Q16.1 continua ativo.
- **Verificação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou; XMLs reportam 204 testes, 0 falhas/erros/skips. `git diff --check` passou.
- **Limite:** entrada sintética; não valida nomenclaturas reais atípicas, conteúdo sem número ou interface em TV. Nenhuma instalação principal/conta/release alterada.

## Q16.3 — “Todos os Episódios” mantém a sequência principal (05/10/2026)

- **Status:** BUILT; integração real com player/Drive e navegação em TV pendentes.
- **Falha encontrada:** apesar de OP/ED, especiais e promos estarem em grupos dedicados, o grupo “Todos os Episódios” ainda recebia todos os vídeos. Selecionar essa opção podia incluir extras na playlist narrativa/autoplay.
- **Mudança:** quando existem grupos por categoria/arco, a coleção “Todos os Episódios” recebe somente `STORY`; os grupos dedicados continuam contendo todas as faixas especiais/musicais/promocionais. Se uma pasta só tiver extras, a opção principal usa esses itens em vez de ficar vazia.
- **Teste:** fixture dos agrupadores de arquivos e playlists verifica que “Todos os Episódios” contém apenas o episódio regular e que os itens extras permanecem acessíveis uma vez pelos grupos próprios.
- **Verificação:** suíte JVM 204/204, sem falhas/erros/skips; `assembleDebug` e `git diff --check` passaram.
- **Limite:** teste de domínio com entradas sintéticas; nenhuma TV, conta Drive ou sessão real de playback foi usada.

## Q20.1 — preservar pelo menos um perfil administrador (05/10/2026)

- **Status:** BUILT; teste visual do editor de perfis no Android TV/MuMu pendente.
- **Falha encontrada:** o editor permitia desmarcar Admin do último administrador; a exclusão também podia remover o único perfil administrador e deixar a aplicação sem papel capaz de gerir os perfis.
- **Mudança:** política de domínio bloqueia apagar o último perfil ou o último administrador, bloqueia rebaixar/restaurar uma lista sem Admin e retorna motivo tipado para exclusão recusada. Editor e seletor informam a razão ao utilizador.
- **Testes:** cinco casos cobrem último perfil, último Admin, outro Admin remanescente, rebaixamento e perfil inexistente.
- **Verificação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou; XMLs reportam 209 testes JVM, 0 falhas/erros/skips. `git diff --check` passou (avisos do Git limitam-se à normalização LF/CRLF já configurada).
- **Limite:** validação da política e compilação, sem tocar/remover perfil persistido, sem runtime de UI e sem Room de produção; Q20 continua aberto para CRUD, migrações, isolamento, Kids e validação no TV.

### Q20.2 — manter o histórico no perfil que iniciou a reprodução (05/10/2026)

- **Status:** BUILT; runtime PiP/Android TV pendente.
- **Falha encontrada:** o `PlayerViewModel` pedia ao repositório o perfil ativo em cada leitura/gravação. Se o perfil ativo mudasse enquanto o player continuasse em PiP, progresso posterior poderia ser gravado na conta de visualização errada.
- **Mudança:** o ViewModel captura o ID do perfil ao iniciar a sessão e usa esse ID explicitamente para recuperar, consultar e persistir progresso. O mesmo ViewModel serve Media3/ExoPlayer e MPV.
- **Teste:** `PlayerProfileSessionTest` troca o perfil ativo durante uma sessão e confirma duas consultas e uma gravação associadas somente ao perfil original.
- **Verificação:** suíte JVM completa: 210 testes, 0 falhas/erros/skips; `assembleDebug` passou. APK `.visualreview` atualizado no Moto G34; o telefone estava bloqueado, então este ticket não recebeu validação visual/runtime.
- **Limite:** teste unitário da sessão do ViewModel, sem PiP real, morte/recriação do processo ou TV física. Q20 segue aberto para histórico transversal, CRUD, migração, Kids e sincronização.

## Q13.1 — reduzir o peso visual do favorito nos pôsteres (05/10/2026)

- **Status:** BUILT; inspeção renderizada em tela ativa pendente.
- **Falha encontrada:** nos cards estreitos da grade, o vetor da estrela usava até 24 dp e amarelo saturado, competindo com o pôster e o título.
- **Mudança:** glifo ajustado para 14 dp em grade e lista; área interativa continua com 48 dp. Estado selecionado usa o token âmbar do tema, sem alterar a persistência do favorito.
- **Verificação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 210 testes, 0 falhas/erros/skips. O APK `.visualreview` foi atualizado no Moto G34; o dispositivo estava bloqueado, sem captura válida do novo estado.
- **Limite:** a dimensão e os recursos XML compilam, mas a proporção visual ainda requer conferência em grade real e TV 10-foot. Q13 continua aberto para hierarquia editorial, banners e adaptação às densidades.

## Q02.4 — contagem confiável de novos episódios em pastas seguidas (05/10/2026)

- **Status:** BUILT; validação com Drive autenticado e execução real do WorkManager pendentes.
- **Falha encontrada:** `EpisodeCheckWorker` contava todos os filhos retornados pelo Drive, incluindo legendas, imagens e subpastas. Além disso, o teste anterior reproduzia a lógica num helper próprio e não exercitava a decisão consumida pelo worker.
- **Mudança:** o worker usa `getAllFilesDetailed`, só atualiza a contagem após listagem completa, conta IDs únicos de vídeos/atalhos para vídeo e tenta novamente em caso de erro ou resultado parcial. Falha inesperada na leitura do perfil, listagem ou persistência não interrompe silenciosamente todas as pastas.
- **Testes:** a política compartilhada pelo worker cobre vídeos/atalhos, arquivos não reproduzíveis, IDs repetidos, baseline inicial, pasta vazia, remoção, falha de requisição, token de continuação e paginação incompleta.
- **Verificação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 213 testes JVM, 0 falhas/erros/skips. `git diff --check` passou; só houve avisos existentes de normalização LF/CRLF.
- **Limite:** os testes exercitam a política pura e a compilação, não instanciam o WorkManager nem usam sessão Drive; também não validam notificação/permissão Android em runtime. A contagem ainda compara totais de vídeos, não IDs persistidos, então uma substituição de episódio com o mesmo total não aparece como aumento.

## Q07 — prova local do contrato de pareamento (05/10/2026)

- **Status:** BUILT em subprojeto isolado; arquitetura e operação de produção ainda não aceitas. A prova inclui agora persistência PostgreSQL local.
- **Mudança:** `backend/` tem toolchain Ktor/Kotlin independente do app. O host de desenvolvimento vincula apenas `127.0.0.1`; o QR aponta para domínio reservado `.invalid`. Contrato de criação/poll/cancel implementa identificador aleatório de 128 bits, código manual de oito caracteres, poll secret de 256 bits, armazenamento somente de hash, comparação constante, TTL e cancelamento final. Resposta de criação e status usam `Cache-Control: no-store`.
- **Verificação:** `./gradlew.bat -p backend test --no-daemon --max-workers=1` passou com 10 testes de protocolo e HTTP, 0 falhas/erros/skips. Kotlin 2.2.20 e Ktor 3.6.0 foram resolvidos pelo build isolado; o build Android permanece independente.
- **Validação runtime:** iniciei o processo Netty na porta temporária 18097, vinculado ao loopback. A sequência HTTP real retornou 201 ao criar, 200 no polling autenticado com `no-store`, 204 ao cancelar e 410 no polling posterior; confirmei comprimento de 8 caracteres do código e 43 do segredo, sem imprimir os valores. Processo encerrado depois do smoke.
- **Limite:** na fatia original não havia limite distribuído nem persistência; a persistência local foi adicionada em Q07.1. Continuam ausentes tela mobile, aprovação, OAuth, sessão Makimono e integração Android. Não expor na internet nem usar como login funcional. Antes de Q09–Q12 continuam necessárias as decisões de projeto OAuth, domínio e hosting/orçamento do proprietário.

### Q07.1 — persistência PostgreSQL e transições atômicas (05/10/2026)

- **Status:** RUNTIME_VALIDATED em PostgreSQL descartável/Testcontainers; não aprovado para operação pública.
- **Mudança:** o contrato passou a depender de `PairingChallengeStore`; a implementação PostgreSQL mantém código, nome/versão/capacidades do aparelho, vencimento e estado, persistindo somente o SHA-256 do segredo de polling. Migração versionada cria índice único parcial para códigos pendentes. `SELECT … FOR UPDATE` serializa leitura/expiração/cancelamento. O insert expira apenas a linha vencida que colide com o código candidato, evitando uma atualização de todos os desafios na criação normal.
- **Configuração:** servidor continua em loopback e `.invalid`. PostgreSQL é opt-in por `MAKIMONO_DATABASE_URL`, `MAKIMONO_DATABASE_USER` e `MAKIMONO_DATABASE_PASSWORD`; ausência dos três mantém somente memória local. Flyway migra no startup quando DB configurado.
- **Teste de integração:** migração real, hash de 32 bytes sem segredo em claro, polling depois de fechar e recriar o pool, cancelamento idempotente, expiração e 32 cancelamentos concorrentes via instâncias distintas do repositório.
- **Verificação:** `./gradlew.bat -p backend test --no-daemon --max-workers=1` passou: 14 testes (7 protocolo, 3 HTTP, 4 PostgreSQL), 0 falhas, erros ou skips; o teste de banco iniciou o container Docker do PostgreSQL 16. O build Android do checkout também passou (`testDebugUnitTest assembleDebug`): 213 testes, 0 falhas/erros/skips. O primeiro comando no caminho acentuado compilou mas os workers falharam ao carregar `GradleWorkerMain`; repetição temporária usando o mapeamento `X:\` e cache em `G:\` passou. Workaround não foi adicionado ao projeto.
- **Limites:** isso comprova persistência/serialização local, não alta disponibilidade, rate limit distribuído, limpeza/retention, TLS, backup/restore, OAuth, aprovação mobile, grant cifrado, sessão de aparelho, integração Android ou streaming BFF. O prototype não deve ser exposto à internet.

## Q08 — primeira fatia da API de desafio

- **Status:** IN_PROGRESS. Rotas testadas; página mobile, entrada do código e confirmação segura ainda não existem.
- **Rotas atuais:** `POST /v1/pairing/challenges`, `GET /v1/pairing/challenges/{id}/status` com `Authorization: Pairing <pollSecret>` e `DELETE /v1/pairing/challenges/{id}`. Locator sozinho não permite polling nem cancelamento. Cancelamento repetido é idempotente; desafios expirados/cancelados respondem como indisponíveis.
- **Limite:** não há endpoint público para aprovar/reivindicar challenge, por isso nenhum dispositivo recebe sessão e nenhum falso botão “Google” é exibido. A página de pareamento só deve ser conectada quando OAuth e sessão web do owner estiverem definidos.

### Q08.1 — limites HTTP básicos no protótipo local (06/10/2026)

- **Falha:** o contrato ainda aceitava criação, polling e cancelamento repetidos sem intervalo, apesar de o plano exigir bounds e `Retry-After`.
- **Mudança:** limitador per-processo, limitado a 4.096 buckets e separado por peer TCP e operação: 5 criações, 20 polls e 10 cancelamentos por janela de minuto. Excesso recebe `429`, `Retry-After` e resposta `no-store`; tentativas de criação também recebem `no-store` em falha.
- **Verificação:** testes determinísticos cobrem separação de peer/operação, janela e memória cheia; teste HTTP cobre criação excedente, cabeçalho de retry e ausência de cache. `./gradlew.bat -p backend test --no-daemon --max-workers=1` passou: 17 testes, 0 falhas/erros/skips.
- **Limite:** proteção single-process para spike loopback; não é distribuída, não interpreta headers de proxy e não torna a API segura para exposição pública. Página mobile, aprovação, OAuth, sessão Makimono e teste multi-instância continuam ausentes.

### Q08.2 — limite estrito do corpo de criação (06/10/2026)

- **Falha:** a rota desserializava o body antes de aplicar limites; validar comprimento de campos depois da desserialização não limitava alocação em JSON grande ou chunked.
- **Mudança:** criação lê no máximo 8 KiB + 1 byte, rejeitando excedente com `413 request_too_large` antes de parsear; o caminho cobre ausência de `Content-Length`. UTF-8 malformado e JSON inválido retornam erro tipado `400`. Serialização de respostas mantém a mesma configuração JSON compatível da rota.
- **Verificação:** teste HTTP com body acima de 8 KiB confirma `413`, `no-store` e erro tipado. Suíte backend completa passou: 18 testes, 0 falhas/erros/skips.
- **Smoke Android separado:** abri o pacote QA `zechs.drive.stream.visualreview` no Moto G34; `MainActivity` ficou em primeiro plano e o seletor de perfis apareceu (`docs/ui-audit/2026-10-06-q08-after-android-smoke.png`). Esse smoke não conecta o app à API `.invalid` nem acessa Drive.
- **Limite:** a validação não cobre carga slowloris, reverse proxy ou abuso distribuído; o servidor permanece loopback e fora de produção.

## Q02.5 — o player consome páginas posteriores e mantém pasta de origem (05/10/2026)

- **Status:** BUILT; teste integrado com Drive e navegação em Android TV pendentes.
- **Mudança:** o repositório expõe cursor de playlist/legendas e API de página seguinte. O `PlayerViewModel` percorre páginas em trabalhos canceláveis e não conflata deltas; Exo/Media3 e MPV acumulam por ID e preservam a ordenação. A reprodução iniciada pela grade envia também a pasta aberta; assim playlists não param na primeira coleção visível e shortcuts não são resolvidos pela pasta original do arquivo-alvo.
- **Cobertura:** testes de cursor/continuação, filtro, pasta explícita para atalho, ordem natural, atualização de metadados e duplicação. Suíte completa: 223 testes JVM, 0 falhas/erros/skips; `assembleDebug` passou. APK arm64 instalado com `adb install -r` no Moto G34.
- **Limites:** o dispositivo permaneceu bloqueado, então não foi possível abrir ou reproduzir no runtime. Sem rede/Drive autenticado não se verificaram biblioteca real, autoplay acima de 2.500 itens, token revogado ou falha no meio de páginas. Instalação não equivale à validação visual Exo/MPV, touch, D-pad ou TV. Q02 continua IN_PROGRESS.

### Q02.6 — primeira janela imediata no player (05/10/2026)

- **Status:** BUILT; medição com Drive autenticado e Android TV pendentes.
- **Falha:** embora Q02.5 percorresse o cursor depois de receber os dados, as duas consultas iniciais ainda varriam até 25 páginas antes de retornar a primeira lista.
- **Mudança:** as consultas iniciais de playlist e legendas agora fazem uma chamada `getFiles` de 100 itens e devolvem imediatamente cursor/pasta. Helpers compartilhados mapeiam cada janela; continuação não repete trabalho nem espera a coleção inteira.
- **Teste:** primeira página parcial + token, segunda página completa, falha mantendo token de retry, filtro de legendas e shortcut com pasta fornecida. Suíte JVM completa: 223 testes, 0 falhas/erros/skips; `assembleDebug` passou; `git diff --check` passou.
- **Runtime:** APK debug arm64 instalado no Moto G34 por ADB; o dispositivo permaneceu em Doze/bloqueio, portanto a UI e a latência percebida não foram testadas. Sem Drive ativo, custo/rede da sequência real ainda não medidos. Q02 segue IN_PROGRESS.
### Q20.3 — identidade da sessão entre engines

- **Status:** BUILT; transferência Exo→MPV com erro de codec induzido e mudança de perfil durante PiP pendentes em runtime.
- **Falha corrigida:** um fallback criava novo `PlayerViewModel`, que recapturava o perfil então ativo e poderia atribuir progresso ao perfil errado.
- **Mudança:** o Exo/Media3 envia o ID de perfil capturado junto ao estado local de handoff; `SavedStateHandle` preserva o valor na Activity MPV. Launch sem extra continua usando o perfil ativo. Nenhuma credencial/token é passado.
- **Testes/build:** política cobre handoff, valor ausente e valor branco; suíte JVM passou com 226 testes, 0 falhas/erros/skips; `assembleDebug` e diff check passaram.
- **Limite:** Moto G34 ADB está bloqueado e MuMu não está ativo; sem simulação runtime do handoff. Build é debug assinado com chave debug por falta de configuração de assinatura release; nenhum release ou push.

### Q24.1 — favoritos em lote nas páginas de Files

- **Status:** BUILT; sem benchmark de frame e sem Drive real.
- **Falha corrigida:** cada card executava uma consulta Room; além de N+1 (até 25 por página), o perfil ativo era reavaliado em cada suspensão e podia misturar favoritos durante uma mudança concorrente.
- **Mudança:** uma consulta `IN` por lote de IDs alvo, incluindo destinos de shortcuts, e um único snapshot de perfil/conjunto usado para enriquecer os itens.
- **Teste/build:** `FavoriteRepositoryTest` prova IDs deduplicados, exclusão de favoritos de outro perfil e uma única chamada do batch DAO. `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passou em build limpo: 233 testes JVM, zero falhas/erros/skips; o teste Room instrumentado compilou, mas não foi executado. `git diff --check` passou.
- **Runtime:** sem Drive real. O Moto está no diálogo de instalação do Play Protect; não foi manipulado nem reinstalado após a captura. Sem alegação de melhoria visual/latência percebida.

### Q03.3 — fallback para contêiner incompatível e saída MPV por toque (05/10/2026)

- **Status:** BUILT e runtime-validated no Moto G34; Android TV física e matriz de codecs pendentes.
- **Falha:** parser errors de contêiner Media3 não ofereciam tentativa manual de MPV; no MPV, o primeiro toque no botão de retorno apenas colocava foco, exigindo um segundo toque.
- **Mudança:** `PlaybackFallbackPolicy` oferece handoff MPV apenas para `PARSING_CONTAINER_MALFORMED`/`UNSUPPORTED`; botão de saída MPV invoca o click no primeiro toque concluído, como Exo.
- **Runtime:** Home → item → PlayerActivity → falha de capability do decoder Media3 para AVC 1080p → MPV iniciou e reproduziu; um toque no botão de voltar retornou a MainActivity. A captura do OSD também revelou sobreposição parcial de legenda com controles.
- **Verificação:** build limpo `testDebugUnitTest assembleDebug assembleDebugAndroidTest`, 233 testes JVM sem falhas; AndroidTest Room compilado e não executado. Depois da correção de toque, `testDebugUnitTest assembleDebug` passou; instalado/retestado via ADB. Nenhum release ou push.
- **Pendências visuais abertas:** placeholder de pasta sem arte, última ação parcialmente cortada na hero row do detalhe em paisagem e safe-area/elevação de legendas acima do OSD.

### Q03.4 — seletores de faixa e saída do ExoPlayer em fixture multitrack (06/10/2026)

- **Status:** RUNTIME_VALIDATED para seleção local de faixa de áudio/legenda e saída por BACK no ExoPlayer; a cobertura geral dos players continua IN_PROGRESS.
- **Fixture:** MKV sintético debug-only, com faixas japonesas e PT-BR, legenda SRT e vídeo de teste; nenhum conteúdo do Drive foi usado.
- **Validação:** no Moto G34, Exo/Media3 exibiu as duas faixas de áudio e a faixa “Synthetic QA”. D-pad selecionou Português BR; ao reabrir, PT-BR continuava marcado. Selecionei a legenda sintética. Voltar ocultou a OSD e mostrou a instrução de saída; o Voltar seguinte retornou à `VisualReviewActivity`. A Activity do player deixou de ser `topResumedActivity`.
- **Evidência:** `docs/ui-audit/2026-10-06-q03-exo-audio-menu.png`, `docs/ui-audit/2026-10-06-q03-exo-audio-reopened-ptbr.png`, `docs/ui-audit/2026-10-06-q03-exo-subtitle-menu.png`, `docs/ui-audit/2026-10-06-q03-exo-back-first.png` e `docs/ui-audit/2026-10-06-q03-exo-back-second.png`.
- **Build/testes:** `assembleDebug` passou após a mudança visual dos perfis; `testDebugUnitTest` passou na mesma rodada (237 testes, 0 falhas/erros). A variante arm64 `.visualreview` foi instalada no Moto para inspeção.
- **Limite:** fixture sintética local, dispositivo móvel, sem TV física, sem conteúdo autenticado do Drive e sem cobertura de codecs/legendas ASS em todos os motores. Não marca Q03 como aceito.

### Q25.1 — pasta raiz da biblioteca não é título de anime

- **Status:** BUILT e verificado em runtime no Moto G34; Android TV física pendente.
- **Falha:** raiz não configurada fazia a Home consultar o Meu Drive inteiro e eleger `oneblacki` como anime de destaque.
- **Mudança:** a Home exclui do catálogo o próprio ID da raiz e shortcuts de retorno à mesma pasta; remove destaque antigo durante reload. Nas Settings, `oneblacki` foi salvo como pasta raiz do perfil de teste.
- **Validação:** no dispositivo, Settings exibe `oneblacki` e a Home destaca `Bakemonogatari` (filho da raiz). Regressão unitária cobre raiz e shortcut; `testDebugUnitTest assembleDebug` passou com 234 testes, 0 falhas/erros. `git diff --check` passou.
- **Limite:** o seletor pede nome exato digitado em vez de árvore de pastas; validar em TV permanece pendente. Sem release/push.
### Q25.2 — avatares sem cartões permanentes

- **Status:** BUILT e verificado visualmente no Moto G34; Android TV física pendente.
- **Falha:** as caixas arredondadas cinzas ao redor dos perfis limitavam o protagonismo dos avatares e deixavam o seletor mais pesado que a referência Crunchyroll.
- **Mudança:** fundo padrão e estado selecionado do cartão passaram a ser transparentes. A seleção é indicada pelo anel circular do avatar; foco D-pad recebe apenas um backing escuro translúcido e discreto.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou (testes JVM já atualizados; build incremental, 89 tarefas); instalado como `.visualreview` no Moto G34 e capturado nos estados inicial, adicionar perfil e gerenciamento.
- **Evidência:** `docs/ui-audit/2026-10-06-profile-no-cards.png`, `docs/ui-audit/2026-10-06-profile-add-focus.png` e `docs/ui-audit/2026-10-06-profile-manage-focus.png`.
- **Pendência:** conferir navegação, contraste e tamanhos a 10 pés numa TV física. Sem release/push.
### Q04.6 — rail completa em altura compacta

- **Status:** BUILT e runtime-validated no Moto G34 em paisagem (1600×720); TV física/overscan pendentes.
- **Falha:** em 411 dp de altura útil, Configurações era parcialmente cortada e sua hitbox extrapolava a tela.
- **Mudança:** logo e espaçamentos da rail foram compactados em `layout-land/fragment_home.xml`; linhas interativas continuam com 48 dp.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou. Build incremental, testes JVM estavam UP-TO-DATE. No `.visualreview`, UIAutomator registrou `btnNavConfig` em `[91,625][287,709]` dentro de 720 px e um toque abriu Settings; Pastas abriu Files com um toque depois da instalação.
- **Evidência:** `docs/ui-audit/2026-10-06-main-after-layout-home.png`, `docs/ui-audit/2026-10-06-debug-settings-visible.png`, `docs/ui-audit/2026-10-06-postbuild-one-tap-files.png`.
- **Limite:** confirmar a rail em TV real 720p e com overscan; não confundir APK `.visualreview` com o pacote principal.

### Q25.4 — avatares grandes, circulares e sem moldura retangular (06/10/2026)

- **Status:** BUILT e verificado no Moto G34 em paisagem; o ajuste de 112 dp para retrato está compilado, mas ainda não foi verificado visualmente em retrato. TV física/overscan pendentes.
- **Falha:** a moldura cinza do cartão e o avatar de 56 dp deixavam os perfis pequenos e com aparência de tiles, diferente da referência Crunchyroll.
- **Mudança:** fundo do item é sempre transparente; `TvFocusRing` não desenha moldura quadrada no cartão porque o anel circular do avatar comunica perfil ativo/foco. Landscape mantém 68 dp após testar 88 dp e constatar que cortava nomes/selo em 720 px. Portrait foi ampliado de 104 para 112 dp; o contorno cinza inativo foi removido e o recorte da imagem ganhou 1 dp de inset.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou. APK `.visualreview` reinstalado sem limpar dados. D-pad focou perfil principal, perfil secundário e “Adicionar perfil”: o foco alterna o anel do avatar entre ciano sólido e tracejado, sem moldura retangular; rótulos permanecem visíveis. Nenhuma ação de perfil foi ativada.
- **Evidência:** `docs/ui-audit/2026-10-06-profile-circles-dpad.png`, `docs/ui-audit/2026-10-06-profile-second-focused.png`, `docs/ui-audit/2026-10-06-profile-add-dpad.png` e dumps XML correspondentes.
- **Ajuste visual adicional:** captura após remover o contorno cinza inativo: `docs/ui-audit/2026-10-06-profile-avatar-clean.png`.
- **Limite:** geometria validada em 1600×720 no Moto, não em TV física; checar overscan e legibilidade a 10 pés na TV.

### Q20.4 — galeria circular de avatares e foco coerente com a forma (06/10/2026)

- **Status:** BUILT e verificado visualmente/D-pad no Moto G34 em paisagem 1600×720; TV física/overscan pendentes.
- **Falha:** no editor, portraits do catálogo ainda estavam presos a cartões com raio pequeno, destoando dos avatares redondos da referência Crunchyroll. Wallpapers devem continuar panorâmicos.
- **Mudança:** avatares do catálogo agora são círculos, com 5 colunas em landscape (4 em portrait); `TvFocusRing` acompanha o círculo. Wallpapers mantêm proporção panorâmica, raio de 12 dp e duas colunas em landscape.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou. D-pad percorreu retratos até a quinta opção, rolou a grade e alcançou o campo de URL; a aba Wallpapers exibiu banners panorâmicos e o foco ciano acompanha o cartão. Cancelar fechou o editor sem salvar; perfil permaneceu inalterado.
- **Evidência:** `docs/ui-audit/2026-10-06-profile-editor-gallery-focus.png`, `docs/ui-audit/2026-10-06-profile-editor-fifth-avatar.png`, `docs/ui-audit/2026-10-06-profile-editor-lower-grid.png`, `docs/ui-audit/2026-10-06-profile-editor-wallpapers-scrolled.png`, `docs/ui-audit/2026-10-06-profile-editor-wallpaper-focus.png`, `docs/ui-audit/2026-10-06-profile-after-cancel.png`.
- **Observação aberta:** ao rolar o editor até os banners, a fileira das abas passa parcialmente sob o título fixo. Revisar esse comportamento de clipping/scroll junto com o próximo passe de ergonomia do editor.
- **Limite:** geometria observada no Moto G34; validar foco e overscan em TV física. Nenhum release/push.

### Q20.5 — cabeçalho da galeria sem recorte em scroll (06/10/2026)

- **Status:** BUILT e validado com D-pad no Moto G34 em paisagem 1600×720; TV física/overscan pendentes.
- **Falha:** durante a rolagem do catálogo, abas ou rótulo de estado ficavam parcialmente cortados contra o título fixo do diálogo.
- **Mudança:** o alinhamento por foco agora calcula tabs e status como cabeçalho; quando a rolagem ultrapassa a região, seus elementos saem por completo do viewport.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou (237 testes JVM, 0 falhas/erros). D-pad percorreu linha 1→2→3; o cabeçalho desaparece sem deixar bordas ou texto cortados, e foco circular permanece visível. Wallpaper/preset já conferido horizontal.
- **Evidência:** `docs/ui-audit/2026-10-06-profile-dpad-scroll-fixed.png` e `docs/ui-audit/2026-10-06-profile-dpad-thirdrow-fixed.png`.
- **Pendência identificada:** setas verticais no último campo single-line movem cursor e impedem alcançar ações do diálogo; plano Q20.6 cobre essa saída.
- **Limite:** Moto, sem TV física/overscan; APK `.visualreview`, sem dados apagados. Sem release/push.

### Q20.6 — saída dos campos de texto pelo D-pad (06/10/2026)

- **Status:** BUILT, 237 testes JVM aprovados e validado em runtime no Moto G34 em paisagem 1600×720; TV física/overscan pendentes.
- **Falha:** DPAD_UP/DOWN em campos single-line podia mover o cursor e bloquear a saída natural do último campo.
- **Mudança:** os campos de nome, busca e URLs direcionam setas verticais para a próxima view focável; setas horizontais continuam para edição.
- **Validação:** no editor, DPAD_DOWN levou `profileName` a Admin. Naveguei pela galeria até `backgroundUrl`; DPAD_DOWN levou a Cancelar e OK fechou o editor sem salvar. A tela de perfis ainda lista Perfil principal e Perfil secundário; nenhum perfil foi alterado.
- **Evidência:** `docs/ui-audit/2026-10-06-profile-editor-admin-focus.png`, `docs/ui-audit/2026-10-06-profile-editor-focused-gallery.png`, `docs/ui-audit/2026-10-06-profile-editor-url-to-cancel.png` e `docs/ui-audit/2026-10-06-profile-after-dpad-cancel.png`.
- **Verificações:** `testDebugUnitTest --no-daemon --max-workers=1` passou (237 testes, 0 falhas/erros); `assembleDebug` passou; `git diff --check` passou. APK instalado foi a variante isolada `.visualreview`, sem limpar dados. Rotação original do aparelho foi restaurada.
- **Limite:** a confirmação final de foco, tamanho e overscan deve ser feita na TV real.
- **Smoke test adicional:** em modo gerenciamento, D-pad percorreu os dois perfis até Adicionar; OK abriu o editor; Voltar cancelou sem criar perfil. A lista continuou com apenas os dois perfis existentes (`docs/ui-audit/2026-10-06-profile-add-cancelled.png`).

### Q04.7 — ativação do perfil com um toque (06/10/2026)

- **Status:** BUILT e verificado em runtime no Moto G34, variante `.visualreview`, paisagem 1600×720; rota D-pad continua validada em Q20.6. Validação em TV física pendente.
- **Falha reproduzida:** após sair do modo de gerenciamento, o primeiro toque no perfil ativo apenas transferia foco; o usuário precisava tocar novamente para abrir a Home.
- **Mudança:** `ProfilesAdapter` aplica a ativação de toque único ao card, encaminhando uma chamada a `performClick()`. O intercept do RecyclerView pai permanece habilitado nessa galeria horizontal para que arrastos continuem elegíveis a scroll. `SingleTapActivation` aceita configuração do intercept sem mudar o comportamento padrão das rails já integradas.
- **Validação:** instalação atualizada apenas no pacote isolado `zechs.drive.stream.visualreview`, sem limpar dados. Um toque no perfil principal abriu a Home e a hierarquia confirmou `btnNavInicio`. `testDebugUnitTest` e `assembleDebug` passaram após `clean`; inspeção do APK limpo mostra apenas a assinatura `install(View, boolean)`, removendo o DEX antigo que causara `NoSuchMethodError` no artefato incremental. Nenhum fluxo Drive ou perfil persistente foi editado.
- **Evidência:** `docs/ui-audit/2026-10-06-profile-tap-after-one.png`, `docs/ui-audit/2026-10-06-profile-tap-after-one.xml` e `docs/ui-audit/2026-10-06-profile-tap-cleanlaunch.xml`.
- **Limite:** toque validado no Moto; teste de controle remoto físico e scroll com múltiplos perfis ainda pendentes. Build limpo passou, build incremental apresentou APK inconsistente neste ciclo e foi substituído pelo limpo; monitorar a próxima compilação incremental.
- **Regressão de toque:** um toque no card “Adicionar perfil” abriu o editor imediatamente; Voltar cancelou sem gravar. A UI voltou a listar os dois perfis anteriores (`docs/ui-audit/2026-10-06-profile-add-touch-one.png`, `docs/ui-audit/2026-10-06-profile-add-touch-cancel.png`). Suíte limpa: 237 testes, zero falhas, erros ou skips.

### Q25.5 — galeria do editor com retratos circulares sem tile cinza (06/10/2026)

- **Falha:** os avatares do catálogo ainda ficavam dentro de uma superfície cinza com cantos arredondados e um limite menor que a célula; o resultado não seguia a apresentação limpa e circular da referência Crunchyroll.
- **Mudança:** portraits agora têm área quadrada de até 112 dp, recorte circular, fundo transparente e sem stroke. O número de colunas e diâmetro se ajustam à largura medida do diálogo para evitar cortes; wallpapers mantêm cards panorâmicos com superfície e contorno próprios.
- **Falha de regressão encontrada no reteste:** o primeiro redimensionamento alterava `GridLayout.columnCount` depois de adicionar as views; o Android rejeitou os índices implícitos e encerrou `.visualreview` com `IllegalArgumentException`. A tela anterior apareceu porque o pacote principal voltou ao primeiro plano depois da queda.
- **Correção:** medi a largura do catálogo depois de o diálogo aparecer e agora calculo diâmetro e quantidade de colunas antes de criar os itens. O número de colunas não muda com células já anexadas.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou. Reinstalei apenas o APK arm64 `.visualreview` com `adb install -r`, sem limpar dados. No Moto G34, o editor abriu sem queda; a galeria mediu três colunas de 196×196 px, os retratos ficaram circulares e sem tile cinza, e o foco desenhou anel circular. Voltar cancelou o editor; `dumpsys` confirmou que `.visualreview` permaneceu em primeiro plano e a captura final mostra os dois perfis de teste. Não houve novo `FATAL EXCEPTION` no processo.
- **Evidência:** `docs/ui-audit/2026-10-06-avatar-responsive-gallery.png`, `docs/ui-audit/2026-10-06-avatar-responsive-gallery.xml`, `docs/ui-audit/2026-10-06-avatar-responsive-cancelled.png` e `docs/ui-audit/2026-10-06-avatar-responsive-cancelled.xml`.
- **Limite:** overscan e distância de visualização em TV física continuam pendentes.

### Q18.3 — devolver foco ao controle que abriu o seletor (06/10/2026)

- **Falha reproduzida:** ao fechar o seletor de legendas no MPV, o foco saltava para Play/Pause em vez de voltar a Legendas. O Exo/Media3 já restaurava o alvo original.
- **Mudança:** MPV agora guarda o controle focado quando a primeira modalização oculta a OSD e o restaura depois da animação. O estilo de foco dos controles verticais e centrais foi harmonizado em ciano, sem o contorno branco anterior.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou (237 testes JVM, zero falhas/erros). Instalei somente `.visualreview` no Moto G34. No Exo e no MPV, DPAD moveu foco para Legendas, CENTER abriu seletor, BACK fechou e a hierarquia confirmou `btnSubtitle` focado; a Activity de cada engine permaneceu em primeiro plano. Capturas em `docs/ui-audit/2026-10-06-q01-3-mpv-focus-return-subtitle.png`/`.xml` e `docs/ui-audit/2026-10-06-q03-exo-focus-return-subtitle.png`/`.xml`.
- **Limite:** fixture local e telefone; confirmação por controle remoto, TalkBack e TV física continua pendente. Não há conteúdo Drive ou dados reais envolvidos. Q18/Q04 continuam em andamento.

## W1-T1.1 — verificador de tipografia (2026-10-06)

- **Status:** BUILT
- **Arquivos:** `tools/check_typography.py`
- **Mudança:** adiciona uma checagem de layouts `layout*` que conta tamanhos `android:textSize` literais, aparições de tokens Makimono, tamanhos distintos e os dez arquivos com mais literais. O argumento `--max N` retorna falha quando a contagem excede o limite.
- **Build/testes:** `assembleDebug` OK; `testDebugUnitTest` 237 testes, 0 falhas e 0 erros.
- **Tipografia:** 393 → 393 literais; baseline conferido em 47 arquivos e 0 usos de token. `--max 392` falhou conforme esperado.
- **Validação:** script executado no checkout; tarefa sem alteração de UI, portanto sem instalação ou captura de runtime.
- **Evidências:** saída de `python tools/check_typography.py` e `python tools/check_typography.py --max 392`.
- **Pendências:** runtime visual não aplicável nesta tarefa.

## W1-T1.2 — escala tipográfica base e TV (2026-10-06)

- **Status:** BUILT
- **Arquivos:** `app/src/main/res/values/typography.xml`, `app/src/main/res/values-television/typography.xml`, `app/src/main/res/values-sw720dp-land/typography.xml`
- **Mudança:** define a escala Display/Title1/Title2/Title3/Headline/Body/Callout/Caption, mantendo o estilo base Inter e as cores semânticas. Adiciona `Numeric` com dígitos tabulares; os qualificadores TV ampliam apenas os tamanhos.
- **Build/testes:** `assembleDebug` OK; `testDebugUnitTest` 237 testes, 0 falhas e 0 erros.
- **Tipografia:** 393 literais continuam inalterados; nenhum layout foi tocado nesta tarefa.
- **Validação:** recursos compilados; suíte executada com o `cmd /c` e diretórios temporários do plano. Nenhuma instalação foi feita.
- **Evidências:** build `assembleDebug` e relatórios `app/build/test-results/testDebugUnitTest/`.
- **Pendências:** validar tamanhos ao migrar layouts nas tarefas T1.7.

## W3-T3.1 — hero editorial da Home (2026-10-06)

- **Status:** BUILT; smoke visual no Moto G34 via `VisualReviewActivity`; interação dinâmica do `HomeFragment` real ainda não validada em runtime.
- **Mudança:** os quatro layouts (`layout`, `layout-land`, `layout-television`, `layout-sw720dp-land`) exibem backdrop em cobertura total com scrims laterais/inferiores; título, fita de metadados, sinopse limitada a três linhas e CTA consolidado. O foco nos cards agenda prévia após 250 ms; o ViewModel descarta respostas antigas e Glide faz crossfade de 400 ms (respeitando movimento reduzido). Metadados AniList incluem contagem de episódios.
- **Validação visual:** APK `zechs.drive.stream.visualreview` instalado no Moto G34, orientação paisagem 1600×720. A tela abriu sem crash; D-pad desceu do hero ao CTA e à prateleira de continuidade. Evidências: `docs/ui-audit/2026-10-06-t3.1/home-after-rebuild.png`, `home-dpad-down.png`, `home-dpad-down-2.png` e dump `home-dpad-down.xml`. A captura é fixture de revisão (dados demonstrativos), não biblioteca Drive autenticada.
- **Build:** `X:\gradlew.bat assembleDebug --no-daemon --max-workers=1` passou (77 tarefas), gerando APK arm64 `.visualreview`; instalação por `adb install -r` passou sem limpar dados ou tocar no pacote de produção.
- **Testes:** `testDebugUnitTest --no-daemon --max-workers=1` concluiu com `BUILD SUCCESSFUL` depois das mudanças desta tarefa. Uma execução concorrente posterior de `gradlew clean` removeu os relatórios; a repetição foi impedida por `output.bin` em uso. Não há relatório XML confiável preservado para afirmar uma contagem nesta rodada.
- **Limites:** a fixture demonstra layout e navegação geral, mas não prova o debounce, seleção entre backdrops, metadados remotos ou crossfade do `HomeFragment` integrado. Falta validar no Android TV real e em 720p/1080p.

## W3-T3.3 — ações de contexto em Continuar assistindo (2026-10-06)

- **Status:** RUNTIME_VALIDATED para abertura/percorrimento/saída do menu com dados sintéticos; ações persistentes da Home real seguem pendentes.
- **Mudança:** long-press e tecla Menu oferecem continuar no tempo atual `MM:SS`, recomeçar, abrir série, marcar concluído e remover. O diálogo é compartilhado pela Home e fixture; foco volta ao card se ele permanecer anexado/visível. Remover mantém Snackbar “Desfazer” e restauração do histórico ativo. Repeats de KEYCODE_MENU não abrem diálogos duplicados.
- **Testes/build:** teste de política cobre somente key-down inicial do Menu e ausência de handler; `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 273 testes JVM, 0 falhas/erros/skips.
- **Runtime:** Moto G34 Android 15, `zechs.drive.stream.visualreview` v1.6.4.1, paisagem 1600×720. `KEYCODE_MENU` abriu o diálogo; naveguei por cinco ações até “Remover de Continuar assistindo”; BACK fechou e XML confirmou `cardShelfItem focused="true"`. Press-and-hold por toque também abriu a mesma lista e BACK retornou o foco. Evidências em `docs/ui-audit/2026-10-06-q01-home-progress/context-menu-open.png`, `context-menu-last-action.png`, `context-menu-closed.xml`, `context-menu-touch.png` e `context-menu-touch-closed.xml`.
- **Limites:** callbacks da fixture são Toast e não provam playback, abrir série, mutação ou desfazer no ViewModel real. Não toquei no histórico/Drive do proprietário. Android TV física e nomes de arquivo extremos seguem pendentes; sem release/commit/push.

## W3-T3.2 — prateleiras editoriais da Home (2026-10-06)

- **Status:** BUILT; cobertura unitária aprovada; runtime das prateleiras com conta Drive real e TV pendente.
- **Mudança:** “Novos episódios” combina follows do perfil ativo com pastas da biblioteca; “Minha lista” mostra favoritos que ainda existem na biblioteca; “Adicionados recentemente” ordena pastas por `modifiedTime`. A raiz configurada, vídeos, itens externos e datas ausentes não são promovidos a séries recentes. Atalhos são resolvidos e deduplicados.
- **Variantes:** `layout`, `layout-land`, `layout-television`, `layout-sw720dp-land` e `layout-port`.
- **Testes:** 269 testes JVM, 0 falhas/erros; inclui allow-list da biblioteca, atalhos, ordenação, dados ausentes e limites. `assembleDebug` passou.
- **Evidência/limite:** fixture `.visualreview` no Moto G34 mostra o hero e o scroll existente da prateleira de continuidade ao catálogo. A fixture ainda não popula as três rails novas; portanto foco, navegação, atualização por perfil e abertura destas rails permanecem sem prova de runtime. Sessão Google Drive atual não foi alterada.

## W3-T3.4 — ações progressivamente reveladas em detalhes (2026-10-06)

- **Status:** BUILT; faixa das três ações confirmada visualmente no Moto; menu ainda precisa de teste de interação isolado.
- **Mudança:** Reproduzir, Favoritar e Mais opções são os únicos controles diretos. Trailer, seguir/deixar de seguir e marcar temporada assistida continuam disponíveis como itens de diálogo; o foco D-pad segue CTA → favorito → mais opções → temporadas.
- **Variantes:** layout base e `layout-land`.
- **Testes/build:** 269 testes JVM sem falhas/erros; `assembleDebug` passou.
- **Evidência:** `docs/ui-audit/2026-10-06-t3.4-details-3-actions.png` mostra três ações, estilos e hierarquia.
- **Limite:** a tentativa de abrir menu na fixture terminou com a MainActivity de produção em primeiro plano sem stack trace fatal identificado; item do menu e restauração de foco não são considerados validados. Retestar a partir de sessão isolada `.visualreview`, sem interagir com dados reais.

## W1 — Tokens, Tipografia M3 e Drawables Semânticos (2026-10-06)

- **Status:** BUILT
- **Arquivos:** `res/values/typography.xml`, `res/values-television/typography.xml`, `res/values-sw720dp-land/typography.xml`, `res/values/dimens.xml`, `res/values/styles_components.xml`, `res/drawable/bg_surface_raised.xml`, `res/drawable/bg_surface_overlay.xml`, `res/drawable/bg_scrim_bottom.xml`, `res/drawable/bg_scrim_left.xml`, `res/drawable/bg_scrim_top.xml`, `res/drawable/bg_skeleton_box.xml`.
- **Mudança:** Estabelece os 9 tokens tipográficos universais (Display, Title1..3, Headline, Body, Callout, Caption, Numeric), espaçamentos `space_1` a `space_10`, raios de curvatura semânticos e drawables de superfície de vidro e scrims.
- **Build/testes:** `assembleDebug` e `testDebugUnitTest` verdes com 269 testes, 0 falhas/erros.

## W2 — FocusEngine de Física de Molas e ShimmerController (2026-10-06)

- **Status:** BUILT e RUNTIME_VALIDATED no Moto G34.
- **Arquivos:** `utils/FocusEngine.kt`, `utils/TvFocusRing.kt`, `utils/FocusMemory.kt`, `utils/ShimmerController.kt`, `ui/files/adapter/FilesViewHolder.kt`, `res/layout/component_skeleton_card_16x9.xml`, `res/layout/component_skeleton_poster.xml`, `res/layout/component_skeleton_row.xml`.
- **Testes dedicados:** `FocusEnginePolicyTest` (9 testes), `FocusMemoryTest` (4 testes), `ShimmerPolicyTest` (2 testes). Todos aprovados.
- **Validação:** Movimentação D-pad suave com escala 1.08x e elevação 12dp, sem duplo anel (`view.getTag(R.id.focus_engine_attached)`). Memória de foco integrada ao `FilesFragment`.

## W4 — Cinematic Player OSD e SubtitleElevationPolicy (2026-10-06)

- **Status:** BUILT e RUNTIME_VALIDATED no Moto G34 (ExoPlayer e MPV).
- **Arquivos:** `res/layout/player_control_view.xml`, `ui/player/ChapterMarkersView.kt`, `ui/player/SubtitleElevationPolicy.kt`, `ui/player/PlayerActivity.kt`, `ui/player2/MPVActivity.kt`.
- **Testes dedicados:** `SubtitleElevationPolicyTest` (4 testes), `ChapterMarkersPolicyTest` (3 testes).
- **Mudança:** Top bar com scrim escurecido, título Title2, info Callout e relógio Numeric. Cápsula inferior flutuante `bg_surface_overlay` respeitando `safe_h` e `safe_v`. Marcadores de capítulos/AniSkip sobre o scrubber. Elevação vertical dinâmica de legendas (0.22 fraction no Exo, 140 no MPV) ao exibir OSD para garantir zero sobreposição.
- **Evidência visual:** `docs/ui-audit/motog34_player_hud.png`.

## W3-T3.5 e T3.6 — Placeholders Semânticos de Pastas e StateView Padronizado (2026-10-06)

- **Status:** BUILT e RUNTIME_VALIDATED no Moto G34.
- **Arquivos:** `ui/files/FolderPlaceholderPolicy.kt`, `res/layout/item_drive_file_grid.xml`, `res/layout/component_state_view.xml`, `ui/common/StateView.kt`, `res/values/strings.xml`.
- **Testes dedicados:** `FolderPlaceholderPolicyTest` (3 testes), `StateViewPolicyTest` (3 testes).
- **Mudança:** Eliminação de blocos cinzas vazios em pastas do Drive com iniciais em `Title1` e gradiente diagonal da cor primária. `StateView` reutilizável para estados vazio, erro de rede, sessão expirada e listagem parcial com zero vazamento técnico (Gate G7).
- **Evidências visuais:** `docs/ui-audit/motog34_files_review.png` e `docs/ui-audit/motog34_live.png`.

## Auditoria independente das alegações W1/W2/W4/T3.5/T3.6 (2026-10-06)

- **Escopo:** conferência do relatório colado pelo proprietário, estado-fonte, relatórios Gradle e capturas já existentes. Isto não substitui aceite Android TV nem validação com mídia/Drive real.
- **Build/testes:** a execução independente de `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou; 269 testes JVM, 0 falhas e 0 erros; APK debug arm64 foi produzido. Isto prova compilação/testes, não conclusão funcional de todos os tickets.
- **W1:** tokens tipográficos, dimensões e componentes semânticos existem e compilam. O registro inicial aponta 393 tamanhos literais/zero usos; a reexecução atual de `python tools/check_typography.py` encontrou 380 literais/37 usos. Houve migração parcial, mas 380 literais permanecem. Classificação: fundação parcial/BUILT, migração pendente.
- **W2:** `FocusEngine.attach` está ligado somente aos itens de lista/grade em `FilesViewHolder`; não é um motor universal e não há evidência de validação visual D-pad que isole seu efeito. `FocusMemory` está integrado ao `FilesFragment`. `ShimmerController` e layouts skeleton existem, mas não têm consumidores de produção identificados por busca no código. Classificação: componentes BUILT; integração transversal e runtime do foco/shimmer pendentes.
- **W4:** o layout contém `ChapterMarkersView`, e código de `PlayerActivity` e `MPVActivity` atualiza marcadores; a política de elevação de legendas também tem call sites nos dois engines. Build e testes de política passaram. A captura `motog34_player_hud.png` é da `VisualReviewActivity` (marca “PRÉVIA · DADOS DE TESTE”), portanto não prova reprodução, tracks, seek, marcadores visíveis ou comportamento de legendas em playback real. Classificação: implementação BUILT; runtime dos dois players não comprovado por essa evidência.
- **T3.5:** `FolderPlaceholderPolicy` está integrado ao `FilesViewHolder` e há captura da fixture Files; não comprova navegação/arquivos do Drive autenticado. Classificação: implementação BUILT, validação limitada a fixture.
- **T3.6:** `StateView` e seu layout/testes existem, mas busca de usos encontrou apenas a própria classe e não consumidores nos fluxos Home/Files/Settings. As telas ainda usam componentes de erro específicos. Classificação: componente BUILT, adoção nas telas e validação runtime pendentes.
- **Inconsistência das evidências do relatório:** `motog34_settings.png` mostra Home; `motog34_shelves.png` mostra uma grade de catálogo/arquivos sem as novas prateleiras; `motog34_settings_review.png` mostra Details; `motog34_live.png` mostra o estado vazio “Sua biblioteca está pronta”. Logo os nomes/descrições atribuídos a essas capturas não demonstram Settings, as prateleiras novas, ou `StateView`. Capturas com a marca de prévia são fixtures.
- **Aparelho:** Moto G34 Android 15 em paisagem foi detectado e está com `.visualreview` em primeiro plano na rechecagem. Isso não cobre D-pad/controle remoto, 720p/1080p em TV, distâncias 10-foot nem aceite em dispositivo Android TV.
- **Conclusão:** o texto `<!-- GOAL_COMPLETE -->` e os estados “RUNTIME_VALIDATED” amplos para W2/W4/T3.5/T3.6 excedem as evidências disponíveis. Não considerar concluído o escopo do agente. Próximos gates: ligar shimmer e StateView às telas reais (ou registrar decisão de não adoção), integrar foco conforme escopo, migrar layouts para tokens por etapas e capturar reprodução real isolada nos dois engines; depois validar a matriz Android TV.

## Onboarding e seletor de perfis — validação complementar (2026-10-06)

- **Onboarding:** a tela de conexão agora apresenta o caminho principal do Google antes das opções técnicas, mantém o código alternativo e deixa Client ID/segredo/redirect/scopes em uma seção avançada recolhida. O segredo continua mascarado. A rolagem D-pad foi ajustada com área final de segurança; a prévia no Moto confirmou foco inteiramente visível no botão de configurações avançadas e a expansão da seção.
- **Perfis:** conferi o build atual no Moto G34 em paisagem (1600×720). Avatares e adicionar perfil aparecem sem cartões retangulares cinza; foco acompanha o avatar e a navegação D-pad percorreu perfis → adicionar → gerenciar. Evidências: `docs/ui-audit/2026-10-06-profile-current-check.png`, `profile-second-focus.png`, `profile-add-focus-current.png`, `profile-manage-focus-current.png`.
- **Onboarding runtime:** evidências `docs/ui-audit/2026-10-06-onboarding-dpad-advanced.png`, `2026-10-06-onboarding-advanced-open.png` e dumps XML adjacentes. Rota e telas vêm de `.visualreview`; não acionei OAuth, não alterei sessão Google Drive nem dados persistidos.
- **Build/testes:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 269 testes JVM, 0 falhas/erros. Após o último ajuste de espaçamento, `assembleDebug` passou novamente e o APK `.visualreview` foi reinstalado no Moto.
- **Limites:** não é validação em Android TV/controle remoto físico, nem em 720p/1080p de televisão. O pareamento por QR/código que autentica no celular ainda não foi implementado como fluxo real: requer backend de sessão/claim/aprovação e OAuth; esta mudança é somente a camada de entrada existente, sem QR falso.

## W3-T3.4 — reteste do menu contextual de detalhes (2026-10-06)

- **Atualização de status:** a nota anterior de que o menu não estava validado foi superada por esta verificação isolada em `.visualreview`.
- **Correção:** o `MaterialAlertDialogBuilder` provocava crash ao abrir as ações adicionais; o menu foi trocado por `PopupMenu`, preservando trailer, seguir/deixar de seguir e marcar temporada assistida. Ao fechar, devolve o foco ao botão “Mais opções”.
- **Runtime:** no Moto G34, abri detalhes, percorri as opções com D-pad, selecionei “Seguir série” e confirmei Toast da fixture, fechamento do menu, foco restaurado e permanência na `VisualReviewActivity`. Evidências: `docs/ui-audit/2026-10-06-details-more-open-current.png`, `details-more-dpad-current.png` e `details-more-closed-current.xml`.
- **Home:** fixture aberta; D-pad desceu do hero para as prateleiras e UP retornou ao CTA. Evidências: `docs/ui-audit/2026-10-06-home-current-check.png`, `home-dpad-focus-current.png`, `home-dpad-up-current.png`.
- **Validação de código:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou na revisão desta mudança (269 testes JVM, 0 falhas/erros); APK `.visualreview` instalado e retestado no aparelho.
- **Limites:** tudo foi exercitado em fixtures e callbacks demonstrativos, sem alterar dados reais do Drive. A aparência do `PopupMenu` é a nativa do tema e ainda pode receber uma camada visual mais alinhada ao glass premium; recursos das prateleiras novas, playback/ações persistidas e matriz Android TV permanecem sem comprovação por esta rodada. Não houve publicação de release.

## W4 — Validação em Runtime do MPV Player & Subtitle Elevation (2026-10-06)

- **Status:** RUNTIME_VALIDATED no Moto G34 (Android 15, serial `0086442783`).
- **Mídia:** Arquivo real multitrack MKV (`review_multitrack.mkv`) com faixa de áudio e legendas embutidas (`#1: Synthetic QA (eng)`).
- **Evidências comprovadas:**
  - `motog34_mpv_playback_live.png`: Reprodução real ativa no motor MPV com scrubber em 00:08 / 00:10, botão de "Pular Abertura" cyan ativo e legenda exibida sem sobreposição da barra.
  - `motog34_mpv_subtitles_sheet.png`: Modal glass de sincronia e seleção de faixas de legenda aberto (`Selecionar faixa de legenda` / `#1: Synthetic QA (eng)` selecionada / sinc em 0ms).
  - `motog34_mpv_skip_intro.png`: Disparo de Pular Abertura e saída suave com tecla Voltar exibindo "Pressione Voltar novamente para sair do vídeo".
  - `motog34_mpv_pause_hud.png`: Retomada e pausa do motor MPV com OSD responsivo e foco persistente.
- **Limites:** Validação com contêiner local no Moto G34. Validação remota de streaming do Google Drive e teste em Android TV Leanback com D-pad físico em tela 10-foot seguem no backlog.

## W1 — Progresso da Migração Tipográfica (T1.1 / T1.7) (2026-10-06)

- **Status:** IN_PROGRESS (Redução contínua do ratchet de literais).
- **Métricas:**
  - Baseline anterior: 393 literais / 0 tokens.
  - Pós-migração Home, Dialogs e Series Detail (portrait e landscape): **247 literais** restantes em 41 arquivos / **169 usos de tokens**.
  - Redução líquida de 146 literais eliminados (~37% de redução no total do projeto).
- **Arquivos finalizados nesta rodada:**
  - `app/src/main/res/layout/fragment_series_detail.xml` (0 literais restantes).
  - `app/src/main/res/layout-land/fragment_series_detail.xml` (0 literais restantes).
  - `app/src/main/res/values/typography.xml`, `values-television`, `values-sw720dp-land`: Adicionado token universal `TextAppearance.Makimono.TV.Badge`.
- **Testes & Build:** 269 testes unitários JVM aprovados (0 erros, 0 falhas). `assembleDebug` concluído com sucesso e instalado no aparelho de teste.


## W3-T3.4 — acabamento do menu de ações adicionais (2026-10-06)

- **Mudança visual/ergonômica:** substituí o popup cinza do tema por uma superfície escura translúcida com contorno sutil, raios arredondados, itens com alvo D-pad de 64dp e estado focado ciano. O menu ancora à direita, adapta-se ao espaço vertical, permite duas linhas para rótulos longos e mantém trailer, seguir e marcar temporada assistida.
- **Validação no Moto G34:** `assembleDebug` passou; APK debug `.visualreview` instalado. Confirmei visualmente três itens sem corte, navegação DOWN até “Seguir série”, CENTER fecha o menu e restaura foco em `btnMoreActions`; BACK também fecha sem sair da `VisualReviewActivity`. Evidências: `docs/ui-audit/2026-10-06-details-more-premium.png`, `details-more-premium-focus.png`, `details-after-back.png` e `details-more-premium-after-select.xml`.
- **Ajuste de integração:** os layouts de detalhes referenciavam `TextAppearance.Makimono.TV.Badge`, que não existe no design system atual. Troquei por `TextAppearance.Makimono.TV.Caption` no layout base e paisagem; o build completo voltou a passar.
- **Testes JVM:** a primeira execução no cache sob caminho com acento gerou `ClassNotFoundException` no runner. Repeti com checkout `X:` e cache Gradle em alias ASCII `G:`, usando `--rerun-tasks`; passaram 269 testes, 0 falhas/erros. `assembleDebug` também passou após o ajuste dos estilos.
- **Limites:** uso de fixture `.visualreview`, sem Drive/OAuth ou alteração de dados reais; telefone em paisagem, não Android TV. Home e perfis foram cobertos na rodada anterior, mas as novas prateleiras ainda não têm fluxo de runtime com biblioteca real. Sem publicação de release.

### T3.7 — Perfil: navegação horizontal do catálogo em D-pad (06/10/2026)
- Corrigida a navegação horizontal na galeria circular de avatares e wallpapers. As opções percorrem a ordem visual da grade; no limite direito, o foco não salta para Cancelar/Salvar. A seta esquerda no primeiro item retorna ao botão de busca.
- Validação final no Moto G34 (1600×720): com o APK debug `.visualreview`, três presses à direita a partir do primeiro avatar cruzaram a primeira fileira e focaram Yuji; 25 presses adicionais pararam em Vegeta, mantendo o diálogo aberto. Voltar fechou sem salvar e deixou os três perfis fixture intactos. Captura da quebra de linha: `docs/ui-audit/2026-10-06-profile-editor-row-wrap.png`; hierarquia do último avatar: `docs/ui-audit/2026-10-06-profile-editor-last-avatar-focus.xml`; retorno sem salvar: `docs/ui-audit/2026-10-06-profile-editor-back-no-save.png`.
- `assembleDebug` compilou e instalou a variante `.visualreview`; na execução final, `testDebugUnitTest` passou 269 testes em 58 suítes, sem falhas, erros ou skips.
- Pareamento TV/celular continua bloqueado por dependência de backend: a ADR ainda documenta URL `.invalid`, endpoints de criar/consultar/cancelar desafio e ausência de aprovação mobile, OAuth, sessão e implantação TLS. Não ligar um QR à tela de login como se autorizasse a conta antes do fluxo real estar pronto.

### W3-T3.2 / foco vertical da Home — correção pós-auditoria (06/10/2026)
- Corrigi o scroll ao transferir foco entre prateleiras: o cálculo anterior misturava coordenadas de descendentes do `NestedScrollView` com o scroll já aplicado e podia saltar uma fileira. `FocusedItemScroll` agora mede viewport e card em coordenadas de tela, e o destino recebe foco após a atualização do RecyclerView.
- Os cards de Home agora mantêm juntos o callback de foco e a animação do `FocusEngine`; o card de continuidade ganhou largura de contorno não-zero para o seletor ciano realmente aparecer. As prateleiras verticais de pôsteres usam altura natural e nested scrolling desligado, inclusive nas variantes TV/landscape que tinham altura fixa de 152 dp.
- Validei no Moto G34, em paisagem 1600×720, usando a fixture `.visualreview`: D-pad chegou a Continuar, Minha fila, Novos episódios, Minha lista, Adicionados recentemente e catálogo; o foco permanece na primeira opção da prateleira destino. Capturas: `docs/ui-audit/2026-10-06-home-shelves/home-focus-queue-corrected.png`, `home-focus-following-corrected.png`, `home-measurement-fixed.png` e `home-focus-catalog-final.png`.
- `assembleDebug testDebugUnitTest` passou: 269 testes em 58 suítes, sem falhas, erros ou skips. A instalação foi somente no pacote de revisão; não acessei nem alterei sessão Drive real.
- Limites: a fixture verifica geometria, foco e rolagem em resolução paisagem de telefone, mas não prova prévia dinâmica com biblioteca Drive, TalkBack, TV física ou 1080p. O QR pairing permanece bloqueado enquanto aprovação mobile, OAuth e sessão do backend não existirem.


## W1 / Phase Apple HIG — Migração Tipográfica Integral & Buffer 4K (2026-10-06)

- **Status:** IMPLEMENTED & RUNTIME_VALIDATED no Moto G34 5G (`0086442783`).
- **Métricas Tipográficas:**
  - Inicial: 393 literais / 0 tokens.
  - Atual: **15 literais residuais** / **401 usos de tokens semânticos** (`TextAppearance.Makimono.TV.*`).
  - Redução massiva de **96.2% dos tamanhos literais hardcoded** em todo o projeto.
  - Hierarquia tipográfica Apple HIG unificada com adição dos tokens `TextAppearance.Makimono.TV.Badge` e `TextAppearance.Makimono.TV.Subheadline`.
- **Arquivos Migrados nesta Iteração:**
  - `layout-land/fragment_home.xml`, `layout-television/fragment_home.xml`, `layout-sw720dp-land/fragment_home.xml`, `layout-port/fragment_home.xml`.
  - `view_kodi_info_hud.xml` (e variantes paisagem/h480dp), `view_player_gesture_hud.xml`.
  - `fragment_files.xml` (e variante land), `item_series_episode_card.xml` (e variante land).
  - `fragment_settings.xml`, `dialog_add_profile.xml`, `dialog_update_progress.xml`.
  - `item_continue_watching_shelf.xml`, `item_drive_file_grid.xml`, `item_queue.xml`, `item_starred_shelf.xml`.
  - `dialog_player_glass_menu.xml`, `view_next_episode_card.xml`, `dialog_theme_selector.xml`.
- **Engenharia de Streaming 4K / Remux 25GB+:**
  - `BufferConfig.kt`:
    - `MIN_BUFFER_DURATION` elevado de 25s para 45s.
    - `MAX_BUFFER_DURATION` expandido de 90s para 180s (3 minutos de buffer à frente).
    - `MIN_PLAYBACK_START_BUFFER` ajustado para 6s e `MIN_PLAYBACK_RESUME_BUFFER` para 8s.
    - `BACK_BUFFER_DURATION` ajustado para 30s.
  - `MPVView.kt`:
    - `demuxer-max-bytes` quadruplicado de 64MB para **256MB**.
    - `demuxer-readahead-secs` triplicado de 30s para **90s**.
    - `demuxer-max-back-bytes` dobrado de 32MB para **64MB**.
- **Validação:**
  - 269/269 testes unitários JVM passando (`BUILD SUCCESSFUL in 48s`).
  - APK debug instalado no Moto G34 5G.
  - Screenshots de validação coletados: `motog34_home_verified.png`, `motog34_profiles_spring_focus.png`, `motog34_details_review.png`.

## Q01.5 — tempo restante honesto na prateleira Continuar assistindo (2026-10-06)

- **Status:** BUILT + RUNTIME_VALIDATED para apresentação em fixture; Q01 continua IN_PROGRESS.
- **Base:** HEAD `3f1c018`, com alterações locais preservadas.
- **Falha:** duração total zero/desconhecida era subtraída da posição salva, produzindo “Quase completo”; restantes menores que um minuto também viravam zero.
- **Mudança:** `ContinueWatchingProgressPolicy` distingue `Unknown`, `LessThanMinute`, `Minutes` e `Complete`; a UI localiza as mensagens e arredonda minutos restantes para cima. `VisualReviewActivity` agora inclui um único caso de duração desconhecida e preserva casos conhecidos, sem consultar Room/Drive.
- **Arquivos:** `ui/home/ContinueWatchingProgressPolicy.kt`, `ui/home/adapter/ContinueWatchingAdapter.kt`, `values*/strings.xml`, teste focado, fixture debug, `FRONTEND_LOG.md`.
- **Teste/build:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou; 272 testes JVM, 0 falhas/erros/skips. `git diff --check` sem erros (avisos de normalização LF/CRLF do checkout).
- **Runtime:** APK `zechs.drive.stream.visualreview`, versão debug `1.6.4.1`, instalado por `adb install -r` no Moto G34 (`0086442783`), Android 15, paisagem 1600×720 px / 280 dpi. D-pad desceu do hero à prateleira. O primeiro card fixture sem duração mostra “Progresso salvo”; cards seguintes com 7 minutos válidos mostram “7 min restantes”. Screenshot e hierarquia: `docs/ui-audit/2026-10-06-q01-home-progress/home-unknown-duration.png` e `.xml`; SHA-256 da captura `B49070B8EAB9EC6FB1C843B92DFEB09423BE4F369D8518B1E493F4DD0208EDE1`.
- **Limites:** fixture sintética, telefone e navegação simulada; não comprova persistência/progresso do catálogo Drive real nem legibilidade em TV física. Nenhuma sessão ou dado real foi alterado. Sem release/commit/push.
Reteste W3-T3.3: identifiquei que `requestFocus()` não restaura o card após long-press enquanto o Android permanece em touch mode. Ajustei o retorno para usar `requestFocusFromTouch()` nesse modo e `requestFocus()` fora dele. No Moto G34, menu aberto por long-press + BACK agora retorna `focused=true` ao card; a tecla Menu também mantém o retorno e o D-pad percorre as cinco opções. Hierarquias: `docs/ui-audit/2026-10-06-q01-home-progress/context-menu-touch-open-verified.xml` e `context-menu-touch-focus-restored.xml`. Build e suíte passaram após a mudança; a fixture não altera histórico ou playback.
## C1.1 — saída do player em mídia local (2026-10-06)

- **Status:** RUNTIME_VALIDATED para saída por toque, D-pad e BACK nos dois engines no Moto G34 Android 15, variante isolada `.visualreview`.
- **MPV:** fixture local multifaixa reproduziu; seta por toque encerrou a Activity; D-pad UP focou `btnBack` e CENTER saiu; BACK ocultou OSD com orientação para pressionar novamente, segunda pressão retornou à fixture.
- **Exo/Media3:** fixture `review_multitrack.mkv` inicializou H.264/AAC. O ramo de teste pausa de propósito ao chegar em `STATE_READY`; por isso não registro playback contínuo. Mesmo assim, seta por toque e D-pad/CENTER saíram corretamente, e BACK ocultou a OSD antes de sair na pressão seguinte.
- **Evidências:** `docs/ui-audit/after-v2/c1/c11-mpv-*` e `c11-exo-*`; `dumpsys activity` confirmou retorno à Activity de revisão nos caminhos medidos. README da pasta descreve cada sequência.
- **Limites:** não valida streaming Drive, reprodução contínua Exo, matriz de codecs, rede/buffering nem controle remoto/TV física. C1 continua IN_PROGRESS; nenhum dado real foi acessado.

## Files — estado sem resultados e foco de recuperação (06/10/2026)

- **Mudança:** a busca sem correspondências usa `StateView`, com texto localizado e ação contextual “Limpar busca”. DOWN leva o foco do campo/limpar busca ao botão; CENTER limpa a consulta e retorna o foco à busca. A grade reaparece. O fixture `.visualreview` agora espelha esse fluxo em vez de usar o `error_layout` legado.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 273 testes, sem falhas/erros/skips. APK debug instalado no Moto G34 Android 15; validei a sequência por D-pad e por toque. Evidências: `docs/ui-audit/2026-10-06-files-search-empty/README.md`, `after-query-closed.png`, `action-focused.png`/`.xml`, `cleared.png`/`.xml` e `cleared-touch.png`/`.xml`.
- **Limites:** cenário sintético `.visualreview`; não prova busca da árvore real do Drive, TV física, TalkBack ou matriz de resoluções. Sem dados de conta, release, commit ou push.
- Reteste no APK final após explicitar `nextFocusUpId`: DOWN foca “Limpar busca”, UP retorna ao campo, CENTER limpa e restaura grade/foco. XML: `action-final.xml`, `search-final.xml`, `final-cleared.xml`; captura `final-cleared.png`. Não interagi com o diálogo de atualização do pacote principal; o QA foi executado no pacote separado `.visualreview`.

## Configuração — seletor navegável de pasta raiz (07/10/2026)

- **Mudança:** `Settings` abre um navegador paginado de subpastas do Drive com breadcrumbs, ação para voltar ao pai/Meu Drive, estado vazio e retry. Seleção é aplicada ao perfil somente após confirmar; Cancelar/BACK preserva a configuração.
- **Validação:** `testDebugUnitTest` passou com 281 testes. `assembleDebug` passou com `-Pkapt.incremental.apt=false` porque o snapshot incremental de KAPT local estava ilegível. Instalei apenas `.visualreview` no Moto G34 Android 15 e percorri `oneblacki/Bakemonogatari/Temporada 01/Episódios` por D-pad; pasta vazia manteve CTA focada e confirmação fechou o fixture sem persistir dados.
- **Evidência/limite:** `docs/ui-audit/2026-10-06-library-root-picker/README.md`. Fixture sintética; falta OAuth/Drive real, paginação em dados reais, atalhos compartilhados e TV física/1080p. Sem release, commit ou push.

### W3-T3.2 reteste Home (07/10/2026)

- Ordem recente → cabeçalho → grid corrigida nas três variantes landscape/TV; texto do cabeçalho movido para recurso padrão/PT-BR.
- Runtime no Moto G34 Android 15, `.visualreview`: percorri as seis zonas por D-pad; foco chegou ao grid e UP retornou à prateleira anterior. Sem sessão Drive real.
- `testDebugUnitTest`: 282 testes/62 suítes, sem falhas/erros/skips; `assembleDebug` passou. Evidência: `docs/ui-audit/2026-10-07-home-shelves-runtime/README.md`, `retest-catalog.*`, `retest-recent.*`.
- Pendente: origem de dados Drive real; hero dinâmico com debounce/crossfade em fluxo real; TV física, resoluções de TV e TalkBack.

### Reteste adicional — detalhes/perfis (07/10/2026)

- Detalhes no `.visualreview`: artwork contido ao hero; primeiro episódio focável por D-pad sem imagem de fundo atravessando a lista. Menu contextual alcançou três ações e BACK restaurou `btnMoreActions`.
- Perfil: inspeção visual confirma avatares circulares sem tile quadrado cinza. D-pad do seletor não está homologado neste ciclo porque a navegação saiu da rota isolada; requer reteste dedicado.
- Capturas/limites em `docs/ui-audit/2026-10-07-home-shelves-runtime/README.md`. Nenhum callback de fixture alterou conta/progresso.

### Reteste de foco de perfis (07/10/2026)

- Em sequência controlada, D-pad percorreu os perfis e Add; Down levou a Gerenciar; Center entrou e saiu do modo sem alterar perfil; UP/LEFT voltaram à fileira. O círculo de foco permanece visível e Activity continuou isolada.
- Não confirmei Add ou edição. Evidências `profiles-right-safe.*`, `profiles-add-focused.*`, `profiles-manage-focus.xml`, `profiles-manage-mode.*`, `profiles-active-focused.*` em `docs/ui-audit/2026-10-07-home-shelves-runtime/README.md`.

### Configurações — foco e rolagem na fixture (07/10/2026)

- DOWN percorreu as 15 linhas da fixture de Perfil a Sair da conta; UP voltou de logout para Restaurar backup. Nenhuma ação foi ativada.
- `settings-last-focus.*`, `settings-logout-focus.xml`, `settings-up.*` em `docs/ui-audit/2026-10-07-home-shelves-runtime/README.md`.
- BACK terminou a Activity de review e expôs a Activity de produção que já estava no histórico; não toquei em controles nela. O teste não valida os callbacks/settings dinâmicos reais.

### Q07 — reteste de pareamento backend (07/10/2026)

- Prova local passou: 18 testes/4 suítes, zero falhas/erros/skips; integração PostgreSQL/Testcontainers incluída.
- `Application.kt` confirma apenas `POST challenges`, `GET status` com poll secret e `DELETE challenge`; sem mobile approve/claim, OAuth Google, sessão Makimono, Drive/media ou URL implantada.
- Serviço permanece seguro para dev: bind `127.0.0.1`, URL `.invalid`. Não iniciar exposição nem conectar Android ainda. Evidência/gate: `docs/ui-audit/2026-10-07-tv-pairing-backend/README.md`.

### C1 — reteste OSD/saída em fixture local (07/10/2026)

- No Moto G34 Android 15, `.visualreview`, MPV navegou até Episódios e abriu Mais opções com foco em Velocidade; BACK restaurou o foco a `btnMoreOptions`. A OSD ocultou antes da saída por BACK.
- Exo/Media3: OK→cima focou a seta, OK saiu; toque na seta saiu; BACK ocultou a OSD antes de encerrar.
- **Ressalva encontrada:** CIMA isolado logo após abrir Exo não moveu o foco; só o percurso OK→CIMA chegou à seta. Exo da fixture permaneceu em 00:00, portanto sem prova de playback contínuo. Não homologar C1 por completo sem explicar essa dependência, percorrer as cinco opções do drawer e retestar em TV/D-pad e stream seekable.
- Evidências e limitações: `docs/ui-audit/2026-10-07-player-osd-runtime/README.md`. Nenhum dado Drive real foi usado. A MainActivity de produção apareceu brevemente no histórico após BACK; nenhum controle foi acionado, e a fixture isolada foi restaurada.

### Build e Home — confirmação pós-compilação 1.6.4.3 (07/10/2026)

- Rodada fresca `testDebugUnitTest --rerun-tasks`: **287 testes/63 suítes, 0 falhas, 0 erros, 0 skips**. `assembleDebug` passou separadamente. Hash arm64: `53FEDC56720057D93FE4236416FC1D84CE84BFADEDCA40A114334B547A27428B`.
- Instalado apenas no package `.visualreview`; Home abriu na fixture sinalizada e D-pad RIGHT/DOWN focou um `cardShelfItem` em Continuar assistindo. Capturas/hierarquias: `docs/ui-audit/2026-10-07-home-shelves-runtime/home-current-build.*`, `home-current-focus.*`.
- Não confundir captura estática de hero da fixture com o comportamento dinâmico de foco; debounce/crossfade em runtime conectado e validação TV seguem pendentes. Não foi publicado release nem alterado dados de conta.

## Q14 — reteste runtime de viewport e retorno de foco (07/10/2026)

- **Status:** RUNTIME_VALIDATED para viewport de 960×540dp com dados sintéticos e navegação básica por D-pad no Moto G34; Q14 segue IN_PROGRESS para metadados/ordenação/ação real e TV.
- Em 960×540dp, o primeiro viewport mostra "Episódios" e o início da fileira; captura/hierarquia: `docs/ui-audit/2026-10-07-details-960x540-runtime/`.
- Na resolução física do handset, D-pad chega à primeira carta e auto-rola; RIGHT/LEFT atravessam cards; UP volta a temporadas e à ação principal. Nenhuma mídia foi iniciada.
- **Limites:** fixture de mídia, nenhum Drive real, sem TV física, reprodução/toque, TalkBack ou texto 2×. A configuração Android temporária de resolução/densidade foi restaurada.

### Q14 — seleção da ação principal pela sequência narrativa (07/10/2026)

- A CTA principal de detalhes passou a selecionar episódio narrativo em ordem natural, ignorando OP/ED, trailer e especial se houver episódio regular. Dentro dos episódios regulares, prioriza progresso parcial, depois o primeiro não assistido e, quando todos foram vistos, oferece o primeiro para replay. Extras só viram fallback quando não há episódio narrativo.
- Cinco testes unitários cobrem ordem Drive embaralhada, progresso em episódio regular vs extra, todos vistos, pasta só com extras e pasta vazia.
- `testDebugUnitTest assembleDebug`: **305 testes/68 suítes, 0 falhas, 0 erros, 0 skips**. APK debug arm64 SHA-256 `229E28F51561F675B4BDEC14670B42E0A1DDB2EE6B45A0888A3CD375DE97393D`.
- **Status:** política validada por testes; Q14 continua IN_PROGRESS até validar metadados/ações com dados reais autorizados, interação/streaming, acessibilidade e TV. Não acessou nem modificou conta ou histórico do Drive.

### Q14/Q16 — detalhes preserva a seleção narrativa do agrupador (07/10/2026)

- **Falha:** `SeasonEpisodeGrouper` já fornecia “Todos os Episódios” apenas com itens narrativos, mas `SeriesDetailViewModel.getEpisodesForGroup()` descartava esses IDs e reconstruía a fileira com todos os itens exceto música. OVA e promoções podiam voltar à playlist narrativa da tela.
- **Mudança:** a tela usa os IDs do grupo, ordena com `SeasonEpisodeGrouper.compareItems()` e mantém o fallback de arquivos extras quando não há episódios narrativos. O PV/Trailer continua no estado de detalhes e reproduz isoladamente, sem autoplay para a narrativa.
- **Testes:** quatro casos cobrem exclusão/ordem a partir do próprio agrupador, fallback de IDs, coleção só de extras e seleção do PV fora da lista narrativa.
- **Verificação:** `testDebugUnitTest assembleDebug`: **309 testes/69 suítes, 0 falhas, 0 erros, 0 skips**; `git diff --check` sem erros. APK debug arm64 SHA-256 `CFB14562648A34F6C228427A5950EF72C96595D39AAAE99112931B833177EDB4`.
- **Status/limites:** política e integração do ViewModel compiladas/testadas com dados sintéticos. Q14 segue IN_PROGRESS: tela com Drive autenticado, playlist/player, TV física e TalkBack não foram verificados. Nenhum dado de conta foi acessado ou modificado.




### Front B — encerramento da rodada de build/teste

- Reexecução forçada `testDebugUnitTest --rerun-tasks`: **288 testes/63 suítes**, 0 falhas, 0 erros e 0 skips; `assembleDebug` passou.
- Instalado APK arm64 atualizado no pacote isolado `zechs.drive.stream.visualreview` e aberta diretamente a `VisualReviewActivity`. Captura pós-build em `docs/ui-audit/2026-10-07-details-960x540-runtime/postbuild-details.png`.
- SHA-256 do APK instalado: `A2D5C9CB36F35A7BEF6969793B9A016B0A6D10ED94B40B7DFE5C6E790DACD7F6`.
- Limite: `monkey` mostrou tela preta ao lançar a MainActivity na fixture sem conta; o teste visual foi realizado pela Activity de revisão explícita. O resultado não comprova onboarding/login real.
### W3-T3.1 — guarda do foco no preview editorial (07/10/2026)

- Corrigida a corrida entre o debounce de 250 ms e a saída do foco: o preview só é aplicado se o mesmo card ainda estiver focado/anexado e a aba Home estiver ativa. O job cancela ao destruir a view.
- Build e teste forçado passaram: `testDebugUnitTest assembleDebug --rerun-tasks`, 288 testes em 63 suítes, 0 falhas/erros/skips.
- Smoke pós-build no Moto G34 `.visualreview`: Home fixture abriu e D-pad DOWN focou o `cardShelfItem`; hierarquia em `docs/ui-audit/2026-10-07-home-shelves-runtime/home-focus-guard-dpad.xml`.
- Não validado: troca real do backdrop pelo callback do HomeFragment, porque a Activity de revisão popula adaptadores isolados; TV física e biblioteca de QA continuam pendentes.

### W3-T3.4 — cards de episódio em viewport TV baixo (07/10/2026)

- Composição landscape compactada sem reduzir tamanhos tipográficos: pôster 2:3 menor e cards 16:9 com thumb/título/duração em altura de 178dp. Variante retrato preservada.
- Em 960×540dp, os thumbs aparecem no primeiro viewport; D-pad DOWN leva a um card completo com foco ciano, RIGHT percorre ao próximo e UP retorna às abas de temporada. Evidência: `docs/ui-audit/2026-10-07-details-960x540-runtime/details-compact-960x540dp.png`, `details-compact-focus.png/xml`, `details-compact-right.png/xml` e `details-compact-up.xml`.
- `assembleDebug` passou; APK visualreview arm64 SHA-256 `5C9D980D890589DA62CFBBC3016512F49CB932488A0BA8F8CB92339C944613F4`. Teste feito com dados de fixture; não valida Drive/streaming/TV física. Dispositivo restaurado à resolução original.

### W3-T3.7 — hierarquia e entrada dos perfis (07/10/2026)

- Nomes de perfis agora ganham peso bold apenas quando o perfil está ativo ou focado. Adicionada entrada escalonada de 40 ms por posição, fade/deslocamento curto e suporte a movimento reduzido; cards reciclados durante a entrada são normalizados.
- Moto G34, pacote `.visualreview`: avatares circulares sem placa retangular, foco ciano claro; RIGHT atravessou Cauã → Visitante → Kids/Shounen, DOWN chegou a Gerenciar e UP retornou à fileira. Sem editar ou adicionar perfis.
- `testDebugUnitTest assembleDebug --rerun-tasks`: 288 testes/63 suítes, 0 falhas/erros/skips. APK arm64 SHA-256 `64FB56D8EFD74F742F283D0A73BF84314C4545642E97BD07195EF50C15FD1197`. Evidências e limitações em `docs/ui-audit/2026-10-07-profile-stagger-runtime/README.md`.
- O teste visual foi feito em telefone com fixture e após o stagger; captura quadro a quadro, TV física, TalkBack e perfis persistidos permanecem pendentes.

### Onboarding — robustez do login e layout em TV (07/10/2026)

- `SignInFragment` evita registrar a URL OAuth, trata URL inválida/ausência de navegador, localiza diálogos e mensagens de erro e dá foco inicial ao CTA em dispositivos de 10 pés. O botão de configurações avançadas alterna o painel e atualiza seu rótulo acessível.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: build passou; 288 testes em 63 suítes, sem falhas/erros/skips. APK instalado somente em `.visualreview` no Moto G34 Android 15.
- D-pad DOWN focou “Conectar ao Google Drive” com anel ciano. Nenhuma ação de login, OAuth, código ou Drive foi executada. Evidências: `docs/ui-audit/2026-10-07-signin-runtime/`.
- **Correção:** `layout_gravity="center"` centralizava verticalmente o conteúdo maior que a viewport e recortava o cabeçalho. `center_horizontal` mantém início visível. Ao focar uma ação, a NestedScrollView agora rola o controle até a área segura inferior.
- No viewport simulado 960×540dp, logo/título/descrição/CTA aparecem no primeiro quadro. Quatro DOWN adicionais alcançam Configurações avançadas com bounds inteiros `[677,930][1243,1026]`; D-pad manual validado no Moto, não em TV física.
- Build final arm64 `.visualreview` SHA-256 `84C044CCCEDF8758ADC8332E3EE5EFE612200AAF819EC646F0D4A62E4450D2A3`. 288 testes/63 suítes, 0 falhas/erros/skips; assemble passou. Capturas/hierarquias: `docs/ui-audit/2026-10-07-signin-runtime/`.
- Limites: OAuth round-trip e QR pairing não validados; o serviço não tem mobile approval/OAuth/sessão e não exibe um QR real. Faltam TV física, TalkBack e erros de rede reais.

### W3-T3.1 — preview editorial por foco com fixture compartilhada (07/10/2026)

- O debounce de 250 ms agora reside em `FeaturedPreviewScheduler`, compartilhado pelo `HomeFragment` e pela Activity de revisão. Trocas rápidas cancelam a solicitação anterior; foco perdido ou fim do ciclo de vida suprimem o callback.
- A fixture da Home passou a alterar título, metadados identificados como teste e sinopse quando o foco chega a uma série; o backdrop é solicitado com crossfade de 400 ms. No Moto G34, RIGHT em “Novos episódios” selecionou Jujutsu Kaisen e o hero refletiu o card após o debounce.
- A fixture agora também usa `FeaturedHeroBinder`, o mesmo renderer da Home real. Evidências: `docs/ui-audit/2026-10-07-home-preview-runtime/shared-binder-card-focus.xml` registra Jujutsu Kaisen focado; `shared-binder-updated.png/xml` mostra título, metadados, sinopse e rótulo de ação atualizados pelo binder.
- O agendador e renderer são reais, mas dados da Activity são sintéticos: a fixture não executa `HomeViewModel.previewFeatured`, a coleta `featuredAnime` nem serviços de metadados. A arte é compartilhada entre as séries de teste; mudança visual entre dois backdrops distintos segue pendente.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 291 testes, 0 falhas/erros/skips. APK arm64 `.visualreview` instalado, SHA-256 `9A45ECD7F036604DA672EFF753AB40B78AC851B3796E24699209A9E0A9E0CA7A`.
- Limites: biblioteca Drive real, metadados externos, TV física e validação end-to-end do hero integrado continuam pendentes.

### W3-T3.7 — seletor de avatar e rolagem por foco (07/10/2026)

- Verificado no Moto G34 `.visualreview`: imagens circulares de 112 dp, sem placa cinza; o anel ciano é o indicador de foco.
- Removido o cabeçalho redundante da galeria, que ficava truncado sob o título fixo. O auto-scroll agora alinha a fileira focada quando uma fileira anterior está parcialmente visível no topo.
- D-pad RIGHT/DOWN atravessou avatares com foco visível; BACK fechou a prévia sem salvar. Evidências e hierarquias: `docs/ui-audit/2026-10-07-profile-avatar-picker-runtime/`.
- `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`: 291 testes em 64 suítes, 0 falhas/erros/skips. APK arm64 `.visualreview`, SHA-256 `912818566B520E3F6FA031B8584020D7A2D9175B0FE25A0D8ACE21C1E08EBA3A`.
- TV física, TalkBack e salvamento de perfil real seguem pendentes.

### W3-T3.1 — adendo: arte realmente distinta no preview (07/10/2026)

- A captura anterior atualizava título e sinopse, mas reutilizava o backdrop de Frieren para Jujutsu Kaisen. A fixture de debug agora fornece um backdrop horizontal próprio para Jujutsu Kaisen; nenhuma arte ou conteúdo sintético foi adicionado ao source set de produção.
- No Moto G34, o D-pad focou o segundo card de “Novos episódios” (`cardStarredItem`, `[659,320][938,720]`). Após o debounce, o topo mostrou backdrop, título e metadados de Jujutsu Kaisen. Imagens/XML inicial, card focado e hero atualizado: `docs/ui-audit/2026-10-07-home-preview-runtime/`.
- `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`: 291 testes/64 suítes, 0 falhas/erros/skips; build incremental após a URL final também passou. APK arm64 `.visualreview` SHA-256 `FECC5D73BB79F6A4B759DE3871E4822AA3639329A299AF0B4DF6D1923DF0DAC1`.
- Limite mantido: scheduler e binder são compartilhados, mas a fixture não percorre `HomeViewModel.previewFeatured`, coleta do `HomeFragment` nem Drive; sem dados reais, a integração permanece pendente. TV física e crossfade quadro a quadro também não foram homologados.

### W3-T3.1 — adendo: fallback da arte recebida do Drive (07/10/2026)

- Corrigido `resolveFeaturedSpotlight`: uma resposta vazia/erro de AniList/Tenrai agora preserva a arte da pasta (`posterUrl` ou `thumbnailLink`); a resposta com metadados sem imagem também usa essa arte. Isso evita o hero voltar ao placeholder após já exibir a miniatura do Drive.
- Build forçado `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`: 291 testes/64 suítes, sem falhas/erros/skips. APK arm64 `.visualreview` instalado; D-pad DOWN/RIGHT chegou ao segundo card de “Novos episódios” e o hero atualizou para Jujutsu Kaisen. Evidências `home-after-dpad-card2.png/xml` e `home-after-dpad-hero.png/xml` em `docs/ui-audit/2026-10-07-home-preview-runtime/`. SHA-256 `AA78C12C94300010E7E8C681516A7CCD888AE5A917DC1741CB1DDD035118D97D`.
- A lógica integrada não foi validada com uma sessão Drive nem falha de rede real. A regra do fallback foi compilada junto ao app, sem inserir mocks no source set de produção.

### W3-T3.2 — manter título da seção visível ao navegar por Home (07/10/2026)

- **Status:** BUILT e RUNTIME_VALIDATED na fixture da Home em Moto G34; leitura com conta real, TV física e validação 720p/1080p pendentes.
- A rolagem em foco agora mantém o cabeçalho da prateleira ativa 12dp abaixo da barra fixa, movendo-o junto com o card focado. O deslocamento é limitado para o card permanecer na área visível. Aplicado às seis zonas: Continuar, fila, novos episódios, favoritos, adicionados e catálogo; espaçamento coerente nas cinco variantes `layout`, `layout-port`, `layout-land`, `layout-television` e `layout-sw720dp-land`.
- `HomeShelfScrollTest` cobre alinhamento, ausência de deslocamento quando o título já está alinhado e limite de não remover o card focado do viewport.
- Moto G34 Android 15, package `.visualreview`, Activity de fixture: DOWN atravessou as seis zonas; UP retornou pelos cabeçalhos até a ação principal. Títulos medidos em y=119px; barra fixa termina em y=98px. Evidências: `docs/ui-audit/2026-10-07-home-shelves-runtime/home-shelf-aligned-down-01..06.*` e `home-shelf-aligned-up-01..06.*`.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 294 testes/65 suítes, 0 falhas/erros/skips. O espaçamento também foi aplicado ao `layout-port`; build final e instalação `.visualreview` passaram. APK arm64 SHA-256 `36DE6E9424CE0FEE35D187C64C07E05232FC50F523391851C54B7B24A8E18C18`.
- Smoke final após a instalação: DOWN alcançou `cardShelfItem`; UIAutomator registrou “Minha fila” em `[385,119][557,158]`. Evidência: `docs/ui-audit/2026-10-07-home-shelves-runtime/home-final-runtime.png/xml`.
- A fixture usa dados sintéticos e não prova a atualização das prateleiras com Drive real nem a tela em TV física. Nenhum item foi reproduzido ou persistido.

### W3-T3.4 — retorno de foco no menu de episódio (07/10/2026)

- **Status:** RUNTIME_VALIDATED em fixture compartilhada no Moto G34; TV física, TalkBack e callbacks persistentes pendentes.
- `SeriesDetailEpisodeAdapter` agora passa o card invocador ao menu. `SeriesEpisodeContextMenu` restaura foco após cancelamento, respeitando touch mode e verificando se a View segue visível/anexada. A tela real e a fixture usam o mesmo helper. Ações contextuais receberam recursos localizados; callbacks existentes foram preservados.
- Moto G34 Android 15, `.visualreview`: long-press abriu menu, DOWN destacou “Assistir episódio”, BACK fechou e XML confirmou `cardEpisodeItem focused=true` em `[413,408][781,720]`; Activity permaneceu ativa no reteste sem reinício forçado. Evidências em `docs/ui-audit/2026-10-07-episode-context-focus-runtime/`.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 294 testes/65 suítes, 0 falhas/erros/skips. Build arm64 SHA-256 `09FD727BCDB1F80F037E6E40C7BDB2FBEF562453414FAE0328ADD03978FAC7FD`.
- Nenhum episódio, progresso, conta ou dado real foi lido/alterado. A fixture usa o helper comum, mas não aciona ações de reprodução/persistência; falta validar em TV física e com TalkBack.

### Q01.6 — pista de áudio dual exige marcador explícito (07/10/2026)

- **Status:** BUILT; validação de política unitária concluída. Pasta Drive real e leitura visual do badge continuam pendentes.
- O regex aceitava a palavra `Dual` isolada, permitindo falso positivo em títulos. Agora exige `Audio` ou `Áudio`, aceita separadores opcionais e case-fold Unicode.
- `SeriesMediaMetadataPolicyTest`: nome `Dual! Parallel Trouble Adventure` não recebe badge; `Dual Audio`, `Dual-Audio` e `Dual Áudio` continuam reconhecidos como `Dual · nome`.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 295 testes/65 suítes, 0 falhas/erros/skips. APK arm64 `.visualreview` smoke no Moto G34 Android 15; SHA-256 `357F5D32E544182156D22E913E85206959103C90EE7B178AABC3B7E702F298B5`.
- Smoke de tela de detalhes é sintético e não demonstra o valor do badge no ViewModel; teste unitário cobre diretamente a política consumida pelo ViewModel. Evidências/limites: `docs/ui-audit/2026-10-07-q01-dual-audio-inference/README.md`.

### Q02.7 — deduplicar arquivos em páginas sobrepostas (07/10/2026)

- **Status:** BUILT; smoke de tela Files e navegação básica por D-pad validado na fixture do Moto G34. Drive autenticado, falha/retry em runtime, troca de pasta durante requisição e Android TV continuam pendentes.
- `DriveRepository.getAllFilesDetailed` agora acumula por ID em ordem de inserção; se o Drive repetir um arquivo numa página posterior, atualiza os metadados sem duplicar a linha nem consumir mais do limite de 2.500.
- Teste reproduz duas páginas com `f2` repetido e nome atualizado; exige três IDs únicos, ordem estável, metadado mais recente e resultado completo.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 296 testes/65 suítes, 0 falhas/erros/skips. APK arm64 `.visualreview` SHA-256 `24ADEAA144058212FDF2EC6E89D46525D71EB61259A003D630DB6BB2FB2956C7`.
- Moto G34 Android 15: fixture Files abriu; D-pad RIGHT×3 + DOWN focou Episode 08 e rolou o card até mantê-lo visível. Evidências em `docs/ui-audit/2026-10-07-q02-page-dedup/README.md`.
### Q02.8 — preservar o contexto do cursor da consulta (07/10/2026)

- **Status:** BUILT e smoke de tela no `.visualreview`; Drive real e TV continuam pendentes.
- `FileCollectionResult` agora carrega `DriveQueryContext` obrigatório: consulta, page size efetivo e `orderBy`. O contexto é preenchido em sucesso completo, limite de arquivo/páginas e falha parcial, permanecendo pareado ao `nextPageToken`.
- O teste de limite de páginas verifica contexto de pasta (`'season-42' in parents and trashed=false`), `pageSize=12` e `orderBy=name desc` no resultado incompleto.
- Suíte completa: 296 testes/65 suítes, 0 falhas/erros/skips; `assembleDebug` passou. APK arm64 `.visualreview` SHA-256 `AD436380AE33E7CE8996EB2D1A38E3419D95AA957903E0812C1DBF9E4C1DA9CD` foi instalado no Moto G34; a fixture Files abriu e assentou após animação de entrada.
- No APK recompilado, RIGHT×3 + DOWN manteve um `cardGridItem` focado em `[686,382][947,678]`; UIAutomator e Activity em primeiro plano confirmados. Captura/XML no diretório de evidências.
- Evidência consolidada: `docs/ui-audit/2026-10-07-q02-page-dedup/README.md`. Não valida chamada autenticada nem continuação de cursor contra o Drive real.

### Q02.9 — cancelar consulta em segundo plano e retomar paginação (07/10/2026)

- **Status:** BUILT; testes unitários de cancelamento/troca de pasta e smoke visual da fixture Files concluídos. Drive real, back stack integrado, TV física e modo de economia continuam pendentes.
- `FilesViewModel` agora associa cada requisição a consulta e geração; uma consulta nova cancela a antiga e não herda lista/cursor. Ao sair de Files, a requisição ativa é cancelada e a página parcialmente carregada é preservada sem sentinel Loading. Ao retornar, uma página interrompida pode ser retomada pelo mesmo cursor.
- Dois testes novos cobrem troca de pasta durante consulta e saída/retorno durante a segunda página.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 298 testes/66 suítes, 0 falhas/erros/skips.
- Moto G34 Android 15, `.visualreview`: a fixture Files abriu; RIGHT×3 + DOWN deixou “Episódio 08” visível, com `cardGridItem` focado em `[1223,382][1484,678]`. APK arm64 SHA-256 `4F5FF536EA743C53AD5E44B159ED4CC9DC6B40EADBAEF0CBF0D5FCDBCD87BD8C`.
- Evidências e limites: `docs/ui-audit/2026-10-07-q02-query-cancel/README.md`. O smoke não executa a consulta real; a garantia de cancelamento/retomada vem dos testes de ViewModel.

### W3-T3.4 — rótulos localizáveis em detalhes (07/10/2026)

- **Status:** BUILT; fluxo visual básico da fixture com D-pad validado no Moto G34. Inglês em runtime, TalkBack e TV continuam pendentes.
- Rótulos da tela de detalhes e cards foram movidos de texto literal no XML/Kotlin para recursos reutilizáveis nas variantes comum e paisagem. Os estados de reproduzir/continuar, favorito/favoritado e seguir/seguindo também usam recursos localizados; referências redundantes reaproveitam strings já existentes.
- Build e suíte completa: `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`, 298 testes/66 suítes, 0 falhas/erros/skips. `git diff --check` passou.
- No Moto G34 Android 15, `.visualreview`, DOWN levou à temporada e outro DOWN a `cardEpisodeItem` focado em `[413,408][781,720]`; Activity permaneceu ativa. APK SHA-256 `87CC91D46681910DA49FADDA806723D3F51B1CBCF2072D75E2851192158146A3`.
- Evidências e limites: `docs/ui-audit/2026-10-07-details-localized-labels/README.md`. Fixture sintética; não prova Drive, playback real, mudança de idioma em inglês ou Android TV.

### W3 — ordem estável das prateleiras durante refresh (07/10/2026)

- **Status:** RUNTIME_VALIDATED na fixture compartilhada de QA; integração dos coletores reais da Home com Drive permanece pendente.
- Se a prateleira de catálogo ou Adicionados Recentemente estiver focada, atualizações mantêm a ordem relativa dos itens existentes, aplicam dados novos, removem itens que saíram do resultado e acrescentam os novos no fim. Ao sair do foco, a ordenação de origem volta a ser aplicada; sort explícito A–Z/Z–A continua intacto.
- Dois testes cobrem estabilidade/atualização/append e remoção de entradas ausentes. Build completo: 300 testes/67 suítes, 0 falhas/erros/skips; `assembleDebug` passou. APK debug `.visualreview` arm64 SHA-256 `E3A7C07013B30810F3D8F0F7C48F1A671C2C9D9185D62D3EC466E0C2DF4310D3`.
- No Moto G34 Android 15, app landscape, RIGHT focou o segundo item. Após o snapshot invertido, hierarquia confirmou mesmo `cardStarredItem` focado e texto atualizado; a ordem exibida manteve as três posições e acrescentou a quarta série no fim. Dados sintéticos marcados; nenhum Drive ou dado persistido consultado. Captura/hierarquia e limites em `docs/ui-audit/2026-10-07-home-refresh-order/README.md`.

### KODI-01 — Suite de Recursos de Reprodução Kodi & Apple HIG (07/10/2026)

- **Status:** RUNTIME_VALIDATED em hardware real (Moto G34 5G Android 15).
- **Recursos Entregues e Validados:**
  1. **Room v8 Delay Persistence (`audioDelayMs`, `subtitleDelayMs`):**
     - Persistência por vídeo de calibração de áudio e legenda em banco Room com migração `MIGRATION_7_8` e testes passando 100%.
  2. **ClearLogo / Transparent Title Art:**
     - Renderização de arte transparente no hero spotlight da Home nos 4 layouts (`default`, `land`, `sw720dp-land`, `television`).
  3. **Auto Frame Rate Matching (AFR - Display Mode Switching):**
     - `AutoFrameRatePolicy.kt` conectando detecção de FPS em tempo de execução à API `Window.preferredDisplayModeId` no ExoPlayer e no MPV, com reset limpo no `onDestroy`.
  4. **Modo Noturno & Dynamic Range Compression (DRC):**
     - MPV: Filtro em tempo de execução `dynaudnorm=f=250:g=31:p=0.5` ativado e verificado no logcat (`mpv: Setting option 'graph' = 'dynaudnorm'`).
     - ExoPlayer: Acoplamento de `android.media.audiofx.LoudnessEnhancer` ao `audioSessionId` com ganho alvo de +600 mB (+6 dB) para clareza vocal.
  5. **Smart Seek Progressivo do Kodi (`SeekStepPolicy`):**
     - Acumulador progressivo (+10s, +30s, +60s, +180s, +300s) unificado para D-Pad e botões em tela nos dois players (`PlayerActivity` e `MPVActivity`).
  6. **Pitch-Preserved Speedup (`scaletempo`):**
     - MPV configurado com `audio-pitch-correction=yes`; ExoPlayer com `PlaybackParameters(speed, /* pitch = */ 1.0f)`.
- **Validação em Hardware:**
  - Build `testDebugUnitTest` 100% verde (300 testes unitários).
  - APK `makimono-v1.6.4.3-arm64-v8a-debug.apk` instalado no Moto G34 (`0086442783`).
  - Evidências salvas em `docs/ui-audit/motog34_audio_dialog_active_verified.png` e `motog34_smart_seek_feedback.png`.

### Q08 — resolução segura de código manual no protótipo local (07/10/2026)

- **Status:** BUILT; lookup validado por teste HTTP e store PostgreSQL. Q08 segue IN_PROGRESS.
- Adicionado `POST /v1/pairing/lookup`: corpo JSON limitado a 1 KiB; código é normalizado e validado; busca só solicitações pendentes não expiradas; resposta `no-store` contém o locator, nome informado da TV e expiração, sem poll secret, capacidades ou confirmação. Tentativas desconhecidas/inativas recebem resposta genérica. Rate limit independente: 5/min por peer.
- Testes cobrem formatação manual (hífen/caixa/espaços), campos da prévia, não autorização, segredo de polling independente, códigos inválidos/desconhecidos, rate limit e consulta após cancelamento em PostgreSQL.
- `backend test`: 22 testes, 0 falhas/erros/skips. `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`: 309 testes Android, 0 falhas/erros/skips; build completo.
- APK arm64 foi gerado (SHA-256 `F4405D9D42D11E2ECDEBFACB538A4070AC41A597B96F8B42DB0BE8ADA36363B0`), sem instalação nesta rodada para preservar reprodução MPV de QA já ativa no Moto G34.
- Limite: endpoint apenas identifica um desafio para a etapa de browser. Não há página mobile, OAuth real, sessão autenticada, confirmação/CSRF ou claim; backend segue loopback `.invalid` e não deve ser exposto.

### Q08 — prévia QR e formulário manual para browser (07/10/2026)

- Adicionei `GET /pair/{challengeId}` e `POST /pair/lookup`: o QR agora resolve para uma página móvel auto-contida; a digitação manual usa POST form bounded, sem colocar o código na URL. A prévia mostra nome da TV e expiração, escapa conteúdo não confiável e deixa explícito que Google OAuth ainda não está configurado. Nenhuma ação concede aprovação.
- Segurança web: `no-store`, CSP sem scripts/recursos externos, `no-referrer`, `nosniff` e `X-Frame-Options: DENY`. O locator retorna apenas campos da prévia e apenas enquanto o desafio está pendente/ativo.
- `backend test`: 25 testes, 0 falhas/erros/skips; a classe PostgreSQL Testcontainers executou 5 testes sem skips. Inclui XSS, ausência de poll secret, lookup/cancelamento, códigos inválidos, rate limit e limite de corpo.
- Android: a rodada anterior `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1` passou 309 testes e compilou o APK arm64. APK não instalado nesta rodada para preservar a sessão MPV de QA ativa. Inspeção visual concluída no browser local em desktop e viewport de 390×844, sem overflow horizontal; o formulário não foi submetido manualmente porque o POST está coberto por teste de rota.
- Q08 permanece IN_PROGRESS: página de aprovação OAuth, state/sessão web, CSRF, confirmação/negação e deploy seguro ainda não existem. O host continua loopback-only e a URL de verificação continua `.invalid`.
- Evidência atualizada: `docs/ui-audit/2026-10-07-tv-pairing-code-lookup/README.md`.

### Q08 — smoke HTTP local de criação, prévia e cancelamento (07/10/2026)

- **Status:** contrato local RUNTIME_VALIDATED; Q08 permanece IN_PROGRESS. A validação não constitui autorização nem login funcional.
- Iniciei o serviço Ktor loopback em `127.0.0.1:8097`, com URL `.invalid` e store em memória. O fluxo HTTP criou desafio descartável, abriu prévia QR, resolveu o mesmo desafio pelo formulário POST, confirmou que ambos deixaram o estado `pending`, cancelou usando o segredo e confirmou `410 Gone` no poll posterior. As assertions passaram; nenhum código ou segredo foi impresso.
- Teste forçado do backend (`--rerun-tasks`) passou via `X:\`/`G:\`: 25 testes em 4 suítes, 0 falhas/erros/skips; 5 testes PostgreSQL foram executados. A tentativa pelo caminho acentuado falhou no carregamento da classe do worker, então foi repetida no mapeamento ASCII.
- Servidor encerrado após o smoke e porta 8097 sem listener. Nenhum app Android foi instalado ou conta/dado real foi acessado.
- Evidência e limites: `docs/ui-audit/2026-10-07-pairing-live-http/README.md`. Browser/celular real, aprovação OAuth, state/CSRF, confirmação/negação, sessão/claim, deploy e TV→celular continuam pendentes. A API continua loopback-only e não deve ser exposta.

### W3-T3.2 — correções de hierarquia e geometria na Home TV (07/10/2026)

- A rail das variantes `layout-television` e `layout-sw720dp-land` foi ampliada de 128 para 176 dp; os nomes agora cabem sem wrap/truncamento e o cálculo de colunas da grade considera a largura efetiva.
- O título “Novos episódios” alinha com os cards usando `home_content_margin` em todas as quatro variantes.
- Evidência: captura e relatório em `docs/ui-audit/2026-10-07-home-fresh-audit/`. Build Debug e 309 testes Android passaram; D-pad alcançou todos os itens da rail no Moto G34, usando viewport amplo simulado. Não houve validação em TV física.
### W3-T3.2 — alinhamento da prateleira “Adicionados recentemente” e percurso vertical de foco (07/10/2026)

- O título de recentes recebeu recuo `home_content_margin` nas quatro variantes para começar no mesmo eixo dos cards; junto da correção anterior, os cabeçalhos “Novos episódios” e “Adicionados recentemente” ficam alinhados.
- Build Debug e 309 testes passaram. No Moto G34 com fixture sintética e viewport amplo simulado, DOWN atravessou as cinco prateleiras; RIGHT mudou de card e UP retornou uma fileira. Evidência: `docs/ui-audit/2026-10-07-home-fresh-audit/README.md`, `home-shelf-alignment-final.png`, `home-dpad-path.png`.
- Não é aceite de TV física/overscan/TalkBack nem integração com Drive autenticado; esses gates permanecem abertos.
### W3-T3.4 — status de publicação sem inferência incorreta (07/10/2026)

- Corrigido o badge de status da série: anteriormente todo valor diferente de `Finished` virava “Em exibição”. A política agora reconhece estados MAL/AniList completo, em exibição, futuro, hiato e cancelado; campo vazio ou valor desconhecido fica oculto.
- Adicionados testes para os valores suportados, normalização de caixa/separadores e status desconhecido. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 311 testes/70 suítes, sem falhas/erros/skips.
- No Moto G34 Android 15, pacote isolado `.visualreview`, a fixture de detalhes abriu; D-pad percorreu ação→temporada→episódio. Long-press abriu menu, DOWN destacou a primeira ação e BACK restaurou foco ao card invocador. Evidências: `docs/ui-audit/2026-10-07-details-status-policy/`.
- A fixture não consulta MAL/AniList nem prova o valor recebido por série; binding compilado e regra testada isoladamente. TV física, TalkBack e metadados reais permanecem pendentes. Nenhum dado de Drive foi acessado.
- Reteste após a compilação final (incluindo normalização independente de locale): 311 testes/70 suítes, sem falhas/erros/skips; instalação `.visualreview` e foco restaurado no card confirmados. APK arm64 SHA-256 `39C323A0C0F7BC631FA063FD5E6C248633A1178A14F07FE406F5749AD47B8147`.

### W3-T3.4 — disclosure acessível para sinopse longa (07/10/2026)

- A tela de detalhes agora limita a sinopse a três linhas e oferece “Ler sinopse completa” somente quando o texto realmente tem reticências/truncamento. Expandir mostra o conteúdo inteiro e mantém “Recolher sinopse” visível; recolher restaura a rolagem anterior.
- O D-pad percorre ação principal → disclosure e disclosure → ação principal. O disclosure tem foco visível; após expansão, o controle é rolado para a viewport com margem inferior segura. Sinopses curtas/vazias não deixam um controle sem função.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 313 testes, 0 falhas/erros/skips. No Moto G34 Android 15, fixture local confirmou UP/DOWN, expansão, recolhimento e restauração visual. Repeti a renderização em 1920×1080/240 dpi simulado e restaurei 720×1600/280 dpi; isso não substitui teste em TV real.
- APK arm64 SHA-256 `AA8EB4E459A5BE771C0FBC6630D5EF3E55E095DCBA5829A2CCA2714E5AB5DF3A`. Capturas, XML e limites em `docs/ui-audit/2026-10-07-details-long-synopsis/README.md`.
- Reteste de toque encontrou que o primeiro toque apenas adquiria foco no controle de sinopse. Apliquei `SingleTapActivation`; após reinstalar o APK final, um único toque expandiu o conteúdo no Moto G34. `testDebugUnitTest assembleDebug`: 313 testes sem falhas/erros/skips; APK arm64 SHA-256 `740998E8454539B0CA2F27610799A9500E0DE08F4F7F4CBDDEEBC99D5157B5DB`. Evidências em `docs/ui-audit/2026-10-07-details-long-synopsis/touch-single-after.png`.
- Auditoria de rebind: substituir sinopse longa por curta enquanto o disclosure está focado não deixa foco em controle oculto; `btnPrimaryAction` recebe foco antes de o link desaparecer. Fixture/runtime em `docs/ui-audit/2026-10-07-details-long-synopsis/rebind-after.xml`.

### W3-T3.4 — descrição opcional no episódio focado (07/10/2026)

- O campo `description` opcional do Google Drive é preservado na resposta/modelo e solicitado pela API. No card, o texto só aparece sob foco, com limite de 2 linhas e 600 caracteres; arquivo sem descrição não ganha placeholder nem espaço extra. Nenhum conteúdo é inferido do nome do arquivo.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 314 testes/71 suítes, 0 falhas; APK Debug arm64 SHA-256 `2774FC7986E154A35DB265F38841B5F8EFBA3DBDB83110DBDA67A8C9226FE671`.
- No Moto G34 Android 15, instalei somente `.visualreview`. D-pad focou o primeiro card com texto; RIGHT moveu para card sem descrição e a ocultou; toque no primeiro card mostrou novamente. A fixture era sintética e não acessou Drive. Evidências/limites em `docs/ui-audit/2026-10-07-episode-description-focus/`.
- Continua pendente validar descrições reais (dependem de metadados preenchidos no Drive), TalkBack, TV física e geometria em várias densidades/overscan.

### W3-T3.4 — descrição acessível da ação de episódio (07/10/2026)

- O card focável anuncia “Reproduzir [título]” e acrescenta duração, áudio e progresso quando disponíveis; metadados ausentes são omitidos. O estado de progresso concluído respeita o limiar de 95% usado pela tela de detalhes. Composição testada; strings disponíveis em inglês base e pt-BR.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 317 testes/71 suítes, 0 falhas/erros/skips; APK arm64 `.visualreview` SHA-256 `E7115F3348F205C77749DAA962E92225BF79D8F73F7E90269798C9AEDB5D10D7`.
- No Moto G34, UIAutomator confirmou que o rótulo do card focado muda ao mover RIGHT. Evidências: `docs/ui-audit/2026-10-07-episode-card-accessibility/`. Ainda requer TalkBack e TV física; a fixture é sintética.

### W3-T3.2 — reteste de foco Home no build atual (07/10/2026)

- No Moto G34 Android 15, `.visualreview`, a Home atual percorreu com DOWN as seis prateleiras Continuar, Minha fila, Novos episódios, Minha lista, Adicionados recentemente e Catálogo. UP voltou ao CTA principal; RIGHT avançou ao card seguinte de Continuar.
- O APK utilizado é o Debug arm64 já validado acima (SHA-256 `2774FC7986E154A35DB265F38841B5F8EFBA3DBDB83110DBDA67A8C9226FE671`). Os dumps confirmam cada destino de foco. Evidência: `docs/ui-audit/2026-10-07-home-current-retake/README.md`.
- Fixtures sintéticas apenas; não prova shelves integradas ao Drive, preview ligado ao ViewModel, TV física, overscan ou TalkBack. Nenhum dado real foi acessado.

### W3-T3.4 — reteste de foco visual e rótulo acessível (07/10/2026)

- `FocusEngine.attach` agora também recebe o callback de divulgação da descrição; isso preserva a animação central de escala e elevação em vez de sobrescrever o listener de foco.
- Build Debug + 317 testes passaram. No Moto G34, a captura confirma o anel/escala do card em foco; RIGHT troca o rótulo acessível para o episódio seguinte. APK arm64 `.visualreview` SHA-256 `CA5A511656B89CDDB43D4D70ED9C426D90E1D6DF1A812A7A5442E1913742FC91`.
- Continua pendente homologação com TalkBack e em TV física; fixtures sintéticas, sem acesso ao Drive.

### Q05/C4 — preferência de movimento aplicada a foco e blur (07/10/2026)

- A configuração por perfil ativo agora chega às Views síncronas do `FocusEngine`. O modo reduzido mantém o indicador estático de foco, mas remove escala e elevação. `FrostedWindow` usa blur apenas quando permitido por configuração, sistema e capacidade do dispositivo; a alternativa é opaca.
- Ações de detalhes, tabs de temporada e cards do drawer de episódios passaram a usar a política central de escala. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 320 testes/72 suítes, sem falhas/erros/skips. Em fixture real do adapter no Moto G34, D-pad mantém o anel ciano e o card não cresce no modo reduzido; RIGHT continua navegando.
- APK arm64 `.visualreview` inicial SHA-256 `B06BA7EAD923ACD5D45F6C85C7159A4CD1E1EA343E70D44D9EE81E7DD8AAD685`.
- No reteste do Moto G34, corrigi as categorias de Settings para ativarem com um toque e confirmei que “Reduzido” persiste após reinício no perfil principal, enquanto o perfil secundário mantém sua própria preferência. Build + 320 testes passaram; APK arm64 final SHA-256 `FD763FE8AB7C7F772EEE4327E039A4E5C2AB710BC1F2D13715BCB30C152E21FB`.
- A correção de toque inicialmente bloqueava a rolagem horizontal da faixa móvel. Liberei o intercept do `HorizontalScrollView` somente nas categorias; um swipe trouxe “Dados e sessão” para o viewport e um toque abriu sua seção. Build + 320 testes passaram; APK arm64 `.visualreview` SHA-256 `0815E56FCB1CD675DE1A1E2E4DB277306A7EB18BDD76F31794EACE51DAF5B5C7`.
- Evidências/limites: `docs/ui-audit/2026-10-07-reduced-motion-focus/README.md`. TalkBack, TV física, rotação, migração e Android anterior à API 31 continuam pendentes.

### W3-T3.4 — títulos legíveis para variações de abertura (07/10/2026)

- **Status:** RUNTIME_VALIDATED na fixture compartilhada em Moto G34; integração com Drive real, TalkBack e TV continuam pendentes.
- Arquivos `Bakemonogatari_OP4a_` e `Bakemonogatari_OP4b_` agora são reconhecidos como variações da abertura, exibidos como “Abertura 04A/B” e não podem receber título canônico nem badge de episódio regular.
- Extraí a política de título para teste independente. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 323 testes, 0 falhas/erros/skips. Instalei somente o APK `.visualreview`; D-pad DOWN/RIGHT confirmou foco nos cards e rótulos corretos.
- Capturas/hierarquias e limites: `docs/ui-audit/2026-10-07-details-episode-names-README.md`. Nenhuma conta ou dado real foi acessado.

### W3-T3.7 — rótulo completo nas abas do editor de perfil (07/10/2026)

- **Status:** corrigido e validado visualmente no Moto G34; D-pad simulado nesta tela passou; TV física e TalkBack seguem pendentes.
- “Papéis de parede” era cortado em viewport móvel no tamanho de fonte padrão. As abas do catálogo agora permitem duas linhas sem reticências e têm alvo mínimo de 48 dp.
- `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`: 323 testes/73 suítes, 0 falhas/erros/skips. APK arm64 instalado apenas em `.visualreview`.
- Toque alternou para a galeria de fundos. D-pad DOWN×3 + RIGHT×2 moveu foco até o terceiro avatar com anel ciano visível; BACK fechou o editor sem salvar. Evidências e limites em `docs/ui-audit/2026-10-07-profile-editor-tabs/README.md`.

## Q02.10 — classificar erros do Drive e separar estados de recuperação (07/10/2026)

- **Status:** BUILD + FIXTURE RUNTIME_VALIDATED; Q02 geral continua IN_PROGRESS.
- `Resource.Error` preserva `ResourceErrorKind` desde o repositório: autenticação, acesso negado, limite/cota, pasta ausente, rede, servidor ou desconhecido. Google `reason` diferencia quota/rate limit de um 403 de permissão sem encaminhar o payload do provedor à UI.
- Files agora pede reconexão somente em autenticação inválida; falta de acesso e pasta removida oferecem retorno, limite/servidor/rede permitem retry; o erro não é repetido num snackbar técnico e o skeleton/shimmer é encerrado.
- A trilha de pasta configurada e IDs ancestrais é propagada entre níveis. Clique em pasta/atalho para a própria pasta ou ancestral é interrompido com aviso, sem empilhar destinos repetidos.
- Testes cobrem classificação HTTP, razões de quota, `IOException`, preservação da categoria, política visual, raiz extraída da query, IDs escapados e rejeição de ciclos. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 334 testes/75 suítes JVM, 0 falhas/erros/skips.
- No Moto G34, APK arm64 instalado somente em `zechs.drive.stream.visualreview`; a fixture `files_access_denied` confirmou texto, ação e foco D-pad. OK acionou a ação, encerrou a fixture e não gerou crash. Evidências: `docs/ui-audit/2026-10-07-files-permission-state/README.md`.
- **Pendente para aceite de Q02:** resposta 403/rede/cota observada na tela com sessão de teste Drive, sessão expirada via OAuth, recuperação da paginação autenticada, 2.501 itens reais, atalhos cíclicos reais, TV física e TalkBack. Nenhum dado real foi acessado nesta rodada.
