# Auditoria e Classificação de Tratamento de Exceções (Q-005)

Este documento classifica 100% dos blocos `catch` identificados no relatório de auditoria do Makimono (`relatorio_auditoria_makimono.md`, item Q-005) com base em sua semântica de arquitetura, ciclo de vida e impacto no usuário.

## Critérios de Classificação

1. **Intencional (Melhor Esforço / Prefetch / Fallback / Lifecycle):**
   - Operações em segundo plano cujo fracasso não impede a funcionalidade principal (ex.: pré-aquecimento de buffer, busca preditiva de capítulos de abertura/encerramento, enriquecimento cosmético de pôsteres, cancelamento padrão de corrotinas `CancellationException`). O fallback gracioso ou descarte seguro é o comportamento correto.
2. **Deve virar estado de erro (UI State / Notificação):**
   - Ações iniciadas pelo usuário ou carregamento primário de dados que falharam e deixam a tela em estado inconsistente ou silenciosamente vazia sem feedback explicativo. Devem transicionar o `UiState` para erro ou exibir `UiError` com ação recomendada (ex.: Tentar novamente, Reconectar).
3. **Deve virar retry (Tentativa Automática ou Ação Clara):**
   - Falhas transientes de rede ou conexões efêmeras onde o app deve tentar novamente ou oferecer botão de ação explícito para repetição imediata.

---

## Tabela de Classificação Completa (100% das ocorrências de Q-005)

