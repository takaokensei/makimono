# Cobertura da Referência (`com.crunchyroll.crunchyroid` v3.61.2)

**Data:** 15/10/2026

| `feature_id` | Estado na Referência | Tipo de Evidência | Resumo Técnico e Limitações |
| :--- | :--- | :--- | :--- |
| `NAV-01` | Observado em runtime e estaticamente | `cr_ref_home_collapsed.png` + `cr_tv_live_ui.xml` | Rail lateral esquerda retraída (`56dp`), ícones puros sobre scrim escuro sem caixa cinza de fundo quando o foco está no Hero/Shelves. |
| `NAV-02` | Observado em runtime e estaticamente | `cr_ref_rail_expanded.png` + `step6_rail.xml` | Expansão da rail (`240dp`) com barra retangular `0dp`-corner ponta a ponta no item focado (ícone e texto escuros `#070A10`) e barra `#262830` no item ativo não focado. |
| `NAV-03` | Observado em runtime | `cr_ref_home_collapsed.png`, `cr_ref_rail_expanded.png` | Avatar circular do perfil no topo absoluto da rail (`y=40dp`, sem logo acima) com duas linhas quando expandido (`Nome` + `"Switch Profile"`), seguido de `Search`, `Home`, `Watchlist`, `Browse` e `Settings` no rodapé. |
| `NAV-04` | Observado em runtime | `step6_rail.xml`, `step7_back.xml` | `Back` a partir das prateleiras retorna o foco à rail lateral antes de oferecer saída do aplicativo. |
| `FOC-01` | Observado em runtime e estaticamente | `cr_ref_shelves.png` + análise de estados | Retorno de tela de detalhes preserva o card focado na prateleira de origem; cards 2:3 usam moldura retangular de foco (`3dp`, cantos `2dp`). |
| `LIB-01` | Proposto para Makimono | N/A na referência | A referência utiliza catálogo remoto próprio; no Makimono a fonte é a pasta `oneblacki` no Google Drive. |
| `MET-01` | Observado estaticamente | JADX (`NetworkModule`, CMS) | A referência obtém metadados de seu backend proprietário autenticado via OAuth2; o Makimono resolve automaticamente via AniList/Jikan/Kitsu/AniSkip. |
| `HOM-01` | Observado em runtime | `cr_ref_home_collapsed.png` + `cr_tv_live_ui.xml` | Hero banner 16:9 full-bleed no topo (`y=0`, sem barra de busca flutuante em `Home`), com título, metadados, sinopse e botões retangulares (`radius=2dp`, texto em caixa alta: `START WATCHING` / `MORE INFO`). |
| `HOM-02` | Observado em runtime | `cr_ref_shelves.png` | Prateleiras horizontais com pôsteres 2:3 + rodapé escuro `#141519` contendo o título em 2 linhas e moldura retangular de foco. |
| `DET-01` | Observado em runtime | `cr_ref_details.png`, `cr_ref_details_episodes.png`, `details.xml` | Tela ShowDetails com Hero full-bleed no topo, botões retangulares, abas `EPISODES | DETAILS | MORE LIKE THIS` e layout de duas colunas (`Seasons` à esquerda + `Episodes` 16:9 com barra de progresso e badge `Watched` à direita). |
| `BUS-01` | Observado em runtime | `cr_ref_search.png` | Tela Search dedicada (acessada pelo item `Search` da rail) com barra de busca superior + botão de voz e grade de pôsteres 2:3. |
| `PRF-01` | Observado em runtime | `cr_ref_whos_watching.png` | Tela `Who's Watching?` com avatares circulares grandes, anel de foco circular, botão de edição e botão retangular inferior. |
| `PLY-01` | Observado estaticamente / Bloqueado por assinatura ativa | `cr_ref_resubscribe_modal.png` + JADX (`ExoPlayer`) | Reprodução de vídeo na conta logada exibe modal de renovação de assinatura (`cr_ref_resubscribe_modal.png`); controles OSD e skip documentados estaticamente via JADX e implementados no Makimono. |
| `CFG-01` | Observado em runtime e estaticamente | `step6_rail.xml` + JADX | Configurações de conta, preferências de idioma/legenda e informações do aplicativo. |

