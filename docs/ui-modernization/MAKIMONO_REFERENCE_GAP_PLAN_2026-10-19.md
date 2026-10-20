# Makimono TV — lacunas funcionais frente às evidências de Crunchyroll e Netflix

Data: 19/10/2026. Diretório avaliado: L:/makimono. Base Git: d3e1a00, main, com 133 entradas locais modificadas/não rastreadas no início da inspeção. A avaliação considera a árvore de trabalho, não apenas o commit.

## 1. Veredito e objetivo

Para a avaliação de UI e beleza, a trilha principal agora é o [Plano de qualidade visual de 20/10](MAKIMONO_VISUAL_QUALITY_PLAN_2026-10-20.md), que registra observações feitas na build ativa do Android TV Emulator. Este documento continua servindo como trilha complementar para dados, jornadas, downloads, reprodução e validação técnica.

O Makimono já possui boa parte das peças necessárias para uma experiência de streaming de qualidade: Home com hero e prateleiras, rail retrátil, memória de foco por identidade, detalhes com temporadas, histórico, perfis persistidos, pareamento Google por QR, downloads e motores Media3/MPV. Recriar essas peças ou migrar toda a interface para Compose não é a prioridade demonstrada por esta avaliação.

A lacuna mais relevante é transformar essas peças em jornadas consistentes: dados corretos, isolamento entre perfis, estados recuperáveis, foco previsível, composição visual resistente a conteúdo real e reprodução medida de ponta a ponta. Existem problemas concretos no código e lacunas de validação que screenshots bonitos e testes de políticas isoladas não resolvem.

É tecnicamente viável evoluir para qualidade percebida comparável à de produtos maduros no escopo da biblioteca pessoal. As evidências atuais não permitem declarar equivalência com Netflix/Crunchyroll, atribuir uma porcentagem de paridade ou prometer esse resultado apenas por ampliar a decompilação. A chegada será definida pelos critérios de aceitação deste plano.

Esta entrega é uma avaliação e um plano de desenvolvimento. Não executa as correções propostas, uma nova sessão completa no emulador, novos benchmarks nem uma nova análise em massa de APKs.

## 2. O que realmente sabemos das referências

### Crunchyroll: duas bases históricas que precisam permanecer distintas

- A cobertura de referência de 15/10 identifica a versão TV 3.61.2. Há PNGs de Home, rail, busca, perfis e detalhes em ../../makimono-dossie/reconstruicao/evidencias/runtime/ e dumps de foco em ../../makimono-dossie/capturas/.
- A inspeção estática de onboarding de 19/10 identifica Crunchyroll Android TV 3.75.0, versionCode 22365, pacote com.crunchyroll.crunchyroid. Ela encontrou splash/startup separados e inicialização assíncrona ligada ao ciclo de vida antes da renderização Compose.
- Não combinar as medidas, comportamentos e símbolos dessas versões em uma única especificação sem identificar a versão de cada evidência.
- Os dumps revisados sustentam entrada na rail, expansão, retorno a um card em uma sequência específica e retorno de detalhes à origem. Não demonstram todas as transições possíveis, curvas de animação ou garantias universais de restauração.
- Os PNGs mostram uma composição editorial utilizável como referência. A captura de detalhes também mostra uma lista de temporadas e episódio em uma disposição diferente de uma grade universal; o produto deve escolher a composição pela tarefa e pelo espaço disponível.
- A inspeção de startup não comprova que a Crunchyroll baixa todas as sinopses, imagens e episódios antes de permitir o uso.
- Os timers de auto-hide, skip e Up Next atribuídos em notas antigas à referência não têm comprovação suficiente no conjunto revisado. Tratá-los como parâmetros de produto a validar.

### Netflix: o APK inspecionado é móvel

