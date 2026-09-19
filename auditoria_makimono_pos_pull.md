# Auditoria Pós-Pull do Makimono

**Repositório:** `takaokensei/makimono`
**Branch:** `main`
**Commit auditado:** `7d8157cd04e1beefd6993a891204904a51f88369`
**Pull:** `git pull --ff-only` concluído; checkout já estava atualizado.
**Base de comparação:** `8f4ce266b98587eb4afe1c8de207e8cc72ff2034`
**Mudança medida desde a base:** 100 arquivos, +8.648 linhas, -597 linhas, em 19 commits.
**Nota atual:** **6,2/10, confiança média-baixa**.

## Resumo Executivo

O trabalho avançou de forma relevante. A base agora tem migrações Room, armazenamento criptografado da sessão, redação de logs, escopo Drive readonly, favoritos locais, isolamento parcial por perfil, cache de catálogo, busca global, notificações, backup, política unificada de progresso, layouts TV e uma superfície visual mais próxima de um media center.

O principal problema não é mais ausência de código. É integração e validação. Vários recursos foram implementados como infraestrutura, mas ainda não possuem entrada visível na UI. A abstração `PlayerEngine`/`PlaybackCoordinator` também existe apenas como código isolado e não é instanciada pelas Activities reais. O CI do commit atual continua falhando, não há build executável neste ambiente e não houve validação em uma TV real.

A sensação de “ainda não parece profissional” é justificada principalmente por:

- design system apenas parcialmente aplicado, com muitos textos e cores hardcoded;
- Home rica em um hero e uma prateleira, mas sem histórico/queue/following realmente navegáveis;
- busca anunciada como anime, pasta e episódio, mas implementada para pastas/atalhos;
- foco D-pad e estados de erro ainda dependentes de lógica espalhada;
- claims e documentos ainda misturam a auditoria antiga com o estado atual.

## Validação Executada

| Verificação | Resultado | Observação |
|---|---|---|
| `git pull --ff-only` | Passou | `Already up to date` no `main` |
| `git status --short --branch` | Passou | checkout remoto limpo antes das alterações desta auditoria |
| `git diff --check 8f4ce266..HEAD` | Passou | sem erros de whitespace no intervalo auditado |
| `bash scripts/verify-claims.sh` | Passou | 6 checks retornaram OK |
| Execução local do Gradle | Bloqueada | não há `java` nem `JAVA_HOME` neste ambiente |
| GitHub Actions CI no commit `7d8157c` | Falhou | run #4: `Lint, Test & Assemble Debug` |
| Validação visual TV/mobile | Não executada | sem emulador, APK ou hardware disponível |

O script de claims passa quando chamado com `bash`, mas possuía BOM UTF-8 antes do shebang e não era seguro para execução direta. Essa falha foi corrigida nesta entrega.

## Nota por Dimensão

| Dimensão | Nota | Confiança | Justificativa |
|---|---:|---|---|
| Arquitetura | 6,4 | Média | mais separação e contratos, mas as Activities continuam grandes e os novos engines não estão conectados |
| Features | 6,3 | Média-baixa | muitos fundamentos novos, vários sem fluxo completo de usuário |
| UI TV | 6,8 | Baixa | layout dedicado e medidas coerentes, porém sem renderização/D-pad real |
| UI mobile | 6,0 | Baixa | navegação e bottom bar existem, mas há duplicação de superfícies e muitos literais |
| Player | 6,5 | Média-baixa | media keys, PiP e progresso evoluíram; coordinator não está em produção |
| Drive/dados | 6,4 | Média | paginação central melhorou, mas há chamadas críticas de uma página |
| Segurança | 6,3 | Média | tokens protegidos e logs reduzidos; pairing HTTP e `runBlocking` ainda preocupam |
| Testes/CI | 4,8 | Alta | muitos testes foram adicionados, mas CI atual está vermelho e não há execução local |
| Performance | 6,0 | Baixa | loader de imagens e cache existem, sem benchmark ou APK medido |
| Design system | 5,2 | Média | tokens/temas existem, mas Home e cards ainda ignoram grande parte deles |
| Documentação | 4,7 | Alta | `AUDIT_REPORT.md`/`Implementation.md` ainda carregam números e claims do estado antigo |

