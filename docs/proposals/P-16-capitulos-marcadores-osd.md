# P-16 — Lista de capítulos e marcadores no OSD

- **ID:** P-16
- **Status:** Implementado (`ChapterMarkersPolicy` + `ChapterMarkersView` + `PlayerGlassMenuDialog`)
- **Referências:** Kodi

## Objetivo

Exibir no OSD tanto os capítulos embutidos do contêiner MKV/MP4 quanto marcadores criados pelo usuário, ordenados cronologicamente, destacando o capítulo/marcador ativo na posição atual e deduplicando marcadores próximos (janela de 2 segundos).

## Implementação e Testes

- **`ChapterMarkersPolicy.buildChapterMenuEntries(chapters, userBookmarksMs, currentPositionMs)`:** Mescla capítulos do contêiner e marcadores do usuário em ordem cronológica e identifica o item ativo.
- **`ChapterMarkersPolicy.addBookmark(existingBookmarksMs, positionMs, dedupeWindowMs)`:** Adiciona marcador deduplicado e ordenado.
- **Testes unitários:** `ChapterMarkersPolicyTest` (`buildChapterMenuEntries merges chapters and bookmarks chronologically and marks current`, `addBookmark deduplicates nearby bookmarks within window and sorts result`).