- O artefato registrado é Netflix 9.88.0 build 5 74722, pacote com.netflix.mediaclient, variante móvel extraída de um APKM existente.
- O método launchProfilesGate analisado indica proteção contra trabalho de login duplicado, progresso e validação assíncrona de sessão antes de prosseguir.
- Isso informa um contrato de entrada e sessão. Não comprova UX de Netflix TV, navegação D-pad, geometria da Home, player de TV, downloads de TV ou troca de perfis por controle remoto.
- A procedência por assinatura não foi revalidada naquela inspeção. Não elevar essa evidência ao nível de uma comparação autenticada completa.

### Limites do dossiê

O VEREDITO_TECNICO e a matriz de aproveitamento de 14/10 usam expressões como “90%+”, “total” e “integração perfeita”. A revisão independente V0–V5 não encontrou base suficiente para essas generalizações. A matriz de cobertura posterior também contém medidas diferentes de rail; nenhuma delas será imposta ao Makimono apenas por aparecer em um relatório.

Os quatro contratos que falharam na auditoria de protótipos de 14/10 são históricos. Há correções documentadas em R00 e implementações mais completas no app atual. Não atribuir automaticamente essas falhas antigas à produção atual.

O problema não pode ser reduzido a “faltam os XMLs originais”. A evidência de Compose em telas da referência explica por que não existe necessariamente um XML equivalente recuperável. O Makimono pode cumprir os mesmos contratos de interação com ViewBinding/XML.

## 3. Método e evidências usadas

As etiquetas abaixo são de confiança técnica:

- CONFIRMADO EM CÓDIGO: comportamento diretamente identificável no código atual; uma falha de runtime ainda pode precisar de reprodução.
- OBSERVADO EM CAPTURA: estado visível em uma captura datada; não garante o estado da build atual.
- IMPLEMENTADO, A HOMOLOGAR: existe implementação, mas a jornada completa não foi comprovada nesta avaliação.
- DESCONHECIDO NA REFERÊNCIA: o material disponível não sustenta uma comparação.
- META DE PRODUTO: decisão proposta para o Makimono; não atribuída à Netflix ou Crunchyroll.

Fontes principais:

- R1 — [Revisão independente V0–V5](../../makimono-dossie/observacoes/REVISAO_INDEPENDENTE_V0_V5.md).
- R2 — [Onboarding das referências, 19/10](../../makimono-dossie/observacoes/onboarding-2026-10-19.md).
- R3 — [Cobertura da referência, 15/10](../../makimono-dossie/reconstruicao/cobertura-da-referencia.md).
- R4 — [Manifesto da auditoria do Makimono, 17/10](../../makimono-dossie/auditoria-2026-10-17/manifest.jsonl).
- R5 — [Cobertura histórica do Makimono](../../makimono-dossie/reconstruicao/cobertura-do-makimono.md) e [pendências históricas](../../makimono-dossie/reconstruicao/pendencias.md).
- R6 — JSONs de evidência em ../../makimono-dossie/jadx-notes/onboarding-*.json. Os IDs exatos constam em R2; foram conferidos nesta avaliação.
- M1 — [Design Obsidian](../design/DESIGN.md).
- M2 — [MainActivity](../../app/src/main/java/zechs/drive/stream/ui/main/MainActivity.kt), [PairingViewModel](../../app/src/main/java/zechs/drive/stream/ui/signin/PairingViewModel.kt), [ProfileManager](../../app/src/main/java/zechs/drive/stream/utils/ProfileManager.kt), [ProfileBootstrapPolicy](../../app/src/main/java/zechs/drive/stream/utils/ProfileBootstrapPolicy.kt).
- M3 — [FranchiseGroupingManager](../../app/src/main/java/zechs/drive/stream/data/repository/FranchiseGroupingManager.kt), [SeriesDetailViewModel](../../app/src/main/java/zechs/drive/stream/ui/series/SeriesDetailViewModel.kt), [EpisodeDeduplicationPolicy](../../app/src/main/java/zechs/drive/stream/domain/library/EpisodeDeduplicationPolicy.kt).
- M4 — [FolderMetadataRepository](../../app/src/main/java/zechs/drive/stream/data/repository/FolderMetadataRepository.kt), [MediaPreloadManager](../../app/src/main/java/zechs/drive/stream/data/repository/MediaPreloadManager.kt).
- M5 — [WatchList](../../app/src/main/java/zechs/drive/stream/data/model/WatchList.kt), [WatchListDao](../../app/src/main/java/zechs/drive/stream/data/local/WatchListDao.kt), [ProgressRecorder](../../app/src/main/java/zechs/drive/stream/playback/session/ProgressRecorder.kt), [WatchHistoryDialog](../../app/src/main/java/zechs/drive/stream/ui/home/WatchHistoryDialog.kt).
- M6 — [DownloadRepository](../../app/src/main/java/zechs/drive/stream/data/repository/DownloadRepository.kt), [DownloadItem](../../app/src/main/java/zechs/drive/stream/data/model/DownloadItem.kt), [DownloadDao](../../app/src/main/java/zechs/drive/stream/data/local/DownloadDao.kt), [DownloadWorker](../../app/src/main/java/zechs/drive/stream/worker/DownloadWorker.kt).
- M7 — [FocusMemory](../../app/src/main/java/zechs/drive/stream/utils/FocusMemory.kt), [RailController](../../app/src/main/java/zechs/drive/stream/ui/home/controller/RailController.kt), [HeroController](../../app/src/main/java/zechs/drive/stream/ui/home/controller/HeroController.kt), [FeaturedHeroBinder](../../app/src/main/java/zechs/drive/stream/ui/home/FeaturedHeroBinder.kt).
- M8 — [PlayerBenchmark](../../benchmark/src/main/java/zechs/drive/stream/benchmark/PlayerBenchmark.kt), [relatório de desempenho](../qa/v6/perf.md), [matriz de dispositivos](../qa/device-matrix.md).
- M9 — [Worker de pareamento](../../backend/cloudflare-worker/src/index.ts).

