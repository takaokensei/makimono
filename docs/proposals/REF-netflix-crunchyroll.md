# Referência de UX e Features: Netflix & Crunchyroll (REF-01)

Documento de análise comparativa e inventário de padrões de UX e funcionalidades observáveis nos aplicativos **Netflix** (Android TV / Ninja) e **Crunchyroll** (Android TV), com classificação para replicação incremental no Makimono.

---

## 1. Diretrizes de Replicação no Makimono
- **Origem Assumida:** O comportamento observável da Netflix e da Crunchyroll constitui a especificação de produto.
- **Identidade Visual:** Aplicação estrita dos tokens semânticos do **Obsidian Design System** (`docs/design/DESIGN.md`), sem hexadecimais brutos e sem blur dinâmico na TV.
- **TV-First (10-Foot Experience):** Margens seguras de overscan (48dp lateral, 27dp vertical), um único elemento com anel de foco visível por vez, navegação D-pad fluida sem aprisionamento de foco.
- **Não-Duplicação:** Recursos já propostos ou implementados em `P-10` a `P-18` e `D-50` são mapeados e não duplicados.

---

## 2. Matriz de Inventário e Classificação de Features

| ID | Funcionalidade / Padrão de UX | Origem | Benefício para o Usuário | Esforço | Impacto em Android TV (D-pad & Overscan) | Dependências | Já existe no Makimono? | Classificação | Justificativa |
|---|---|:---:|---|:---:|---|---|:---:|:---:|---|
| **REF-01** | **Badges de Áudio e Dublagem ("DUB" / "SUB" / "PT-BR") nos Cards de Episódios** | Crunchyroll | O usuário identifica instantaneamente se o episódio possui áudio dublado ou apenas legendas em japonês antes de iniciar o play. | **Baixo** | Visível a 3m sem poluir o layout do card; respeita o piso de 12sp. | `EpisodeParser`, metadados de arquivos MKV | **Parcial** (`layoutAudioBadge` existe no XML, falta política de dados e bind) | **COPIAR** | Padrão essencial em apps de anime. Aumenta a clareza e economiza tempo do usuário. |
| **REF-02** | **Selo Semântico "Assistido" (Checkmark Badge) nos Cards de Episódios Concluídos** | Netflix & Crunchyroll | Distinção visual imediata entre episódios não vistos, em andamento e concluídos (>95%). | **Baixo** | Foco D-pad inalterado; badge em verde sucesso (`@color/obsidian_success`) sobre miniatura 16:9. | `WatchListDao`, Room | **Parcial** (`badgeWatched` existe no XML; bind requer consistência) | **ADAPTAR** | Excelente retorno de UX para maratonas de séries longas. |
| **REF-03** | **Menu de Contexto / Ações Rápidas no Card de "Continuar Assistindo" (Long-Press / More Actions)** | Netflix | Permite "Remover da Linha", "Assistir do Início" ou "Ver Detalhes da Série" direto da Home sem abrir o player. | **Médio** | Modal/Bottom Sheet com foco automático no primeiro item, fechamento com botão Voltar. | `WatchListDao`, `HomeFragment` | **Não** | **COPIAR** | Resolve a frustração comum de animes abandonados poluindo a primeira prateleira da Home. |
| **REF-04** | **Linha de Filtro Rápido por Gênero / Categoria na Home (Genre Chips / Pills)** | Crunchyroll | Navegação por categorias populares de anime ("Ação", "Isekai", "Shonen", "Romance", "Fantasia") com 1 clique. | **Médio** | Linha horizontal de pills com navegação D-pad suave (esquerda/direita) acima do grid/prateleiras. | Metadados Room / TMDB / AniList | **Não** | **ADAPTAR** | Facilita a descoberta de novos títulos sem exigir busca por texto no teclado virtual da TV. |
| **REF-05** | **Seletor de Temporadas em Modal Dropdown Compacto na Tela de Detalhe** | Netflix | Para séries com muitas temporadas (ex: Monogatari, Fate), evita navegação horizontal infinita por abas. | **Médio** | Diálogo vertical com foco inicial na temporada atual, rolagem suave e seleção instantânea. | `SeriesDetailFragment` | **Não** (usa abas horizontais) | **ADAPTAR** | Complementa as abas horizontais quando a série ultrapassar 4 temporadas/partes. |
| **REF-06** | **Efeito de Foco Expandido (Scale 1.05x com Sombra/Glow Ciano nos Cards)** | Netflix & Crunchyroll | Feedback tátil e visual instantâneo indicando exatamente qual card está ativo na TV. | **Baixo** | Aplicação de `FocusEnginePolicy` com duração de 150ms desacelerada (`decelerate_interpolator`). | `FocusEnginePolicy`, `motion_spec.xml` | **Parcial** (implementado em alguns cards, falta padronização global) | **ADAPTAR** | Padroniza o comportamento de 100% dos cards interativos do app. |
| **REF-07** | **Pílula de Toggle Rápido "+ Minha Lista" / "✓ Na Lista" com Feedback Visual** | Netflix | Adicionar ou remover da lista de favoritos com animação de ícone e atualização instantânea da Home. | **Baixo** | Botão secundário na barra de ações da série; acessível por D-pad logo após o botão principal "Assistir". | `WatchListDao`, `SeriesDetailViewModel` | **Parcial** (`btnFavorite` existe no XML) | **COPIAR** | Garante que o usuário consiga curar sua própria lista com o mínimo de cliques. |
| **REF-08** | **Banner Hero com Título em Logo Transparente (Clearlogo) e Metadados Alinhados** | Netflix | Visual cinematográfico premium no topo da tela com logo em PNG transparente sobre backdrop com gradiente direcional. | **Médio** | Não compete com foco; botões de ação abaixo do texto respeitando overscan de 48dp. | API TMDB / AniList / Fanart.tv | **Parcial** (P-18 só desenho) | **ADAPTAR** | Eleva a estética da Home para padrão comercial de streaming. |
| **REF-09** | **Reprodução Automática de Vídeo com Áudio no Hero da Home** | Netflix | Reproduzir trailer em tela cheia silencioso/com som enquanto o foco permanece parado no destaque. | **Alto** | Disputa decodificador de hardware (`MediaCodec`) com o player e consome cota de API Google Drive. | `PlayerEngine`, Google Drive API | **Não** (avaliado em P-18) | **DESCARTAR** | Descartado conforme P-18 Opção B: satura cota de requisições do Drive e prejudica TVs de entrada de baixa RAM/GPU. |
| **REF-10** | **Download Offline de Episódios para Armazenamento Local** | Netflix & Crunchyroll | Baixar vídeos do Drive diretamente para a memória interna da TV ou celular para reprodução sem internet. | **Alto** | Exige gerenciador de downloads robusto com background service, pausar/retomar e armazenamento. | `DownloadManager`, `WorkManager` | **Parcial** (`item_download_card.xml` existe) | **DESCARTAR (temporariamente)** | Foco atual é streaming ágil no ecossistema Android TV local-first; postergar para fase de mobilidade. |