## Achados Prioritários

### P1-01 — CI vermelho no commit atual

**Evidência:** o run #4 de `.github/workflows/ci.yml` para `7d8157c` terminou com `failure`. Os runs anteriores do novo CI e os runs recentes de release também terminaram falhando.

**Impacto:** não há evidência pública de que lint, testes e `assembleDebug` estejam verdes no estado que será publicado. Isso impede afirmar robustez de produção e torna arriscado continuar adicionando features visuais sobre uma base não validada.

**Correção recomendada:** obter o log do job `Lint, Test & Assemble Debug`, corrigir a primeira falha, reexecutar lint, testes e assemble, e exigir status verde antes de qualquer release.

**Aceitação:** run de CI para o HEAD termina verde nos três passos: `lintDebug`, `testDebugUnitTest` e `assembleDebug`.

### P1-02 — `PlayerEngine` e `PlaybackCoordinator` não estão conectados ao player real

**Evidência:** `PlayerEngine.kt`, `ExoPlayerEngine.kt`, `MpvPlayerEngine.kt` e `PlaybackCoordinator.kt` aparecem somente no próprio pacote `ui/player/engine` quando o código de produção é pesquisado. `PlayerActivity.kt` e `MPVActivity.kt` não instanciam esses tipos.

**Impacto:** o commit de arquitetura fornece contratos e testes, mas a aplicação continua executando lógica duplicada nas duas Activities. Correções futuras ainda podem divergir entre ExoPlayer e MPV.

**Correção recomendada:** injetar um coordinator real no fluxo de reprodução, delegar next/previous/progresso/autoplay a ele e deixar Activities como adaptadores de UI. Alternativamente, remover os contratos até que estejam realmente conectados, evitando falsa sensação de paridade.

**Aceitação:** uma sequência única de next/previous/resume/autoplay executa nos dois engines usando o mesmo coordinator e um teste de integração comprova a ligação.

### P1-03 — Busca global não busca episódios e pode exibir resultados obsoletos

**Evidência:** `HomeViewModel.kt:596-603` monta a query Drive apenas para `application/vnd.google-apps.folder` ou shortcut. O campo da Home promete “anime, pasta ou episódio”. O pipeline em `HomeViewModel.kt:557-615` também não cancela a requisição remota anterior quando o usuário muda rapidamente o texto.

**Impacto:** episódios não aparecem nos resultados apesar do texto da UI. Uma resposta lenta para a busca anterior pode ser mesclada à busca atual, gerando cards errados e comportamento visual instável.

**Correção recomendada:** separar query local de busca global, aceitar vídeo/shortcut de vídeo, usar `flatMapLatest` ou Job cancelável, e guardar resultados por query antes de publicar.

**Aceitação:** busca encontra uma pasta e um episódio em páginas diferentes; ao digitar `nar` e imediatamente `ble`, nenhum resultado de `nar` aparece na tela de `ble`.

### P1-04 — Há caminhos críticos que continuam sem paginação

**Evidência:** `HomeViewModel.kt:702-766` usa `getFiles` uma vez para localizar vídeos da pasta e subpastas. `FilesViewModel.kt:49-55` também busca o primeiro episódio com uma única página. `DriveRepository.getAllFiles` é paginado, mas esses fluxos não o utilizam.

**Impacto:** quick play pode não encontrar o episódio correto em pastas grandes. A correção de paginação não cobre todos os caminhos de produto, especialmente bibliotecas longas.

**Correção recomendada:** centralizar uma API paginada também para resolução de primeiro/último episódio, com limite explícito e erro visível quando o limite for atingido.

**Aceitação:** fake Drive com três páginas encontra o primeiro vídeo, o próximo episódio e o episódio em andamento; nenhuma chamada crítica termina silenciosamente na página 1.

### P1-05 — Pairing de TV ainda transporta refresh token por HTTP aberto

**Evidência:** `TvLoginServer.kt:88` cria `ServerSocket` sem limitar a interface; `TvLoginServer.kt:226-239` recebe refresh token; `TvLoginServer.kt:336-350` responde HTTP puro com CORS `*`.

