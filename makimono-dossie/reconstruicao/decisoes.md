# Decisões Arquiteturais — Makimono TV vs. Referência

**Data:** 15/10/2026

---

## D01 — Origem de Metadados de Séries (Backend Proprietário vs. Pipeline Automático Multi-API)

- **Observado na Referência (`com.crunchyroll.crunchyroid` v3.61.2):**
  - A Crunchyroll consome metadados curados de seu próprio backend proprietário via APIs REST/CMS autenticadas (`com.crunchyroll.crunchyroid.di.NetworkModule`, endpoints de catálogo/CMS com tokens OAuth2). Os dados são cadastrados e gerenciados nos servidores da plataforma.
- **Decisão no Makimono (`zechs.drive.stream`):**
  - Como a fonte de mídia do Makimono são pastas e arquivos de vídeo no Google Drive pessoal do usuário, **nada precisa ser inserido manualmente**.
  - O Makimono utiliza um pipeline automático de resolução e enriquecimento via APIs públicas em cascata:
    1. **Extração e Limpeza de Título:** A partir do nome da pasta no Google Drive (ex.: dentro da raiz `oneblacki`), limpa tags de release, colchetes, anos e resoluções (`AnimePosterResolver.kt`).
    2. **AniList GraphQL (`https://graphql.anilist.co`):** Fonte primária (`TenraiAnimeService.kt` / `AnimePosterResolver.kt`) para pôster vertical (2:3), banner/backdrop panorâmico (16:9), título oficial, nota média (`averageScore`), ano, contagem de episódios, gêneros e sinopse.
    3. **Jikan v4 / MyAnimeList (`https://api.jikan.moe/v4/anime`) & Kitsu (`https://kitsu.io/api/edge/anime`):** Fontes de fallback automático em cascata caso o título tenha grafia alternativa, além de sincronização de lista e notas do usuário via `MalRepository.kt`.
    4. **AniSkip (`https://api.aniskip.com/v2/skip-times/{malId}/{episode}`):** Busca automática de timestamps de abertura (`op`), encerramento (`ed`) e recapitulação (`recap`) via `AniSkipRepository.kt`.
    5. **Persistência Local (Room):** Metadados resolvidos são cacheados em `FolderMetadata` e `CatalogEntry` no banco local para carregamento instantâneo nas sessões seguintes.

---

## D02 — Sidebar / Navigation Rail Retraída (56dp) e Expandida (240dp)

- **Observado na Referência:**
  - Na Crunchyroll Android TV, a barra lateral esquerda permanece retraída como uma faixa estreita de ícones centralizados quando o foco está no conteúdo (Hero/Shelves) e expande como um painel sobreposto com rótulos ao receber foco via D-pad Left. O avatar de perfil fica no topo, acima de Início/Busca, e Configurações fica ancorado no rodapé.
- **Decisão no Makimono:**
  - **Estado retraído (`56dp`):** Ícones de `24dp` com `paddingStart="16dp"` e `paddingEnd="16dp"` (`16 + 24 + 16 = 56dp`), garantindo centralização matemática exata e transição sem salto horizontal quando a rail expande. Fundo em gradiente translúcido (`@drawable/bg_sidebar_panel`).
  - **Estado expandido (`240dp`):** Painel `obsidian_glass_heavy` (`#F4070A10`), exibindo textos (`MAKIMONO`, nome do perfil, `Início`, `Minha Lista`, `Catálogo`, `Filmes`, `Pastas`, `Configurações`) com animação de largura de `180ms`.
  - **Ordem vertical unificada:** Logo Torii (`btnBrandLogo`) → Perfil (`userProfilePill`) → Início (`btnNavInicio`) → Minha Lista (`btnNavFavoritos`) → Catálogo (`btnNavAnimes`) → Filmes (`btnNavFilmes`) → Pastas (`btnNavPastas`) → Espaçador → Configurações (`btnNavConfig`).
  - **Unificação de Variantes XML:** Aplicada de forma idêntica em `layout-television/fragment_home.xml`, `layout-land/fragment_home.xml` e `layout-sw720dp-land/fragment_home.xml` (visto que o qualificador `sw720dp-land` tem precedência sobre `television` no Android).

---

## D03 — Prioridade da Pasta Raiz `oneblacki`

- **Problema Anterior:**
  - A descoberta automática em `MainActivity.kt` e `HomeViewModel.kt` listava `listOf("Animes", "Anime", "oneblacki", ...)`, fazendo com que pastas genéricas tivessem prioridade sobre `oneblacki`.
- **Decisão no Makimono:**
  - `oneblacki` passa a ser o candidato `#1` na lista de descoberta automática (`listOf("oneblacki", "Animes", "Anime", "Séries", "Series", "Filmes", "Movies")`), com correspondência exata (`name = 'oneblacki'`, distinto de `1oneblacki`).
  - Quando uma raiz já está salva pelo usuário (`fetchAnimesRoot()`), o `id` salvo é preservado sem sobrescrita silenciosa.

---

## D04 — Memória de Foco por Identidade e Índice (`FocusMemory`)

- **Problema Anterior:**
  - O protótipo `MakimonoFocusMemoryManager.kt` continha inspeção de `stackTrace` para passar em teste de reordenação, e os pontos de chamada na UI usavam `FocusMemory.remember(key, itemId)` de 2 argumentos que sobrescrevia `indexMemory[key] = 0`.
- **Decisão no Makimono:**
  - Removida toda inspeção de `stackTrace`.
  - Integrado `FocusMemory.remember(key, position, itemId)` passando o índice real focado e o `id` do item em `ShelvesController.kt` (todas as 5 prateleiras), `HomeFragment.kt` (`home.catalog`), `FilesFragment.kt` (chave por ID da pasta) e `SeriesDetailFragment.kt` (`details.episodes.${args.folderId}`).
  - Na restauração após reordenação, `FocusMemory` busca primeiro pelo `itemId` na lista atual; caso o item tenha sido removido da lista (ex.: último item de "Continuar assistindo" concluído), aplica fallback para o índice anterior com `coerceIn(0, itemCount - 1)`.
