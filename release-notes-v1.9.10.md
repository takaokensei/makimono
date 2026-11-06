## Makimono v1.9.10 — Instant Modular Catalog, Obsidian Shimmer, Universal Screen Motion & TV D-Pad Ergonomics

Esta versão consolida uma grande evolução na experiência visual e ergonômica do Makimono no Android TV e Mobile, introduzindo carregamento modular sob demanda para o catálogo de arte, transições cinematográficas universais em todas as telas e diálogos, estabilização dos seletores de destaque e navegação fluida com recuo inteligente do OSD no reprodutor de vídeo.

---

### ⚡ Carregamento Modular Sob Demanda & Shimmer Obsidian
- **Arquitetura Two-Tier para Catálogo de Arte:**
  - *Tier 1 Instantâneo:* Carregamento síncrono e imediato (< 2ms) dos animes populares em destaque em `ProfileArtCatalog`, eliminando telas pretas e tempos de espera ao abrir o seletor de avatares e papéis de parede.
  - *Tier 2 Assíncrono com Timeout Seguro:* Varredura de metadados da biblioteca Room executada em `Dispatchers.IO` com `withTimeoutOrNull(2000ms)` e streaming compassado a cada frame (`16ms delay`), preenchendo seções sob demanda sem travamento da thread principal.
- **Placeholders Dedicados Obsidian:**
  - Cartões e avatares exibem placeholders geométricos nativos (`bg_profile_card_placeholder` e `bg_profile_avatar_placeholder`) com acabamento Obsidian (`#101218` e borda de vidro a 16% de opacidade).
  - Animação de pulso/shimmer suave e não intrusiva via `ShimmerController` durante o carregamento de imagens remotas.
- **Resiliência e Correção de URLs:**
  - Atualização de assets com links quebrados do AniList (incluindo Steins;Gate e Vinland Saga) e remoção do colapso de cartões em caso de falha de rede.

---

### 🎬 Transições de Tela Universais & Feedback Tátil
- **Navegação Cinematográfica Coesa:**
  - Definição universal de transições de entrada, saída e retorno (`nav_fade_in`, `nav_fade_out`, `nav_pop_enter`, `nav_pop_exit`) no grafo de navegação (`nav_graph.xml`) e nas extensões de navegação segura (`NavigateExt.kt`).
  - Transições suaves com aceleração e desaceleração harmônicas (220ms e 160ms) em todas as rotas entre telas, substituindo cortes abruptos.
- **Diálogos com Animação de Fade:**
  - Estilo `Animation.Makimono.DialogFade` aplicado consistentemente ao `ProfileEditorDialog`, `ProfileOnboardingDialog`, seletor de episódios e menus contextuais.
- **Feedback Tátil em Botões e Ações:**
  - Efeitos de escala sutil (1.02x no foco / 0.98x no clique) e elevação dinâmica nos botões de CTA primários ("Criar Perfil", "Entrar", "Assistir Agora").

---

### 🎮 Ergonomia no Reprodutor de Vídeo & Controle Remoto
- **Saída Ágil sem Confirmações Desnecessárias:**
  - Eliminação da exigência de duplo clique ou diálogos de confirmação intrusivos para sair da reprodução: um único toque na tecla Voltar encerra o player imediatamente quando a reprodução está em tela cheia limpa.
- **Recuo Inteligente do OSD:**
  - Se o painel OSD, seletor de faixas ou menu de opções estiver visível na tela, a tecla Voltar retrai o menu para o modo tela cheia limpa sem interromper a reprodução nem sair do episódio.

---

### 🌟 Destaques Hero Carousel Estabilizados & Sidebar Harmonizada
- **Pílulas de Navegação com Contagem Estável:**
  - Correção na reatividade do `HomeViewModel` que causava mutação na contagem de bolinhas do carousel ao carregar metadados em segundo plano.
  - Animação contínua da largura do indicador ativo (10dp para 28dp via `ValueAnimator`), desacoplada de índices mutáveis.
- **Harmonização da Barra Lateral:**
  - Gradiente de fundo da barra de navegação retolhada harmonizado (`bg_anime_library` e `bg_sidebar_panel`), eliminando a divisão de blocos sólidos sem comprometer o contraste das capas.

---

### 📐 Correções de Layout & Tipografia
- **Aba de Temporadas e Grade de Episódios:**
  - Resolução do conflito de sobreposição horizontal entre o seletor de temporadas e a lista de episódios em `SeriesDetailFragment`.
- **Sanitização de Títulos e Paridade de Idiomas:**
  - Limpeza de tags de release groups e numerações espúrias em `PlaybackLabel` e `TitleFormattingPolicy`.
  - Paridade de 100% nas 703 strings traduzíveis entre Inglês e Português do Brasil.

---

### 🧪 Integridade, Qualidade e Governança
- **Testes Unitários:** 742 anotações `@Test` aprovadas com 100% de sucesso (0 falhas).
- **Worker Cloudflare:** 17 testes Vitest executados com sucesso.
- **Android Lint:** Zero erros bloqueantes em conformidade com a baseline estrita (681 issues catalogadas).
- **Design System:** Zero hexadecimais brutos e conformidade total com o padrão Obsidian e taxas de contraste WCAG AAA.