O material de build existente foi consultado, sem executar novamente a suíte: 126 relatórios JUnit, 663 execuções, zero falhas e zero erros, com relatório mais recente em 19/10/2026. O log local L:/makimono-verify-final.log registra verify-all aprovado em 286,9 s e baseline de lint com 681 issues. São evidências anteriores disponíveis no disco, não uma certificação de runtime produzida por esta revisão.

## 4. Lacunas por subsistema

### G01 — Isolamento dos downloads: P0, confirmado em código

Existe download com WorkManager, progresso, arquivo parcial e suporte a Range. O problema encontrado é o contrato entre perfis:

- DownloadItem usa somente fileId como chave primária.
- enqueueUniqueWork e cancelUniqueWork usam download_ + fileId, sem perfil.
- deleteAllDownloadsForProfile consulta todos os downloads ativos, cancela todos e percorre todos os arquivos de downloadDir para exclusão; só a exclusão dos registros do banco é filtrada por perfil.
- O comando é chamado por DownloadsViewModel. A suíte consultada não demonstra proteção dessa limpeza com dois perfis.

Consequência: apagar downloads de A pode afetar os arquivos/trabalhos de B; baixar o mesmo arquivo por perfis diferentes não possui propriedade independente no modelo atual. Isso é uma lacuna do Makimono demonstrável sem depender da referência.

Objetivo: definir propriedade por perfil, podendo compartilhar o blob físico apenas com referências explícitas e exclusão quando não houver outro proprietário. Validar migração, cancelamento e reprodução offline antes de ampliar Smart Downloads.

### G02 — Sessão → preparação → criação de perfil: P0, implementado, a homologar

Já existem QR, polling, expiração, recuperação de rede, sessão por aparelho, preparação inicial limitada e perfil inicial explícito. ProfileManager persiste o marcador initial_profile_setup_pending e reconhece o par legado de perfis padrão. Não planejar isso como feature inexistente.

A lacuna é provar o percurso com estado realmente novo e retomada de processo: sem sessão → QR → autorização no celular → preparação → editor obrigatório → salvar um único perfil → biblioteca certa. Também provar a conta existente, cancelamento, expiração e retorno após interrupção.