| # | Arquivo:Linha | Operação | Classificação | Motivo e Comportamento Alvo |
|---|---|---|---|---|
| 1 | `FilesViewModel.kt:57` | `recordFolderOpened` | **Intencional** | Persistência local em segundo plano de metadados de histórico recente. Falha no Room não deve bloquear navegação de pastas. |
| 2 | `FilesViewModel.kt:83` | `getFirstEpisodeInFolder` | **Intencional** | Resolução em background do primeiro episódio para miniatura ou atalho. Em caso de erro, retorna `null` graciosamente via callback. |
| 3 | `FilesViewModel.kt:186` | `CancellationException` em paginação | **Intencional** | Cancelamento padrão de corrotina ao navegar para outra pasta ou tela. Não deve ser reportado como erro de UI. |
| 4 | `FilesViewModel.kt:188` | `SocketTimeoutException` em paginação | **Já vira erro / Retry** | Emite `publishPageError` com `ResourceErrorKind.NETWORK` e mensagem amigável, permitindo ação "Tentar novamente". |
| 5 | `FilesViewModel.kt:408` | `resolveMissingPosters` / `enrich Drive folder posters` | **Intencional** | Enriquecimento visual assíncrono de pastas. Se falhar, exibe os arquivos e pastas normalmente sem pôster sem travar a lista. |
| 6 | `HomeViewModel.kt:315` | `removeWatchItem` | **Deve virar estado de erro** | Remoção de item do histórico solicitada pelo usuário no menu de contexto. Falha de persistência deve notificar o usuário via evento/estado. |
| 7 | `HomeViewModel.kt:325` | `restoreWatchItem` | **Deve virar estado de erro** | Desfazer remoção de item do histórico. Falha de persistência deve emitir estado observável de falha. |
| 8 | `HomeViewModel.kt:341` | `markWatchItemFinished` | **Deve virar estado de erro** | Ação explícita do usuário de marcar como concluído. Falha deve notificar a interface. |
| 9 | `HomeViewModel.kt:350` | `recordFolderOpened` | **Intencional** | Telemetria interna de pasta aberta recentemente. Não bloqueia renderização de tela. |
| 10 | `HomeViewModel.kt:416` | `getLibraryRootFolder` | **Intencional** | Resolução do ID de pasta raiz configurado. Falha faz fallback seguro para `"root"` ("Meu Drive"). |
| 11 | `HomeViewModel.kt:487` | `getStarredFiles` | **Deve virar estado de erro** | Carregamento de itens favoritos do Drive. Falha não deve ser engolida silenciosamente, devendo expor estado observável no fluxo da Home. |
| 12 | `FilesSearchFilterHandler.kt:128` | Resolução de arcos/temporadas | **Intencional** | Enriquecimento estético dos seletores de temporada com títulos da AniList/Tenrai. Fallback mantém nomes originais de pasta. |
| 13 | `SeriesDetailViewModel.kt:164` | `searchAnime` (Tenrai) | **Intencional** | Enriquecimento de sinopse e arcos de franquia via API externa. Se falhar, a tela exibe os arquivos locais e do Drive normalmente. |
| 14 | `SeriesDetailViewModel.kt:174` | `resolveMetadata` (AniList) | **Intencional** | Busca de banner/pôster em alta resolução. Se falhar, utiliza banner local ou placeholder sem interromper navegação. |
| 15 | `SeriesDetailViewModel.kt:294` | `Error loading series details` | **Já vira estado de erro** | Captura falha geral ao carregar a pasta e já emite `SeriesDetailUiState.Error(appError)` com mapeador de falha. Falso positivo da heurística. |
| 16 | `PlayerActivity.kt:2673` | `searchSubtitles` online | **Intencional** | Busca online de legendas no OpenSubtitles. Se falhar, já exibe Snackbar na UI informando que nenhuma legenda foi encontrada. |
| 17 | `PlayerActivity.kt:2730` | `saveSubtitleStyle` | **Intencional** | Gravação de preferência visual no DataStore. Falha não deve interromper o player nem travar o diálogo. |
| 18 | `PlayerActivity.kt:2777` | `saveSubtitleSize` | **Intencional** | Gravação de tamanho de fonte no DataStore. Falha não interrompe a reprodução de vídeo. |
| 19 | `MPVActivity.kt:303` | Atualização do relógio no OSD | **Intencional** | Formatação cosmética da hora atual ao exibir o OSD. Falha descartada com segurança. |
| 20 | `MPVActivity.kt:416` | `fetchSubtitleStyle` na inicialização do MPV | **Intencional** | Leitura de estilo personalizado. Se falhar, MPV utiliza o estilo padrão do libmpv sem abortar execução. |
| 21 | `MPVActivity.kt:1404` | Match MAL / AniSkip no MPV | **Intencional** | Obtenção de tempos de abertura/créditos via rede externa. Se falhar, o episódio é reproduzido normalmente sem AniSkip. |
| 22 | `BingeBufferManager.kt:106` | Extração preditiva de capítulos MKV | **Intencional** | Pré-aquecimento em segundo plano do próximo episódio. Se falhar, não afeta a reprodução atual e a extração ocorre normalmente na transição. |
| 23 | `BingeBufferManager.kt:123` | Pré-busca preditiva de AniSkip | **Intencional** | Pré-aquecimento do próximo episódio. O episódio atual segue intocado. |
| 24 | `AnimePosterResolver.kt:384` | Consulta AniList GraphQL | **Intencional** | Primeiro elo da cadeia de busca de metadados. Se falhar, chaveia graciosamente para Jikan (MAL). |
| 25 | `AnimePosterResolver.kt:447` | Consulta Jikan (MAL) API | **Intencional** | Segundo elo da cadeia. Se falhar, chaveia graciosamente para Kitsu. |
| 26 | `AnimePosterResolver.kt:504` | Consulta Kitsu API | **Intencional** | Terceiro elo da cadeia. Se falhar, chaveia graciosamente para TMDB. |
| 27 | `AnimePosterResolver.kt:593` | Consulta TMDB API | **Intencional** | Elo final da cadeia de scraping. Se falhar, retorna `null` para permitir renderização de layout sem arte externa. |
| 28 | `TenraiAnimeService.kt:152` | Busca de animes Tenrai | **Intencional** | Camada remota de metadados com cache. Falha de rede retorna lista vazia para degradação suave. |
| 29 | `TenraiAnimeService.kt:199` | Relações de franquia Tenrai | **Intencional** | Dados complementares de franquia. Falha retorna lista vazia. |
| 30 | `TenraiAnimeService.kt:236` | Episódios canônicos Tenrai | **Intencional** | Dados complementares de títulos. Falha retorna lista vazia. |
| 31 | `TenraiAnimeService.kt:378` | Detalhes de anime Tenrai | **Intencional** | Dados complementares. Falha retorna `null`. |
| 32 | `AniSkipRepository.kt:69` | Busca de capítulos AniSkip | **Intencional** | Serviço de pulo de abertura. Falha remota não deve falhar reprodução do vídeo do usuário. |
| 33 | `DriveRepository.kt:347` | Atualização de Access Token OAuth | **Já vira erro / Retry** | Captura exceção e já retorna `Resource.Error` com código `AUTHENTICATION` ou `doOnError(e)`. Falso positivo da heurística. |

---

## Síntese de Auditoria

- **Total auditado:** 33 pontos em 11 arquivos.
- **Intencionais (melhor esforço / fallback / prefetch seguro):** 27 casos (81,8%).
- **Já emitiam estado de erro estruturado (falsos positivos da heurística de 3 linhas):** 3 casos (`FilesViewModel:188`, `SeriesDetailViewModel:294`, `DriveRepository:347`).
- **Genuínos pontos que devem emitir estado/notificação observável de erro na UI:** 3 fluxos (`HomeViewModel:315/325/341` em histórico e `HomeViewModel:487` em favoritos).
