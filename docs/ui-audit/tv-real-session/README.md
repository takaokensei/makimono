# Auditoria Visual Android TV — Sessão Real do Google Drive

Este diretório consolida os registros visuais de auditoria da interface do **Makimono** em modo **Android TV (1080p Leanback / 320 DPI)** operando com a sessão real autenticada do Google Drive, tendo como raiz configurada a biblioteca `oneblacki/`.

---

## 1. Contexto do Ambiente e da Biblioteca

- **Perfil de Tela:** 1920×1080 (1080p), Landscape, Density 320 dpi (xhdpi TV).
- **Controlador:** D-pad / Controle Remoto Android TV (teclas DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER, BACK).
- **Sessão Ativa:** Google Drive OAuth autenticado com token de longa duração e cache SQLite local persistente.
- **Pasta Raiz (Media Root):** `oneblacki/` selecionada e persistida no `SessionManager` e `ProfileManager`.
- **Modo de Rede & Fetching:** Coleta paralela de metadados e capas em lotes de 6 itens (`coroutineScope + async/awaitAll`).

---

## 2. Telas Registradas na Auditoria

| Arquivo | Tela / Fluxo | Descrição e Elementos Verificados |
| :--- | :--- | :--- |
| `01-profile-select.png` | **Seleção de Perfil** | Tela inicial "Quem está assistindo?" com suporte a múltiplos perfis locais, avatares personalizáveis e foco por D-pad. |
| `02-home-shelves-real.png` | **Home / Estantes da Biblioteca Real** | Tela inicial pós-vinheta exibindo Hero Banner em destaque e estantes reais ("Minha Lista", "Adicionados recentemente") com nomes e capas da biblioteca do usuário. |
| `03-nav-sidebar.png` | **Barra de Navegação Lateral (Sidebar)** | Rail lateral compacto expandido via `DPAD_LEFT` destacando as seções: *Início*, *Catálogo*, *Pastas*, *Favoritos* e *Configurações*. |
| `04-catalog-grid-real.png` | **Catálogo / Grid de Mídia** | Visualização em grade 2D navegável de animes e filmes da biblioteca real extraída diretamente do Google Drive. |
| `05-movie-details-homem-aranha.png` | **Detalhes do Filme / Mídia Real** | Tela de detalhes com fanart em alta resolução, sinopse formatada, botões de ação com foco de alto contraste e metadados reais. |
| `06-player-playback-osd.png` | **Player OSD / HUD de Controles** | Interface de reprodução na TV exibindo barra de progresso, tempo decorrido/total, botões de reproduzir/pausar, seletor de faixas de áudio e legendas. |
| `07-player-mpv-render.png` | **Player MPV / Pipeline de Renderização** | Execução direta de streaming com aceleração de hardware e renderizador nativo libmpv. |
| `08-settings-root-oneblacki.png` | **Configurações / Raiz de Mídia Ativa** | Painel de configurações demonstrando a vinculação persistente da raiz da biblioteca apontando para `oneblacki/`. |
| `09-folder-picker-oneblacki.png` | **Navegador de Pastas do Drive** | Diálogo de seleção de pastas remotas navegando dentro da hierarquia da pasta raiz do usuário. |

---

## 3. Melhorias e Correções Específicas Validadas

1. **Priorização de Pôsteres Oficiais de Animes (Anti-Flyer / Anti-Live-Action):**
   - O algoritmo de pontuação de candidatos do AniList foi calibrado para priorizar lançamentos completos e séries consagradas (`status = FINISHED | RELEASING`, bônus de popularidade até +0.20), aplicando penalidade rigorosa (-0.25) em teasers não lançados (`NOT_YET_RELEASED`) que utilizam flyers promocionais com kanjis de broadcast.
   - Casos como `Tensei Shitara 4th Season` agora convergem confiavelmente para a arte oficial da franquia principal, evitando materiais de teaser obscuros.
   - Pastas nomeadas de animes mantêm estrita precedência para capas do AniList, impedindo falso match com versões live-action do TMDB.

2. **Higienização de Títulos nas Estantes ("Minha Lista" / "Adicionados Recentemente"):**
   - Implementado filtro sanitizador (`AnimePosterResolver.cleanAnimeTitle`) integrado ao `StarredShelfAdapter`.
   - Remove tags técnicas de release presentes nos nomes de arquivo (e.g. `2026`, `2160p`, `1080p`, `HEVC`, `x265`, `Dolby`, `Atmos`, `TrueHD`, `DTS`, `HDR`, extensões `.mkv`, `.mp4`). Títulos como `Homem-Aranha - Um Novo Dia 2026 HEVC Dolby 7.1` são exibidos limpos e legíveis como `Homem-Aranha - Um Novo Dia`.

3. **Início Impossível de Pular e Fade Out Suave (Estilo Netflix):**
   - A vinheta inicial consome todos os eventos de toque e teclas de controle remoto (`dispatchKeyEvent` e `setOnClickListener` bloqueados enquanto ativa).
   - Ao término do vídeo, a transição para a seleção de perfil ocorre via animação suave de 600ms com interpolação `AccelerateDecelerateInterpolator` (fade-out cinematográfico).

4. **Busca Paralela de Metadados e Capas:**
   - As requisições de metadados das pastas e mídias do Drive agora operam em lotes concorrentes (`chunked(6)`) com coroutines assíncronas, acelerando substancialmente o preenchimento da estante na TV.

5. **Garantia de Persistência das Credenciais:**
   - Atualizações in-app preservam o banco de dados Room, SharedPreferences e DataStore, assegurando que o PAT do GitHub, credenciais do Google Drive, tokens OAuth e preferências do usuário permaneçam intactos.