O gate de sessão da Netflix móvel e a inicialização da Crunchyroll apoiam separar estados e evitar trabalho duplicado; não fornecem a implementação do OAuth Drive. O Worker atual já contém CSRF/cookies, callback, consumo de sessão, logout por sessão e revogação separada. A pendência antiga que dizia “somente stub local” está superada no código.

Objetivo: transição determinística e recuperável, sem perfil secundário automático e sem limpar os dados da conta real para simular uma instalação nova. Usar ambiente/pacote de teste isolado.

### G03 — Identidade de obra e franquia: P1, confirmado em código

FranchiseGroupingManager resolve um conjunto manual de franquias com contains. Inclui termos amplos, como zoku, que isoladamente não identificam uma franquia. Isso limita generalização e cria risco de agrupamentos incorretos. Há também heurísticas específicas em cleanCorruptedMetadata, incluindo o texto de Reborn e um caso de Monogatari.

As referências exibem obras organizadas por catálogo; seu backend não resolve o mesmo problema de pastas arbitrárias do Drive. O REA de UI não substitui essa camada de identidade.

Objetivo: manter ID estável da pasta/arquivo como origem e adicionar identidade de provedor, tipo de mídia, relações de temporada/franquia, aliases, origem da associação e confiança. Quando a associação for ambígua, preservar o nome local e permitir correção persistente. Uma correção manual não deve ser sobrescrita por novo preload.

### G04 — Deduplicação, temporadas e nota: P1, parcialmente implementado

EpisodeDeduplicationPolicy já considera checksum ou título normalizado com tamanho/duração próximos; SeriesDetailViewModel já aplica a política e possui activeArcScore atualizado na seleção. A exigência de nota por temporada não está ausente do código.

As lacunas são validar essa cadeia com arquivos reais e tornar a inferência segura: distinguir T1E1 de T2E1, especiais de episódio regular, cortes diferentes e versões de idioma. O modelo Candidate não recebe identidade explícita de temporada/edição. A escolha atual de usar checksum ou fallback varia conforme o candidato tem checksum; exigir resultados consistentes sob reordenação da entrada.

Objetivo: duplicatas fortes geram um item lógico com fontes alternativas preservadas; variantes não equivalentes continuam acessíveis. Não apagar arquivos no Drive. Mushoku deve ter hub de franquia, temporadas/partes corretas e nota MAL da seleção atual; quando não houver nota da seleção, mostrar ausência, sem média simples nem nota herdada enganosa.

### G05 — Metadados, atualização e idioma: P1, confirmado em código e histórico visual

O cache-first evita chamadas repetidas, mas MediaPreloadManager considera pronto um registro com sinopse e nota; isso não garante identidade, idioma, duração ou frescor. FolderMetadataRepository já tenta reparar corrupção, porém com heurísticas específicas. Não afirmar que “não existe reparo”; falta um contrato geral de validação e atualização.

A captura de Home de 17/10 mostra gêneros e sinopse em inglês. Não prova que toda a UI esteja sem tradução: strings de recursos e conteúdo de provedores são camadas diferentes.

Objetivo: informação sempre associada à obra/tipo/temporada certos; idioma preferido explícito; fallback previsível; revalidação por versão da política/origem/data. Duração do filme deve preferir a mídia local/Drive quando disponível, sem inventar duração de catálogo. Evitar que a correção de um metadado errado dependa de apagar cache ou reiniciar.

### G06 — Histórico e retomada: P1, confirmado em código

Já há entrada Histórico na rail, grade, filtros Todos/Em andamento/Concluídos, progresso e tempo restante. Não é necessário iniciar uma cópia dessa tela do zero.

WatchList não possui lastPlayedAt. WatchHistoryDialog ordena por id; DAO também usa id DESC para recentes. ProgressRecorder atualiza um registro existente por copy, preservando seu ID. Portanto revisitar um episódio antigo não o move necessariamente para o topo e a data real de reprodução não pode ser exibida com esse modelo.