**Impacto:** qualquer processo/dispositivo na mesma rede capaz de observar o tráfego pode capturar uma credencial de longa duração. O nonce reduz replay, mas não transforma HTTP em canal confidencial.

**Correção recomendada:** preferir fluxo sem transferência de refresh token, usar código de pareamento de uso único trocado pela TV, restringir bind à interface necessária e remover CORS wildcard. Se o token continuar sendo transferido, documentar o risco e exigir uma confirmação explícita de rede confiável.

**Aceitação:** o browser nunca envia refresh token diretamente; a TV troca apenas um código curto de uso único por sessão, com expiração e invalidação após o primeiro uso.

### P1-06 — Worker de episódios não acompanha perfis reais e limita contagem silenciosamente

**Evidência:** `EpisodeCheckWorker.kt:64-79` usa `listOf("caua", "anime")` e `maxPages = 3`. O próprio código mantém `TODO: read from ProfileManager`.

**Impacto:** perfis criados pelo usuário não recebem notificações. Pastas com mais de 300 filhos podem ter contagem incorreta e gerar ou omitir notificações.

**Correção recomendada:** injetar `ProfileManager`/fonte persistida de perfis, iterar perfis existentes e separar “contagem de episódios” de “arquivos da pasta”. Para pastas grandes, persistir cursor ou consultar uma operação de contagem confiável.

**Aceitação:** um perfil criado depois da instalação é incluído no worker; uma pasta com mais de três páginas não é tratada como estável enquanto houver páginas pendentes.

### P1-07 — Queue e following foram criados, mas não têm fluxo de usuário completo

**Evidência:** `WatchQueueRepository` e `FollowedFolderRepository` existem. `HomeViewModel` expõe `addToQueue`/`removeFromQueue`, mas a pesquisa de chamadas não encontra uma ação de UI que os invoque. Não há tela/rail de queue. O following também só é consumido pelo worker e backup, sem ação visível de seguir/deixar de seguir.

**Impacto:** o usuário não consegue descobrir, adicionar ou revisar essas features. A infraestrutura aumenta complexidade sem aumentar valor percebido, que é justamente o que faz o produto parecer incompleto.

**Correção recomendada:** entregar primeiro um fluxo vertical completo: menu contextual em card, estado “na fila”, tela Queue, reordenação, remover e autoplay do próximo. Para following, botão na série, indicador ativo e tela de gerenciamento.

**Aceitação:** usuário adiciona um anime pela Home, encontra-o na Queue após reiniciar, remove-o, e consegue seguir uma pasta pela tela de detalhes.

### P1-08 — `TokenAuthenticator` ainda usa `runBlocking`

**Evidência:** `TokenAuthenticator.kt:4-5,55-56` chama `runBlocking` durante o callback síncrono do OkHttp.

**Impacto:** renovação de token bloqueia uma thread do dispatcher HTTP. Sob concorrência ou rede lenta isso pode atrasar requisições e contradiz a alegação de token provider sem bloqueio.

**Correção recomendada:** manter renovação fora do callback síncrono, pré-aquecer o token antes da requisição e retornar apenas um token cacheado no authenticator; quando não houver token, falhar e deixar o chamador assíncrono renovar.

**Aceitação:** teste com dispatcher bloqueante prova que o caminho de `authenticate` não espera rede; uma requisição 401 gera no máximo uma renovação coordenada.

## Achados de Design e UX

### P2-01 — Design tokens existem, mas a Home ainda é majoritariamente hardcoded

**Evidência:** `themes.xml` define cinco famílias de tokens, mas `fragment_home.xml`, `layout-land/fragment_home.xml` e `item_continue_watching_shelf.xml` usam diretamente cores como `#12A0C7`, `#F0F0F0`, `#94A3B8` e textos literais. `FilesViewHolder.kt` também usa `Color.parseColor("#FFD700")`.

**Impacto:** trocar Tokyo Night, Nord, Dracula ou Kodi Estuary não muda toda a interface de maneira coerente. A aparência parece um mockup único com nomes de temas, não um sistema visual real.

