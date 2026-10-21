## Makimono v1.9.4 — Netflix & Crunchyroll UX Polish & TV Performance Overhaul

Esta release traz melhorias críticas de desempenho para Android TV e eleva a qualidade visual e de navegação ao padrão comercial de apps de streaming como Crunchyroll e Netflix.

### ⚡ Desempenho e Responsividade em TV
- **Eliminação do travamento de 4-5 segundos ao voltar:**
  - Desativadas transições de superfície pesadas (`MaterialSharedAxis.X`) em modo TV Leanback / 10-foot e dispositivos com RAM reduzida, evitando a renderização simultânea de duas hierarquias 1080p complexas.
  - Registrado callback explícito no `onBackPressedDispatcher` do `SeriesDetailFragment` para retorno instantâneo.
  - Liberação imediata de adaptadores e descarregamento de cache de imagens do Glide no `onDestroyView()`.
  - Remoção de chamadas síncronas (`runBlocking`) a queries SQLite do Room para status de downloads na UI thread, adotando cache em memória `completedDownloadPathCache`.

### 🎨 Ícones Vetoriais SVG em Nível Crunchyroll / Netflix
- Modernização completa dos ícones de navegação e controles utilizando geometrias vetoriais nativas extraídas por engenharia reversa do APK da Crunchyroll TV:
  - **Início (`ic_home_24`):** Silhueta com beiral estilizado e proporções refinadas.
  - **Busca (`ic_search_24`):** Lente circular perfeita com haste arredondada a 45°.
  - **Player (`ic_play_24`, `ic_pause_24`):** Triângulo de play com vértices arredondados e barras verticais em cápsula dupla.
  - **Histórico (`ic_history_24`):** Relógio de rebobinar limpo com ponteiros sem serrilhamento.
  - **Configurações (`ic_settings_24`):** Engrenagem mecânica de 8 dentes simétricos com furo circular.
  - **Ações (`ic_check_24`, `ic_add_24`):** Checkmark suave e botão de adicionar em pílula.
  - **Pastas e Cinema (`ic_folder_24`, `ic_movie_24`):** Abas de pasta com cantos suaves e claquete de cinema moderna.

### 🍿 Recursos de Streaming (Netflix & Crunchyroll)
- **Badges de Áudio Crunchyroll:** Tags visuais DUB, DUAL e LEG nos cartões de episódios.
- **Filtro de Gêneros por Pílulas na Home:** Barra horizontal de categorias/gêneros (Ação, Comédia, Romance, Shounen, etc.) com rolagem suave e foco otimizado para D-pad.
- **Toggle Interativo de Lista / Watchlist:** Botão de adicionar/remover da lista estilo Netflix com feedback dinâmico.
- **Pills de Metadados e Glassmorphism:** Elevação dos cartões para 12dp de raio com efeito de vidro fosco (`Obsidian Design System`).
- **Badge de Episódio Assistido:** Checkmark vetorial discreto indicando progresso concluído.

### 📦 Verificação e Integridade
- 100% aprovado no suite unificado `verify-all` (671 testes unitários JUnit, Android Lint sem novas violações, Claims auditadas, Vitest do backend).