Objetivo: lastPlayedAt atualizado em reprodução real, migração conservadora dos registros antigos, ordenação consistente no Histórico/Continuar/último assistido e agrupamento por data quando houver timestamp conhecido. Não inventar datas antigas. Restaurar foco no item ao sair de filtros, detalhes ou player.

### G07 — Home, detalhes e coerência visual: P1, observado em captura, a revalidar na build atual

Nas capturas examinadas, a referência mantém hero e hierarquia claros. Em 57_home_atual.png, do Makimono em 17/10, o título longo de Bleach tem linhas visualmente sobrepostas. A rail expandida da mesma auditoria usa composição e espaçamento distintos da referência. Isso é evidência histórica concreta; não declara que o bug continua na build atual.

O caso de “Mais informações” ilegível no foco fornecido pelo usuário deve entrar na regressão visual. A semelhança das posições não garante contraste nos estados normal/focado/pressionado/desabilitado.

Objetivo: título longo sem colisão, metadados sem separadores vazios, sinopse legível sobre arte clara/escura e ações com rótulo sempre visível. Revisar todos os qualificadores existentes de cada layout. Usar tokens Obsidian, fontes do projeto e scrim direcional, conforme DESIGN.md; não introduzir blur dinâmico.

### G08 — D-pad, Back e restauração: P1, base existente, cobertura de integração a ampliar

FocusMemory já guarda ID e posição e prefere o ID ao resolver uma lista reordenada. RailController já implementa expansão e entrada de histórico. Há relatórios anteriores de navegação; isso não sustenta afirmar que todo modal e toda mutação de catálogo estão homologados.

Objetivo: um único foco visível; seleção de aba independente de foco; retorno ao mesmo item após detalhes/player; fallback determinístico quando ele é removido; teclado e dialogs sem armadilhas; rail sem rótulos truncados em pt-BR. Medir o percurso também com lista vazia, atualização assíncrona e pressionamento rápido das setas.

### G09 — Reprodução e continuidade: P1, base existente, referência incompleta

O app já tem PlayerEngine, PlaybackController, ProgressRecorder, SkipController, NextEpisodePolicy e OsdStateMachine, além de Media3/MPV. Os testes dessas políticas são valiosos. Não há base para substituir a implementação por um protótipo reduzido nem para afirmar paridade integral com o player comercial.

A lacuna é validar as integrações: seek seguido de tick antigo, áudio/legenda persistidos, troca/fallback de motor, buffer, sessão expirada, interrupção de rede, próximo episódio e Back ao item de origem. O comportamento premium da referência permanece parcialmente desconhecido; validar os requisitos próprios com mídia de teste acessível.

### G10 — Desempenho e observabilidade: P1, lacuna de medição confirmada

Existe módulo benchmark, preload limitado por concorrência, cache e baseline profile. Porém PlayerBenchmark.playerFirstFrameLaunch usa FrameTimingMetric e espera player_view aparecer. A ação é opcional e o resultado booleano da espera não é verificado. Isso pode terminar sem reproduzir vídeo e não mede primeiro frame renderizado.

Objetivo: falhar quando o cenário não for atingido, usar mídia/controladores de teste determinísticos e instrumentar pedido de reprodução, primeira imagem renderizada, buffering e erro. Diferenciar resultado em AVD de resultado em hardware TV. Uma superfície criada não é uma imagem decodificada.

### G11 — Busca, erros e acessibilidade: P2, implementação presente, percurso incompleto

HomeViewModel já tem consulta/cancelamento de busca e FilesSearchFilterHandler filtra arquivos. Há relatórios de descrições acessíveis e contraste. Não há evidência suficiente nesta revisão para declarar a busca da Netflix TV ou a cobertura de todos os estados do Makimono equivalente.

Objetivo: consulta rápida sem resultado obsoleto, contexto preservado ao abrir resultado, diferenças claras entre vazio/sem rede/401/403/quota, retry no mesmo contexto, nomes longos legíveis e leitura acessível coerente. Validar também conteúdo de metadados em inglês e controles em pt-BR sem misturar esse diagnóstico com traduções faltantes no XML.

## 5. Plano de execução e dependências

