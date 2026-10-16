# Inventário Funcional Versionado — Makimono TV (v1.0 — 15/10/2026)

Este inventário define o denominador verificável de cobertura entre o aplicativo de referência (`com.crunchyroll.crunchyroid` v3.61.2) e o Makimono TV (`zechs.drive.stream`).

| `feature_id` | Superfície | Comportamento Observável | Fase Alvo |
| :--- | :--- | :--- | :--- |
| `NAV-01` | Rail / Sidebar | Rail retraída de `56dp` com ícones de `24dp` centralizados (`16dp` padding horizontal) e fundo em gradiente translúcido quando o foco está no conteúdo | R00 / R05 |
| `NAV-02` | Rail / Sidebar | Expansão animada para `240dp` (`#F4070A10`) ao receber foco via D-pad Left, exibindo rótulos e mantendo o item ativo selecionado | R00 / R05 |
| `NAV-03` | Rail / Sidebar | Ordem de topo a base estilo Crunchyroll: Logo Torii → Perfil ativo → Início → Minha Lista → Catálogo → Filmes → Pastas → Configurações (rodapé) | R00 / R05 |
| `NAV-04` | Rail / Back | Pressionar `Back` no conteúdo (prateleiras/catálogo) recolhe o foco para a Rail; pressionar `Back` na Rail em outra seção volta para `Início`; em `Início` abre diálogo de saída | R00 / R05 |
| `FOC-01` | Memória de Foco | `FocusMemory` armazena `(shelfKey, itemIndex, itemId)` reais e restaura pelo `itemId` após reordenação da lista ou aplica clamping de índice após remoção | R00 / R05 |
| `LIB-01` | Raiz Drive | Descoberta automática prioriza estritamente a pasta `oneblacki` (`name = 'oneblacki'`, distinto de `1oneblacki`) e preserva o folder ID configurado | R00 / R06 |
| `MET-01` | Metadados | Resolução automática multi-API em cascata (AniList GraphQL → Jikan/MAL v4 → Kitsu → AniSkip) com cache local Room (`FolderMetadata`, `CatalogEntry`) | R00 / R06 |
| `HOM-01` | Home Hero | Destaque principal (Hero) com backdrop 16:9, scrim lateral/inferior, título, metadados, sinopse e CTAs `Assistir` / `Detalhes` com navegação D-pad determinística | R06 |
| `HOM-02` | Home Prateleiras | Prateleiras horizontais (`Continuar assistindo`, `Minha fila`, `Novos episódios`, `Minha lista`, `Adicionados recentemente`) e grade `Catálogo` | R06 |
| `DET-01` | Series Detail | Tela de detalhes da série com banner, sinopse, seletor de temporadas, lista de episódios com progresso, retomada e marcação de visto/não visto | R07 |
| `BUS-01` | Busca & Catálogo | Busca por texto/voz com debounce, limpeza rápida, alternância grade/lista e ordenação | R08 |
| `PRF-01` | Perfis & Sessão | Seleção, criação e troca de perfil local (`ProfileSelectionFragment`), isolamento de histórico/favoritos por perfil e login/pareamento OAuth | R09 / R10 |
| `PLY-01` | Player & OSD | Reprodução ExoPlayer/Media3 + MPV, OSD com auto-hide em 4s, Skip Intro/Ending (AniSkip / +85s), seleção de áudio/legenda e próximo episódio | R11 |
| `CFG-01` | Configurações | Ajuste de pasta raiz (`oneblacki`), aparência de legendas, player padrão, sincronização MAL e diagnóstico | R12 / R13 |
