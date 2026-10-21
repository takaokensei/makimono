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
| **REF-01** | **Badges de Áudio e Dublagem ("DUB" / "DUAL" / "LEG") nos Cards de Episódios** | Crunchyroll | O usuário identifica instantaneamente se o episódio possui áudio dublado ou apenas legendas em japonês antes de iniciar o play. | **Baixo** | Visível a 3m sem poluir o layout do card; respeita o piso de 12sp. | `EpisodeParser`, metadados de arquivos MKV | **Sim** | **IMPLEMENTADO** | Commit `4cf8327`: política pura `EpisodeAudioBadgePolicy`, 8 testes unitários, coloração semântica (`obsidian_accent` para dub, `obsidian_link_blue_gray` para sub). |
| **REF-02** | **Selo Semântico "Assistido" (Checkmark Badge) nos Cards de Episódios Concluídos** | Netflix & Crunchyroll | Distinção visual imediata entre episódios não vistos, em andamento e concluídos (>=90%). | **Baixo** | Foco D-pad inalterado; badge com checkmark vetorial sobre miniatura 16:9. | `WatchListDao`, Room | **Sim** | **IMPLEMENTADO** | Commit `2541266`: ícone vetorial `ic_check_12`, tokens Obsidian no background (`obsidian_media_scrim`/`obsidian_border_glass`), barra 100% verde Obsidian. |
| **REF-03** | **Menu de Contexto / Ações Rápidas no Card de "Continuar Assistindo" (Long-Press / More Actions)** | Netflix | Permite "Remover da Linha", "Assistir do Início" ou "Ver Detalhes da Série" direto da Home sem abrir o player. | **Médio** | Modal/Bottom Sheet com foco automático no primeiro item, fechamento com botão Voltar. | `WatchListDao`, `HomeFragment` | **Não** | **COPIAR** | Resolve a frustração comum de animes abandonados poluindo a primeira prateleira da Home. |
| **REF-04** | **Linha de Filtro Rápido por Gênero / Categoria na Home (Genre Chips / Pills)** | Crunchyroll | Navegação por categorias populares de anime ("Ação", "Isekai", "Shonen", "Romance", "Fantasia") com 1 clique. | **Médio** | Linha horizontal de pills com navegação D-pad suave (esquerda/direita) acima do grid/prateleiras. | Metadados Room / TMDB / AniList | **Não** | **ADAPTAR** | Facilita a descoberta de novos títulos sem exigir busca por texto no teclado virtual da TV. |
| **REF-05** | **Seletor de Temporadas em Modal Dropdown Compacto na Tela de Detalhe** | Netflix | Para séries com muitas temporadas (ex: Monogatari, Fate), evita navegação horizontal infinita por abas. | **Médio** | Diálogo vertical com foco inicial na temporada atual, rolagem suave e seleção instantânea. | `SeriesDetailFragment` | **Não** (usa abas horizontais) | **ADAPTAR** | Complementa as abas horizontais quando a série ultrapassar 4 temporadas/partes. |
| **REF-06** | **Efeito de Foco Expandido (Scale 1.05x com Sombra/Glow Ciano nos Cards)** | Netflix & Crunchyroll | Feedback tátil e visual instantâneo indicando exatamente qual card está ativo na TV. | **Baixo** | Aplicação de `FocusEnginePolicy` com duração de 150ms desacelerada (`decelerate_interpolator`). | `FocusEnginePolicy`, `motion_spec.xml` | **Parcial** (implementado em alguns cards, falta padronização global) | **ADAPTAR** | Padroniza o comportamento de 100% dos cards interativos do app. |
| **REF-07** | **Pílula de Toggle Rápido "+ Minha Lista" / "✓ Na Lista" com Feedback Visual** | Netflix | Adicionar ou remover da lista de favoritos com animação de ícone e atualização instantânea da Home. | **Baixo** | Botão secundário na barra de ações da série; acessível por D-pad logo após o botão principal "Assistir". | `WatchListDao`, `SeriesDetailViewModel` | **Sim** | **IMPLEMENTADO** | Commit `a5311cf`: alternância entre `+ Minha Lista` e `✓ Na Lista`, ícones vetoriais `ic_add_24`/`ic_check_24`, remoção de `amber_500` e suporte nativo ao D-pad. |
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

## 4. Status de Implementação (Fase 2 Concluída)

A implementação dos itens selecionados foi realizada com estrito rigor de arquitetura e conformidade com o DoD:

1. **REF-01 (Badges de Áudio Crunchyroll-Style):**
   - **Classe Pura:** `EpisodeAudioBadgePolicy` (`app/src/main/java/zechs/drive/stream/ui/series/EpisodeAudioBadgePolicy.kt`).
   - **Suíte de Testes:** `EpisodeAudioBadgePolicyTest` com 8 testes unitários cobrindo áudio duplo, dublagens, legendas implícitas/explícitas, internacionalização e filtros de extensões não-vídeo.
   - **Apresentação:** Integração no `SeriesDetailViewModel` e `SeriesDetailEpisodeAdapter`, com destaque semântico em `@color/obsidian_accent` para faixas dubladas e `@color/obsidian_link_blue_gray` para legendadas.
   - **Commit:** `4cf8327` (`feat(series): add Crunchyroll-style DUB/DUAL/LEG audio badges to episode cards (REF-01)`).

2. **REF-02 (Selo Semântico "Assistido" com Checkmark):**
   - **Assets & Layouts:** Novo ícone vetorial `ic_check_12` integrado ao `badgeWatched` em `item_series_episode_card.xml` (portrait e landscape).
   - **Design System:** Limpeza de cores raw em `badge_episode_watched.xml` substituídas pelos tokens `@color/obsidian_media_scrim` e `@color/obsidian_border_glass`.
   - **Internacionalização:** String em inglês corrigida para `WATCHED` com paridade integral com `ASSISTIDO` em português.
   - **Commit:** `2541266` (`feat(series): enhance completed episode watched badge with vector checkmark and obsidian tokens (REF-02)`).

3. **REF-07 (Toggle "+ Minha Lista" / "✓ Na Lista"):**
   - **UX & Iconografia:** Substituição de ícones de estrela genéricos por `ic_add_24` e `ic_check_24`, replicando o padrão visual da Netflix.
   - **D-pad & Acessibilidade:** Remoção de `amber_500` hardcoded em favor de `btn_secondary_action_foreground` com suporte a estados de foco do D-pad e `contentDescription` dinâmico para TalkBack.
   - **Commit:** `a5311cf` (`feat(series): add interactive Netflix-style watchlist toggle with dynamic iconography (REF-07)`).

---

## 5. Próximos Passos
Prosseguir para os próximos itens da matriz conforme prioridade de produto:
- **REF-03:** Menu de contexto de ações rápidas no card de "Continuar Assistindo" (long-press).
- **REF-05:** Seletor de temporadas em modal dropdown compacto para séries extensas.
- **REF-06:** Padronização global da animação de escala (1.05x) com foco Obsidian.