### E0 — Fixar uma baseline reproduzível

Prioridade: imediata. Dependência: nenhuma.

1. Identificar build, hash do APK, pacote, versão, commit e diff usado na auditoria. Preservar as alterações locais de todos os autores; não executar limpeza da árvore de trabalho.
2. Montar fixtures pequenas de biblioteca: filme, duas temporadas com E01, especial, duplicata por hash, variantes de áudio, obra ambígua, nome muito longo e metadado em idioma alternativo.
3. Vincular cada cenário a evidência de referência ou à etiqueta META DE PRODUTO. Separar Crunchyroll 3.61.2, 3.75.0 e Netflix móvel.
4. Reproduzir G01 num diretório temporário de teste antes de alterar a lógica. Registrar resultado sem executar exclusão sobre downloads reais.

Aceite: cada falha do backlog tem cenário, estado inicial, ação, resultado esperado e fonte. Nenhum “concluído” genérico sem build/cenário.

### E1 — Proteger dados e propriedade dos downloads

Prioridade: P0. Dependência: E0. Escopo: G01.

1. Definir política pura de propriedade/compartilhamento; testar dois perfis com arquivos distintos e com o mesmo fileId.
2. Migrar o esquema para separar arquivo físico e associação ao perfil, ou usar chave composta com caminhos/trabalhos isolados. Escolher uma abordagem e documentar a decisão antes de codificar.
3. Restringir exclusão/cancelamento às associações do perfil; só remover blob compartilhado sem outros proprietários.
4. Validar download interrompido, Range ignorado, retomada após reinício, falha de espaço e reprodução offline.

Aceite: limpar A mantém arquivos, registros e trabalhos de B utilizáveis; A e B baixam o mesmo fileId sem sobrescrever propriedade; migração preserva downloads existentes. Testes com filesystem temporário e integração Room/Worker, além da UI D-pad.

### E2 — Fechar a jornada de primeiro uso

Prioridade: P0. Dependência: E0; não depende da análise adicional da Netflix. Escopo: G02.

1. Validar pacote de teste sem sessão e sem perfis. QR é o primeiro fluxo interativo.
2. Executar OAuth real em ambiente autorizado: novo código, confirmação, retorno à TV e consumo único.
3. Interromper processo em preparação e no editor; ao retornar, continuar na etapa pendente sem criar perfis padrão.
4. Cobrir expiração/negação/sem rede, dois aparelhos e logout de apenas um.
5. Confirmar seleção/detecção da pasta raiz pelo ID e atualização imediata da biblioteca, inclusive oneblacki distinta de 1oneblacki.

Aceite: perfil só é finalizado após ação explícita do usuário; um perfil criado, nenhum secundário automático; sessão existente preservada; falha de metadados não impede criar perfil e acessar recuperação. Registrar vídeo ou sequência PNG/XML do percurso completo.

### E3 — Corrigir catálogo e metadados antes do polimento

Prioridade: P1. Dependência: E0 e acesso à biblioteca após E2. Escopo: G03, G04, G05.

1. Criar contratos de identidade, associação de provedor e override manual; testar antes de integrar na UI.
2. Reduzir dependência de contains e casos por nome sem remover suporte existente até haver substituição coberta por testes.
3. Tornar deduplicação estável sob permutação da entrada e explícita quanto a temporada/edição; preservar fontes alternativas.
4. Integrar nota e metadados da temporada selecionada; título do hub deve representar a franquia quando sustentado pelas relações.
5. Implementar revalidação de cache por política/origem/data e correção acionável pelo usuário; atualizar a UI por estado observado.

Aceite: Spider-Man nunca recebe sinopse/contagem de Reborn nas fixtures; filme mostra duração conhecida e omite dado desconhecido; Mushoku mantém temporadas/especiais e nota da seleção; correção persiste após reinício/preload; nomes semelhantes não causam associação silenciosa errada.

### E4 — Histórico cronológico e foco consistente

Prioridade: P1. Dependência: E0; integrar com E3 para identidade lógica. Escopo: G06, G08.

