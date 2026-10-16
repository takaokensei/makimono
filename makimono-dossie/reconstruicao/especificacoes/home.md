# Especificação — Home, Hero, Prateleiras e Pipeline de Metadados

**Data:** 15/10/2026

## 1. Descoberta e Fixação da Raiz `oneblacki`
- A descoberta automática (`MainActivity.autoDetectLibraryRootsIfNeeded()` e `HomeViewModel.discoverAnimesRoot()`) prioriza estritamente a pasta `oneblacki` na ordem:
  `listOf("oneblacki", "Animes", "Anime", "Séries", "Series", "Filmes", "Movies")`.
- A consulta à API do Google Drive usa `name = 'oneblacki' and mimeType = 'application/vnd.google-apps.folder' and trashed = false`, distinguindo `oneblacki` de `1oneblacki`.
- O ID da pasta selecionada é persistido e nunca sobrescrito silenciosamente.

## 2. Pipeline Automático de Metadados de Séries
- O Makimono extrai o título a partir do nome da pasta no Google Drive e consulta automaticamente:
  1. **AniList GraphQL (`https://graphql.anilist.co`)**: pôster vertical, banner 16:9, título oficial, sinopse, gêneros, ano, contagem de episódios e nota média.
  2. **Jikan v4 / MyAnimeList (`https://api.jikan.moe/v4/anime`) & Kitsu (`https://kitsu.io/api/edge/anime`)**: fallback em cascata para resolução de arte e metadados.
  3. **Room Cache (`FolderMetadata`, `CatalogEntry`)**: armazenamento local persistente para exibição imediata sem necessidade de cadastro manual.

## 3. Composição da Home (`Início`)
- **Hero (`featuredHeroContainer`)**: exibe backdrop 16:9 com preview dinâmico (`scheduleFeaturedPreview` com debounce de `260ms`) ao navegar pelas prateleiras, botão primário `Assistir` (`btnFeaturedPlay`) e botão secundário `Detalhes` (`btnFeaturedInfo`).
- **Prateleiras Horizontais**:
  1. `Continuar assistindo` (`rvContinueWatchingShelf`)
  2. `Minha fila` (`rvWatchQueueShelf`)
  3. `Novos episódios` (`rvNewEpisodesShelf`)
  4. `Minha lista` (`rvStarredShelf`)
  5. `Adicionados recentemente` (`rvRecentlyAddedShelf`)
- **Grade Catálogo (`rvAnimeLibrary`)**: exibida abaixo das prateleiras em `Início` ou em tela cheia nas abas `Catálogo` / `Minha Lista` / `Filmes`.