**Correção recomendada:** migrar cores para atributos semânticos, dimensões para recursos e textos para `strings.xml`; manter uma única linguagem visual por tema. Criar componentes reutilizáveis para rail item, shelf card, badge e CTA.

**Aceitação:** alternar cada tema altera Home, Files, Details, Settings e dialogs sem restos visíveis do tema anterior; lint não encontra novo texto hardcoded.

### P2-02 — O hero depende de metadado remoto para existir

**Evidência:** `featuredHeroContainer` começa `gone` nos layouts. `HomeFragment.kt` só o revela quando `featuredAnime` não é nulo. `resolveFeaturedSpotlight` depende de AniList/Tenrai para enriquecer o catálogo.

**Impacto:** em primeiro acesso, rede lenta, quota ou indisponibilidade do provedor, a Home perde seu principal elemento cinematográfico e cai para uma grade/prateleira comum.

**Correção recomendada:** renderizar imediatamente um hero local baseado em `DriveFile.thumbnailLink`/poster, com título da pasta, e enriquecer depois sem alterar foco ou geometria.

**Aceitação:** desligar AniList/Tenrai mantém hero visível com arte local, CTA funcional e estado de carregamento discreto.

### P2-03 — O botão de play visual da grade está desativado permanentemente

**Evidência:** `FilesViewHolder.kt:296-297` define `ivPlayOverlay.isVisible = false`; o listener em `FilesViewHolder.kt:327-334` fica sem caminho visual para ser acionado.

**Impacto:** cards de vídeo exibem affordance de play no layout, mas não entregam essa affordance em runtime. Isso cria inconsistência e aumenta a sensação de interface inacabada.

**Correção recomendada:** exibir o overlay somente em vídeos, ou remover completamente o elemento do XML. Em TV, o Enter do card deve ter uma ação visual inequívoca.

**Aceitação:** card de vídeo mostra play e inicia o player; card de pasta não mostra um controle que não tem ação.

### P2-04 — `ScreenState` não é o modelo de estado real das telas

**Evidência:** `ScreenState.kt` foi criado, mas Home e Files continuam usando combinações de `Resource`, `isLoading`, `hasFailed`, `layoutEmpty` e visibilidades manuais.

**Impacto:** loading, vazio, offline, 401/403, 429 e sessão expirada continuam com tratamento desigual. O D-pad pode perder contexto quando uma lista muda para erro ou volta de retry.

**Correção recomendada:** adotar um único estado por tela e uma tabela de ações/feedback. O estado deve carregar conteúdo em cache durante refresh e preservar o alvo de foco.

**Aceitação:** cada tela tem estados testáveis para loading inicial, refresh, empty, offline, 401, 403, 429 e retry.

### P2-05 — Recursos prometidos ainda não formam uma Home profissional

**Evidência:** Home possui um hero e uma shelf de Continue Watching, mas “VER HISTÓRICO” apenas faz `smoothScrollToPosition` para o último item em `HomeFragment.kt:128-132`. Queue, following, “Assistir depois”, sugestões, filtros e histórico completo não têm superfície navegável.

**Impacto:** a tela parece visualmente melhor, mas não cria a densidade de produto encontrada em Kodi, Jellyfin, Plex ou Netflix.

**Correção recomendada:** evoluir a Home em rails com propósito: Continuar, Minha fila, Favoritos, Adicionados recentemente e Seguindo. Cada rail deve ter regra de ordenação, estado vazio e ação “ver tudo”.

**Aceitação:** cada rail possui dados reais, foco restaurável, navegação para coleção completa e estado vazio específico.

### P2-06 — Strings e acessibilidade ainda não estão consolidadas

**Evidência:** há strings literais em layouts e Kotlin, por exemplo `HomeFragment.kt`, `FilesFragment.kt:529-543` e vários textos de `fragment_home.xml`. A auditoria visual não foi executada com TalkBack, escala de fonte ou Accessibility Scanner.

**Impacto:** tradução pode ficar incompleta, textos podem cortar em nomes longos e ações podem ser anunciadas de forma inconsistente.