1. Migrar lastPlayedAt sem fabricar datas para o histórico legado.
2. Alinhar ordenação do histórico, retomada e último assistido à reprodução real.
3. Cobrir restauração por ID em filtros, reordenação, remoção, atualização e volta do player.
4. Criar roteiros D-pad determinísticos para Home, detalhes, histórico, busca, perfis, configurações e dialogs.

Aceite: voltar a assistir um episódio antigo leva-o ao topo; perfis não misturam histórico; cada retorno preserva origem ou escolhe fallback documentado; nenhuma tela depende de touchscreen. Data, duração e progresso devem ser legíveis e semanticamente corretos.

### E5 — Consolidar o design de Home, detalhes e perfis

Prioridade: P1. Dependência: E3/E4 para evitar aprovar telas com dados incorretos. Escopo: G07 e parte de G11.

1. Reproduzir o título longo de Bleach e o foco em “Mais informações” na build atual; corrigir apenas se reproduzidos ou se o contrato visual falhar.
2. Especificar título, metadados, sinopse, ações, cards e rail com estados normal/focado/selecionado/pressionado/desabilitado.
3. Ajustar composição usando tokens Obsidian, fontes existentes, áreas seguras e gradientes. Avatares devem ter área de foco adequada sem caixa cinza desnecessária.
4. Validar todos os qualificadores de layout aplicáveis e separar dimensões de tela em pixels de dimensões de UI em dp.
5. Melhorar histórico com informação útil e respiro; conservar os filtros/progresso existentes. Não copiar banners de assinatura que não têm função no Makimono.

Aceite: 1080p e 720p sem sobreposição, cortes em ações ou foco ilegível; título de 80 caracteres e nomes longos de perfil funcionam; artes claras e escuras preservam contraste; screenshots de VisualReviewActivity e fluxo real da build identificada.

### E6 — Homologar reprodução e downloads em sessão longa

Prioridade: P1. Dependência: E1, E3, E4. Escopo: G09.

1. Usar corpus pequeno e conhecido: vídeo simples, múltiplas faixas, ASS/SSA, capítulos, arquivo offline e erro controlado.
2. Cobrir Media3 e MPV separadamente: iniciar, pausar, seek, alterar faixas, sair, retomar e trocar episódio.
3. Exercitar janela de skip e ticks atrasados; Up Next com cancelar, ausência de próximo arquivo e erro ao abrir próximo.
4. Validar progresso pelo perfil de início da reprodução mesmo se outra parte do app trocar perfil.
5. Fazer sessão de 60 minutos e ciclos de entrada/saída para observar memória, foco, áudio duplicado e recursos não liberados.

Aceite: zero crash/ANR e perda de progresso no roteiro; nenhum salto/autoplay duplicado; preferências de áudio/legenda coerentes; retorno ao detalhe/episódio correto. Bloqueio de acesso na referência não bloqueia esses testes próprios.

### E7 — Medir qualidade e remover falsos positivos

Prioridade: P1/P2. Dependência: E2–E6 para jornadas funcionais. Escopo: G10/G11.

1. Corrigir os pré-requisitos/assertions dos benchmarks. Cenário que não abriu o player deve falhar.
2. Medir TTID, disponibilidade da primeira interação, primeira imagem de vídeo, buffering, frames lentos e memória no mesmo corpus.
3. Registrar logs estruturados sem credenciais, com causa de falha e etapa da jornada.
4. Cobrir busca concorrente, rede lenta, quota, expiração de sessão, lista vazia e retorno do teclado.
5. Validar AVD TV 1080p/720p; executar campanha em um dispositivo físico TV para metas de desempenho. O Moto não homologa Android TV.

Metas iniciais propostas, a calibrar com uma baseline real: feedback visual em até 100 ms para ação D-pad; Home em cache utilizável em até 2 s no hardware alvo; início de vídeo de teste em até 3 s na rede controlada; menos de 1% de frames acima de 32 ms no roteiro de navegação; sem crescimento contínuo de memória após 20 ciclos equivalentes. São metas do Makimono, não medições das referências.

