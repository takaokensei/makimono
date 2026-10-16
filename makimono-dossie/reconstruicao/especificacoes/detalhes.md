# Especificação — Detalhes da Série (`SeriesDetailFragment`), Busca, Perfis, Player e Estados

**Data:** 15/10/2026

## 1. Detalhes da Série (`SeriesDetailFragment`)
- Exibe banner panorâmico, pôster, título, metadados, sinopse, botões de ação (`Continuar`/`Assistir do início`, `Favoritar`, `Seguir`, `Vincular MAL`) e lista de episódios ordenada por número de episódio (`EpisodeParser`).
- Preserva a posição e o ID do episódio focado em `FocusMemory` (`details.episodes.${args.folderId}`) ao abrir o player ou menu de contexto, restaurando o foco exatamente no episódio ao retornar.

## 2. Busca e Catálogo (`topHeaderBar` / `SearchFilterHandler`)
- Campo de busca com debounce, botão de voz (`btnVoiceSearch`), botão de limpar (`btnClearSearch`), alternância grade/lista e ordenação (`btnCatalogSort`).

## 3. Perfis (`ProfileSelectionFragment`)
- Suporte a múltiplos perfis locais com avatar personalizado, isolando histórico de reprodução (`WatchHistory`), fila (`WatchQueue`) e pastas seguidas (`FollowedFolder`) por `profileId`.

## 4. Player e OSD (`PlayerActivity` / `MakimonoPlayerOsdSkipCoordinator`)
- Suporte a ExoPlayer/Media3 integrado com OSD TV e player externo/interno MPV.
- Auto-hide do OSD após `4000ms` de inatividade durante a reprodução; qualquer tecla D-pad reabre o OSD e reinicia o timer.
- Botão contextual `Pular Abertura` / `Pular Encerramento` alimentado por timestamps do AniSkip ou salto configurável (`+85s`), com foco prioritário quando visível.
- Comportamento de `Back` em duas etapas: se o OSD estiver visível, `Back` apenas recolhe o OSD; se o OSD estiver oculto, `Back` salva o progresso e encerra o player retornando à tela anterior.