**Correção recomendada:** extrair textos, usar plurais e content descriptions semânticos, testar fonte 1,3x e navegar as telas com TalkBack e D-pad.

## Estado Real das Features

| Feature | Estado auditado | Veredito |
|---|---|---|
| Scope Drive readonly | Constante, UI e claims alinhados | Confirmado por leitura |
| Tokens de sessão | `EncryptedSessionStore` com migração legada | Parcial; falta instrumentação real |
| Logs HTTP | redaction e sem BODY em release | Parcial; CI deve cobrir regressões |
| Migrações Room | v1 até v6 declaradas | Parcial; falta build/teste neste ambiente |
| Favoritos locais | repository/DAO e uso em Files/Home | Funcional por código, sem teste visual |
| Isolamento de perfis | watchlist, favoritos, queue e settings usam profileId | Parcial; worker ainda hardcoded |
| Busca global | cache + Drive paginado até 3 páginas | Parcial; não inclui episódios e pode misturar queries |
| Queue | DAO/repository/ViewModel | Não entregue ao usuário |
| Following/notificações | DAO/repository/worker | Não entregue ao usuário; perfis hardcoded |
| Cache offline | catalog Room e leitura inicial | Parcial; sem estados uniformes de offline |
| Player coordinator | contrato e testes | Não conectado às Activities |
| MediaSession/PiP | helpers e integração declarada | Não verificado em hardware |
| Updater | checksum e certificado | Parcial; precisa de release real e teste de instalação |
| Home TV | land layout com rail, hero e shelf | Promissor; sem screenshot/D-pad real |
| Temas | cinco famílias de tokens | Parcial; muitos literais ignoram tokens |

## Plano Recomendado

### Fase 0 — Fechar a base

1. Obter e corrigir a primeira falha do CI atual.
2. Fazer o script `verify-claims.sh` executar diretamente e chamar esse script no CI.
3. Atualizar `AUDIT_REPORT.md` e `Implementation.md` para refletirem o commit atual, sem manter a nota 9,5 como fato validado.
4. Rodar `lintDebug`, `testDebugUnitTest` e `assembleDebug` em CI verde.
5. Adicionar teste para `TokenAuthenticator` sem rede bloqueante e para o limite de paginação.

### Fase 1 — Completar fluxos visíveis

1. Entregar Queue vertical completa: adicionar, remover, ordenar e reproduzir.
2. Entregar Following na série e nas configurações, incluindo perfis criados dinamicamente.
3. Corrigir busca para episódios, cancelamento de query e paginação completa.
4. Conectar `PlaybackCoordinator` aos dois players ou retirar a implementação morta.
5. Unificar `ScreenState` em Home, Files e Details.

### Fase 2 — Transformar o mockup em produto

1. Criar componentes visuais reutilizáveis para rail, rail item, hero, shelf card, badge e CTA.
2. Migrar cores e dimensões hardcoded para tokens semânticos.
3. Renderizar hero local imediatamente e enriquecer sem depender de rede.
4. Criar rails adicionais com dados reais e estados vazios úteis.
5. Remover controles mortos, duplicações de navegação e affordances sem ação.

### Fase 3 — Validar em TV real

1. Gerar APK debug em CI.
2. Testar em 1920x1080 e 4K com D-pad real.
3. Testar cold start, primeiro frame, retorno do player, PiP, rotação e sessão expirada.
4. Testar fonte 1,3x, TalkBack e contraste.
5. Anexar screenshots/GIFs à próxima auditoria; sem isso, a nota de UI deve permanecer limitada.

## Conclusão

As mudanças recentes são substanciais e resolveram vários problemas concretos da auditoria anterior. Ainda assim, o checkout atual deve ser tratado como **beta avançado**, não como produto profissional pronto. A próxima melhoria de maior retorno não é adicionar mais uma camada de infraestrutura: é fechar os fluxos de Queue/Following/Histórico, conectar o coordinator ao player real, corrigir a busca e consolidar o design system com validação em TV.

**Recomendação de release:** não publicar uma release de produção até o CI ficar verde e a Home/Player passarem por uma rodada real de TV com screenshots e D-pad.