Aceite: relatório com ambiente, amostras, percentis e falhas, incluindo resultados que não atingiram a meta. Não converter presença de baseline-prof ou de código de teste em prova de ganho de desempenho.

### E8 — Completar as referências apenas para decisões pendentes

Prioridade: P2; pode ocorrer entre entregas, com um agente por vez no working tree. Não bloqueia E1–E7.

1. Crunchyroll: capturar vídeos curtos de entrada/saída da rail, retorno de detalhes e navegação de temporadas, identificando versão/densidade.
2. Pergunta estática por operação: quem guarda identidade do item focado? como o startup decide a próxima tela? quais estados públicos do player são alcançáveis? Parar ao obter resposta útil ou limitação concreta.
3. Netflix: obter e identificar uma variante efetivamente TV antes de comparações de controle remoto; registrar APK/splits, versão e procedência verificável.
4. Se a versão TV não for acessível, registrar a lacuna e conservar somente os achados móveis já sustentados. Não inventar uma Netflix TV a partir de classes móveis.
5. Limitar cada pergunta a uma sessão curta; após duas falhas da mesma operação, investigar a causa e documentar alternativa. Sem varredura global de classes por padrão.

Aceite: cada observação tem origem, versão, ação, resultado, confiança e decisão concreta de produto. Texto decompilado e recursos de referência permanecem no dossiê; especificações implementáveis usam contratos e código próprio.

## 6. Critério de liberação por incremento

Antes de chamar uma etapa de pronta:

1. Políticas novas têm testes de ramos/casos de borda e integração relevante; não enfraquecer testes existentes.
2. verify-all completo aprovado; lint sem novas violações frente à baseline; nenhum warning novo introduzido. Registrar warnings preexistentes em vez de chamar o build de limpo.
3. UI validada com D-pad em Android TV; captura isolada e percurso real cumprem objetivos diferentes e ambos são necessários quando aplicáveis.
4. Migrations preservam banco, perfis, raiz, histórico e downloads. Exclusões de teste usam apenas dados descartáveis.
5. Evidência identifica APK/hash, versão, dispositivo, resolução/densidade e roteiro. Uma captura antiga não aprova código recém-alterado.
6. Um commit pequeno por incremento quando a verificação estiver verde e apenas com os arquivos daquele incremento; nunca incluir trabalho preexistente de outro autor por conveniência. Sem push automático.

## 7. Ordem de investimento e resultado esperado

Ordem recomendada: E0 → E1 e E2 em sequência no working tree → E3 → E4 → E5 → E6 → E7. E8 responde dúvidas pontuais conforme surgirem. Não iniciar reescrita ampla de navegação, player ou toolkit de UI como pré-requisito.

Após E1/E2: dados protegidos entre perfis e primeiro uso confiável. Após E3/E4: catálogo correto, retomada cronológica e navegação previsível. Após E5/E6: interface consistente e sessão de reprodução demonstrada. Após E7: qualidade mensurada com limites conhecidos. E8 aumenta a precisão da comparação sem substituir homologação.

O próximo incremento executável recomendado é E0 + regressão de G01, seguido da correção de propriedade dos downloads. A jornada de instalação nova de E2 deve ser validada logo depois. São os riscos de maior impacto encontrados nesta avaliação.

O que falta para um produto de altíssima qualidade é fechar esses contratos em conjunto, com dados reais e falhas de rede, e manter a regressão automatizada. Mais código recuperado da referência só vale o esforço quando resolve uma pergunta que muda uma decisão concreta do produto.

## 8. Registro desta avaliação

- Inspeção local de documentação, evidências REA preservadas, capturas selecionadas, código e relatórios JUnit existentes.
- Nenhuma nova medição dos apps comerciais, decompilação em massa, autenticação ou alteração de conta.
- O código do aplicativo e os secrets não foram alterados nesta entrega.
- Este plano atualiza a prioridade de execução dos roadmaps anteriores; preserva os relatórios históricos e explicita quais conclusões ficaram ultrapassadas.