---

## 3. Detalhamento dos Itens Selecionados para Execução Imediata

### Item Prioritário 1 (REF-01): Badges de Áudio e Dublagem ("DUB" / "SUB" / "DUBLADO") nos Cards
- **Origem:** Crunchyroll.
- **Arquivos Tocáveis:**
  - `app/src/main/java/zechs/drive/stream/ui/series/adapter/SeriesEpisodeAdapter.kt`
  - `app/src/main/java/zechs/drive/stream/ui/series/EpisodeAudioBadgePolicy.kt` (Nova classe pura de política)
  - `app/src/test/java/zechs/drive/stream/ui/series/EpisodeAudioBadgePolicyTest.kt` (Testes unitários)
  - `app/src/main/res/values/strings.xml` & `app/src/main/res/values-pt/strings.xml`
- **Comportamento:**
  - Inspeciona o nome do arquivo, metadados ou tracks de áudio disponíveis:
    - Se contiver termos como `Dual`, `Dub`, `PT-BR`, `Dublado`, `Multi-Audio` -> exibe badge `@string/badge_dubbed` ("DUB").
    - Se contiver apenas faixas em japonês com legendas -> exibe badge `@string/badge_subtitled` ("SUB").
  - Formatação com fundo translúcido `obsidian_surface` e borda `obsidian_border_glass`, texto em 12sp.

### Item Prioritário 2 (REF-02): Selo Semântico "Assistido" (Checkmark Badge) Concluído
- **Origem:** Netflix & Crunchyroll.
- **Arquivos Tocáveis:**
  - `app/src/main/java/zechs/drive/stream/ui/series/adapter/SeriesEpisodeAdapter.kt`
  - `app/src/main/res/drawable/badge_episode_watched.xml`
  - `app/src/test/java/zechs/drive/stream/ui/series/SeriesEpisodePresentationTest.kt`
- **Comportamento:**
  - Quando o progresso registrado no `watch_list` for $\ge 95\%$ (conforme regra `hasFinished()`), exibe o badge "✓ Assistido" no canto superior direito da miniatura, ocultando a barra de progresso linear para manter o visual limpo como na Netflix.

### Item Prioritário 3 (REF-07): Ação "+ Minha Lista" / "✓ Na Lista" com Sincronização Instantânea
- **Origem:** Netflix.
- **Arquivos Tocáveis:**
  - `app/src/main/java/zechs/drive/stream/ui/series/SeriesDetailFragment.kt`
  - `app/src/main/java/zechs/drive/stream/ui/series/SeriesDetailViewModel.kt`
  - `app/src/main/res/drawable/btn_favorite_selector.xml`
  - `app/src/test/java/zechs/drive/stream/ui/series/SeriesDetailViewModelTest.kt`
- **Comportamento:**
  - No detalhe da série, clicar no botão de lista alterna entre `+ Minha Lista` e `✓ Na Lista`, com animação de escala e atualização imediata do estado nos favoritos em Room.

---

## 4. Conclusão e Próximos Passos
Os itens **REF-01**, **REF-02** e **REF-07** representam o conjunto ideal de alto valor percebido pelo usuário, baixo risco arquitetural e perfeita aderência às diretrizes de Android TV. A execução seguirá em passos atômicos com `verify-all` 100% verde antes de cada commit.
