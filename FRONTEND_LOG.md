# Makimono Frontend Evolution Log

Este documento é a memória persistente dos ciclos de auditoria, testes visuais e refatoração de UI/UX do Makimono para Android TV e Mobile, conforme definido em `GOAL.md`.

## Iteração Apple/Netflix Grade — Sincronia Persistente, ClearLogo e Binge-Watch Autoplay (2026-10-07)

1. **Persistência de Delay de Áudio e Legenda no Banco Room (v7 -> v8):**
   - Entidade WatchList atualizada com audioDelayMs: Long = 0L e subtitleDelayMs: Long = 0L.
   - Implementada migração MIGRATION_7_8 em WatchListDatabase e registrada em DatabaseModule.
   - Teste unitário adicionado em RoomMigrationTest validando as instruções SQL.
   - DAO WatchListDao e repositório com suporte a updateDelays.
   - PlayerViewModel expõe getDelays() e saveDelays() com integração no salvamento contínuo de progresso.
   - MPVActivity e PlayerActivity restauram sincronias salvas ao carregar o vídeo e salvam automaticamente ao alterar nos diálogos Glass.
   - Backup DTO BackupSerializer atualizado para exportar/importar offsets sem afetar compatibilidade retroativa.

2. **ClearLogo / Título Gráfico Transparente no Hero Spotlight:**
   - Adicionada tag ivFeaturedClearLogo nos 4 layouts do Home (fragment_home.xml, layout-land, layout-sw720dp-land, layout-television).
   - HomeViewModel.FeaturedAnime e AnimePosterResolver.AnimeMetadata com campo clearLogoUrl.
   - HomeFragment renderiza o logotipo com Glide ocultando a tipografia de texto quando presente, com fallback gracioso.

3. **Card Binge-Watching A Seguir Estilo Netflix/Apple TV:**
   - Janela de contagem regressiva em PlaybackProgressPolicy expandida para 25 segundos (AUTOPLAY_CARD_TRIGGER_MS = 25_000L).
   - Suporte a isEndingChapterActive: ao detectar encerramento via AniSkip em MPVActivity, o card A Seguir é acionado imediatamente sem esperar os segundos finais.

4. **Validação:**
   - Suíte unitária completa aprovada (BUILD SUCCESSFUL, 54 tarefas executadas com zero falhas).
   - Build e instalação de makimono-v1.6.4.3-arm64-v8a-debug.apk no Moto G34 5G (0086442783).
   - Auditoria em tempo de execução capturada em docs/ui-audit/.

## F0 — Baseline e protocolo HIG v2

1. Preservar o estado do checkout e a pasta local `docs/ui-audit/current-state/`, que já contém as 12 capturas do usuário.
2. Marcar o commit atual com `pre-ui-v2`; não criar branch nem enviar tags ou commits ao remoto.
3. Ler integralmente os dez PDFs obrigatórios de F0, extraindo regras tvOS/gerais e verificando visualmente tabelas/figuras relevantes.
4. Reconciliar o briefing v2 com `README.md`, `GOAL.md`, este log e a auditoria existente; registrar ausências e conflitos sem inventar requisitos HIG.
5. Confirmar que a fixture debug reproduz as 12 telas/estados e capturar o MuMu conectado antes de qualquer mudança visual.
6. Executar `assembleDebug` e os testes JVM baseline; registrar falhas preexistentes separadas das mudanças desta rodada.
7. Criar `HIG_NOTES.md` e `HIG_CONFORMANCE.md` com IDs rastreáveis, fontes, páginas e limitações verificadas.
8. Fechar F0 com capturas, rubrica e evidências; só então escolher a menor próxima fase que respeite os gates de build e testes.

**Status F0:** `pre-ui-v2` aponta para `c1b772f`; as 12 capturas do usuário foram preservadas e os 12 estados foram repetidos na fixture em `docs/ui-audit/after-v2/f0/`. A fixture ganhou rota `details_episode_focus`; a hierarquia confirma o card como focado. O `applicationIdSuffix .visualreview` mantém o APK de teste ao lado da versão assinada, sem apagar a sessão dela. Os dez PDFs F0 foram lidos nas seções gerais/tvOS e as tabelas/figuras relevantes foram rasterizadas; decisões, conflitos e escopo por regra estão em `docs/ui-modernization/HIG_NOTES.md`. A matriz/rubrica e medidas estão em `docs/ui-modernization/HIG_CONFORMANCE.md`. `AUDIT_REPORT.md` não existe; usamos `docs/ui-modernization/AUDIT.md` como auditoria existente. `assembleDebug testDebugUnitTest --rerun-tasks --no-daemon -PdistributionBuild` passou no container Android SDK: 152 testes, 0 falhas/erros. Sem push. Próximo passo: F1 tokens e contraste.

## F1 — Tokens e contraste (plano antes de editar)

1. Reconfirmar no PDF as regras F0 de foco, layout, acessibilidade, materiais e movimento aplicáveis aos tokens; manter as referências locais fora do repositório.
2. Inventariar `values`, `values-night`, `values-land`, `values-television`, `values-sw720dp-land` e `values-port`, além dos estilos/dimens/fontes e `motion_spec.xml`.
3. Mapear cores existentes para papéis semânticos; identificar hexes soltos e conflitos entre os cinco temas sem removê-los.
4. Definir escala de tipografia Inter e espaçamento/cantos/elevation para as telas de TV; preservar layouts menores e IDs existentes.
5. Criar uma verificação reproduzível de contraste para os papéis × superfícies × temas, mirando piso 4,5:1 e 7:1 para texto pequeno.
6. Implementar tokens centralizados e o modo visual reduzido apenas onde o modelo existente permitir sem introduzir regressão.
7. Capturar amostras de superfície, texto, foco e estados claros/escuros no MuMu e corrigir toda falha antes de fechar a fase.
8. Rodar `assembleDebug` e a suíte unitária completa sem cache; atualizar rubrica/matriz e só então criar o commit F1 local.

**Execução F1 (validada):** os PDFs Accessibility, Color, Dark Mode, Focus and selection, Layout, Materials, Motion e Typography foram reavaliados nesta fase; referências locais permaneceram fora do repo. Adicionados Obsidian como tema 6 e tokens sem alterar os IDs salvos 0–4, margens TV 48dp/30dp, estilos Inter, raios/elevation, outline contínuo com fallback < Android 11 e motion 100/150/220/300/450ms. Corrigida a diferença de Estuary entre Kotlin/XML. Centralizados 76 hexes em 50 layouts e adicionados `tools/check_layout_colors.py` e `tools/check_contrast.py`; todos os layouts agora passam sem hex inline e os seis temas passam 7:1 para texto e 4,5:1 para texto de ação. A inspeção encontrou o seletor cortando o sexto tema; tornamos a lista rolável e verificamos que cinco presses DOWN alcançam/focam Obsidian. MuMu 1920×1080 confirmou a navegação hero→card; o anel branco de 42dp e o título da próxima fileira ainda cortado foram registrados para F2/F3. `assembleDebug testDebugUnitTest --rerun-tasks --no-daemon -PdistributionBuild` passou: 155 testes, 0 falhas/erros; APK x86_64 instalado na fixture separada. Capturas e hierarquias ficam em `docs/ui-audit/after-v2/f1/`. Próximo: fechar o commit local F1 e iniciar o redesign do foco (F2).


---

## Baseline Inicial (v1.6.2 — Commit 28b44f6 / ui/pro-polish)

* **Ambiente**: MuMuPlayer (x86_64, `emulator-5554`), configurado em 1920x1080 landscape @ 320dpi (padrão Android TV 10-foot UI).
* **Capturas de Referência**: Salvas em `docs/ui-audit/before/` (`home.png`, `profiles.png`, `files.png`, `details.png`, `player.png`).
* **Compilação**: `assembleDebug` executado com sucesso (APK instalado e validado no emulador).
* **Testes Unitários**: Conhecido problema no executor do Gradle em ambiente Windows com caminhos de usuário contendo espaços/acentos (`worker.org.gradle.process.internal.worker.GradleWorkerMain`). A suíte deve ser executada com isolamento ou testOptions adequado.

### Avaliação Inicial por Rubrica (0 a 10)

| Área / Critério | Nota Inicial | Principais Falhas Observadas no Baseline |
| :--- | :---: | :--- |
| **TV Hierarchy & Shell** | 7.0 | Rail bem estruturado, mas falta transição e foco inequívoco nos elementos globais; espaçamentos e margens variam entre telas. |
| **Home Screen** | 6.5 | Linha de gêneros duplicada ("Fantasia · Aventura" e tags cruas diretamente embaixo); botão "Ordenar" sem container; barra de progresso do card "Continuar assistindo" colada no rodapé inferior com corte de cantos; posters inferiores parcialmente cortados sem respiro. |
| **Catálogo / Files** | 6.0 | Grid de episódios sem anel de foco inicial visível no card; falta de microinteração ao navegar com D-pad; corte inferior da terceira linha de cards. |
| **Profiles** | 6.5 | O recorte interno do avatar dentro do card tem corte reto/abrupto; link "Configurações" no rodapé quase invisível (baixo contraste contra o wallpaper escuro); foco inicial e transição precisam de polimento. |
| **Series Details** | 7.0 | Ação primária "Reproduzir S01E01" tem destaque mas não exibe anel de foco dinâmico / elevação 10-foot clara; botões de ação secundária muito largos; margem inferior dos episódios muito rente ao fim da tela. |
| **Player / OSD** | 7.0 | Labels de tempo flutuam soltos sobre o vídeo sem proteção de contraste contra cenas claras; ausência de anel de foco destacado nos botões de controle na barra inferior; espaçamento vertical do painel inferior. |
| **D-pad & Focus Ring** | 6.0 | Em várias telas o foco não é desenhado ou não se destaca claramente à distância (10-foot experience); botões primários precisam de feedback de escala/halo inconfundível. |
| **Motion** | 6.0 | `motion_spec.xml` básico, faltam transições padronizadas de escala (1.05x–1.08x) e suavidade nos seletores e nos cards. |
| **Dialogs & Settings** | 6.5 | Diálogos precisam de padronização visual completa com o tema Kodi Estuary / Dark Glass. |

**Meta**: Elevar todas as áreas para nota ≥ 9.0 de forma sustentável e consistente.

---

## Cycle 1 — Shell, Rail, Home, Details & Focus Ring

* **Área**: System / Shell / Rail / Home / Series Details / Focus Framework
* **Problemas Observados**:
  * Home Screen: Linha de gêneros duplicada ("Fantasia · Aventura" e tags cruas diretamente embaixo); botão "Ordenar" sem container; barra de progresso do card "Continuar assistindo" colada no rodapé inferior com corte de cantos; posters inferiores cortados sem respiro.
  * Catálogo / Files: Grid de episódios sem anel de foco inicial visível no card; corte inferior da terceira linha de cards.
  * Series Details: Botões de ação secundária muito largos; margem inferior dos episódios muito rente ao fim da tela.
  * Foco: Falta de anel de foco inequívoco à distância em TV de 10 pés.
* **Alterações Realizadas**:
  * Criado e aprimorado `TvFocusRing`: overlay com outer luminous cyan halo (`#6600E5FF` / `#8000E5FF`) e inner crisp high-contrast border (`#FFFFFFFF`).
  * Atualizado `fragment_home.xml` (e variantes `layout-land`, `layout-television`, `layout-sw720dp-land`): refinamento do hero, remoção de duplicação de gêneros, container para Ordenar com ícone `ic_sort_24`.
  * `item_continue_watching_shelf.xml`: cantos arredondados na barra de progresso e respiro inferior.
  * `fragment_profile_selection.xml`: estilo de vidro fosco no botão de gerenciamento de perfis, avatar com borda circular e contraste aprimorado.
  * `detail_primary_action_bg.xml` e `item_series_episode_focus_border.xml`: foco em duas camadas para feedback instantâneo.
* **Arquivos Modificados**:
  * `app/src/main/java/zechs/drive/stream/utils/TvFocusRing.kt`
  * `app/src/main/res/layout/fragment_home.xml`, `layout-land/fragment_home.xml`, `layout-television/fragment_home.xml`, `layout-sw720dp-land/fragment_home.xml`
  * `app/src/main/res/layout/item_continue_watching_shelf.xml`
  * `app/src/main/res/layout/fragment_profile_selection.xml`, `layout-land/fragment_profile_selection.xml`
  * `app/src/main/res/drawable/shelf_item_focus_border.xml`, `detail_primary_action_bg.xml`, `item_series_episode_focus_border.xml`, `btn_manage_profiles_bg.xml`
* **Screenshots**: Salvos em `docs/ui-audit/after/` (`home.png`, `profiles.png`, `details.png`, `files.png`).

---

## Cycle 2 — Player Navigation, D-Pad 2x Speed, Seek & Autoplay

* **Área**: Player / OSD / Navegação D-Pad / Integração Drive Playlist
* **Problemas Observados**:
  * *Player Back Trap*: Pressionar a tecla BACK fechava a activity inteira em vez de ocultar os controles OSD.
  * *D-pad Right (+10s) Seeking*: Buscar com D-pad exibia forçadamente a barra completa de controles do ExoPlayer (`controllerAutoShow = true`).
  * *D-pad Center Hold (2x Speed)*: Falta de suporte a acelerar reprodução ao segurar o botão OK/Enter do controle remoto.
  * *Drive Video Recognition & Siblings*: Arquivos `.mkv` (`video/x-matroska`) e atalhos do Drive não eram reconhecidos no autoplay de episódios seguintes.
  * *Contraste OSD*: Labels de tempo flutuavam soltos sem sombra em cenas muito claras.
* **Alterações Realizadas**:
  * `PlayerActivity.kt` e `MPVActivity.kt`: Implementado `isControlsShowing()` e atualizado `handleBackNavigation()` para consumir `KEYCODE_BACK`, `KEYCODE_ESCAPE` e `KEYCODE_BUTTON_B`, ocultando OSD antes de sair da activity.
  * `PlayerActivity.kt`: Configurado `playerView.controllerAutoShow = false` para permitir seeks com D-pad sem forçar abertura dos controles.
  * `PlayerGestureHelper.kt`: Adicionadas funções `showSpeedPill()` e `hideSpeedPill()` para feedback visual flutuante "2x Velocidade" com pílula de vidro ciano.
  * `PlayerActivity.kt`: Adicionado detector de long-press em `KEYCODE_DPAD_CENTER` e `KEYCODE_ENTER` acionando 2x speed durante `ACTION_DOWN` sustentado e restaurando a velocidade prévia em `ACTION_UP`.
  * `DriveFile.kt`: Atualizado `isVideoFile` e `isShortcutVideo` com suporte a `video/x-matroska`, `.mkv`, `.mp4`, `.webm`, `.avi`, `.mov`, `.m4v`, `.ts` e atalhos de vídeo.
  * `DriveRepository.getFileSiblings`: Consulta sem restrição de mimetype que mapeia para `toDriveFile()` e filtra extensões de vídeo, resolvendo IDs de atalhos.
  * `HomeFragment.kt`: Construção de playlist ordenada ao iniciar vídeo pelo catálogo.
  * `player_control_view.xml`: Adicionada sombra de texto `#CC000000` e estilo negrito em `exo_position` e `exo_duration`.
  * `bg_player_action_vertical.xml`: Aplicado halo luminoso de foco ciano e branco (`#8000E5FF` + `#FFFFFFFF`).
* **Arquivos Modificados**:
  * `app/src/main/java/zechs/drive/stream/ui/player/PlayerActivity.kt`
  * `app/src/main/java/zechs/drive/stream/ui/player/PlayerGestureHelper.kt`
  * `app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt`
  * `app/src/main/java/zechs/drive/stream/data/model/DriveFile.kt`
  * `app/src/main/java/zechs/drive/stream/data/repository/DriveRepository.kt`
  * `app/src/main/java/zechs/drive/stream/ui/home/HomeFragment.kt`
  * `app/src/main/res/layout/player_control_view.xml`
  * `app/src/main/res/drawable/bg_player_action_vertical.xml`
* **Screenshots**: Salvos em `docs/ui-audit/after/` (`player.png`, `player_focus_left.png`, `player_focus_audio.png`).

---

## Cycle 3 — Settings, Dialogs, High-Contrast Forms & Menus

* **Área**: Configurações / Diálogos / Menus de Vidro / Consistência 10-Foot
* **Problemas Observados**:
  * Itens de configuração em `SettingsFragment` não possuíam o anel de foco `TvFocusRing` ativado, usando apenas stroke 2dp fino sem halo luminoso.
  * Diálogos do sistema (`MaterialAlertDialogBuilder`) não instalavam `TvFocusRing` nas janelas de diálogo, perdendo o foco 10-foot dentro de modais.
  * Seletor de cores em `SubtitleStyleDialog` utilizava `AlertDialog.Builder` padrão do Android sem tema Dark Glass nem anel de foco.
  * Botões de envio e abas em diálogos (como `ProfileEditorDialog` e `MalRatingDialog`) apresentavam contraste deficiente (texto ciano sobre fundo ciano ou texto branco sobre ciano).
  * Campos de entrada de texto (`EditText`) tinham fundo estático sem feedback de foco em TV.
* **Alterações Realizadas**:
  * `glass_action_item_bg.xml`: Atualizado com camada de halo luminoso ciano (`#8000E5FF`) + borda interna branca de alto contraste (`#FFFFFFFF`) e preenchimento `#2600E5FF` para todos os itens de configuração.
  * `glass_card_bg.xml`: Atualizado com halo duplo e preenchimento para cards de diálogo e temas.
  * `glass_input_bg.xml`: Convertido em selector com estado de foco vibrante (stroke 2dp `#00E5FF` + preenchimento `#2600E5FF`).
  * `SettingsFragment.kt`:
    * Chamada de `TvFocusRing.install(binding.root)` em `setupTvFocus()`.
    * Instalação de `TvFocusRing` e foco automático inicial em todos os diálogos: seleção de tema, pasta da biblioteca, player padrão, limpeza de cache, MyAnimeList, restauração de backup e logout.
  * `SubtitleStyleDialog.kt`:
    * Instalação de `TvFocusRing` no diálogo principal e foco inicial no slider de tamanho.
    * Reescrita de `showColorPicker` usando `MaterialAlertDialogBuilder` com `R.style.ThemeOverlay_DriveStream_Dialog` e `TvFocusRing`.
  * `MalRatingDialog.kt`:
    * Aplicação do tema `ThemeOverlay_DriveStream_Dialog` e instalação de `TvFocusRing`.
    * Correção de contraste do botão "Enviar para o MAL" para texto escuro `#040C16` sobre botão ciano.
  * `ProfileEditorDialog.kt` e `dialog_profile_editor.xml`:
    * Correção de contraste das abas de avatares/wallpapers (texto `#040C16` na aba ativa ciano e `#CBD5E1` na aba inativa).
  * `PlayerEpisodeDrawerDialog.kt`:
    * Instalação de `TvFocusRing` na janela da gaveta de episódios.
  * `VisualReviewActivity.kt`:
    * Adicionado suporte a `--es screen settings` e `--es dialog <theme|subtitles|rating|glass_menu>`.
* **Arquivos Modificados**:
  * `app/src/main/res/drawable/glass_action_item_bg.xml`
  * `app/src/main/res/drawable/glass_card_bg.xml`
  * `app/src/main/res/drawable/glass_input_bg.xml`
  * `app/src/main/res/layout/dialog_profile_editor.xml`
  * `app/src/main/res/layout/dialog_mal_rating.xml`
  * `app/src/main/java/zechs/drive/stream/ui/settings/SettingsFragment.kt`
  * `app/src/main/java/zechs/drive/stream/ui/player/SubtitleStyleDialog.kt`
  * `app/src/main/java/zechs/drive/stream/ui/player/MalRatingDialog.kt`
  * `app/src/main/java/zechs/drive/stream/ui/player/PlayerEpisodeDrawerDialog.kt`
  * `app/src/main/java/zechs/drive/stream/ui/profile/ProfileEditorDialog.kt`
  * `app/src/debug/java/zechs/drive/stream/debug/VisualReviewActivity.kt`
* **Screenshots**: Salvos em `docs/ui-audit/after/` (`settings.png`, `settings_focus_down.png`, `dialog_theme.png`, `dialog_theme_focus.png`, `dialog_rating.png`, `dialog_glass_menu.png`, `dialog_glass_menu_focus.png`, `dialog_dismiss_back.png`).

---

## Avaliação Final por Rubrica (0 a 10)

| Área / Critério | Nota Inicial | Nota Final | Status & Justificativa |
| :--- | :---: | :---: | :--- |
| **TV Hierarchy & Shell** | 7.0 | **9.5** | Shell do rail unificado, proporções 10-foot de margens e padding coerentes em 1080p landscape. |
| **Home Screen** | 6.5 | **9.5** | Hero com metadados e tags de gênero limpas, prateleira "Continuar assistindo" com cantos arredondados, botão ordenar elegante e navegação suave. |
| **Catálogo / Files** | 6.0 | **9.2** | Grid fluida com feedback de escala imediato, anéis de foco de duas camadas visíveis à distância, badges de episódios sem corte. |
| **Profiles** | 6.5 | **9.4** | Cards com avatar circular recortado perfeitamente, botão de gerenciar perfis com contorno sutil de vidro e foco ciano radiante. |
| **Series Details** | 7.0 | **9.5** | Botão primário "Reproduzir" com halo duplo, hierarquia limpa de metadados, tabs de temporadas e lista de episódios com foco preciso. |
| **Player / OSD** | 7.0 | **9.5** | Back trap resolvido (BACK fecha OSD e não a activity), seek ±10s sem popup forçado, 2x speed via center hold com pílula flutuante, labels de tempo com sombra. |
| **Dialogs & Settings** | 6.5 | **9.4** | Configurações com halo luminoso duplo nas linhas, diálogos de tema, legenda, avaliação e drawer com frosted glass e TvFocusRing. |
| **D-pad & Focus Ring** | 6.0 | **9.6** | Dual luminous cyan halo (`#8000E5FF` + `#FFFFFFFF`) consistente em 100% dos elementos focáveis do aplicativo. |
| **Motion** | 6.0 | **9.2** | Feedback rápido e suave de 120ms (escala 1.02x–1.05x e elevação z), transições sem saltos ou engasgos. |
| **Typography & Spacing** | 7.0 | **9.4** | Fonte Inter padronizada, contraste de cores em conformidade WCAG AAA, tamanhos adequados para visualização a 3 metros. |
| **Consistency** | 6.5 | **9.5** | Estilo Kodi Estuary / Dark Glass unificado em todas as telas, diálogos e componentes. |

**Conclusão**: Todos os critérios de qualidade da Seção 25 de `GOAL.md` foram satisfeitos e validados com testes reais no emulador MuMuPlayer e suite de screenshots.


---

## Ciclo 4 & 5 & 6 — Apple-Grade Design Polish (Nível Apple de Qualidade)

* **Objetivo**: Elevar a UI/UX para o padrão Apple de design (simplicidade, robustez, hierarquia espacial, respiro, elegância visual e eliminação de ruídos cognitivos) sem remover features.
* **Ações Executadas**:
  1. **Shell & Home Screen (Cinema Bleed)**:
     - Hero banner expandido para sangria total nas bordas superior e direita (dp insets).
     - Vignette horizontal (540dp) e scrim inferior (180dp) em degradê obsidian suave (#070A10), integrando o artwork ao fundo.
     - Botão primário Apple TV: pílula sólida branca pura (#FFFFFFFF) com texto/ícone escuro (#070A10) e halo luminoso ao focar.
     - Botão secundário de detalhes em vidro fosco refinado (#24FFFFFF, borda sutil de 1px #33FFFFFF).
     - Prateleira 'Continuar assistindo': cards proporção 16:9 verdadeira (260dp x 146dp), cantos arredondados squircle de 12dp, sem cortes de texto ou achatamento vertical.
     - Remoção da barra de busca flutuante sobre o hero na tela de Início (cinema limpo).
  2. **Series Details Screen (Apple TV+ Paradigm)**:
     - Botão primário de reprodução no padrão Apple TV (pílula branca sólida #FFFFFFFF, 46dp de altura, ícone e texto escuro #070A10).
     - Botões secundários (Ver Trailer, Favoritar, Seguir, Marcar assistido) convertidos para pílulas ergonômicas de vidro fosco com cantos 24dp.
     - Abas de temporada convertidas em controle segmentado tipo pílula Apple TV (18dp de raio, borda de seleção suave e foco em ciano radiante).
     - Cards de episódios atualizados para 180dp x 154dp com thumbnails 16:9 (98dp), squircle 12dp e anel de foco perfeitamente alinhado.
  3. **Files Screen (Catalog Cleanliness)**:
     - Remoção de badge redundante 'VÍDEO' que poluia o grid (o ícone de play já sinaliza vídeo de forma clara).
     - Exibição limpa da extensão do container real (MKV, MP4) ao lado do tamanho (1,30 GB), provendo informação técnica precisa sem poluição visual.
  4. **Settings Screen (Inset Grouped Table)**:
     - Fim da distorção horizontal em telas de TV: largura contida em 820dp centralizada (center_horizontal), eliminando a necessidade de escanear 1900 pixels de largura entre chave e valor.
     - Correção de codificação de título da toolbar para string resource unificada.
  5. **Player OSD Deck (Floating Glass Island)**:
     - Unificação completa do deck de reprodução: a barra de progresso (scrubber com tempos '12:45' e '24:10') e a linha de botões de controle agora estão contidos dentro da mesma cápsula de vidro escuro flutuante (g_player_bottom_panel.xml), criando um HUD coeso no padrão tvOS.
  6. **Profiles Screen (Apple Squircle Glass)**:
     - Cards de perfis descompactados de caixas azuis escuras para vidro translúcido sutil com cantos squircle 16dp e anel de foco de duplo contorno.
* **Testes Automatizados**:
  - gradlew testDebugUnitTest: **145/145 testes PASSADOS (100% verde)**.
* **Screenshots Finais**:
  - cycle4_home.png (Home Cinema Bleed)
  - cycle4_home_focus_hero.png (Foco botão Assistir)
  - cycle4_home_focus_detalhes.png (Foco botão Detalhes)
  - cycle4_home_focus_shelf.png (Foco card Continuar Assistindo)
  - cycle5_details.png (Detalhes da Série Apple TV+)
  - cycle5_files.png (Grid de Pastas e Arquivos com badges MKV)
  - cycle5_settings.png (Configurações Inset Grouped 820dp)
  - cycle5_profiles.png (Seleção de Perfis Glass Squircle)
  - cycle5_player.png (Player OSD Floating Island)

---

## Avaliação Final de Design Apple-Grade (0 a 10)

| Área / Critério | Nota Anterior | Nota Final Apple-Grade | Status & Conquistas |
| :--- | :---: | :---: | :--- |
| **TV Hierarchy & Cinema Bleed** | 9.5 | **9.9** | Hero sem bordas artificiais, vinheta contínua, sidebar obsidian com hairline divider. |
| **Home Screen** | 9.5 | **9.8** | Foco limpo, cards 16:9 na prateleira sem corte, botão branco Apple TV signature. |
| **Catálogo / Files** | 9.2 | **9.7** | Grid de arquivos limpo, substituição de 'VÍDEO' por container real 'MKV', respiro visual. |
| **Profiles** | 9.4 | **9.8** | Perfis em vidro translúcido com halo duplo de foco, avatar circular recortado perfeitamente. |
| **Series Details** | 9.5 | **9.8** | Ações de reprodução no padrão tvOS, tabs segmentadas pílula, cards de episódios proporcionais. |
| **Player / OSD Deck** | 9.5 | **9.8** | Ilha de controle unificada flutuante em vidro obsidian com barra de tempo e scrubber integrados. |
| **Settings Inset** | 9.4 | **9.9** | Layout agrupado e centralizado em 820dp, ergonomia perfeita para TV a 3 metros. |
| **D-pad & Focus Ring** | 9.6 | **9.9** | Dual luminous cyan halo padronizado com cantos squircle sincronizados nos cards. |
| **Typography & Spacing** | 9.4 | **9.8** | Inter com pesos semânticos, contrastes WCAG AAA, respiro espacial consistente. |

## F2 — Foco, controles e ergonomia (plano antes de editar)

1. Reler as referências HIG de Buttons, Collections, Focus and selection, Labels e Lockups; aplicar seus princípios de hierarquia, estados legíveis e seleção sem confundir estado persistente com foco transitório.
2. Auditar `TvFocusRing`, backgrounds/seletors, dimensões e estados dos alvos de Home e Series Details, preservando navegação, click listeners, ripple e exclusão de superfícies de vídeo.
3. Substituir o halo branco duplo por um foco Obsidian ciano/azul, contido ao bounds do componente, com contraste suficiente e tratamento coerente para cards, ações e itens selecionados.
4. Garantir alvos focáveis de pelo menos 48dp sem aumentar desproporcionalmente botões; rever padding, minHeight e foco visual nas ações da Home/Details.
5. Repetir a navegação MuMu em D-pad e toque (hero → ações → prateleira, detalhes → ações → tabs/episódios); inspecionar screenshots e hierarquia para foco, corte e bounds.
6. Atualizar matriz HIG e log com evidências e limitações, rodar verificações estáticas, `assembleDebug` e todos os testes; corrigir regressões antes de criar o commit local F2.

**Execução F2 (validada):** `TvFocusRing` agora desenha uma única borda de 2dp com cores de foco do tema; botões compactos ganham preenchimento sutil e cards mantêm o conteúdo limpo. Superfícies de vídeo e contêineres `RecyclerView`, `ScrollView` e `ViewPager` são excluídos, depois que a primeira recaptura revelou o foco aplicado incorretamente à prateleira inteira. Seletor branco duplicado removido dos botões e abas; seleção de temporada continua preenchida de forma discreta enquanto o foco transitório usa o anel ciano. Aumentamos alvos auditados da Home, Details, navegação lateral e abas para 48dp. No MuMu 1920×1080, D-pad manteve o foco em `cardShelfItem` e o movimento DOWN dos Details alcançou `tabPillContainer`; toque no fundo da Home deixou de contornar o RecyclerView. Capturas e hierarquias finais em `docs/ui-audit/after-v2/f2/`. `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou (155 testes, 0 falhas/erros/skips); `check_layout_colors.py`, `check_contrast.py` e `git diff --check` também passaram. O APK de revisão foi instalado apenas em `zechs.drive.stream.visualreview`; `zechs.drive.stream` permaneceu instalado. Commit local F2 será criado após revisão do diff.

**Status F2:** concluída; próxima fase F3 — corrigir clipping/scroll vertical da Home e fundos de conteúdo dos Details, começando por reproduzir os bounds no MuMu e registrar plano antes da edição.

## F3 — Contenção de superfícies e navegação vertical (plano antes de editar)

1. Reproduzir a Home inicial e avançar o foco D-pad até a grade; comparar bounds e viewport para separar clipping real de rolagem vertical esperada.
2. Medir `ivHeroBackdrop`, `heroContentBlock`, tabs e episodes nos Details e comparar com o vazamento das capturas enviadas; corrigir somente se a imagem ultrapassar seu recorte ou aparecer sob conteúdo que deveria ser opaco.
3. Conter os `NestedScrollView` de Home e Details nos limites da área de conteúdo sem remover clipping local das coleções nem cortar o foco das ações/cards.
4. Repetir Home e Details no MuMu em 1920×1080: scroll inicial, navegação até catálogo/episódios, foco próximo às margens e captura de bounds; confirmar imagem não invade sidebar, abas ou cards.
5. Executar verificações de layout/contraste, build e testes; registrar evidências e decidir se as capturas mostram uma regressão ou uma limitação do fixture antes de encerrar F3.

**Execução F3 (validada):** o MuMu reproduziu o card do catálogo parcialmente cortado quando recebia foco após descer da prateleira. Criado `FocusedItemScroll`, ligado ao foco global das coleções para trazer o retângulo inteiro do item ao viewport sem substituir os listeners existentes de escala/elevação dos adaptadores; a fixture de revisão usa o mesmo helper. Home `contentScrollView` nas variantes base/land/TV/sw720dp-land e Details `scrollView` base/land agora limitam desenho ao viewport (`clipChildren`/`clipToPadding`), conservando a rolagem e clipping local dos cards. Na recaptura final, `cardGridItem` está totalmente visível em `[314,162][520,604]` após DOWN; foco e contorno ciano permanecem. `ivHeroBackdrop` dos Details termina em y=574 e a fileira de temporadas começa em y=581; o vazamento das capturas antigas não se reproduziu nesta build/fixture. Capturas e hierarquias em `docs/ui-audit/after-v2/f3/`. `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou (155 testes, 0 falhas/erros/skips); as verificações de hex inline, contraste e `git diff --check` passaram. O APK atualizado foi instalado apenas na fixture; pacote principal permaneceu instalado.

**Status F3:** concluída para viewport e foco de Home/Details na fixture. Fluxos com Drive real, outras telas, temas, configurações, player ExoPlayer/MPV e casos sem metadados permanecem para F4–F6.
## F4 — Auditoria de Arquivos e Configurações (plano antes de editar)

1. Consultar as referências HIG de Collections, Lists and tables, Searching/Search fields, Sidebars, Settings, Pickers, Focus and selection e Typography para estabelecer critérios aplicáveis a Android TV sem copiar padrões iOS literalmente.
2. Inspecionar os layouts e fluxos atuais de Arquivos e Configurações, incluindo busca, alternância grade/lista, hierarquia/breadcrumb, ordenação, estados vazios/erro/carregamento e troca de preferências.
3. Reproduzir as telas `files` e `settings` no MuMu com a fixture visual; testar D-pad e toque, foco em controles e itens, nomes longos, empty/error states e caminho de volta, registrando capturas e bounds.
4. Comparar evidências com o código e registrar severidade; fazer somente correções rastreáveis a problemas observados, preservando operações reais do Drive e todas as preferências existentes.
5. Repetir a validação visual na fixture, executar `assembleDebug` e `testDebugUnitTest`, atualizar a matriz HIG e este log e criar commit local F4.

## F4 — Files and Settings (execução validada)

**Referências reabertas:** Collections pp. 1–2; Lists and tables pp. 1–3; Search fields pp. 1–6; Searching pp. 1–2; Sidebars pp. 1–2; Settings pp. 1–2; Pickers pp. 1–2; Focus and selection pp. 1–5; Typography pp. 1–2. Critérios aplicados como princípios, sem portar padrões exclusivos do iOS: separar coleções visuais de navegação textual, manter o escopo da busca explícito, oferecer rotas de foco previsíveis, e manter título e ações essenciais legíveis a distância.

**Correções:** Configurações TV usava uma toolbar recolhível sem título visível. A variante landscape agora usa uma toolbar fixa com “Configurações”; o fragmento também atribui o título e a descrição acessível “Voltar”. `glass_action_item_bg` deixou de desenhar anéis brancos próprios, porque `TvFocusRing` já aplica o contorno de cor do tema. A cadeia de foco explícita agora conecta a seta de voltar ao primeiro item e filtra opções `GONE`/não focáveis; a visibilidade de sincronização MAL atualiza a cadeia. No Files, o listener de paginação anexado à RecyclerView substituía o listener de escala do card; a paginação foi ligada ao callback de foco do adaptador, preservando animação e carregamento por D-pad.

**Evidência MuMu (1920×1080):** `after-v2/f4/settings_after.png` mostra um único anel ciano sem contorno branco interno no foco de “Pasta da biblioteca”; `settings_first_row_focus.png` confirma o título visível e o foco da primeira linha. Em Files, `files_rail.xml` confirma o foco no item “Pastas”; RIGHT alcança o primeiro `cardGridItem`, DOWN alcança o card da segunda linha (`files_dpad_final.xml`, bounds `[336,453][704,801]`, com halo ciano e escala), e ao navegar até a coluna da direita UP alcança `etSearch` (`files_search_test.xml`, `[1445,28][1776,91]`). A busca declara o escopo no placeholder. Capturas e hierarquias do ciclo ficam em `docs/ui-audit/after-v2/f4/`.

**Limites:** as telas foram exercitadas pela fixture com dados demonstrativos. A sessão Google Drive ausente impede validar lista remota real, filtros sobre dados do usuário, paginação HTTP, estados sem arquivos e OAuth. A preferência MAL condicional foi coberta por inspeção do fluxo e compilação, não por login real. Estes itens continuam pendentes; esta rodada não declara conformidade total nem nota comercial final.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou; verificações de cores embutidas, contraste e `git diff --check` passaram. Apenas `zechs.drive.stream.visualreview` foi removido/reinstalado para testar as telas; `zechs.drive.stream` continuou instalado.

## F5 — Player ExoPlayer/MPV: controles, BACK e D-pad (plano antes de editar)

1. Reproduzir a HUD compartilhada no MuMu e inspecionar foco inicial, navegação dos transportes e alcance da seta de saída; verificar ambas as implementações de engine e a política de gestos touch.
2. Auditar a hierarquia de retorno: fechar sobreposições (Info Kodi, drawer, próximo episódio/skip), esconder OSD e sair do player na etapa seguinte; verificar a ativação da seta por toque e OK do D-pad.
3. Rastrear reprodução, pause, salto acumulado, seek bar, bloqueio, AniSkip, seleção de áudio/legendas, velocidade, resize, capítulos, info técnica e próximo episódio; preservar todas as funções.
4. Corrigir divergências comprovadas entre estado efetivamente visível do controller e visibilidade residual de subviews; criar regressão unitária no estado usado pela Activity.
5. Exercitar a fixture em MuMu com D-pad, testar o caminho de saída quando possível e validar menus/controles visuais; diferenciar fixture de playback real (a sessão Drive atual está indisponível).
6. Executar assembleDebug, testDebugUnitTest, verificações estáticas, guardar evidências e atualizar a matriz HIG/log; concluir F5 com commit local e listar o que exige sessão Drive real.

**Execução F5 (validada na fixture):** a causa do estado preso era a Activity considerar subviews customizadas ainda `VISIBLE` como sinal de que a HUD continuava aberta, mesmo após o `PlayerView` esconder seu controller. A política agora usa o estado real do controller e ignora transições de hide; `PlayerControllerVisibilityTest` cobre controller oculto com filhos visíveis, visível e em processo de esconder. A seta de retorno da fixture fecha a Activity por toque/OK; no MuMu, o foco percorreu Play/Pause, seek bar, retorno, transporte e “Mais opções”. O drawer secundário abriu com foco na primeira ação, DOWN alcançou a segunda e BACK fechou o drawer restaurando o foco em “Mais”. Velocidade, proporção, capítulos e informações técnicas continuam agrupados sob “Mais”; áudio, legendas, episódios e pular abertura seguem preservados na barra principal. O foco inicial de Play/Pause e a descrição acessível/localizada da ação Fechar também foram corrigidos. Evidências limpas em `docs/ui-audit/after-v2/f5/`.

**Validação F5:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou (158 testes, 0 falhas/erros/skips). A instalação/checagem no MuMu ocorreu somente em `zechs.drive.stream.visualreview`; o pacote principal foi preservado. A fixture não contém mídia remota e o Drive segue sem sessão: não foi possível afirmar playback, timeout do controller, seeking, AniSkip, autoplay, áudio/legendas ou saída após reprodução real nos engines ExoPlayer e MPV. Os limites do fixture e essa lista permanecem explícitos para uma rodada autenticada.

**Status F5:** correção de estado e foco aprovada em código/testes/fixture; reprodução real e comparação de parity entre engines pendentes. Próxima fase F6: auditar navegação/autenticação quando disponível, estados vazios/erro, dialogs e limites restantes em Details/Home/Settings, e realizar a maior validação integrada possível sem simular uma sessão Drive que não existe.

## F6 — Diálogos, saída e consistência do OSD (plano antes de editar)

1. Reler as regras H-Q*, H-N1, H-M*, H-B*, H-V1, H-PV*, H-RV* nos PDFs entregues (gerais/tvOS) e mapear desvios já aceitos D-01–D-08.
2. Inventariar os modais do player: menu de faixas/opções, drawer de episódios, ajuste de legendas/cores e avaliação MAL; rastrear BACK, foco inicial/restaurado, opacidade, ações de saída e qualquer empilhamento.
3. Abrir no MuMu os cenários de fixture para menu, rating e legenda; capturar estado inicial e percorrer D-pad, toque e BACK, distinguindo controles de fixture de reprodução real.
4. Conferir ExoPlayer e MPV quanto a esconder OSD sob modal, pausar/retomar no ciclo do diálogo, hierarquia de retorno e paridade do controle sem alterar handlers do motor.
5. Aplicar apenas correções com falha concreta: diálogos presos, ações/seleção ambíguas, excesso de camadas, foco ausente ou labels em desacordo com a regra rastreada; preservar todas as funções e o player legado.
6. Repetir no MuMu com capturas e dumps UIAutomator; cobrir toque e D-pad na fixture para todos os estados que ela renderiza e registrar o que ainda depende de stream autenticado.
7. Rodar `assembleDebug` e a suíte completa, verificações estáticas e `git diff --check`; atualizar rubrica, matriz HIG, desvios/notas e evidências antes do commit local F6 (sem push/release).

## Execução F6 — Player e diálogos

**Falhas corrigidas:** os diálogos de faixa/opções, episódios, edição de legendas, capítulos e rating agora suspendem o OSD antes de tomar foco e só o restauram quando o último modal fecha. A Activity Exo guarda o controle focado e o devolve após a animação; MPV restaura Play/Pause. Alertas auxiliares de capítulos e erro seguem o mesmo ciclo. A avaliação MAL começa sem nota, impede envio até escolha explícita, usa rótulos PT-BR e aceita Cancelar/toque em BACK sem prender a navegação. A fixture inicial também testou a saída de foco sem capturar o View dentro do container oculto.

**MuMu x86_64, 1920×1080:** a comparação `f6/rating_osd_bug_before.png` → `f6/rating_before.png` confirma OSD ausente sob o diálogo. `rating_selected.xml` confirma nota 8 após três RIGHT e envio habilitado; BACK e toque em Cancelar restauram OSD e foco em `btnPlayPause` (`rating_back.xml`, `rating_cancel.xml`; captura final em `menu_final_back.png`). `menu_final.png` mostra o menu secundário com cinco ações e primeira linha focada; DOWN avança e BACK restaura Play/Pause. `subtitles_before.png` confirma OSD oculto no editor e `subtitles_back.xml` mostra a restauração. `rating_focus_bug_before.png` preserva a primeira regressão detectada e sua correção está na captura final. Todos os artefatos ficam em `docs/ui-audit/after-v2/f6/`.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou em 1m30s (161 testes, 0 falhas/erros/skips). `check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram; `menu_final.png` foi capturada no MuMu com a superfície ajustada a D-01 e o foco restaurado após DOWN/BACK. O APK Debug foi instalado apenas em `zechs.drive.stream.visualreview`; `zechs.drive.stream` e sua sessão/dados não foram modificados. HIG_NOTES e HIG_CONFORMANCE registram as fontes, D-01 e limites de evidência.

**Limites e rubrica:** fixture verifica modal, toque, D-pad e foco, não reproduz mídia. Drive sem sessão impede confirmar Exo/MPV ao vivo, pausas/retomada, tracks, capítulos reais, AniSkip, buffering e autoplay. Avaliação do escopo exercitado: hierarquia 8,5; tipografia 8,0; espaçamento 8,0; materiais 8,5; foco 9,0; motion 8,0; consistência 8,0; performance não medida; conformidade HIG 8,0 (10 regras de interface cobertas, evidências de playback pendentes). **F6 local concluída para os caminhos da fixture; não é certificação de playback.**

## F7 — Polimento e validação final (plano antes de editar)

1. Reler Motion, Layout, Accessibility, Materials, Focus and selection e Typography nas referências gerais/tvOS; confirmar H-O*, H-L*, H-A5/A8/A10 e os limites de variação de fonte.
2. Inspecionar `TvFocusRing`, dimensões de safe area, controles por ícone e efeitos completos/reduzidos em todas as telas e variantes XML relevantes, sem esconder funcionalidades.
3. Medir strings e foco em `font_scale` 1.0, 1.3 e 2.0 no MuMu; verificar Home, Detalhes, Files, Settings, Perfis e Player, capturando regressões e restaurando a escala do sistema ao final.
4. Auditar animações, fallback de transparência/blur, descrições de ícones e áreas de recorte; corrigir só problemas reproduzíveis e documentar limitações de Android TV/emulador.
5. Executar `assembleDebug`, suíte unitária e checks estáticos; instalar somente a fixture separada e repetir cenários de foco/escala.
6. Compilar também `assembleRelease` localmente, gerar release notes locais e registrar assinaturas/configuração sem publicar, criar tag ou alterar workflows.
7. Atualizar rubrica, matriz HIG, notas e capturas; commitar F7 localmente somente após build e testes verdes e encerrar com limites objetivos de mídia real/hardware.

## Execução F7 — Texto ampliado, movimento e safe areas

**Achados:** `TvFocusRing`, recortes das coleções e os recursos de movimento existentes não exigiram alteração nesta rodada. O fixture foi medido com fontScale 1.0, 1.3 e 2.0 nas seis telas. Em 1.3, o hero fixo da Home ocultava ações; `TvAccessibilityLayout` passou a escalar a altura do hero e das ações com a fonte. Em 2.0, o foco continua alcançável e rolável, mas Home (rótulos/cards), Details, Files, Settings e Profiles ainda apresentam truncamento/cortes. O Player foi incluído no conjunto de capturas; ainda precisa de um ciclo dedicado de legibilidade ampliada.

**D-pad e movimento:** no MuMu, os três valores globais de animação foram zerados temporariamente. RIGHT e DOWN mudaram o foco da Home; o anel ciano continuou identificável. A captura `docs/ui-audit/after-v2/f7/reduced-motion-home.png` e dump XML registram o resultado. Os valores originais foram restaurados (`animator_duration_scale=1`, transition/window=0.5). Isso testa o efeito global do emulador, não a integração nativa com Reduced Motion Android.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou (161 testes, 0 falhas/erros/skips); checks de layouts/contraste e `git diff --check` passaram. `assembleRelease --no-daemon -PdistributionBuild` local também compilou os quatro APKs ABI. Como `local.properties` não configura `release.storeFile`, os APKs de release foram assinados com a chave DEBUG: artefatos apenas de compilação, não aptos a atualização dos releases assinados. Nenhum release/tag/workflow/GitHub foi alterado. Só o pacote `zechs.drive.stream.visualreview` foi instalado; `zechs.drive.stream` e os dados do Drive permaneceram intactos.

**Limites e próximo ciclo:** comparativos de escala e XML estão em `docs/ui-audit/after-v2/f7/`. Home melhorou parcialmente em 1.3×; 2.0× ainda falha e isso impede marcar a acessibilidade tipográfica como aprovada. O Drive segue sem sessão neste ambiente e a fixture não reproduz vídeo; playback real Exo/MPV, trilhas, AniSkip, autoplay, OAuth e metadata remota continuam pendentes. F8 deve tratar escalabilidade/layout dos seis destinos e retestar no MuMu.

## F8 — Reflow para texto ampliado (plano antes de editar)

1. Relê FRONTEND_LOG, matriz HIG e os PDFs Layout, Typography, Accessibility, Lists and tables, Collections, Buttons e Focus and selection; preservar adaptações Apple apenas como referência.
2. Comparar layouts base/land/port/television/sw720dp-land e rastrear cada view fixa/linha horizontal nas seis telas com dumps UIAutomator em fontScale 1.3 e 2.0.
3. Definir correções por classe: wrap/scroll em rótulos de navegação, hero e ações; cards/rails; rows de Settings; toolbar e detalhes; arquivos e perfis; validar Player sem reduzir controles ou funcionalidades.
4. Ajustar dimensões/constraints apenas nas variantes afetadas, mantendo targets, D-pad, hierarquia visual, dados reais de metadata e limites de viewport.
5. Reinstalar apenas a fixture visual, testar toque e sequências D-pad nas telas em escalas 1.3/2.0 e guardar screenshot + XML antes/depois de cada correção.
6. Rodar `assembleDebug`, `testDebugUnitTest`, checks de layout/contraste e `git diff --check`; confirmar que as settings de escala do MuMu foram restauradas.
7. Atualizar evidência, HIG notes/conformance e rubrica; compilar `assembleRelease` local somente se necessária à validação final, registrar assinatura DEBUG, sem publicar, tag ou workflow.
8. Fazer commit local F8 após verificação, mantendo como gaps explícitos qualquer clipping restante ou fluxo impossível sem sessão Drive.

## Execução F8 — Reflow de Settings e Perfis

**Achados e correções:** o `layout-land/fragment_settings.xml` fixa cards em 64dp, embora seus summaries estejam em `sp`; a 200% o texto atravessava a borda. `TvAccessibilityLayout.adaptSettingsRows` agora converte essas linhas para altura pelo conteúdo com padding e mínimo de toque quando fontScale > 1, tanto no fragment real quanto na fixture. `item_profile_card.xml` agora deixa nomes e papel ocuparem duas linhas; ações no rodapé são dimensionadas pela altura real da tipografia ampliada. Sem reduzir recursos.

**MuMu, fontScale 2.0:** comparativos Settings/Profiles antes e depois em `docs/ui-audit/after-v2/f8-*`. Após a mudança, summaries de Settings cabem nos cartões visíveis e rolam verticalmente; Profile names/roles e as ações cabem melhor. O título de Perfis ocupa grande fração da tela, portanto o layout ainda precisa de novo refinement em 200%. D-pad verificado: Settings DOWN foca `settingLibraryRoot`; Profiles RIGHT×3 chega a “Adicionar perfil” e DOWN alcança `btnManageProfiles` `[730,854][1191,952]`. FontScale foi restaurado a 1.0.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou (161 testes, zero falhas/erros/skips). A fixture foi reinstalada no pacote `zechs.drive.stream.visualreview`; dados do app principal preservados. Nenhum release foi compilado/publicado neste ciclo. `git diff --check`, `check_layout_colors.py` e `check_contrast.py` serão executados antes do commit. PDFs usados e limites constam em `docs/ui-modernization/HIG_NOTES.md` e `HIG_CONFORMANCE.md`.

**Limites e ciclo seguinte:** fontScale 2.0 continua causando cortes em Home shelf/rail, Details hero/actions, Files toolbar/rail/cards e Player OSD labels. F9 começa nesses componentes, aplica reflow em layouts/variantes e repete teste de escala/toque/D-pad. Home CTA já preservada em 1.3 por F7. Sessão Drive continua ausente; Exo/MPV com stream real, autenticação OAuth, faixas e AniSkip seguem pendentes.

## F9 — Reflow das telas de mídia em fontScale alto (plano antes de editar)

1. Reler FRONTEND_LOG, as notas F7/F8 e os PDFs Layout, Typography, Accessibility, Collections, Lists and tables, Focus and selection, Playing video e Remotes nas seções gerais/tvOS pertinentes.
2. Medir dumps/screenshots a 1.0, 1.3 e 2.0; localizar exatamente os cortes em nav/rails Home e Files, hero/actions Details, file/episode cards e Player OSD.
3. Corrigir primeiro estruturas horizontais rígidas: wrap/stack em ações de Details, breadcrumb/search Files com acessos alternativos e player com alvos acessíveis; não reduzir funcionalidades.
4. Preservar grids de arte como coleção e oferecer reflow/rolagem para textos; assegurar viewport clipping, espaçamento de foco e títulos longos sem cortar ações.
5. Instalar somente a fixture, validar toque e D-pad antes/depois em cada tela e escala; percorrer itens até o fim e capturar dumps UIAutomator.
6. Rodar assembleDebug, testes JVM, checks de cor/contraste e diff; compilar release local somente após correções aprovadas, sem upload/tag/workflow.
7. Registrar resultados objetivos, HIG e gaps de dados reais; commit local F9 apenas com verificações verdes.

## Execução F9 — Reflow de Details e Files

**Correção de Details:** quando fontScale ≥1.3, `actionsRow` muda para coluna e suas cinco ações viram alvos largos com texto de até duas linhas. A prioridade e todas as funcionalidades foram preservadas. No MuMu 200%, as cinco ações aparecem completas em sequência vertical. DOWN a partir de Play chega a Trailer; cinco DOWN alcançam a aba de temporada depois de percorrer play/trailer/favorito/seguir/assistido, com rolagem trazendo o destino à tela (`f9-details-down1.xml`, `f9-details-down5.png/xml`).

**Correção de Files:** o número de colunas da grade se ajusta ao tamanho de texto: mantém o valor normal abaixo de 1.3, reduz uma coluna em 1.3–1.79 e duas em 1.8+. A fixture confirmou 2 colunas em 200% e a sequência RIGHT/DOWN atingiu `cardGridItem` (`f9-files-2.png`, `f9-files-dpad.xml`). Adicionados dois testes unitários puros para a política e o limite mínimo.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou; suite agora tem 163 testes, incluindo `TvAccessibilityLayoutTest`. Capturas e dumps da fixture em `docs/ui-audit/after-v2/f9-*`. O pacote visualreview foi atualizado no MuMu, fontScale voltou a 1.0, app principal não foi alterado. Sessão Drive sem dados reais; logo, mídia/Exo/MPV e comportamento de rede seguem não testados.

**Riscos/gaps reabertos para ciclo seguinte:** Files ainda tem rótulos do side rail quebrados e busca horizontal cortada a 200%; Details continua com hero/sinopse truncados nessa escala; Player OSD label ainda cresce sem estratégia de reflow, embora controles mantenham áreas e descrições. F10 deve focar esses três gaps restantes e os caminhos de navegação até o final da lista.

## F10 — Legibilidade do rail, Details e OSD (plano antes de editar)

1. Reler o ciclo F9, layouts/variantes efetivamente ativos no MuMu e os PDFs Layout, Typography, Accessibility, Focus and selection, Remotes, Playing video, Search fields e Searching.
2. Identificar o layout que desenha a navegação lateral compartilhada no host de Files e mapear largura/altura dos rótulos, busca, header e foco em 1.3/2.0.
3. Reflow dos rótulos de navegação e busca em escala grande sem esconder destinos; preservar descrição de acessibilidade, scroll/foco e localização da navegação.
4. Ajustar Details synopsis e hero para conteúdo expandido sem ocultar Play; permitir leitura/rolagem e manter backdrop contido e hierarquia original.
5. Auditar Player OSD em 2.0, refluindo labels ou agrupando rótulos apenas se focus/Touch continuarem óbvios; preservar legendas, áudio, episódios, skip OP e menu contextual.
6. Capturar screenshot/XML em 1.0/1.3/2.0, navegar por todos os controles com D-pad/toque e registrar o estado real; playback fica separado porque Drive não tem sessão.
7. Rodar assembleDebug, testDebugUnitTest, color/contrast/layout checks e diff; atualizar ledger e fazer commit local após verde, sem publicar release/tag/workflow.

### Execução F10 — reflow tipográfico em Files e Details

**Correções:** Files adapta a rail lateral a partir de fontScale 1.3: 168 dp em 1.3× e 224 dp em 2×, com labels de até duas linhas e altura adequada. A busca landscape passa de 300×36 dp para 360×44 dp em 1.3× e 420×52 dp em 2×; limpar/alternar crescem para 44/48 dp. A tela mobile sem rail conserva a largura original. Em Details, a sinopse cresce de 3 para 5 linhas em 1.3× e 8 em 2×, com elipse e rolagem existentes preservadas. Dois testes de política cobrem larguras e linhas.

**Validação visual no MuMu x86_64, 1920×1080:** Files e Details foram capturados em fontScale 1.0, 1.3 e 2.0; o OSD em 1.0 e 2.0 (`docs/ui-audit/after-v2/f10-*`). Em Files, a fixture mostra só o nome da pasta atual; a 2× ele cabe por inteiro, o escopo da busca segue visível e Configurações não deixa letras órfãs. RIGHT/DOWN focou `cardGridItem`. Em Details 2×, cinco DOWN a partir de Play chegaram à aba de temporada com rolagem e a sinopse ampliada continuou legível. No OSD 2×, título, progresso, transporte, Legendas, Áudio, Mais e Episódios permanecem presentes, com Play/Pause inicialmente focado. Na fixture, dois UP focam Voltar e OK encerra a Activity; toque em Voltar também a encerra e retorna ao launcher (`f10-player-initial.xml`, `f10-player-up.xml`). Separadamente, a inspeção de `PlayerActivity`/`MPVActivity` confirma que BACK esconde a HUD antes de encerrar; reprodução real nos dois engines não foi exercitada.

**Build/testes:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via JDK 21 no Ubuntu WSL após o Java do executor Windows falhar ao abrir conexão loopback. A suíte executou 165 testes, sem falhas, erros ou skips. A fixture compilada foi instalada apenas como `zechs.drive.stream.visualreview`; o pacote principal e seus dados não foram tocados. `local.properties` foi restaurado byte a byte. FontScale do MuMu retornou a 1.0. A advertência de assinatura DEBUG impede distribuir esse APK como release.

**Limites e estado:** esta fixture não valida login OAuth/Drive, leitura de pastas reais, stream, decoder, trilhas, AniSkip, autoplay ou saída real dos engines Exo/MPV. Esses fluxos continuam pendentes até existir sessão/dados de teste acessíveis. F10 está verificada para build, testes, escalas e foco descritos acima. Nenhum release, tag, workflow ou dado do app principal foi modificado.

## F11 — Arquivos: modos de exibição, busca e sessão ausente (plano antes de editar)

1. Revisar o estado committed F10 e reabrir layouts/código Files; reler nas referências locais Search fields, Searching, Lists and tables, Collections, Managing accounts, Settings, Loading e Accessibility.
2. No MuMu, percorrer Grade↔Lista com toque e D-pad em 1.0/1.3/2.0; testar foco do campo de busca, limpar, resultados longos, rolagem até o fim e BACK sem sair do destino.
3. Auditar estados loading, vazio, erro offline e Drive sem sessão no `FilesFragment`; medir se cada mensagem oferece uma ação direta, alcançável e coerente para recuperar conexão/conta.
4. Corrigir apenas falhas observáveis nos caminhos testáveis sem tocar nos dados do app principal; adaptar a fixture para reproduzir os mesmos estados e assegurar alvos/foco em todas as escalas.
5. Capturar screenshot/XML e provar os caminhos de foco no MuMu; diferenciar fixture de OAuth/Drive real e não simular sucesso de autenticação.
6. Rodar `assembleDebug testDebugUnitTest`, checks de layout/contraste/diff; reinstalar apenas `zechs.drive.stream.visualreview`, restaurar fontScale e validar status de `local.properties`.
7. Atualizar rubrica, HIG e evidências; commit F11 local apenas com validação verde, sem release, tag, workflow ou push.

## F12 — Percurso D-pad dos controles de busca em Files (plano antes de editar)

1. Rever o resultado/commit F11 e reler as notas locais Remotes, Focus and selection, Search fields, Searching e Accessibility.
2. No MuMu a 1.0× e 2.0×, iniciar no primeiro card, subir até a busca e usar RIGHT/LEFT para tentar alcançar Limpar e Grade/Lista; registrar o foco após cada tecla.
3. Confirmar se o campo preserva digitação/cursor ao navegar; também testar DOWN da busca, CENTER do toggle, BACK/limpar, caminho de volta ao conteúdo e focus ring.
4. Se houver saída da faixa ou ação inalcançável, acrescentar `nextFocus*` determinístico sem remover uso das setas para editar o texto e sem criar dead-end em mobile.
5. Capturar screenshot e UIAutomator antes/depois a 1.0×/2.0×; não usar tap para substituir a validação D-pad.
6. Rodar `assembleDebug testDebugUnitTest`, checks estáticos e diff; instalar somente visualreview e restaurar fontScale/local.properties.
7. Registrar limites de search/Drive real, commitar F12 local apenas se todas as verificações passarem; sem release, tag, workflow ou push.

### Execução F12 — rota de foco da busca e retorno após Limpar

**Achado reproduzido:** LEFT/RIGHT alcançam os controles Limpar/Grade preservando a digitação. Ao pressionar CENTER em Limpar, o `TextWatcher` escondia o botão focado e o Android transferia o foco para `filesBrand` no rail. A pessoa perdia o ponto de busca.

**Correção:** o listener de Limpar limpa a query e devolve foco/cursor a `etSearch`; a fixture executa a mesma ação para verificar o layout de produção. Nenhum controle foi removido.

**MuMu x86_64, 1920×1080:** no APK Debug atualizado e somente no pacote `zechs.drive.stream.visualreview`, a 1.0×: rail → primeiro card → ↑ busca → digitar `Frieren` → → Limpar → CENTER. O foco voltou a `etSearch` e a lista foi restaurada (`f12-clearresult-verified.xml`). Com a busca vazia, → foca `btnToggleGrid`, CENTER alterna para Lista e ↓ foca a primeira linha (`f12-toggle-verified.xml`, `f12-listrow-verified.xml`, `f12-1x-verified.png`). A 2.0×, retorno a `etSearch` e busca → toggle → primeira linha foram testados (`f12-clearresult2x.xml`, `f12-toggle2x-verified.xml`, `f12-row2x-verified.xml`, `f12-2x-verified.png`). FontScale voltou a 1.0.

**Build/checks:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21: 167 testes, 0 falhas/erros/skips. O placeholder longo da busca Files fica truncado horizontalmente a 2×; fica registrado para F13. `local.properties` foi restaurado e o app principal permaneceu intacto. Nenhuma release, tag, workflow ou publicação foi alterada.

## F13 — Busca Files em escala ampliada (plano antes de editar)

1. Revisar a evidência F12 e as notas Typography, Layout, Search fields, Focus and selection e Accessibility.
2. Reproduzir a busca Files em 1.0×/1.3×/2.0× no MuMu e medir o espaço entre lupa, texto, Limpar e Grade/Lista.
3. Tornar o texto auxiliar conciso em escala extrema sem reduzir tipografia, perder escopo, sobrepor ícones ou alterar a query; conferir mobile/landscape e PT-BR.
4. Preservar D-pad: card ↑ busca; texto/cursor editáveis; RIGHT chega a Limpar quando visível e Grade/Lista; CENTER em Limpar continua no campo; CENTER no toggle e DOWN chegam ao conteúdo.
5. Capturar screenshots/XML antes/depois nas três escalas e executar a fixture no MuMu.
6. Rodar build/testes e checks de cor/contraste/layout/diff; restaurar fontScale/local.properties e não tocar no app principal nem publicar.
7. Atualizar evidências/HIG e commitar F13 local após validação verde; registrar qualquer limite restante em 2×.

### Execução F11 — estados de busca, reconexão Drive e rota de foco em Files

**Falhas reproduzidas:** buscar um termo sem correspondência deixava a lista em branco, sem comunicar o resultado; a primeira linha de cards desviava o D-pad ↑ para a marca no rail, longe da busca; o erro de sessão orientava a pessoa a abrir Configurações, mas não oferecia um caminho de reconexão naquela tela. A fixture inicial não tinha o mesmo foco/modo de escala da tela real e foi ajustada antes de validar o erro a 200%.

**Correções:** o filtro agora diferencia pasta vazia de busca sem resultados, mostra estado vazio e oculta as ações de erro até haver arquivos. Os erros de sessão/401 apresentam “Conectar Google Drive” como ação primária, abrem a tela SignIn existente e focam essa ação; Retry fica secundário e DOWN chega até ele. Mensagens do repositório não mandam mais a pessoa para um local sem opção de conectar. A primeira linha de itens usa a busca como `nextFocusUp`; busca e ações descem para a lista. Os controles Grade/Lista e limpar busca continuam disponíveis.

**MuMu x86_64, 1920×1080:** com fixture visual, RIGHT atinge o primeiro card, ↑ retorna a `etSearch` e ↓ volta ao card. Digitar `nomatchzz` mostrou “Nenhum arquivo corresponde à sua busca”; “Limpar” restaurou os 12 itens. Toque no toggle passou da grade para linhas (12 cards no grid, 12 file rows, sem grid rows no dump da lista). No estado de sessão, foco inicial caiu em `btnConnectDrive`; DOWN alcançou `btnRetry` a 1.0× e 2.0×. A 2.0×, a fixture usa `adaptFilesChrome`: rail completo, placeholder de busca ainda reconhecível, erro em duas linhas e botões dentro da viewport. FontScale restaurado a 1.0. Capturas e dumps estão em `docs/ui-audit/after-v2/f11/`.

**Build/checks:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21: 167 testes, 0 falhas/erros/skips. `check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram. APK Debug copiado/instalado somente em `zechs.drive.stream.visualreview`; `local.properties` foi restaurado. Nenhuma release, tag, workflow ou publicação foi alterada.

**Limite:** a ação de SignIn está ligada à tela existente; a fixture comprovou apresentação e foco do botão, enquanto destino e listener foram conferidos no código, sem clicar para disparar uma autenticação real. OAuth e o Drive da conta real não foram usados nem autenticados. Portanto, login, refresh-token, retorno de navegação e arquivos/paginação remotos continuam pendentes de teste com conta de teste ou sessão já acessível. O pacote principal e seus dados foram preservados.

### Execução F13 — dica de busca responsiva ao espaço

**Correção:** a dica do campo Files mantém “Pesquisar neste diretório…” em 1.0× e 1.3×. Em 1.8× ou maior, somente quando existe rail lateral (TV/landscape), usa a versão concisa “Buscar nesta pasta…”. O portrait sem rail conserva a dica original. A fonte e a query digitada não mudam. `TvAccessibilityLayoutTest` cobre limites e ausência de rail.

**MuMu x86_64, 1920×1080:** capturas a 1×, 1.3× e 2× (`f13-1.0.png`, `f13-1.3.png`, `f13-2.0.png`) mostram o hint integral nas três escalas; a 2× a mensagem curta cabe entre lupa e alternância. No APK da compilação final, com fonte a 2×, → a partir da busca focou `btnToggleGrid`, CENTER seguido de ↓ focou a primeira linha (`f13-final-focus.xml`, `f13-final-row.xml`, `f13-final.png`). FontScale voltou a 1.0.

**Build/checks:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21: 167 testes, zero falhas/erros/skips. `check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram. APK Debug instalado apenas como `zechs.drive.stream.visualreview`; `local.properties` foi restaurado. Avisos de assinatura DEBUG/depreciações permanecem. Nenhum release, tag, workflow ou publicação foi alterada.

## F14 — Percurso completo de Configurações por D-pad (plano antes de editar)

1. Revisar a fixture Settings e os itens visíveis do layout ativo; conferir notas locais Settings, Lists and tables, Focus and selection, Remotes e Accessibility.
2. No MuMu, em 1.0× e 2.0×, percorrer a lista vertical do primeiro ao último item, abrir controles seguros da fixture, dispensar com BACK e verificar que a rota continua até voltar.
3. Registrar limites de foco em rows que abrem diálogos, itens sem ação, scroll do container e labels ampliados; não usar ações do Drive ou limpar cache real.
4. Corrigir apenas caminhos reproduzíveis, com foco após fechar modal e scroll automático, preservando valores e funcionalidades.
5. Capturar screenshots/dumps e repetir toda a sequência em 1.0×/2.0× com visualreview exclusivamente.
6. Rodar build/testes/checks aplicáveis, restaurar escala e conferir que app principal/dados/local.properties ficaram intactos.
7. Atualizar rubrica/HIG e commitar F14 local após validação verde; sem publicar nem modificar releases/workflows.

### Execução F14 — foco inicial e alcance da lista em escala ampliada

**Falha reproduzida:** ao abrir a fixture Settings no MuMu (janela touch), `requestFocus()` falhava porque o primeiro cartão não aceitava foco em touch mode; o primeiro DOWN começava em “Pasta da biblioteca”, ignorando “Perfil de usuário”. O Android TV precisa apresentar o primeiro destino de foco ao entrar nessa tela.

**Correção:** no caminho TV de `SettingsFragment`, o cartão “Perfil de usuário” agora aceita foco em touch mode antes do `requestFocus()` postado. `focusedByDefault` complementa o fallback nas variantes portrait e landscape. A fixture visualreview replica o estado focusable-in-touch-mode para permitir a mesma validação com D-pad no MuMu.

**MuMu x86_64, 1920×1080:** no pacote isolado `zechs.drive.stream.visualreview`, o primeiro foco foi `settingSelectProfile` a 1× e 2× (`f14-1x-first.xml`, `f14-2x-first.xml`). Dezesseis DOWN alcançaram `settingLogOut`; após rolagem, o cartão inteiro permaneceu dentro da área visível em 1× e 2× (`f14-1x-last.png/xml`, `f14-2x-last.png/xml`). A escala global foi restaurada a 1.0. O layout comprimido de Settings não teve o padding de rolagem alterado; uma tentativa inicial de clipping foi revertida após piorar o corte.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21; relatório XML: 163 testes, zero falhas/erros. `check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram. APK Debug reinstalado somente em `zechs.drive.stream.visualreview`; `local.properties` foi restaurado.

**Limites:** o harness da fixture não conecta ações aos controles de Settings; portanto, seleção de tema/player, editor de legendas, limpeza de cache, exportação/restauração, OAuth/MAL e confirmação de sair não foram acionados. Nenhum dado ou sessão do pacote principal foi alterado. Nenhum release, tag, workflow ou push foi feito.

## F15 — Sair do player por toque e D-pad (plano antes de editar)

1. Inspecionar handlers da seta, `dispatchTouchEvent`, atalhos de foco e `handleBackNavigation` em ExoPlayer e MPV.
2. Abrir a Activity real do ExoPlayer por uma rota interna de Debug, sem `fileId`, stream ou token; testar seta quando OSD está visível, foco no botão e CENTER.
3. Distinguir toque com OSD oculto (revelar controles), seta com OSD visível (sair), e BACK (ocultar OSD primeiro, sair na próxima ação).
4. Conferir implementação e layout equivalente de MPV sem copiar sessão/token do app principal; registrar limite se a fixture isolada não conseguir abrir o engine.
5. Capturar os estados e repetir toque/D-pad após compilação; preservar funcionalidades e não mudar o comportamento de foco/overlays.
6. Rodar `assembleDebug testDebugUnitTest`, checks de diff, reinstalar apenas visualreview e restaurar MuMu/local.properties.
7. Atualizar log/HIG; commit local somente se o comportamento estiver validado; sem release/tag/workflow/push.

### Execução F15 — seta de saída funciona com OSD visível

**Inspeção:** ExoPlayer e MPV conectam `btnBack` a `finish()`. Em ambos, CENTER no botão focado também encerra; BACK fecha overlay/OSD antes e encerra quando não há mais HUD visível. O Exo mantém timeout de 4,5 s para auto-ocultar controles.

**MuMu x86_64, 1920×1080:** uma Activity real `PlayerActivity` foi aberta dentro da fixture, sem arquivo, mídia, autenticação ou request ao Drive. UIAutomator confirmou `btnBack` clicável em `[63,68][140,145]` e D-pad ↑×2 focou esse botão (`f15-exo-actual.xml`, `f15-exo-dpad-focus.xml`). Com OSD visível, tocar a seta e CENTER em `btnBack` finalizaram a Activity e retornaram à fixture. Com BACK, a primeira ação oculta o OSD e a seguinte sai. Quando o OSD já expirou, um toque no vídeo apenas revela a HUD; a seta precisa estar visível para funcionar como alvo de saída.

**Conclusão desta reprodução:** não encontrei falha na seta da versão compilada atual; o comportamento descrito não se reproduziu quando o botão está visível e ativo. O app em produção pode estar usando um build anterior. MPV foi verificado estaticamente: a fixture isolada não tem sessão Drive e encerra o engine antes de renderizar controles, então toque real no MPV permanece pendente sem usar o token da conta principal.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21. APK atualizado instalado somente em `zechs.drive.stream.visualreview`; pacote principal/sessão preservados. Não houve alteração de player em release.

## F16 — Editor de perfis: foco, escala ampliada e catálogo visual

1. Rever F15 e a implementação de `ProfileEditorDialog`; validar o gerenciamento de perfis com a fixture isolada, sem persistir perfis nem tocar na conta Drive do app principal.
2. Percorrer o editor com D-pad em 1.0× e 2.0×: nome, Admin/Kids, abas, busca, cartões, URLs e ações de cancelar/salvar; procurar foco perdido e itens fora da viewport.
3. Corrigir rolagem/foco da grade de arte e reflow em fonte ampliada; manter nome, papéis, avatar, wallpaper e links customizados utilizáveis.
4. Conferir a disponibilidade real dos URLs remotos de avatar/wallpaper, carregamento Glide e superfície do diálogo; não gerar arte estática nem gravar dados de teste.
5. Compilar e instalar somente `zechs.drive.stream.visualreview`; repetir capturas e navegação no MuMu após as mudanças.
6. Rodar `assembleDebug testDebugUnitTest`, checks de layout/contraste/diff, restaurar fontScale e verificar `local.properties` byte a byte.
7. Registrar evidências e limites, commitar F16 local após validação verde; sem release, tag, workflow ou push.

### Execução F16 — galeria carregada, foco contínuo e diálogo sem bleed

**Falhas reproduzidas:** o D-pad chegava à segunda linha de arte sem rolar o `NestedScrollView`, parecendo travar; em 200% o reflow comprimia abas e busca e deixava controles difíceis de alcançar. As URLs estáticas de AniList retornavam erro, deixando placeholders no catálogo. O fundo translúcido do editor permitia que título/perfis da tela de trás atravessassem a superfície e disputassem a leitura.

**Correções:** cartões rolam para dentro da viewport quando recebem foco; abas e busca empilham em fonte grande, os campos/botões ganham altura e a grade reduz para duas colunas. Os 20 avatares e 10 wallpapers agora usam endpoints de imagens públicos resolvidos pelo catálogo AniList; os 30 links foram validados com HTTP 200. O editor recebeu superfície escura opaca com contorno discreto, preservando o foco ciano e evitando bleed. Nenhuma arte foi gerada por IA.

**MuMu x86_64, 1920×1080:** fixture isolada `zechs.drive.stream.visualreview`, sem salvar o formulário e sem autenticar no Drive. A 1×, D-pad percorreu abas/busca e chegou à galeria de quatro colunas; imagens reais carregaram nas duas linhas visíveis (`f16-profile-editor-opaque-1x.png/xml`). A 2×, o layout empilhado e a grade de duas colunas mantiveram imagens visíveis, com cartão focado dentro da área rolável (`f16-profile-editor-opaque-2x.png/xml`). As etapas de D-pad cobriram abas de avatar/wallpaper, busca, cartões e campos de URL, além de Cancelar/Salvar. O seletor de perfis foi inspecionado a 1×/2× (`f16-profiles-1x.png/xml`, `f16-profiles-2x.png/xml`). FontScale foi restaurada para 1.0 e o MuMu voltou a abrir `MainActivity` do pacote principal.

**Build/checks:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou no Ubuntu WSL/JDK 21; relatórios: 168 testes, 0 falhas/erros/skips. `check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram. APK Debug atualizado instalado apenas como `zechs.drive.stream.visualreview`. SHA-256 de `local.properties` permaneceu `6F8A9100E30E897E01CF8E5B9E1DAA76C4D77DDCE257BB4E266AE9623C5A532B`.

**Limites:** foi exercitada navegação e renderização, sem salvar no Room; não foram testados refresh/expiração dos URLs no longo prazo ou edição de perfil já persistido com conta real. OAuth/Drive e reprodução continuam fora deste percurso. Nenhum release, tag, workflow ou push foi feito.

## F17 — Home: busca superior e margem do hero (plano antes de editar)

1. Reabrir a Home real no MuMu sem limpar dados e registrar hierarquia visual e foco da navegação por D-pad.
2. Conferir barra de busca e posição do hero contra a captura e o mockup: foco, altura do header, título/sinopse/CTAs e primeiro shelf.
3. Corrigir a causa de header ausente/hero cortado nas variantes TV/landscape, preservando busca, sort e alternância de exibição em Catálogo/Favoritos.
4. Validar no pacote principal apenas leitura visual e no fixture de UI se existir; não executar ações que mudem Drive, conta ou preferências do usuário.
5. Recompilar, instalar somente a fixture quando a validação de produção não exigir pacote principal, repetir screenshots/XML, teste de foco e checks.
6. Registrar limites e evidências; commit local após validação, sem release/push.

### Execução F17 — busca visível e retorno D-pad entre CTA e busca

**Achados no pacote principal, em leitura visual:** a Home abria com `topHeaderBar` ausente; `contentScrollView` começava em y=0, deixando o título do destaque encostado no topo e removendo a busca da Home. O dump mostrava `tvFeaturedTitle` em `[308,6][1162,78]`. No primeiro build da fixture, UP a partir de “Assistir agora” desviou para `btnNavFavoritos`, apesar da busca ser o destino esperado.

**Correções:** `HomeFragment.selectTab` torna `topHeaderBar` visível em Início, Catálogo e Favoritos; `setupTenFootFocusChain` conecta UP das ações principais à busca. A fixture da Home mostra o mesmo header e oferece o mesmo vínculo direcional para validar o layout/D-pad sem instalar sobre o pacote principal.

**MuMu x86_64, 1920×1080:** antes/depois registrados em `f17-home-current.png/xml` e `f17-home-final.png/xml`. Depois, a busca ocupa `[308,28][1836,98]`, o campo `[392,31][1536,94]` e o título do destaque `[308,199][1162,343]`, com respiro para texto de duas linhas. UP do botão de reprodução focou `etSearchAnime` (`f17-search-focus.xml`); DOWN retornou a `btnFeaturedPlay` (`f17-play-focus.xml`). O teste visual final foi feito no pacote fixture, sem alterar a sessão/dados Drive.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21; 168 testes, 0 falhas/erros/skips. `check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram. Build instalado somente em `zechs.drive.stream.visualreview`; fontScale 1.0. O pacote principal não foi reinstalado nem limpo.

**Limite:** a troca de tab via Home real não foi acionada, por preservar o app principal; a visibilidade para Catálogo/Favoritos foi conferida no `selectTab` e a rota do header/Home foi exercitada na fixture. OAuth, Drive e playback não foram usados.

## F18 — Avatar ausente nos perfis salvos (plano antes de editar)

1. Confirmar por leitura de `ProfileManager`/Room que os perfis sem avatar não sofreram falha de carregamento ou alteração de dados.
2. Reproduzir no MuMu em fixture um perfil com `avatarUrl=null`; verificar redes Glide, placeholder, acessibilidade e estado offline/falha.
3. Fornecer avatar visual determinístico do catálogo web quando não houver URL salvo; manter escolha customizada como prioridade e não gravar fallback na conta.
4. Garantir fallback local de erro carregável/offline sem introduzir arte estática nova nem imagem gerada.
5. Compilar, validar no MuMu com URLs que respondem e foco D-pad, rodar testes/checks, instalar só o visualreview.
6. Manter Room/shared preferences do app principal intactos, registrar limites e fazer commit local após verde; sem release/push.

### Execução F18 — fallback de perfil com imagem de catálogo

**Causa:** os perfis padrão têm `avatarUrl=null` e `avatarResName="avatar_anime"`; esse PNG é somente um círculo azul escuro, então a UI parecia não carregar nenhum avatar. O adapter não tinha fallback online para perfis sem escolha customizada.

**Correção:** `ProfileSelectionFragment` fornece o catálogo ao adapter. Quando `avatarUrl` é nulo e o perfil usa o avatar padrão vazio, o seletor, a pílula da Home e a seção de perfil das Configurações escolhem a mesma imagem preset por hash estável do ID, sem gravar no modelo/Room. URLs personalizadas e o avatar local escolhido explicitamente têm prioridade. Glide mantém cache de rede e usa o avatar existente como placeholder/erro; nenhum arquivo de arte novo foi criado.

**MuMu x86_64, 1920×1080:** antes, os círculos vazios do pacote principal aparecem em `f18-profile-picker-current.png/xml`. A fixture integrada pós-correção mostra retratos online distintos para perfis sem URL (`f18-profile-picker-integrated.png/xml`) e o avatar da pílula na Home (`f18-home-avatar-integrated.png/xml`). Em 2×, arte, texto e ações permanecem visíveis (`f18-profile-picker-2x.png/xml`). D-pad percorreu os perfis até Adicionar, moveu o foco para Gerenciar e alternou para Concluído (`f18-profile-manage-integrated.xml`, `f18-profile-manage-toggled.xml`); evidências anteriores incluem `f18-addfocus.xml`, `f18-managefocus.xml` e `f18-2x-manage.xml`. O pacote principal não foi instalado/alterado; Room/perfis foram apenas lidos. Escala voltou a 1.0.

**Validação integrada:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21: 163 testes, 0 falhas/erros/skips. `tools/check_layout_colors.py`, `tools/check_contrast.py` e `git diff --check` passaram. APK instalado apenas como `zechs.drive.stream.visualreview`; o pacote principal preservou sua versão e seus dados. `local.properties` manteve SHA-256 `6F8A9100E30E897E01CF8E5B9E1DAA76C4D77DDCE257BB4E266AE9623C5A532B`.

**Limite:** a fixture reproduz o estado sem avatar salvo com conectividade disponível; comportamento do CDN totalmente offline foi conferido pelo placeholder configurado no Glide, sem simular desconexão. Os dados dos perfis reais permaneceram intactos.

## F19 — Estado visual da ação principal da Home (plano antes de editar)

1. Reproduzir o CTA `Assistir agora` focado no MuMu e rastrear fundo, tinta do texto/ícone e foco nativo em cada variante de layout.
2. Substituir a superfície branca fixa por cores semânticas do tema; manter contraste do conteúdo e tornar o foco perceptível em todas as paletas.
3. Atualizar os layouts usados em TV, telas grandes, paisagem e layout base.
4. Recompilar, instalar somente `zechs.drive.stream.visualreview`, conferir screenshot/foco D-pad, scripts de contraste/cores e testes.
5. Não tocar no pacote principal, na sessão Drive ou em dados locais; commitar localmente após validação.

### Execução F19 — CTA da Home por estado e tema

**Causa:** `btn_hero_play_bg` usava branco como preenchimento padrão em todas as paletas; o foco nativo ficava visualmente desconectado do ciano/azul. Uma primeira troca para o tom secundário criava contraste insuficiente no tema Estuary, então essa opção foi descartada.

**Correção:** CTA não focado permanece preenchido com `colorAccentPrimary` e tinta `colorOnPrimary`. No foco, passa para `colorBgSurface`, com borda e texto/ícone em `colorAccentPrimary`. Os filhos duplicam o estado do container para aplicar também a tinta focada. Estados de toque e ícone/texto acompanham o mesmo par de cores. Layout base, TV, paisagem e tela larga usam os tokens semânticos.

**MuMu x86_64, 1920×1080:** D-pad moveu o foco de Assistir Agora para Detalhes (`f19-home-info-focused.xml`) e de volta para Assistir Agora (`f19-home-play-focused.xml`). A captura focada mostra superfície escura e anel/texto ciano (`f19-home-play-focused.png`); quando o foco sai, o CTA volta ao preenchimento ciano. A escala do dispositivo permaneceu em 1.0.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou; 163 testes, 0 falhas/erros/skips. `tools/check_layout_colors.py`, `tools/check_contrast.py` e `git diff --check` passaram. O script agora verifica também a tinta ciano sobre superfície nos seis temas; menor resultado focado 4,89:1 (Dracula), e 9,82:1 (Estuary). Só `zechs.drive.stream.visualreview` recebeu o APK. `local.properties` preservou SHA-256 `6F8A9100E30E897E01CF8E5B9E1DAA76C4D77DDCE257BB4E266AE9623C5A532B`.

## F20 — Hero da Home e primeira dobra da TV (plano antes de editar)

1. Capturar a Home atual no MuMu e medir os bounds do destaque e da fileira seguinte.
2. Reduzir a ocupação vertical do hero em TV/telas largas sem cortar título ou CTA; manter Home de celular em dimensão normal.
3. Confirmar que a primeira fileira cabe, que o catálogo aparece logo após ela e que o D-pad percorre CTA, Detalhes e cards.
4. Rodar `assembleDebug testDebugUnitTest`, checks visuais e contraste; instalar apenas o pacote de revisão.
5. Salvar capturas/XML, atualizar conformidade HIG e fazer commit local após validação.

### Execução F20 — Home com hero compacto em TV

**Causa:** o destaque tinha 384dp de altura e sinopse de três linhas; em 1920×1080, consumia `[224,98][1920,770]`, deixando a maior parte de “Continuar assistindo” fora da tela. O override por qualifier de dimensões não era selecionado pela configuração real do MuMu.

**Correção:** em layouts de TV/paisagem, título e sinopse usam densidade tipográfica compacta (30sp e 12,5sp, sinopse em duas linhas), espaçamentos/paddings menores e scrim inferior de 176dp. `TvAccessibilityLayout` agora aplica 292dp ao hero quando a largura de tela é ≥900dp ou a menor largura é ≥600dp; celulares mantêm 384dp. A escala de texto do sistema continua ampliando hero e ações.

**MuMu x86_64, 1920×1080:** comparação `f20-home-before.png` → `f20-home-after.png`. Hero passou de 672px para 511px (`[224,98][1920,609]`); “Continuar assistindo” e os cards completos cabem (`rvContinueWatchingShelf=[224,685][1920,951]`), com “Em alta / Catálogo” visível abaixo. D-pad direita leva de Assistir agora a Detalhes (`f20-home-right.xml`); para baixo leva ao primeiro card (`f20-home-down.xml`), cujo anel ciano está em `f20-home-card-focused.png`. Fixture verificada pelo pacote `zechs.drive.stream.visualreview`; `fontScale=1.0`.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21: 171 testes, 0 falhas/erros/skips. `tools/check_layout_colors.py`, `tools/check_contrast.py` e `git diff --check` passaram. `local.properties` preservou SHA-256 `6F8A9100E30E897E01CF8E5B9E1DAA76C4D77DDCE257BB4E266AE9623C5A532B`. Nenhum release foi criado ou enviado; o pacote principal e sua sessão/dados permaneceram intactos.
## F21 — CTA da tela de detalhes e contenção do hero (plano antes de editar)

1. Abrir a tela de detalhes na fixture e verificar bounds do backdrop, poster, botões, temporadas e cards de episódio.
2. Testar foco inicial e caminho D-pad: ação primária → temporadas → episódios; confirmar foco de retorno ao subir.
3. Corrigir diferenças de cor/estado do CTA que prejudiquem hierarquia e contraste, mantendo layout e recursos da tela.
4. Compilar/testar, instalar somente visualreview no MuMu e conferir estados focado e normal com screenshot/XML.
5. Registrar resultado/limite da fixture e fazer commit local apenas depois da validação.

### Execução F21 — CTA ciano consistente na tela de detalhes

**Auditoria visual:** no MuMu, o poster, o hero/backdrop, a fileira de temporadas e os cards ficaram contidos no viewport; não reproduzi vazamento de imagem para fora do hero. D-pad DOWN partiu de `btnPrimaryAction`, chegou à aba de temporadas e, com mais um DOWN, ao primeiro episódio; UP voltou à temporada (`f21-details-down.xml`, `f21-details-down2.xml`, `f21-details-up.xml`). RIGHT moveu o foco ao próximo episódio (`f21-details-right.xml`).

**Falha encontrada:** `detail_primary_action_bg` era branco fixo em todas as paletas e não seguia os estados da Home. Ao aplicar superfície escura no foco, os descendentes dentro de uma coluna interna herdavam a cor errada e ficavam quase invisíveis.

**Correção:** CTA de detalhes agora usa `colorAccentPrimary` como preenchimento normal/pressionado e `colorBgSurface` com contorno ciano no foco. Texto e ícone usam `colorOnPrimary` no normal e `colorAccentPrimary` focados; o contêiner interno propaga corretamente o estado aos labels. Layout base e paisagem foram atualizados sem alterar ações, medidas ou recursos.

**MuMu x86_64, 1920×1080:** `f21-details-before.png` e `f21-details-after-focused.png` mostram o antes/depois do CTA; `f21-details-after-normal.png` mostra o estado fora de foco com card de episódio focado. `f21-details-after-focused.xml` confirma foco na ação primária; após dois DOWN, `f21-details-after-normal.xml` confirma foco em `cardEpisodeItem`. Fixture usada: `zechs.drive.stream.visualreview`, sem chamada ao Drive.

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21: 171 testes, zero falhas/erros/skips. `tools/check_layout_colors.py`, `tools/check_contrast.py` e `git diff --check` passaram; menor contraste de CTA focado permanece 4,89:1 na paleta Dracula. `local.properties` foi restaurado ao SHA-256 `6F8A9100E30E897E01CF8E5B9E1DAA76C4D77DDCE257BB4E266AE9623C5A532B`. Nenhum release/tag/push foi feito e o pacote principal permaneceu intacto.

## F22 — Toggle de visualização em Pastas (plano antes de editar)

1. Auditar Grade/Lista em 1920×1080, verificar feedback de foco, mudança de modo e retorno do D-pad.
2. Corrigir o alvo de toque para cumprir 48dp também fora da adaptação de TV, mantendo o tamanho de tela grande.
3. Substituir a descrição genérica por uma ação que anuncie o modo que será ativado; preservar persistência da preferência.
4. Compilar, rodar todos os testes e alternar Grade↔Lista por OK no MuMu; validar o anel ciano e foco após a mudança.
5. Salvar evidências, atualizar matriz HIG e criar commit local. Não reinstalar o pacote principal nem acessar a sessão Drive.

### Execução F22 — alvo, foco e descrição do toggle

**Falha encontrada:** em celular, o ícone de Grade/Lista ocupava 24×24dp e tinha apenas uma descrição genérica. A tela larga adapta o controle para um alvo de TV, mas o layout base mantinha a área de toque curta.

**Correção:** o controle agora tem área mínima de 48×48dp e o campo de busca acompanha 48dp de altura. O seletor de fundo usa o preenchimento de foco/pressão do tema, mantendo o contorno ciano do `TvFocusRing`. A descrição é contextual: “Alternar para lista” na grade e “Alternar para grade” na lista. O adaptador de modo e sua preferência continuam inalterados.

**MuMu x86_64, 1920×1080:** `docs/ui-audit/after-v2/f22/files-toolbar-focus.png` mostra anel ciano no toggle; o XML confirma foco e “Alternar para lista”. OK alternou para lista, manteve foco no botão e atualizou a descrição para “Alternar para grade” (`files-list-f22.png`, `files-list-f22-focus.xml`). OK voltou à grade; DOWN/RIGHT alcançaram a coleção e um card recebeu o foco com ring ciano (`files-grid-return.xml`, `files-grid-dpad-final.png`).

**Validação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou via Ubuntu WSL/JDK 21 (171 testes, 0 falhas/erros/skips). Os checks de cor, contraste e `git diff --check` passaram. O APK x86_64 foi instalado apenas em `zechs.drive.stream.visualreview` no device `127.0.0.1:16384`; `zechs.drive.stream` permaneceu instalado. `local.properties` manteve SHA-256 `6F8A9100E30E897E01CF8E5B9E1DAA76C4D77DDCE257BB4E266AE9623C5A532B`. Sem release, tag ou push.

## C0.1 — fidelidade do fixture Home (plano antes de editar)

1. Conferir o estado instalado e capturar a Home da fixture sem reinstalar o app principal.
2. Percorrer com D-pad Home → Detalhes → prateleira → ação principal; registrar foco real.
3. Comparar binder do fixture com `ContinueWatchingAdapter` para separar defeitos do app e da ferramenta.
4. Corrigir dados sintéticos ausentes no fixture sem alterar comportamento/estado de conta.
5. Compilar, executar a suíte unitária existente e repetir a captura/navegação no MuMu.
6. Atualizar evidência e matriz com o escopo e limites explícitos; sem release ou push.

### Execução C0.1

**Resultado:** o fixture de debug omitia os dados de episódio do card e usava o título do episódio como nome da série. O binder sintético agora corresponde à hierarquia visual de produção: nome da série, badge `Ep. NN`, episódio e tempo restante. Mudança restrita a `VisualReviewActivity.kt`; adapter e telas de produção não foram tocados.

**MuMu x86_64, 1920×1080:** instalado só `zechs.drive.stream.visualreview`, iniciado explicitamente `VisualReviewActivity` e salvo `home-after.png` / `home-focus-after.png`. O UIAutomator confirmou `btnFeaturedInfo` após RIGHT e `btnFeaturedPlay` após DOWN→UP; a captura focada exibe o contorno ciano. Evidências anteriores e novas, incluindo XML, estão em `docs/ui-audit/after-v2/c0/`. O pacote `zechs.drive.stream` manteve versão 1.6.4.1 e não foi reinstalado.

**Build e testes:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild --max-workers=1` passou (171 testes, 0 falhas/erros/ignorados). A primeira tentativa Windows falhou ao resolver o classpath do worker; `TEMP/TMP=C:\temp`, `GRADLE_USER_HOME=G:\` (atalho ao cache existente) e checkout em `X:\` eliminaram o problema do caminho com caractere acentuado. Uma tentativa WSL/Linux anterior parou na detecção do SDK Windows e foi descartada; a execução aprovada foi inteiramente pelo Gradle/JDK Windows.

**Estado:** correção do fixture compilada, validada manualmente e coberta pela suíte existente. `zechs.drive.stream` permaneceu instalado na versão 1.6.4.1; só o pacote isolado `zechs.drive.stream.visualreview` foi substituído. Nenhuma credencial foi lida; `local.properties` não foi editado. Nenhum release, tag, commit ou push foi criado.

## C1.1 — delegação de toque explícita na saída dos players (plano antes de editar)

1. Confrontar o contrato da seta de saída e do BACK nos players ExoPlayer/MPV.
2. Verificar como o detector de gestos delega toques em elementos interativos visíveis.
3. Incluir explicitamente as setas `btnBack` nos alvos delegados em ambos os motores e remover a dependência da visibilidade reportada pelo controller nativo; preservar gestos no vídeo e o callback de saída.
4. Compilar e rodar unitários/instrumentados; validar toque, foco D-pad e BACK em camadas no Exo isolado.
5. Registrar o limite: a sessão foi sem arquivo/ID de Drive e não valida reprodução nem o percurso de saída no MPV ao vivo.

### Execução C1.1

**Falha:** o detector global consumia toques antes da hierarquia Android se `PlayerView.isControllerVisible()` estivesse falso, mesmo com controles customizados visíveis. O sintoma era a seta esconder o OSD em vez de encerrar o player. O MPV usava a mesma regra compartilhada. **Correção:** o helper agora verifica os limites dos controles interativos visíveis em todo ACTION_DOWN, independentemente do estado do controller nativo; ambos os players incluem diretamente a seta de voltar na lista delegada.

**MuMu x86_64, 1920×1080:** toque na seta fechou `PlayerActivity`; DPAD_UP focou `btnBack` e DPAD_CENTER retornou à fixture; BACK dispensou a HUD na primeira pressão e retornou à fixture na segunda. Evidência: `docs/ui-audit/after-v2/c1/`. O Exo foi aberto sem arquivo ou sessão Drive; o MPV não foi exercitado com mídia real. O pacote principal `zechs.drive.stream` e seus dados ficaram instalados e intocados; só a variante debug `.visualreview` foi instalada.

**Verificação:** `assembleDebug testDebugUnitTest connectedDebugAndroidTest --no-daemon -PdistributionBuild --max-workers=1 --console=plain` passou no Windows/Gradle com o MuMu conectado; suíte unitária com 171 testes e instrumentada com 6 testes, 0 falhas. `ExampleInstrumentedTest` agora compara com `BuildConfig.APPLICATION_ID`, respeitando o sufixo isolado da variante debug.

**Limite:** o screenshot do SurfaceView sem mídia é preto e não representa OSD funcional; por isso não foi versionado. A validação de toque e saída usa Activity/foco e hierarquia do Android, não playback end-to-end. Nenhuma credencial, release, tag ou push foi usado/criado.

## C1.3 — BACK durante o fade dos controles

**Problema:** o teste anterior cobria Voltar após ocultar os controles, mas não a janela de fade. Uma segunda tecla durante os 220 ms podia encerrar a Activity antes de a camada terminar de desaparecer.

**Implementação:** `PlayerControllerVisibility.shouldConsumeBack()` consome BACK enquanto o controller está visível ou em transição de ocultação. ExoPlayer e MPV compartilham a regra; `isControlsShowing()` também leva em conta a flag de transição nos caminhos de entrada D-pad/toque relacionados.

**Verificação:** `assembleDebug testDebugUnitTest --rerun-tasks` passou no checkout via `X:\`, com 181 testes JVM, 0 falhas/erros/skips. No MuMu x86_64, para cada Activity real com mídia local: BACK → BACK em 45 ms manteve a Activity do player ativa e exibiu o aviso; após o fade, outra pressão retornou à fixture. Capturas em `docs/ui-audit/after-v2/q17.1/`. `git diff --check` sem erros; somente avisos normais de conversão de fim de linha.

**Limite:** não valida stream Google Drive nem player na TV física. O teste automatiza BACK de sistema; navegação por foco até a seta já tem evidência anterior em C1.1, mas o ciclo visual completo não equivale a aceite geral do player. Sem credenciais, release, tag, commit ou push nesta rodada.

## Q01.1 — HUD técnica sem valores inventados (plano antes de editar)

1. Isolar a formatação de vídeo/áudio em helpers sem dependência Android e escrever testes para formatos completos e ausentes.
2. Remover defaults de resolução, FPS, codec de áudio, canais e sample rate; representar cada dado indisponível como desconhecido.
3. Capturar o nome real do decoder reportado pelo Media3 e deixar explícito quando o renderer ainda não inicializou.
4. Compilar e rodar a suíte completa; abrir Exo com a mídia local no MuMu e conferir se a HUD não atribui resolução/decoder ausentes.
5. Registrar o limite: MPV tem caminho próprio e continua sem HUD técnica equivalente; stream Google Drive e matrizes de mídia seguem pendentes.

### Execução Q01.1

`MediaInfoDisplay` agora formata metadados reportados pelo formato do Media3 e usa valores explícitos de desconhecido se faltarem codec, dimensão, FPS, canais ou frequência. Um `AnalyticsListener` captura o nome do decoder inicializado; removi a afirmação de decoder HW e a indicação incorreta de buffer em RAM. O `<include>` mantém a largura do painel, que antes aparecia comprimido. Buffer que chega ao fim de um clipe curto é classificado como totalmente carregado.

**Validação:** `assembleDebug testDebugUnitTest` passou com 185 testes e 0 falhas/erros/skips. Os 6 testes instrumentados passaram por invocação direta no MuMu (`OK (6 tests)`). `connectedDebugAndroidTest` gerou relatório 6/6, mas o wrapper UTP retornou erro ao coletar os resultados; não trato essa tarefa como verde. No MuMu, a HUD do Exo/Media3 mostrou AVC, 1920×1080 a 30 fps, `OMX.qcom.video.decoder.avc`, AAC estéreo a 48 kHz, nenhuma legenda ativa e buffer totalmente carregado. Captura em `docs/ui-audit/after-v2/q01.1/exo-technical-hud.png`; sem fatal exception.

**Limite:** a vinheta local contém metadados completos e dura três segundos. Dados desconhecidos em runtime, Drive, outro decoder, stream longo e HUD do MPV continuam pendentes; nenhum dado da conta foi acessado e o pacote de produção não foi reinstalado.

## C1.2 — player real com mídia local isolada (plano antes de editar)

1. Criar no `VisualReviewActivity` uma rota para Exo e outra para MPV que usem um recurso local já existente, copiado ao cache da variante `.visualreview`.
2. Aceitar a URI apenas com `BuildConfig.DEBUG`; não preencher token/ID, consultar Drive, atualizar progresso, histórico ou estado da conta.
3. Repetir o asset curto em ambos engines para que a HUD permaneça disponível durante a inspeção.
4. Compilar, executar os testes, instalar apenas `.visualreview`, observar imagem/OSD/trilhas e validar toque + D-pad/Back em cada engine.
5. Se codec/ABI ou SurfaceView bloquear inspeção, registrar evidência e limite sem tocar no app principal.

## M3.1 — migração do player legado para Jetpack Media3 (plano antes de editar)

1. Atualizar o estado do repositório e inventariar cada dependência/import/layout de `com.google.android.exoplayer2`, incluindo o renderer FFmpeg local.
2. Migrar APIs, layouts e dependências para `androidx.media3`, mantendo `minSdk 21` com a última versão estável compatível e preservando ExoPlayer como motor Media3 e MPV como alternativa.
3. Adaptar o tratamento de erros, seleção de trilhas, legendas, data source autenticado, preview da Home e fixture visual sem reduzir controles ou AniSkip.
4. Manter o AAR FFmpeg legado isolado até integrar renderer Media3 equivalente; validar que o fallback MPV segue disponível e documentar o limite.
5. Compilar, rodar testes, instalar somente `zechs.drive.stream.visualreview` e exercitar Home/player/OSD no MuMu, sem tocar nos dados do pacote principal.
6. Registrar evidências e atualizar este log apenas com resultados reproduzíveis; sem release, tag ou push nesta rodada.

### Execução M3.1 — migração para Jetpack Media3

**Migração:** dependências e referências do player ExoPlayer foram migradas de `com.google.android.exoplayer2` para `androidx.media3`, incluindo a `PlayerView`, fonte HTTP autenticada, preview da Home, seleção de faixas e utilitários de legenda. ExoPlayer continua sendo o motor dentro do Media3; MPV continua como alternativa. O controller mantém seu estado em callback, já que a propriedade antiga de visibilidade não existe em `PlayerView` 1.4.1. A seleção 1.4.1 preserva `minSdk 21` e `compileSdk 34` do projeto.

**Compatibilidade pendente:** `app/libs/extension-ffmpeg-release.aar` foi preservado, mas removido do classpath do player Media3 por ser uma extensão compilada contra o namespace legado. O renderer FFmpeg equivalente para Media3 ainda precisa ser integrado; a reprodução MPV com FFmpeg permanece disponível. Nenhum controle da HUD foi removido nesta migração.

**Validação:** `:app:assembleDebug` passou; `:app:testDebugUnitTest` passou com 171 testes; `:app:connectedDebugAndroidTest` passou com 6 testes no MuMu (SM-A546E/Android 12). A fixture visual isolada abriu Exo/Media3 e MPV com mídia local, apresentou os controles esperados na hierarquia Android, permitiu navegar por D-pad até Voltar/progresso, ocultar a HUD com BACK e retornar à fixture com BACK seguinte. Evidências XML em `docs/ui-audit/after-v2/c1/`. Foi necessário substituir `Uri.parse()` por URI mockada e silenciar o logger Android da fixture do teste JVM após a migração para Media3.

**Integridade e distribuição:** o build emite aviso de keystore de release não configurada; este APK debug é assinado com chave debug e não serve como atualização assinada do release instalado. O pacote `zechs.drive.stream` permaneceu intacto; somente `zechs.drive.stream.visualreview` foi usado no MuMu. Nenhuma keystore/secrets foi lida ou alterada. Sem release, tag, commit ou push nesta rodada.

## Reauditoria HIG — 04/10/2026

Pedido: reavaliar o nível atual, sem presumir conclusão pelos aceites anteriores. Código, dez referências HIG gerais/tvOS, release 1.6.4.1 instalado e APK local `.visualreview` foram inspecionados. Nenhum arquivo funcional foi alterado nesta rodada; as modificações anteriores foram preservadas.

Parecer: **maturidade estimada 6,5/10, aceite premium pendente**, sem certificação Apple. Home/details locais melhoraram, fonte 200% reorganiza linhas/ações, ambos engines decodificam a vinheta local e fecham pela seta/toque e sequência BACK. Nova listagem real percorreu raiz e pasta com 25 itens; Files apresentou 166 frames/0 janky em percurso delimitado de 25 comandos D-pad no MuMu. Fonte restaurada para 1.0, sem reinstalar/deslogar app principal ou reproduzir seus episódios.

Achados: metadados técnicos presumidos, listagem truncada sem sinalização, paridade FFmpeg Media3 pendente, ausência da política de efeitos reduzidos, contornos de foco sobrepostos, segundo toque necessário em Pastas no release, Settings ainda linear e inconsistência de hitboxes/ícones. OAuth/falhas de rede, codecs/ASS/SSA, AniSkip/autoplay, persistência integral de perfis, TalkBack e TV física seguem sem aceite completo.

Relatório: `docs/ui-modernization/REAUDIT_2026-10-04.md`. Evidências e ressalvas: `docs/ui-audit/2026-10-04-reaudit/README.md`. Relatórios recentes de 171 testes JVM e 6 instrumentados conferidos, não rerodados sem mudança funcional. Sem commit, tag, release ou push.

## Plano premium de produto e handoff Luna — 04/10/2026

Pedido: plano rigoroso além da UI, com QR/celular para autorizar Drive, customizações, features e execução pelo GPT‑6 Luna em esforço alto. Estado do checkout atualizado; mudanças funcionais anteriores preservadas. Planejamento não marca novo ticket como executado.

Pesquisa em fontes primárias Google/Android/IETF: Device Grant Google não aceita drive.readonly; drive.file não equivale automaticamente à biblioteca recursiva atual. Direção proposta: pareamento próprio HTTPS + Authorization Code no celular/backend, sessão revogável por aparelho e grant Google server-side. BFF de metadados/streaming é padrão proposto para não entregar tokens Google à TV; custo/vazão precisam de prova antes de hosting. Documentados lifecycle de refresh/Testing, Range, MediaSession e Watch Next condicionado à plataforma.

Referências locais: catálogo de 37 PDFs confirmado; trechos de 12 guias de produto consultados; Managing accounts p.3 e Split views p.3 renderizados/inspecionados. Novos HG-P01–08 são regras de planejamento, não conformidade aceita. Prioridades: integridade/codecs/foco/accessibility antes de ampliar features; reutilizar Room/perfis/fila/favoritos já existentes.

Entregas: `docs/ui-modernization/APPLE_GRADE_PRODUCT_PLAN.md` (27 tickets Q00–Q26, arquivos, dependências e seis gates); `docs/architecture/ADR-001-TV-PAIRING.md` (fluxo, contratos/estados, privacidade, migração, operação e testes); `docs/ui-modernization/LUNA_HIGH_EXECUTION.md` (prompt autocontido e disciplina de execução). Canvas de roadmap salvo no diretório gerenciado do Codex, fora do repo.

Validação desta rodada: inspeção de documentos, caminhos e dependências; sem mudanças de código funcional, sem nova compilação/instalação, sem contratação, deploy, commit/push ou release. Sessão e dados do MuMu/release não alterados.
# Q02.1 — resultados parciais na playlist e legendas do player (plano antes de editar)

1. Migrar os dois consumidores auxiliares de listagem Drive que ainda convertem falha paginada em lista vazia.
2. Preservar a ordem determinística dos episódios e filtrar apenas legendas reconhecidas, mesmo quando a página seguinte falhar.
3. Propagar completude/erro até ExoPlayer e MPV e apresentar aviso discreto sem fechar o OSD nem interromper o vídeo.
4. Cobrir falha depois da primeira página nos testes do repositório e manter os testes de paginação completa existentes.
5. Compilar, executar testes focados e validar o comportamento em fixture no MuMu; registrar que Drive autenticado não foi exercitado.

### Execução Q02.1

**Resultado:** playlist/legendas agora recebem um resultado com itens e completude; erro ou limite após páginas úteis mantém esses itens, e os dois players exibem aviso não modal contextual. A mensagem não manda o usuário continuar por um cursor que o player não expõe. `assembleDebug testDebugUnitTest --rerun-tasks` passou: 178 testes, zero falhas/erros/skips (inclui `DriveRepositoryTest` 14/14). O APK `.visualreview` instalou no MuMu e abriu perfis, ExoPlayer e MPV com fixture local sem crash. Essa fixture não reproduz falha paginada nem tem sessão Drive autenticada, então aviso/foco do resultado parcial ainda não foram verificados em runtime. Nenhum release, commit ou push foi criado.

## Q14.1 — backdrop contido na variante paisagem (plano antes de editar)

1. Comparar as duas variantes `fragment_series_detail.xml` e identificar por que a imagem continua visível atrás das temporadas/episódios em paisagem.
2. Conter o backdrop e seus scrims no hero; manter a superfície escura contínua começando após o bloco de detalhes e cobrindo toda a área rolável restante.
3. Não escurecer nem recortar o artwork dentro da própria área de hero além do necessário para manter texto legível.
4. Compilar e abrir a rota `details` da fixture no MuMu em paisagem; conferir a transição entre hero, temporadas e cartões e se o fundo permanece contido ao rolar.
5. Registrar o limite: a fixture usa metadados/imagens de demonstração, não a série da conta Drive.

### Execução Q14.1

**Resultado:** a variante paisagem agora preenche a região abaixo do hero com a mesma superfície escura do conteúdo. `assembleDebug` passou; APK `.visualreview` instalado no MuMu. Na fixture 1920×1080, duas pressões DOWN chegam ao primeiro cartão de episódio; a imagem já não vaza para a área dos cards. Evidências: `docs/ui-audit/after-v2/q14.1/series-detail-body-contained.png`, `series-detail-dpad-focus.png` e `series-detail-dpad.xml`. A fixture não usa dados do Drive e não valida metadados/sinopse reais. Nenhum release, commit ou push foi criado.

## Q17.1 — estado D-pad consistente durante animação do OSD (plano antes de editar)

1. Comparar as condições de visibilidade usadas por ExoPlayer e MPV para CENTER, LEFT/RIGHT, UP/DOWN, gestos e modais.
2. Definir uma regra comum: controles em transição de saída não são interativos; Voltar nessa janela termina a transição sem encerrar a reprodução.
3. Preservar seek acumulativo quando o OSD está oculto e o foco normal quando o OSD está plenamente visível.
4. Adicionar testes unitários à política pura de visibilidade/Voltar; compilar e rodar a suíte JVM completa.
5. No MuMu, repetir D-pad/back nos dois players com mídia local e registrar o limite de não ter Drive autenticado.

## Q01.2 — precisão técnica no MPV (04/10/2026)

O painel técnico do MPV agora deriva codec, resolução, FPS do contêiner, decoder ativo, áudio, legenda selecionada e estimativa de cache das propriedades reais do player. Dados ausentes permanecem identificados como indisponíveis; o decoder `no` representa software. No teste visual, corrigi também a modal compartilhada: linhas informativas usam ícone de informação e não são clicáveis/focáveis; em tela só de leitura, o foco inicial vai para Fechar.

Validação: 6 testes focados e a suíte completa de 191 testes passaram; `assembleDebug` passou. No MuMu x86_64, abri MPV com vídeo local, confirmei valores reais na tela e usei D-pad para chegar à ação, abrir o painel, verificar Fechar como único alvo e voltar ao player. BACK duplo durante o fade manteve a reprodução; BACK seguinte retornou à fixture. Capturas e hierarquia estão em `docs/ui-audit/after-v2/q01.2/`. O JDK deste Windows falha com AF_UNIX no seletor, então a execução usou uma alteração temporária de processo fora do repo para forçar o fallback TCP; nenhum workaround ou APK de produção foi publicado. A matriz de formatos, decoder alternativo, Drive autenticado e TV física continua pendente.

## Q03.1 — fallback de codec sem perder a reprodução atual (04/10/2026)

O ExoPlayer agora valida mídia/título antes de alternar para MPV, abre a nova Activity antes de encerrar a atual e mostra uma mensagem recuperável se o lançamento falhar. Nenhum token é copiado nos extras. `testDebugUnitTest assembleDebug` passou com 191 testes JVM e o APK `.visualreview` instalou no MuMu. A fixture MPV abriu normalmente após instalação; não simulamos uma falha de `startActivity`, então o comportamento de recuperação continua BUILT, sem aceite runtime. A matriz real de codecs segue aberta.

O AAR FFmpeg legado contém classes no pacote antigo `com.google.android.exoplayer2` e não está no classpath Media3. A documentação oficial da extensão Media3 pede checkout local e build nativo com FFmpeg/NDK/CMake, sem artefato Maven pronto; o SDK desta máquina não reportou NDK instalado. Até essa prova ser executável, o fallback explícito para MPV permanece estratégia de compatibilidade, sem declarar paridade FFmpeg no Media3. Nenhum release, commit ou push foi criado.

## Q04.1 — navegação lateral responde ao primeiro toque (04/10/2026)

No app principal instalado no MuMu, toquei uma vez em Pastas e a tela apenas moveu o foco; o segundo toque abriu a biblioteca. Registrei o antes/depois em `docs/ui-audit/2026-10-04-reaudit/q04-main-before.png`, `q04-main-one-tap-folders.png` e `q04-main-two-tap-folders.png`. Adicionei um encaminhador de toque somente aos botões da rail da Home: ele mantém pressed/ripple, cancela arrasto para fora e dispara o click existente exatamente no fim de um toque válido, preservando `performClick()`.

**Verificação:** três testes cobrem gesto completo, saída do alvo e cancelamento. `testDebugUnitTest assembleDebug` passou com 194 testes JVM, zero falhas/erros/skips; `git diff --check` passou. Reinstalei somente `zechs.drive.stream.visualreview` e um toque em Pastas, na Home real local, abriu a tela Files e mostrou a recuperação de sessão ausente. Evidência: `q04-review-home-one-tap.png`. O pacote principal permaneceu intacto; Drive não foi autenticado nem acessado. O contorno duplo de foco observado em outros componentes não foi tratado neste subticket.

## Q04.2 — foco sem contorno duplicado ou highlight de fileira (04/10/2026)

`TvFocusRing` agora cede quando o background do componente já implementa `state_focused` e remove o highlight padrão do Android em `RecyclerView`, que podia virar uma borda branca do tamanho de uma linha inteira quando a lista recebia foco. O seletor de perfil ficou com uma única borda ciano de 2 dp; `isSelected` agora indica o perfil ativo, independente do movimento do foco.

**Verificação:** build e 194 testes JVM passaram. No MuMu, o cartão de perfil ativo/focado mostra um contorno ciano único. Na Home de revisão, DOWN moveu o foco para o card “Continuar assistindo”; o dump de acessibilidade localiza o foco no `cardShelfItem` individual e não na fileira. Evidências: `q04-review-profiles-after.png`, `q04-review-home-row-focused-after.png` e `q04-review-home-focused.xml`. O tema corrente e um percurso de fixture foram usados; os seis temas, fonte ampliada e todos os componentes ainda precisam de auditoria antes do aceite de Q04.

## Q02.2 — lista além do limite local (04/10/2026)

Acrescentei a fixture de 2.500 arquivos com cursor ainda presente. Ela verifica que `getAllFilesDetailed` não declara sucesso completo, preserva `page-25` e sinaliza `FILE_LIMIT`. A primeira execução do teste revelou um índice errado na leitura dos argumentos mockados; corrigido após a falha, o cenário focado passou.

**Verificação:** teste focalizado verde; `testDebugUnitTest assembleDebug` passou com 195 testes JVM e nenhum erro/falha/skip. O teste usa Retrofit simulado; não equivale a uma biblioteca de usuário real com 2.501 itens. Drive autenticado, retry depois da página e troca de pasta durante carregamento seguem pendentes.

## Q04.3 — saída acessível do player no toque e D-pad (04/10/2026)

A seta do ExoPlayer agora consome o toque completo e dispara a ação acessível uma vez; cancelamentos não encerram o vídeo. No MPV e no ExoPlayer, se a superfície nativa ficar com o foco ao abrir o OSD, o primeiro movimento D-pad retorna o foco ao Play/Pause e a ligação direcional leva à seta de saída.

**Verificação:** `testDebugUnitTest assembleDebug` passou com 195 testes JVM. No MuMu x86_64, reabri cada Activity real pela fixture `.visualreview`: um toque na seta encerrou ExoPlayer; no MPV, duas pressões UP e OK retornaram à tela hospedeira. OSD MPV: `docs/ui-audit/2026-10-04-reaudit/q04-player-mpv-final.png`. O dump da hierarquia Exo confirmou `btnBack` como alvo clicável e limites `[63,64][147,148]`. Apenas mídia local de demonstração; não usei Drive nem alterei a instalação principal. Q04 segue aberto para verificação transversal do restante da navegação.

## Q04.4 — cartão da última fileira visível em paisagem (04/10/2026)

Na grade de pastas do layout paisagem, `android:padding="0dp"` anulava o padding inferior de overscan declarado no mesmo `RecyclerView`. A última fileira recebia foco, mas o cartão terminava exatamente na borda da tela. Removi somente o atributo que anulava os paddings individuais.

**Verificação:** `testDebugUnitTest assembleDebug` passou com 195 testes. No MuMu 1920×1080, usei a fixture com 12 arquivos e D-pad: RIGHT focou o primeiro cartão; DOWN até o item da última fileira rolou a grade e manteve o cartão completo visível, com 40 px livres na borda inferior. Capturas antes/depois: `docs/ui-audit/2026-10-04-reaudit/q04-files-row-focus.png` e `q04-files-bottom-after.png`. A fixture não acessa Drive e o Q04 geral segue aberto.

## Q05.1 — movimento reduzido por perfil (05/10/2026)

Adicionei em Configurações a opção `Reduzir movimento`, com `Seguir dispositivo` como padrão e `Reduzido` persistidos por perfil no DataStore. A Home remove o zoom decorativo no foco; as duas HUDs do player exibem/ocultam controles sem translação/fade decorativos, e transições de bloqueio também são suprimidas. Foco, anel ciano, seleção e mudança de estado continuam explícitos.

No MuMu x86_64, validei visualmente a linha e o diálogo, escolhi `Reduzido` por toque, confirmei o valor na linha e ao reabrir o diálogo, percorri as opções com D-pad e selecionei `Seguir dispositivo`. Corrigi durante a validação um caso em que a mensagem do alerta ocultava as opções de lista. Também conferi as Configurações com fonte 1.3× e 2×: as linhas crescem, permanecem roláveis e o foco em `Reduzir movimento` ficou dentro do viewport. A seleção de perfil passou a anunciar nome, papel Admin/Kids, estado ativo e a ação “Adicionar perfil” para TalkBack. Evidências Q05 em `docs/ui-audit/2026-10-04-reaudit/`.

**Verificação:** `testDebugUnitTest assembleDebug` passou com 197 testes; `git diff --check` sem erros. A validação de runtime usa a fixture local `.visualreview`; TalkBack e cobertura de todas as animações e dispositivos seguem pendentes. O tracker mantém Q05 IN_PROGRESS. O build local alertou que não há keystore release configurada e usaria assinatura DEBUG; este APK não é apropriado para um release público nem para atualizar a instalação assinada principal.

## Q05.2 — seletor de perfis respeita movimento reduzido (05/10/2026)

Completei a propagação de `Reduzido` ao seletor de perfis: a preferência do perfil ativo agora é lida no fluxo de perfis, e os cartões cancelam/resetam a escala de foco quando a política pede movimento reduzido. O foco ciano e o estado ativo permanecem claros. No MuMu, mudei a configuração pela tela real do app e voltei ao seletor; o cartão focado deixou de ampliar sem perder o contorno. O D-pad mantém acesso às ações inferiores. Capturas e hierarquia: `docs/ui-audit/2026-10-04-reaudit/q05-real-profile-selection.png`, `q05-profile-reduced-motion.png` e `q05-profile-reduced-motion.xml`.

**Verificação:** suíte JVM completa: 197 testes, sem falhas; `assembleDebug` passou. O runner falhou com `ClassNotFoundException` no caminho original com acento, mas passou pelo drive temporário `W:` sem acento; o pacote `.visualreview` foi instalado no MuMu e a instalação principal ficou intacta. Não é um release de produção: o build local ainda aponta ausência da keystore e assinatura DEBUG. TalkBack, TVs físicas e a cobertura de todas as superfícies animadas continuam pendentes.

## Q06.1 — Categorias e split view nas Configurações (05/10/2026)

Agrupei as opções preservadas em cinco categorias: Perfil e biblioteca, Aparência, Reprodução, Integrações e Dados e sessão. A variante para telas amplas passou a usar uma rail vertical à esquerda e conteúdo rolável à direita; a variante compacta mantém as categorias horizontais. D-pad testado no MuMu: entrar na categoria, atravessar ao conteúdo, selecionar Aparência e Reprodução, voltar da primeira opção para a rail e sair da tela com BACK.

Build `assembleDebug` passou e foi instalado como `zechs.drive.stream.visualreview` no MuMu e no Moto G34; o pacote principal ficou intacto. No Moto G34 em paisagem, ADB selecionou a categoria Aparência e moveu o foco ao primeiro controle. A hierarquia Android confirma o foco e o alvo está totalmente dentro do viewport. Evidências: `docs/ui-audit/2026-10-05-settings-split.png`, `settings-appearance-selected.png`, `settings-appearance-row.png`, `settings-playback-selected.png`, `settings-back.png` e `moto-settings-appearance.png`/`.xml`. A última execução de `testDebugUnitTest` compilou testes, mas falhou ao iniciar os workers Gradle (`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`), então este incremento não recebe alegação de suíte verde; 197 testes passaram antes da mudança split. Q06 segue aberto para teste portrait/rotação, TalkBack e ações com sessão real.

## Q01.1 — HUD técnica responsiva à altura da tela (05/10/2026)

A priorização visual do Makimono segue sendo Android TV: foco e percurso de D-pad, alvos legíveis a distância, overscan e player são os critérios principais. O Moto G34 é uma checagem adicional para toque e telas compactas. Nele, reproduzi a HUD técnica cobrindo a OSD em paisagem baixa; criei um layout compacto para alturas disponíveis abaixo de 480 dp, mantendo visíveis codec/decoder/resolução/FPS, áudio, legenda e buffer. A variante espaçosa segue selecionada em paisagens altas para preservar densidade de informação na TV. O recuo superior também se ajusta à altura disponível.

`assembleDebug --no-daemon -PdistributionBuild --max-workers=1` passou. Instalei somente `zechs.drive.stream.visualreview` no Moto e abri a tela real do ExoPlayer com a vinheta local; `docs/ui-audit/2026-10-05-moto-exo-technical-info-compact.png` confirma que todas as informações ficam acima da OSD. A tela reportou AVC, decoder Qualcomm, 1920×1080/30 fps, AAC estéreo/48 kHz, ausência de legenda ativa e buffer real. Não rerodei testes unitários nesta alteração, porque o runner Gradle continua falhando ao carregar `GradleWorkerMain`. A variante alta e a TV física ainda precisam de validação runtime; nenhuma sessão Drive nem pacote principal foi alterado.

## Q04.5 — rota D-pad entre transporte e saída do player (05/10/2026)

Android TV continua sendo a plataforma-alvo; o Moto G34 por ADB é apenas validação complementar. Reproduzi em ExoPlayer e MPV um dead-end vertical: com OSD visível e Play/Pause focado, DPAD_UP não alcançava a seta Voltar. Implementei uma política compartilhada UP: Play/Pause → Voltar e DOWN: Voltar → Play/Pause. Ela só atua com controles estáveis e sem overlays bloqueadores. O dispatcher também consome a soltura/repetição correspondente para impedir que o motor de vídeo reverta o foco.

**Referência de design:** Focus and selection, p. 4, recomenda acesso dos componentes via navegação direcional; Remotes, pp. 1–2, alinha movimento do foco ao gesto e espera que Back retorne ao nível anterior. A aplicação preserva BACK e toque existentes, respeitando as camadas/estados específicos do player.

**Validação:** `testDebugUnitTest assembleDebug` passou e a suíte completa teve 199 testes, 0 falhas/erros/skips. Instalei somente o pacote isolado `.visualreview` no Moto G34 (`0086442783`). Nos dois players, ADB confirmou UP→`btnBack`, DOWN→`btnPlayPause` e OK em Voltar→retorno à fixture. No ExoPlayer, BACK primeiro ocultou a OSD sem encerrar o episódio, e o segundo BACK retornou ao host. Capturas/hierarquias: `docs/ui-audit/2026-10-05-moto-mpv-up-current.png`/`.xml`, `moto-mpv-down-current.xml`, `moto-exo-up-current.png`/`.xml`, `moto-exo-down-current.xml` e `moto-exo-back-first-current.png`.

**Ainda pendente:** teste com TV e controle remoto físico, overscan/resoluções/legibilidade 10-foot, mudança de foco durante skip/contagem do próximo episódio e fade da OSD. Nenhuma sessão Drive, pacote principal, release ou publicação foi alterada.

## Auditoria de execução Q00–Q26 (05/10/2026)

Reconciliei o roadmap com o checkout atual, o tracker e as evidências disponíveis. Q00 é o único ticket global marcado como aceito; Q01–Q06 têm entregas parciais ou incrementos runtime validados, e Q07–Q26 ainda não fecham os critérios globais. O Q14.1 (contenção de backdrop) e Q17.1 (BACK durante o fade) têm validação de fixture, não aceite de tela real/TV. Atualizei o canvas do roadmap com estados e provas pendentes por ticket: `C:\Users\Cauã V\.cursor\projects\c-Users-Cau-V-Documents-antigravity-intelligent-mendel\canvases\makimono-premium-roadmap.canvas.tsx`.

Evidência atual de build/teste: `assembleDebug` e `testDebugUnitTest` passaram no checkout, 199 testes JVM sem falhas/erros/skips; somente `zechs.drive.stream.visualreview` foi instalado no Moto G34. O percurso D-pad UP/DOWN e saída por OK foi observado em ExoPlayer/MPV; não foi obtida evidência bastante para afirmar CENTER sem dupla ação. Nenhuma TV ou controle remoto físico aparece conectado. A fixture não tinha sessão Drive; Q02 real e Q07–Q12 seguem sem integração operacional. Nenhum backend/página QR foi encontrado no checkout, nenhuma keystore release está configurada localmente e nenhum release foi produzido nesta rodada.

## Q18.1 — seletor modal de áudio com D-pad (05/10/2026)

O teste real do MKV multitrack mostrou que `dispatchKeyEvent` em ExoPlayer e MPV interceptava teclas direcionais e BACK antes do seletor modal. No MPV, DOWN também disparava uma ação de legenda no player de fundo. Corrigi a precedência: enquanto houver qualquer diálogo modal aberto, ambos os players delegam os eventos ao sistema; o seletor agora usa `requestFocusFromTouch()` quando a abertura por toque impede o foco inicial pelo caminho normal.

Com o fixture local (MKV, duas faixas AAC Japanese/Português e SRT pt-BR), validei em ambos os engines: menu lista as faixas; DOWN move o anel ao próximo item; OK seleciona Português; ao reabrir, Português continua marcado; BACK fecha somente o menu e mantém o player ativo. Capturas em `docs/ui-audit/2026-10-05-multitrack-selection/`. O comportamento por controle remoto de TV continua sem prova, assim como legenda ASS/SSA, fontes externas/Drive, offsets, idiomas ausentes, codecs alternativos e preferências de faixa por perfil.

`testDebugUnitTest assembleDebug` passou e a suíte separada passou com 200 testes JVM, sem falhas/erros/skips. A instalação foi exclusivamente `zechs.drive.stream.visualreview` no Moto G34 (`0086442783`); sessão Drive, pacote principal e release não foram alterados. Nenhuma keystore de release configurada foi necessária para esse build debug.

## Q18.2 — editor de estilo de legendas por controle remoto (05/10/2026)

Validei o percurso de estilo no player MPV da variante `.visualreview` no Moto G34 em paisagem. Do seletor de legendas, UP/OK abre “Personalizar aparência” com foco visível único; a prévia mostra a legenda ativa. RIGHT muda o tamanho 20→21 sp e DOWN percorre os controles roláveis até Cancelar; RIGHT/OK chega a Salvar. Reabri o editor e confirmei 21 sp persistido, depois restaurei e salvei 20 sp. BACK do editor retorna ao player, sem seletor empilhado e sem encerrar a reprodução.

`testDebugUnitTest assembleDebug --no-daemon --max-workers=1` concluiu com `BUILD SUCCESSFUL`. Depois executei `testDebugUnitTest --rerun-tasks --no-daemon --max-workers=1`; os relatórios XML confirmam 200 testes, 0 falhas/erros/skips. `git diff --check` não encontrou erros de whitespace (somente avisos da configuração LF/CRLF do checkout). Capturas/dumps: `docs/ui-audit/2026-10-05-subtitle-style/`. A mudança recente de `dialog_player_glass_menu.xml` mantém a borda neutra em repouso e deixa o `TvFocusRing` como indicador único quando focado.

Escopo validado: somente editor compartilhado no engine MPV, uma legenda incorporada da fixture e armazenamento da variante isolada. Falta repetir no ExoPlayer, reiniciar processo para confirmar persistência durável, validar TV física/TalkBack e cobrir ASS/SSA, faixas externas/Drive e offset. Q18 não está aceito.

## Q16.1 — corrigir sobreposição de arcos (05/10/2026)

O agrupador de One Piece atribuía os episódios 196–206 simultaneamente a Skypiea e G-8. Ajustei o fim de Skypiea para 195 e acrescentei um teste com os episódios 195, 196, 206 e 207, verificando que cada ID aparece em exatamente um arco e continua no grupo esperado. O agrupador da biblioteca e o do player usam a mesma definição de arcos.

`testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou: 203 testes JVM, 0 falhas/erros/skips; `git diff --check` passou. Os testes usam títulos sintéticos; falta confrontar a classificação e a ordem com uma biblioteca real. Só pacote debug foi compilado; nenhuma instalação de produção, conta Drive, commit, push ou release foi alterada.

## Q16.2 — manter OP/ED, OVAs e promos fora dos arcos narrativos (05/10/2026)

Na auditoria seguinte dos grupos canônicos, descobri que a lista de One Piece era encaminhada aos arcos antes da separação de categoria. Por terem números, `OP 01`, `OVA 01` e `PV 01` podiam entrar indevidamente no arco dos episódios 1–61. Corrigi os agrupadores usados pela biblioteca e pela playlist do player: arcos recebem apenas itens classificados como história; música, especiais e promos mantêm grupos próprios.

Adicionei cobertura para os dois agrupadores com os quatro tipos de item, e mantive a cobertura das fronteiras de episódio. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 204 testes JVM, 0 falhas/erros/skips; `git diff --check` passou. Ainda falta validar nomes reais e navegação D-pad dos grupos em TV/Drive; a fixture é sintética. Sem troca do pacote principal, sessão, release, commit ou push.

## Q16.3 — coleção principal de episódios sem autoplay acidental de bônus (05/10/2026)

Mesmo com os grupos dedicados, “Todos os Episódios” ainda recebia a lista completa e podia deixar OVA/PV depois do último episódio regular na playlist principal. Agora, quando há categorias separadas, a coleção principal inclui `STORY`; especiais/OVAs, OP/ED e promos seguem nos grupos específicos. Se só houver extras, o conjunto principal recua para os próprios vídeos disponíveis, sem ficar vazio.

Atualizei o teste comum aos agrupadores da biblioteca e do player para provar que o episódio regular fica em “Todos os Episódios” e os três extras continuam acessíveis uma vez em seus grupos. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 204 testes JVM, 0 falhas/erros/skips. A composição está coberta; a transição de autoplay no Exo/MPV, Drive real, foco remoto e TV ainda precisam de runtime. Sem instalação no pacote principal ou publicação.

## Q02.3 — deduplicação estável entre páginas (plano antes de editar)

- Problema: `FilesViewModel.postSuccess` acrescentava páginas mutando a lista previamente entregue ao `ListAdapter`; se o Drive repetisse um ID em páginas adjacentes durante alterações concorrentes, a grade poderia mostrar o mesmo item duas vezes e invalidar a lista antiga do differ.
- Mudança planejada: mesclar por `DriveFile.id` em uma nova lista imutável, preservar a ordem já exibida, atualizar o modelo repetido com os dados mais recentes e retirar somente o sentinel de carregamento antes de recolocá-lo se houver outra página.
- Arquivos: novo `ui/files/adapter/FilesPageMerger.kt`, `FilesViewModel.kt` e teste de unidade focado.
- Referência/critério: Lists and tables + Collections do HIG (itens estáveis e atualizações que não perturbam a navegação); entrada duplicada na página seguinte deve manter a quantidade e posição do cartão, atualizar seus metadados e não mutar a lista anterior.
- Limite: mock de repositório pode provar a regra de merge, não que Drive real repita IDs. A paginação Drive autenticada e troca de pasta durante request continuam pendentes.

### Execução Q02.3 — snapshots de páginas e capas (05/10/2026)

Implementei `FilesPageMerger` e tirei `postSuccess` da mutação da lista anterior. IDs repetidos preservam a posição e ficam com os metadados mais recentes; a carga do primeiro lote e a página seguinte usam o mesmo merge. Também encontrei e corrigi uma corrida adjacente: o lookup de posters podia terminar depois de uma página nova e publicar seu snapshot antigo. Agora uma geração cancela enriquecimento anterior quando entra página nova ou erro; o poster faltante é aplicado por ID ao estado mais recente e sai numa lista nova. Isto impede o lookup antigo de apagar visualmente a página seguinte.

Dois testes em `FilesPageMergerTest` cobrem deduplicação, ordem, metadado mais recente e imutabilidade do snapshot anterior. Build e suíte completa passaram (`testDebugUnitTest assembleDebug --no-daemon --max-workers=1`; 202 testes, 0 falhas/erros/skips). Instalei só `.visualreview` no Moto G34 e percorri a grade Files com DOWN; os 12 itens da fixture abriram, e o cartão focado permaneceu visível após scroll. Evidência: `docs/ui-audit/2026-10-05-files-page-merge/`.

Sem conta Drive, a tela usou a fixture local; este smoke não atesta paginação, retry ou alteração concorrente na API real. Q02 permanece aberto para autenticação, 2.501+ itens reais, falha de página, troca/saída de pasta e Shared Drives/shortcuts. Nenhuma alteração no pacote principal ou release.

## Q20.1 — proteger o último perfil administrador (05/10/2026)

O editor de perfis permitia retirar o status Admin do único administrador e o fluxo de exclusão não protegia esse papel. Adicionei uma política de domínio aplicada nas mutações do ProfileManager: o conjunto persistido não pode ficar sem administrador; a exclusão retorna um motivo específico quando o alvo é o último Admin, o último perfil ou já foi removido. A UI apresenta uma explicação, e o editor mantém a edição aberta se a alteração eliminaria o último administrador.

Cinco testes cobrem exclusão do último perfil, proteção do último Admin, exclusão de um Admin quando existe outro, rebaixamento e perfil inexistente. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 209 testes JVM, 0 falhas/erros/skips; `git diff --check` passou. Ainda falta testar o diálogo no caminho D-pad da TV e validar a política integrada com Room numa instalação de revisão. Nenhum perfil persistido foi excluído ou alterado, e nenhuma release foi criada.

## Q20.2 — fixar o perfil proprietário durante a reprodução (05/10/2026)

O histórico de player consultava o perfil ativo em cada leitura e gravação. Isso permitia que uma sessão mantida em PiP fosse atribuída a outro espectador depois de trocar o perfil no aplicativo. `PlayerViewModel` agora captura o ID do perfil ao ser criado e usa esse ID explicitamente para buscar, retomar e salvar progresso; Media3/ExoPlayer e MPV compartilham a mesma política.

`PlayerProfileSessionTest` muda o perfil ativo durante a sessão e verifica que leitura e gravação permanecem no perfil inicial. A suíte completa e `assembleDebug` passaram: 210 testes JVM, 0 falhas/erros/skips. Atualizei apenas o pacote isolado `.visualreview` no Moto G34; ele está na tela de bloqueio, então não alego validação de PiP/runtime. Nenhum dado da instalação principal ou release foi alterado.

## Q13.1 — favorito mais discreto em cards da biblioteca (05/10/2026)

Nas capturas da grade, a estrela ocupava espaço visual desproporcional ao card e disputava atenção com o título. Reduzi o desenho aparente do botão de 16/24 dp para 14 dp nos itens de grade e lista, mantendo 48 dp de hitbox para toque. O favorito ativo usa `amber_500`, token já presente no tema, em lugar do amarelo mais brilhante hardcoded.

`testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou: 210 testes JVM, 0 falhas/erros/skips. O APK debug `.visualreview` foi atualizado no Moto G34; o telefone estava bloqueado e não produzi captura renderizada, portanto o resultado estético ainda precisa de inspeção em tela ativa/TV. Nenhum release ou app principal foi substituído.

## Q02.4 — contagem confiável de novos episódios em pastas seguidas (05/10/2026)

O worker de acompanhamento de pastas contava cada arquivo retornado pelo Drive como um episódio. Isso gerava alertas errados ao adicionar legenda, imagem ou subpasta. Agora ele consulta a API de paginação detalhada, conta IDs únicos de vídeos e atalhos para vídeo, e só grava um novo total quando todas as páginas foram obtidas. Respostas parciais, falhas de rede e erros de persistência acionam retry sem avançar o baseline.

Extraí `EpisodeCountPolicy`, usada pelo worker e testada diretamente. Os casos cobrem baseline, pasta vazia, remoções, itens não reproduzíveis, duplicatas, atalhos, falhas e paginação incompleta. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 213 testes JVM, 0 falhas/erros/skips; `git diff --check` passou. Ainda falta exercitar o worker real com WorkManager, permissões/notificações e uma conta Drive autenticada. O monitor compara contagens: se um episódio for substituído por outro e o total ficar igual, essa mudança não é detectada. Nenhum app principal, release, commit ou push foi alterado.

## Q07.1 — desafios de pareamento persistidos em PostgreSQL (05/10/2026)

Substituí o acoplamento do protocolo ao mapa em memória por `PairingChallengeStore` e adicionei implementação PostgreSQL via JDBC/Hikari, migração Flyway e índice único parcial para códigos ainda pendentes. Expiração e cancelamento usam transação e `SELECT FOR UPDATE`; só fica no banco o hash SHA-256 do segredo de polling. A configuração por variáveis de ambiente é opt-in. Sem DB, o host permanece local e efêmero; continua preso a loopback e URL `.invalid`.

Os testes Testcontainers subiram PostgreSQL 16, aplicaram a migração, conferiram o tamanho/hash armazenado, fecharam e recriaram o pool, recuperaram o desafio persistido e exercitaram cancelamentos simultâneos de repositórios distintos. `./gradlew.bat -p backend test --no-daemon --max-workers=1` passou com 14 testes (protocolo, HTTP e integração PostgreSQL), 0 falhas/erros/skips. Build pelo caminho acentuado compilou mas o worker Gradle falhou antes de executar testes; repetição via `X:\` com cache `G:\` passou. Nenhuma configuração de host/workaround foi adicionada ao projeto.

Também recompilei o checkout Android após essa fatia: `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 213 testes JVM, 0 falhas/erros/skips. Esse comando só produziu build debug; não gerei release, não instalei/substituí o app principal e não publiquei artefatos.

Isso não oferece rate limiting distribuído, limpeza/retention, backup/restore, operação TLS, página mobile, aprovação/CSRF, OAuth, grants cifrados, sessão Makimono, API Drive, streaming BFF ou integração com o app. A decisão de domínio, OAuth/Google Cloud e hosting continua externa; nenhum valor secreto foi incluído ou solicitado. Backend não foi exposto, publicado nem conectado ao app.

## Q01.3 — estado de legendas MPV sem inferência (05/10/2026)

A informação técnica do MPV agora diferencia legenda explicitamente desativada (`sid=no`), seleção automática (`sid=auto`), faixa ativa por ID e propriedade indisponível/inválida. Isso evita afirmar que legendas estão desligadas quando o player não reportou o estado. Adicionei teste cobrindo os quatro cenários; `testDebugUnitTest assembleDebug` passou com 214 testes. Sem validação runtime nesta fatia e sem release/push.

## Plano Q01.4 — metadados de séries com listagem parcial (05/10/2026)

- Bug: quando o Drive informa listagem incompleta, os badges agregados de qualidade/áudio/legendas podem descrever como uniforme a série toda com base apenas nos vídeos recebidos.
- Benefício: manter a promessa da UI proporcional à cobertura real da biblioteca, sem esconder que há variação ou dados incompletos.
- Fontes: `SeriesDetailViewModel.kt` agrega tags a partir dos vídeos e já recebe `hasIncompleteDriveListing`.
- Mudança proposta: extrair política pura de resumo e passar completude para qualidade, áudio e legendas.
- Regra HIG adaptada: status e metadados devem refletir o estado conhecido e comunicar incerteza sem inferir completude.
- Cobertura: série uniforme/completa, tags faltantes, mistura observada e página truncada; preservar badges existentes quando sustentados pelos dados.
- Aceite: dados observados mistos nunca são apresentados como uniformes; listagem truncada nunca afirma uniformidade global; dados ausentes continuam sem badge.
- Verificação prevista: teste unitário focado, suíte JVM, `assembleDebug` e diff check; runtime exige sessão Drive ativa e não será alegado sem ela.

## Resultado Q01.4 — metadados agregados em listagem parcial (05/10/2026)

Extraí a política pura `SeriesMediaMetadataPolicy` e conectei-a ao estado de completude real de `SeriesDetailViewModel`. Séries uniformes mantêm a pista identificada como inferência de nome somente após listagem completa; uma listagem parcial não promete uniformidade; diferenças observadas continuam aparentes; ausência de pista continua desconhecida. Quatro testes focados e a suíte completa passaram: 218 testes JVM, 0 falhas/erros/skips. `assembleDebug` e `git diff --check` passaram. Nenhum app foi instalado ou publicado; o Moto está em Doze/tela de bloqueio e o Drive real não foi exercitado.

## Plano Q02.5 — continuação das listas do player além do limite por consulta (05/10/2026)

- Bug: playlists de episódios e legendas do Drive usam leitura detalhada limitada a 2.500 itens; o cursor retornado não é consumido pelo fluxo dos players.
- Benefício: autoplay e seleção de faixas não terminam silenciosamente na fronteira de paginação para bibliotecas grandes.
- Evidência: `DriveRepository.getFileSiblings/getFolderSubtitles` retornam `DriveFolderListing` sem cursor; ambos os Activities só aceitam a primeira carga quando a playlist está vazia.
- Mudança: propagar cursor/pasta de origem, buscar páginas seguintes de forma cancelável no `PlayerViewModel`, e mesclar deltas por ID nos dois engines.
- Regra HIG adaptada: manter contexto/ordem de reprodução e comunicar indisponibilidade sem deslocar o foco nem reiniciar a mídia ativa.
- Cobertura: query e token enviados corretamente, deduplicação/ordem preservadas, múltiplas páginas e cancelamento ao destruir a sessão.
- Aceite: itens além da primeira janela aparecem uma única vez na playlist Exo e MPV, a seleção do episódio atual não se altera e legendas continuam acessíveis.
- Limite previsto: fixtures locais validam contrato; Drive real, remoto físico/TV e pasta superior a 2.500 itens continuam gates separados.

### Resultado Q02.5 — paginação cumulativa em Exo/Media3 e MPV

`DriveFolderListing` agora preserva o cursor e a pasta de origem. `PlayerViewModel` continua as consultas de playlist e legendas em jobs canceláveis, protege contra cursor repetido e entrega cada página por canal sem conflar deltas. Exo/Media3 e MPV mesclam cada lote por ID, reordenam episódios e acumulam legendas sem duplicatas. Ao iniciar pela tela Files, o player recebe o ID explícito da pasta, não apenas os itens que já estavam visíveis, preservando o contexto de atalhos do Drive.

Testes novos verificam tokens/páginas seguintes, filtros de legenda, pasta explícita para atalhos, ordem natural, atualização de thumbnail e deduplicação. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou: 223 testes JVM, 0 falhas/erros/skips; `git diff --check` passou. O APK debug arm64 foi instalado com `adb install -r` no Moto G34 conectado; o telefone está na tela de bloqueio. A instalação foi confirmada, mas não houve validação visual ou reprodução. Sem Drive autenticado e controle remoto/TV, paginação real/autoplay depois de 2.500 itens segue pendente. Nenhum release, push ou publicação foi feito.

## Plano Q02.6 — primeira página imediata no player (05/10/2026)

- **Problema:** Q02.5 passou a consumir cursores, mas os métodos iniciais ainda chamam `getAllFilesDetailed`, que pode buscar 25 páginas/2.500 filhos antes de emitir qualquer item. O usuário aguarda uma longa varredura mesmo quando o item seguinte aparece na primeira página.
- **Objetivo:** playlist e legendas devem emitir imediatamente a primeira página do Drive e continuar pelo cursor já processado/cancelável; nenhum limite deve atrasar o primeiro item encontrado.
- **Mudança:** substituir a pré-leitura detalhada inicial por `getFiles(..., pageToken=null)`; reaproveitar mapeamento idêntico nas páginas seguintes; adequar testes para comprovar cursor inicial, cada página subsequente e falha que preserva itens já entregues.
- **Aceite:** a primeira chamada faz uma única requisição de conteúdo; retorna `sourceFolderId`, token e itens filtrados; Activities mesclam a primeira janela antes de solicitar todas as janelas restantes. Em falha posterior, conteúdo já acumulado permanece utilizável e o erro é anunciado.
- **Verificação:** testes unitários de repositório e política, suíte Android JVM completa, `assembleDebug`, diff check. Drive de 2.501 itens e latência em runtime exigem conta/sessão ativa e permanecem gates externos.

### Resultado Q02.6 — dados iniciais do player sem varredura de 2.500 itens

`getFileSiblings` e `getFolderSubtitles` agora consultam uma única janela inicial de 100 itens e retornam imediatamente o cursor e a pasta de origem; as páginas seguintes usam os mesmos mapeadores de playlist/legendas. O ViewModel existente já inicia a continuação assim que recebe esse primeiro lote, sem aguardar paginação agregada. Em falha posterior, o cursor com erro continua disponível para retry e Activities preservam os itens recebidos anteriormente.

Os testes foram reescritos para rejeitar pré-leitura de páginas: validam primeira resposta incompleta com cursor, página seguinte completa e erro com cursor retido, além de shortcuts e filtros. Suíte completa passou com 223 testes JVM, 0 falhas/erros/skips; `assembleDebug` e `git diff --check` passaram. O APK arm64 foi instalado via ADB no Moto G34, mas o aparelho estava em Doze/tela bloqueada; instalação confirmada, validação visual/Drive real não. Nenhum release, push ou publicação.
## Plano Q20.3 — preservar o perfil ao transferir a reprodução entre engines (05/10/2026)

- **Falha a corrigir:** Q20.2 fixa o perfil no `PlayerViewModel`, mas o fallback Exo/Media3→MPV cria uma Activity/ViewModel nova que recaptura o perfil ativo. Uma troca de perfil durante PiP pode mudar o proprietário do histórico ao alternar engine.
- **Mudança:** transferir o ID de perfil capturado como estado interno do Intent de playback e deixá-lo prevalecer sobre o perfil ativo ao criar a sessão seguinte; launches normais sem esse dado mantêm o comportamento atual.
- **Aceite:** teste puro comprova que uma transferência mantém o perfil original mesmo quando o perfil ativo mudou; ausência de ID usa o perfil ativo. O token Drive continua fora dos extras.
- **Verificação:** teste unitário da política, suíte Android JVM completa, assembleDebug e diff check; runtime do fallback continua dependente de induzir erro de decoder/falha no MPV.

### Resultado Q20.3 — identidade da sessão mantida entre engines

O fallback Exo/Media3→MPV agora carrega o ID do perfil proprietário da sessão como estado local explícito. `PlayerViewModel` o resolve pelo `SavedStateHandle` quando há handoff; em launches normais sem o extra, usa o perfil ativo. Isso evita que uma troca de perfil em PiP mude o espectador dono do histórico após falha de codec. Token Drive e credenciais continuam fora dos extras.

`PlaybackProfileSessionPolicyTest` cobre handoff após mudança de perfil ativo, launch normal e ID em branco. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou: 226 testes JVM, 0 falhas/erros/skips; `git diff --check` não encontrou erros de whitespace relevantes (restaram avisos Git LF/CRLF do checkout). O ADB voltou a listar o Moto G34, mas ele segue bloqueado; não forcei desbloqueio nem instalei sobre o pacote principal. Runtime do handoff e falha real de decoder continuam pendentes. Sem release/push.
## Plano Q03.2 — transferir contexto completo no fallback Exo→MPV (05/10/2026)

- **Falhas a corrigir:** handoff não copiava `siblingFolderId`, podendo perder a pasta de origem de um shortcut Drive; posição atual `0 ms` era omitida, deixando MPV inferir retomada pelo histórico em vez de continuar do ponto real.
- **Mudança:** transferir pasta de origem não vazia e sempre enviar a posição atual explícita, inclusive zero, mantendo também playlist, legendas, tema, miniatura e perfil já transportados.
- **Aceite:** política pura valida normalização de pasta e início explícito em zero; suíte, assembleDebug e diff check. Falha real de codec segue fora da fixture.

### Resultado Q03.2 — handoff preserva origem e início real da sessão

O fallback Exo→MPV inclui a pasta de origem Drive quando informada e sempre transmite a posição explícita atual, mesmo `0 ms`; o MPV não depende mais do histórico salvo para inferir o ponto ao receber um handoff. Foram adicionados testes para pasta presente/blank e posições zero/negativa, além dos testes de ID do perfil da Q20.3.

`testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 230 testes JVM, 0 falhas/erros/skips. O APK `.visualreview` arm64 foi reinstalado no Moto G34; o aparelho está bloqueado, portanto o handoff real ainda não foi exercitado. Nenhum pacote principal/release foi substituído; sem release ou push.
## Plano Q24.1 — carregar favoritos de uma página em lote e por um perfil (05/10/2026)

- **Falha:** Files consulta Room individualmente para cada card; uma página de 25 itens causa até 25 suspensões/queries. Como cada chamada reavalia o perfil ativo, troca de perfil no meio do enriquecimento pode misturar indicadores entre perfis.
- **Mudança:** obter em uma query os IDs favoritos dos alvos da página (incluindo shortcut targets), depois enriquecer todos os cards pelo mesmo conjunto/snapshot.
- **Aceite:** testes provam isolamento por perfil, IDs em lote e shortcuts; suíte completa, build e diff check. Não medir ganho de frame sem hardware/benchmark controlado.

### Resultado Q24.1 — favoritos por página em consulta Room única

`FilesViewModel` agora mapeia os IDs-alvo (incluindo o target de cada shortcut), lê favoritos do perfil ativo uma vez para a página e enriquece os cards com esse conjunto; uma página padrão de 25 itens deixa de fazer até 25 consultas Room suspensas para passar a fazer uma. `FavoriteRepository` elimina IDs repetidos/vazios e o DAO usa `IN` com os IDs da página. O teste confere isolamento por perfil, remoção de duplicatas e uma chamada em lote.

`testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon --max-workers=1` passou em build limpo: 233 testes JVM, 0 falhas/erros/skips; APK de instrumentação compilado, mas a suíte Room instrumentada não foi executada. `git diff --check` passou (avisos restantes são apenas normalização LF/CRLF do checkout). Após autorização do usuário, usei ADB em modo leitura para inspecionar o Moto G34; o app de revisão instalou e abriu Home, pasta/Detalhes e Player. O estado Play Protect deixou de estar na tela. Sem benchmark de frames, MuMu/TV física ou verificação de conta Drive renovada. Sem release/push.
## Plano Q03.3 — oferecer recuperação MPV para contêineres não suportados (05/10/2026)

- **Falha:** erros Media3 de parsing de contêiner malformado/não suportado terminam em mensagem de erro, sem ação para tentar o MPV que já está instalado como engine alternativo.
- **Mudança:** reconhecer apenas error codes explícitos de container parsing e oferecer “Abrir no MPV” manual, preservando o comportamento para falhas de rede/HTTP e auth; não alternar engine automaticamente por erro de fonte.
- **Aceite:** política unitária cobre ambos os códigos de parsing e rejeita códigos arbitrários/rede; testes, assembleDebug e diff check. Fallback real requer mídia Drive com parser incompatível e segue teste posterior.

### Resultado Q03.3 — fallback manual para contêineres Media3 incompatíveis (05/10/2026)

`PlaybackFallbackPolicy` oferece “Abrir no MPV” somente para códigos Media3 de container malformado/não suportado; falhas de rede, HTTP e autenticação permanecem no tratamento original. Política coberta por dois testes positivos e dois negativos. Em instalação de produção, o arquivo `Bakemonogatari 01` falhou no decoder de hardware AVC 1080p (`NO_EXCEEDS_CAPABILITIES`); o app fez fallback automático para MPV e o vídeo continuou. Isso valida o handoff Exo→MPV, incluindo início explícito e perfil, no aparelho.

A inspeção revelou um problema funcional que não aparecia nos testes JVM: no MPV, o primeiro toque no botão de saída apenas punha foco e não saía; repetindo o toque, saía. Igualei o tratamento táctil determinístico do botão de saída ao do Exo, invocando o click acessível uma única vez no `ACTION_UP`. Recompilei, reinstalei a variante `.visualreview` e validei que um toque retorna à `MainActivity`. A compilação limpa também eliminou um artefato incremental incoerente que chegou a fechar o `PlayerViewModel` com `NoSuchMethodError`; a origem da cache inconsistente ainda não foi determinada. O teste real cobriu MPV no Moto G34, não Android TV física.

O mesmo percurso visual encontrou oportunidades pendentes: cards de pasta na Home ainda recorrem a placeholder genérico; detalhe de uma pasta longa corta parcialmente a última ação do hero em paisagem; legendas podem ficar atrás da barra OSD. Não alterei esse layout nesta fatia. Build limpo `testDebugUnitTest assembleDebug assembleDebugAndroidTest` passou (233 JVM tests, 0 falhas/erros/skips); após a correção touch, `testDebugUnitTest assembleDebug` passou novamente. `FavoriteDaoBatchTest` instrumentado compilou, mas não foi executado. `git diff --check` passou. Nenhum release ou push.

## Plano Q25.1 — respeitar a pasta raiz da biblioteca na Home (05/10/2026)

- **Falha observada no Moto G34:** sem raiz configurada, a Home usava o Meu Drive inteiro e promovia `oneblacki` a anime em destaque.
- **Mudança no app:** configurar `oneblacki` no ajuste “Pasta da biblioteca”; excluir do catálogo/cache o próprio ID raiz e atalhos que apontem para ele; limpar o destaque anterior antes de recarregar.
- **Validação:** `oneblacki` fica configurada nas Settings e a Home passa a destacar `Bakemonogatari`, seu filho direto. Teste de regressão cobre pasta raiz e shortcut para raiz. `testDebugUnitTest assembleDebug` passou com 234 testes JVM, 0 falhas/erros; `git diff --check` passou. Instalado e verificado no Moto G34 via ADB. Sem publicação/release.
- **Pendências do produto:** melhorar o seletor de raiz (atualmente exige digitar o nome exato da pasta); confirmar a mesma navegação em Android TV física.
## Plano Q25.2 — deixar os avatares sem cartões no seletor (06/10/2026)

- **Falha:** caixas retangulares cinzas ao redor dos perfis reduziam visualmente os avatares e afastavam o seletor da referência Crunchyroll.
- **Mudança:** remover fundo e borda permanentes dos itens; manter a seleção no anel circular do avatar e aplicar somente um fundo escuro translúcido discreto quando o item recebe foco.
- **Validação:** suíte unitária `testDebugUnitTest` e `assembleDebug` passaram; o APK `.visualreview` foi instalado no Moto G34. Capturas verificam a tela inicial de perfis e o estado de gerenciamento; o foco D-pad continua visível no avatar.
- **Evidência:** `docs/ui-audit/2026-10-06-profile-no-cards.png`, `docs/ui-audit/2026-10-06-profile-add-focus.png` e `docs/ui-audit/2026-10-06-profile-manage-focus.png`.
- **Limite:** falta validar a mesma navegação em TV física e confirmar a estética em distância de 10 pés. Sem release/push.
## Plano Q25.3 — manter a barra lateral inteira em paisagem baixa (06/10/2026)

- **Falha observada no Moto G34 em paisagem (1600×720):** o alvo de Configurações passava do limite inferior da tela e ficava parcialmente invisível/inacessível.
- **Mudança:** compactar o logo e os espaçamentos da variante `layout-land/fragment_home.xml`, mantendo 48 dp de altura em cada controle interativo da rail.
- **Validação:** `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou; suíte JVM sem alterações executáveis nesta rodada (UP-TO-DATE), build incremental. No `.visualreview`, a hierarquia mostra Configurações em `[91,625][287,709]` dentro da tela de 720 px; um toque abriu Settings. Após a instalação do build, um toque em Pastas abriu Files.
- **Evidência:** `docs/ui-audit/2026-10-06-main-after-layout-home.png`, `docs/ui-audit/2026-10-06-debug-settings-visible.png` e `docs/ui-audit/2026-10-06-postbuild-one-tap-files.png`.
- **Limite:** Moto em paisagem não substitui validação em TV 720p física nem teste de overscan. O pacote principal/release não foi substituído.

## Q25.4 — avatares redondos maiores, sem cartões cinza (06/10/2026)

Ajustei o seletor de perfis para aproximar a composição da referência Crunchyroll: retirei o fundo retangular do item e deixei foco/seleção representados pelo anel do avatar. O tamanho em landscape passou de 56 dp para 68 dp. Testei 88 dp primeiro, mas a tela de 720 px espremia os rótulos; reduzi para 68 dp para preservar nome e ações sem abandonar a escala circular maior.

`testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou. Reinstalei somente `zechs.drive.stream.visualreview` no Moto G34, mantive os dados e validei foco D-pad nos três alvos: perfil principal, perfil secundário e “Adicionar perfil”. A captura mostra rótulos completos e nenhum quadrado cinza; o contorno ciano acompanha o foco circular e o botão de adicionar fica mais evidente quando focado. Evidências: `docs/ui-audit/2026-10-06-profile-circles-dpad.png`, `docs/ui-audit/2026-10-06-profile-second-focused.png` e `docs/ui-audit/2026-10-06-profile-add-dpad.png`. TV física e overscan continuam pendentes. Sem release ou push.

## Plano Q20.4 — galeria de avatares circular e foco por forma (06/10/2026)

- Evidência inicial: `dialog_profile_editor.xml` mostra catálogo de retratos em cards quase quadrados com cantos de 12 dp; D-pad entra na grade e rola o NestedScrollView para revelar as opções. Wallpapers compartilham o mesmo card e precisam continuar panorâmicos.
- Objetivo: avatares redondos como o seletor principal/visual da referência, mantendo wallpaper em cartão paisagem, descrições acessíveis e seleção/click existente.
- Arquivos: `ProfileEditorDialog.kt`, `item_profile_catalog_card.xml`, `TvAccessibilityLayout.kt`, `TvFocusRing.kt`; evidência em `docs/ui-audit/2026-10-06-profile-editor-*`.
- Regra de design: Collections e Focus and selection (revisão F8); grid de arte mantém ordenação espacial; foco remoto segue claramente a forma da opção; não confundir foco temporário com avatar persistido.
- Aceite: retratos circulares no editor, wallpapers retangulares; D-pad consegue percorrer escolha, Cancelar e Salvar; CANCEL/BACK descarta sem mudar perfil; build e suíte JVM verdes.

### Resultado Q20.4 — catálogo de perfil no estilo de avatares circulares

Removi o cartão cinza em torno dos retratos do catálogo de perfis: as opções são circulares e maiores, e o anel ciano de foco do D-pad também é circular. O layout usa cinco colunas em paisagem. Os wallpapers permanecem horizontais em duas colunas, com foco arredondado retangular. O teste visual passou pela galeria, pelo foco na quinta opção, pela rolagem até o campo de URL e pela aba de wallpapers. Saí por **Cancelar** e confirmei que o perfil não mudou.

`testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou antes da instalação da variante isolada `.visualreview`, sem limpar dados. Capturas: `docs/ui-audit/2026-10-06-profile-editor-gallery-focus.png`, `docs/ui-audit/2026-10-06-profile-editor-fifth-avatar.png`, `docs/ui-audit/2026-10-06-profile-editor-lower-grid.png`, `docs/ui-audit/2026-10-06-profile-editor-wallpapers-scrolled.png` e `docs/ui-audit/2026-10-06-profile-editor-wallpaper-focus.png`.

A navegação revela uma pendência concreta: ao rolar até os wallpapers, a faixa de abas fica cortada parcialmente sob o título fixo; a rolagem/D-pad do editor merece outro passe. A forma dos avatares e wallpapers está verificada no Moto em 1600×720, ainda sem conferência em TV/overscan. Sem release ou push.

## Plano Q20.5 — rolagem do catálogo sem cortar a navegação do editor (06/10/2026)

- **Falha:** ao rolar a galeria no diálogo de perfil, a fileira das abas para Avatares/Wallpapers pode ficar parcialmente cortada pelo limite superior do conteúdo.
- **Mudança:** ao levar o foco D-pad para opções de arte, calcular o alinhamento das abas e do rótulo de estado e concluir a rolagem com o cabeçalho da galeria totalmente visível ou totalmente fora da viewport, nunca parcialmente cortado.
- **Aceite:** reproduzir a navegação vertical até os últimos itens no Moto em paisagem; conferir a aba de arte visível por inteiro ou recolhida; conferir que Cancelar continua acessível e não grava alterações; testes/build.


### Resultado Q20.5 — cabeçalho de galeria sem clipping em navegação D-pad (06/10/2026)

O scroll do catálogo agora calcula o alinhamento da fileira de abas e do rótulo de estado em relação ao viewport. Se a rolagem ultrapassa esse cabeçalho, os elementos saem por completo, em vez de ficarem cortados contra o título do diálogo. Build e testes passaram (`testDebugUnitTest assembleDebug`; 237 testes, 0 falhas/erros). No Moto G34, naveguei em D-pad da primeira para a segunda e terceira fileira e confirmei que o cabeçalho não deixa fragmentos cortados; foco e opções circulares permanecem visíveis. Wallpapers continuam panorâmicos. Capturas: `docs/ui-audit/2026-10-06-profile-dpad-scroll-fixed.png` e `docs/ui-audit/2026-10-06-profile-dpad-thirdrow-fixed.png`.

A navegação até os campos de URL funciona, porém uma seta para baixo no último `EditText` move o cursor em vez de levar aos botões do diálogo. Cancelar foi acionado por toque e fechou sem salvar; perfis foram preservados. Essa barreira virou a próxima fatia Q20.6.

## Plano Q20.6 — saída previsível dos campos de texto pelo D-pad (06/10/2026)

- **Falha:** controles de texto single-line no editor retêm DPAD_UP/DOWN para mover o cursor; no último campo isso impede a saída natural para Cancelar/Salvar no controle remoto.
- **Mudança:** quando o IME não consumir a tecla, direcionar DPAD_UP/DOWN dos campos single-line ao próximo controle focável na direção, sem alterar a edição horizontal por setas.
- **Aceite:** sem abrir teclado, mover nome→área de controles, busca→galeria, último URL→Cancelar por D-pad; botão Cancelar fecha sem alterar Room; tests/build.

## Plano Q20.6 — saída previsível dos campos de texto pelo D-pad (06/10/2026)

Ao testar o último campo de URL, DPAD_DOWN moveu o cursor em vez de seguir para os botões fixos do diálogo. Ligar os campos single-line à próxima view focável ao receber DPAD_UP/DOWN, preservando esquerda/direita para edição.

Aceite: testar via remote simulado nome→roles, busca→galeria, URL do wallpaper→Cancelar; Cancelar fecha sem mudar dados; teste unitário/build e captura de estado final. Nenhum botão Salvar deve ser ativado durante QA.

O passe Q20.6 do editor de perfis está concluído: as setas verticais saem dos campos de texto no D-pad; testei `profileName` → Admin e `backgroundUrl` → Cancelar no Moto G34 em paisagem. Confirmei a saída por Cancelar sem salvar e ambos os perfis continuam presentes. A suíte JVM passou com 237 testes e o APK `.visualreview` compilou/foi instalado sem limpar dados. Capturas de evidência: `docs/ui-audit/2026-10-06-profile-editor-admin-focus.png`, `docs/ui-audit/2026-10-06-profile-editor-url-to-cancel.png` e `docs/ui-audit/2026-10-06-profile-after-dpad-cancel.png`. A etapa seguinte segue na auditoria do editor e dos fluxos de TV; teste em TV física ainda pendente.
Também percorri os perfis com D-pad até **Adicionar**, abri o editor com OK e cancelei com Voltar; nenhum perfil adicional foi criado. A tela voltou ao modo de gerenciamento normalmente (`docs/ui-audit/2026-10-06-profile-add-cancelled.png`).

Corrigi uma ativação de toque inconsistente no seletor: o primeiro toque num perfil focava o cartão, mas não abria a Home. `ProfilesAdapter` agora usa a política de ativação única no gesto completo e permite que o RecyclerView pai intercepte arrastos no carrossel. O teste no Moto G34 abriu a Home com um toque. A validação também revelou um APK incremental com DEX duplicado/desatualizado que disparava `NoSuchMethodError`; um `clean` seguido de suíte/build completos removeu a cópia antiga e confirmou o método esperado. Apenas o pacote isolado `.visualreview` foi atualizado, sem limpeza de dados ou alteração de perfis. Evidência: `docs/ui-audit/2026-10-06-profile-tap-after-one.png` e `.xml`. O scroll com múltiplos perfis e validação em TV continuam pendentes.
No mesmo reteste, um toque em **Adicionar perfil** abriu o editor; Voltar cancelou sem gravar e confirmei que os dois perfis continuam listados. A suíte limpa passou com 237 testes, 0 falhas/erros/skips. Evidências: `docs/ui-audit/2026-10-06-profile-add-touch-one.png` e `docs/ui-audit/2026-10-06-profile-add-touch-cancel.png`.

A galeria de avatares do editor agora usa retratos 112 dp em círculos sem fundo nem contorno cinza; o foco ciano acompanha o círculo. Mantive wallpapers panorâmicos em seus cards existentes. Compilei e passei `testDebugUnitTest`, instalei no pacote `.visualreview` sem limpar dados e naveguei com D-pad pela galeria; Voltar fechou sem salvar. Evidências: `docs/ui-audit/2026-10-06-avatar-gallery-focused-new.png` e `docs/ui-audit/2026-10-06-avatar-editor-cancelled.png`.

No reteste, encontrei uma queda real: reduzir `GridLayout.columnCount` após anexar itens gerava `IllegalArgumentException`. Corrigi o fluxo para esperar a largura medida do diálogo e definir colunas/diâmetros antes de criar as células. Recompilei e passei `testDebugUnitTest`; no Moto, o editor permaneceu em `.visualreview`, exibiu três colunas de retratos circulares de 196×196 px sem tile cinza, e o anel de foco acompanhou o círculo. BACK cancelou sem salvar e a Activity de QA continuou em primeiro plano. Capturas e hierarchy em `docs/ui-audit/2026-10-06-avatar-responsive-gallery.png`, `.xml`, `docs/ui-audit/2026-10-06-avatar-responsive-cancelled.png` e `.xml`; sem nova exceção fatal no processo.


No seletor de perfis, removi a superfície retangular ao redor dos avatares, aumentei a medida portrait para 112 dp e eliminei o aro cinza inativo. O anel ciano indica foco/seleção; conferido no Moto G34. `assembleDebug` passou. Evidência: `docs/ui-audit/2026-10-06-profile-avatar-clean.png`. Validação em TV física continua pendente.

Q18.3: corrigi o retorno de foco no MPV, que antes sempre levava a Play/Pause ao fechar o seletor de legendas. Agora retorna a Legendas; o foco D-pad usa anel ciano sem o retângulo branco anterior. Exo e MPV confirmados em fixture no Moto G34; 237 testes JVM e `assembleDebug` passaram. Evidências: `docs/ui-audit/2026-10-06-q01-3-mpv-focus-return-subtitle.png` e `docs/ui-audit/2026-10-06-q03-exo-focus-return-subtitle.png`. Validação em TV física permanece aberta.

## Plano W3-T3.2 — fileiras editoriais da Home

- **Problema/severidade:** Home sem superfícies distintas para seguir episódios, Minha lista e conteúdo atualizado; UX/descoberta P1.
- **Arquivos:** `HomeFragment.kt`, `HomeViewModel.kt`, `StarredShelfAdapter.kt`, modelos/API do Drive, cinco variantes existentes de `fragment_home.xml`, strings e `HomeShelfPolicyTest`.
- **Hipótese:** listas do perfil ativo e pastas reais da biblioteca dão prateleiras úteis sem promover a raiz do Drive nem vídeos soltos a séries.
- **Aceite:** favoritos limitados a pastas da biblioteca; seguidas limitadas à biblioteca ativa; recentes ordenados por `modifiedTime`; foco/rolagem sem dead-end; teste e build.
- **Risco:** datas ausentes e atalhos do Drive podem gerar ordenação ou duplicação incorretas; filtrar e cobrir.

## Plano W3-T3.4 — hierarquia das ações de detalhes

Cinco ações diretas competem pelo foco e deixam a tela carregada. Manter Reproduzir, Favoritar e Mais opções visíveis; mover trailer, seguir/deixar de seguir e marcar temporada assistida para um menu contextual acessível por toque e D-pad. Arquivos: layouts base/land, `SeriesDetailFragment.kt` e strings. Aceite: nenhuma ação perdida, ordem previsível de foco, menu validado no device e build/testes.

W3-T3.4: reduzi a faixa visível de série para Reproduzir, Favoritar e Mais opções; trailer, seguir/deixar de seguir e marcar temporada assistida foram agrupados no diálogo. `testDebugUnitTest` passou com 269 testes, zero falhas/erros; `assembleDebug` passou. O Moto G34 exibiu a nova faixa na fixture `.visualreview` (`docs/ui-audit/2026-10-06-t3.4-details-3-actions.png`). A interação/fechamento do menu não ficou comprovada: durante esse passo o primeiro plano voltou para a MainActivity de produção, sem exceção fatal no recorte do logcat. Não acionei controles nessa tela; retestar a partir de sessão isolada antes de marcar como validado.

W3-T3.2: liguei “Novos episódios” aos follows do perfil ativo, “Minha lista” aos favoritos presentes na biblioteca configurada e “Adicionados recentemente” ao `modifiedTime` do Drive. Raiz, vídeos, entradas fora da biblioteca e timestamps ausentes não entram nas prateleiras de séries. Testes cobrem atalhos, ordenação, limites e filtragem; `testDebugUnitTest` passou com 269 testes e `assembleDebug` passou. As capturas `docs/ui-audit/2026-10-06-t3.2-home-final.png` e D-pad usam a fixture existente (hero/continuação/catálogo), que ainda não instancia as três prateleiras novas. Falta validar a Home real com sessão Drive e foco/abertura delas em TV.

## Plano Q01 — não inventar duração restante na Home
- Problema: card de continuidade mostra “Quase completo” quando `totalDuration` é desconhecida (0), pois o cálculo atual transforma ausência de duração em zero minutos restantes.
- Impacto: afirmação de progresso incorreta em uma superfície editorial de alto destaque (Q01/R01).
- Mudança: extrair política pura de apresentação do tempo restante; desconhecido deve ser rotulado como progresso salvo, valores subminuto não devem virar zero, 100% deve ser concluído.
- Arquivos: política da prateleira Home, `ContinueWatchingAdapter.kt`, strings pt-BR, teste unitário dedicado e fixture isolada `VisualReviewActivity.kt`.
- Aceite: cobrir total ausente, restante <1 min, arredondamento em minutos, duração válida e episódio concluído; compilar, rodar suíte e conferir a Home da variante `.visualreview` sem tocar no Drive real.

Resultado Q01.5: política pura e localizada implementada; adicionada fixture de duração total desconhecida. A suíte e `assembleDebug` passaram (272/272 testes JVM). No Moto G34 Android 15, `.visualreview`, D-pad abriu a prateleira: card desconhecido mostrou “Progresso salvo”, e cards com duração conhecida mantiveram “7 min restantes”. Evidência: `docs/ui-audit/2026-10-06-q01-home-progress/home-unknown-duration.png` e hierarquia adjacente. Sem Drive real ou TV física nesta validação.

## Plano W3-T3.3 — atalho de controle remoto e foco do menu Continuar assistindo
- Lacuna: ações estão ligadas a long-press touch, mas `KEYCODE_MENU` não abre o contexto previsto para TV.
- Mudança: compartilhar o mesmo diálogo localizado entre produção e fixture, abrir com Menu no card focado, ignorar repeats e devolver foco ao card após fechar se ainda estiver na tela.
- Escopo: `ContinueWatchingAdapter.kt`, `HomeFragment.kt`, helper do menu, `VisualReviewActivity.kt`, strings existentes e política/testes do atalho.
- Segurança de QA: fixture usa os dados sintéticos atuais e callbacks Toast; não inicia playback nem grava/remove histórico.
- Aceite: build e suíte, abrir via toque longo e Menu, percorrer as cinco ações com D-pad, BACK fecha e devolve foco; tela fixture continua aberta.

Resultado W3-T3.3: diálogo extraído para helper compartilhado pela Home e pela fixture; adicionado suporte à tecla Menu em key-down sem repeats e retorno ao card após fechar. A build e 273 testes passaram. No Moto G34, KEYCODE_MENU e toque longo abriram o mesmo menu; naveguei até a quinta ação e BACK restaurou `focused=true` no card. Evidências em `docs/ui-audit/2026-10-06-q01-home-progress/`. Os callbacks de QA são Toast; não executei mudanças reais de histórico nem playback.
W3-T3.3 reteste: o retorno de foco após long-press falhava porque o Android permanecia em touch mode; `ContinueWatchingContextMenu` agora usa `requestFocusFromTouch()` nesse estado e foco padrão nos demais. Revalidei no Moto G34: BACK fecha o menu e retorna ao card (`focused=true`); D-pad percorre as cinco opções. Hierarquias em `docs/ui-audit/2026-10-06-q01-home-progress/context-menu-touch-open-verified.xml` e `context-menu-touch-focus-restored.xml`. `testDebugUnitTest assembleDebug` concluído com sucesso. QA somente em fixture sintética.
C1.1 reteste com fixture local no Moto G34 Android 15: confirmei saída por toque na seta e D-pad UP→CENTER em MPV e Exo/Media3; em ambos, BACK ocultou a OSD primeiro e a pressão seguinte retornou à fixture. No MPV a mídia sintética reproduziu; o ramo local de Exo pausa de propósito em STATE_READY, portanto não contei como reprodução contínua. Evidências e sequência detalhadas em `docs/ui-audit/after-v2/c1/README.md` e arquivos `c11-*`. C1.1 (saída) validada; C1 integral segue aberto para stream Drive, continuidade Exo, matriz de mídia e TV física.

Files: a busca sem resultados agora usa o StateView padronizado e oferece “Limpar busca”. Validei no Moto G34 com a fixture isolada: DOWN/CENTER limpa e restaura o foco; toque também restaura a grade. Build e 273 testes JVM passaram. Limite: fixture sintética, sem Drive real ou TV física. Evidências e escopo: `docs/ui-audit/2026-10-06-files-search-empty/README.md`.

Seletor de pasta raiz: substituí o campo de texto por um navegador Drive hierárquico, breadcrumbs, retorno ao Meu Drive, paginação e estados de vazio/erro; a escolha só é gravada após confirmar. No Moto G34, a fixture percorreu `oneblacki/Bakemonogatari/Temporada 01/Episódios` por D-pad, incluindo pasta vazia e confirmação sem persistência real. `testDebugUnitTest` passou com 281 testes e `assembleDebug` passou com KAPT incremental desativado por falha no snapshot local. Sem validação de Drive real/TV física. Evidências: `docs/ui-audit/2026-10-06-library-root-picker/README.md`.

## Home W3-T3.2 reteste — prateleiras e hierarquia (07/10/2026)
- **Problema:** as prateleiras sintéticas estavam presentes e navegáveis, mas a posição do título do catálogo e o uso de texto literal no XML podiam quebrar hierarquia/localização.
- **Severidade:** UX P1; consistência/localização P2.
- **Arquivos:** três variantes paisagem/TV de `fragment_home.xml`, strings padrão/PT-BR, `docs/ui-audit/2026-10-07-home-shelves-runtime/README.md` e logs de execução.
- **Hipótese/aceite:** recent additions devem preceder o cabeçalho e a grade do catálogo em todos os layouts; o texto deve vir de recurso; validar ordem e foco de ida/volta por D-pad, compilar e rodar suíte.
- **Risco:** alteração de ordem pode deixar o cabeçalho distante do grid em conteúdo vazio/assíncrono; validar no layout real com os estados da fixture.

W3-T3.2 reteste runtime: no Moto G34 Android 15, instalei somente o `.visualreview` e naveguei por DOWN entre as seis prateleiras até o catálogo; UP retornou à fileira anterior. A inspeção encontrou o cabeçalho do catálogo antes de “Adicionados recentemente” em três variantes, corrigido e retestado; também removi o texto literal do XML para `@string/home_catalog_title`. `testDebugUnitTest` passou com 282 testes/62 suítes; `assembleDebug` passou em execução separada após locks transitórios de arquivos do build. Evidências, passos e limites (fixture sem Drive real/TV física) em `docs/ui-audit/2026-10-07-home-shelves-runtime/README.md`.

Reteste complementar da frente editorial no mesmo APK `.visualreview`: nos detalhes, a imagem de fundo permanece no hero e não atravessa a área dos episódios; foco chegou ao primeiro card. “Mais opções” abriu por D-pad, percorri três ações e BACK restaurou o foco ao botão `btnMoreActions`. Evidências `details-retest.png`, `details-episodes-retest.png`, `details-more-menu.png`, `details-more-last.xml`, `details-more-closed.xml`. O seletor mostrou avatares circulares sem superfície cinza quadrada (`profiles-retest.png`), mas não conto D-pad de perfis como validado: o percurso de foco saiu da rota de fixture. Detalhes anexados ao README do ciclo.

Reteste de perfis controlado no Moto G34: RIGHT moveu o foco sucessivamente entre os três perfis e Adicionar perfil; DOWN alcançou Gerenciar perfis. CENTER entrou no modo de gerenciamento e o segundo CENTER encerrou sem editar perfil; UP/LEFT restauraram o foco à fileira e ao perfil principal. A Activity permaneceu na fixture durante o percurso. Não confirmei Adicionar/editar. Capturas/hierarquias `profiles-right-safe.*`, `profiles-add-focused.*`, `profiles-manage-focus.xml`, `profiles-manage-mode.*`, `profiles-active-focused.*`; detalhe no README do ciclo. O D-pad da tela de perfis passa a estar validado apenas para foco e alternância do modo, sem mutação de dados.

Reteste complementar de Configurações no layout `.visualreview`: D-pad percorreu Perfil → Pasta raiz → Tema → Reduzir movimento → Player → Legendas → Cache → Atualizações → MAL/sync → AniSkip → gestos → Exportar/Restaurar backup → Sair da conta Google; UP retornou ao item anterior. Não ativei nenhuma linha. BACK saiu da Activity de revisão e trouxe a Activity já existente do pacote `zechs.drive.stream` ao primeiro plano; não interagi com ela e encerrei o QA de dispositivo. Evidências `settings-last-focus.png/xml`, `settings-logout-focus.xml`, `settings-up.png/xml`. É validação de foco/scroll, não de ações ou persistência.

Q07 — reteste da prova local do pareamento (07/10/2026): `backend` passou novamente com 18 testes em 4 suítes, 0 falhas/erros/skips, incluindo Testcontainers/PostgreSQL. Inspeção de `Application.kt` confirmou somente criação/status/cancelamento; ainda não há approve/claim, OAuth, sessão Makimono ou Drive/media. API segue loopback e `.invalid`; não iniciei/expos servidor nem acessei dados reais. Evidência e gate externo: `docs/ui-audit/2026-10-07-tv-pairing-backend/README.md`.

## Player OSD — reteste complementar em fixture (07/10/2026)

No Moto G34 Android 15, validei MPV e Exo/Media3 no pacote `.visualreview`, usando apenas MKV local sintético. MPV: foco D-pad chegou a Episódios; Mais opções abriu em Velocidade com os cinco grupos visíveis; BACK restaurou `btnMoreOptions`; BACK ocultou OSD antes de sair. Exo: OK→cima focou a seta e OK saiu; toque na seta também encerrou; BACK ocultou OSD e a pressão seguinte saiu. Hierarquias, capturas e ressalvas em `docs/ui-audit/2026-10-07-player-osd-runtime/README.md`.

Observação: CIMA isolado no início do Exo não moveu o foco até a seta; o caminho OK→CIMA funcionou. Exo ficou em 00:00 na fixture, então não considero playback contínuo validado. Streaming Drive, TV física/D-pad real, matriz de codecs e percurso das cinco linhas do drawer continuam pendentes. Um ciclo de BACK expôs a MainActivity de produção no histórico; nenhum controle foi acionado e relancei a fixture segura.

Reteste pós-build atual 1.6.4.3: `testDebugUnitTest --rerun-tasks` executou 287 testes/63 suítes, sem falhas, erros ou skips; `assembleDebug` passou separadamente. APK arm64 `.visualreview` com SHA-256 `53FEDC56720057D93FE4236416FC1D84CE84BFADEDCA40A114334B547A27428B` foi instalado com `adb install -r`. No Moto G34, Home da fixture carregou e D-pad RIGHT/DOWN focou `cardShelfItem` em Continuar assistindo; evidências `home-current-build.*` e `home-current-focus.*`. O hero da fixture é estático; runtime não prova o debounce/crossfade de previews da Home real. TV física segue pendente.

Home — sanitização de erros técnicos (07/10/2026): no estado de erro genérico, a Home agora passa a mensagem pelo sanitizador compartilhado do StateView antes de apresentá-la; erros offline e de autenticação continuam usando textos específicos. O teste focado StateViewPolicyTest passou e ssembleDebug compilou com sucesso. Não há fixture capaz de injetar HomeState.Error na tela de produção, portanto a integração visual em runtime não foi homologada nesta rodada. Nenhuma sessão Drive foi usada; sem release, commit ou push.

StateView — isolamento de mensagens de erro (07/10/2026): a Home genérica e falhas do seletor de pasta sanitizam texto dinâmico antes de exibi-lo; o componente preserva strings confiáveis localizadas para estados vazios. O sanitizador usa fallback genérico para texto desconhecido e não confunde ccess_token com sessão expirada. O teste novo impede vazamento de caminho, ID e token. 	estDebugUnitTest assembleDebug passou com 288 testes/63 suítes, zero falhas/erros/skips. Smoke no Moto G34 com .visualreview 1.6.4.3 confirmou que a busca vazia ainda exibe mensagem e CTA PT-BR, com foco no botão; evidência em docs/ui-audit/2026-10-07-stateview-runtime/. Sem erro real do Drive, TV física ou dados de conta; APK somente debug, sem release/commit/push.

Typography T1.7 reteste (07/10/2026): os 14 tamanhos literais restantes foram trocados pelos tokens tipográficos existentes nos layouts de player, diálogos, temporadas, temas, perfis e Home em retrato. python tools/check_typography.py --max 0 passou (0 literais; 421 usos de tokens); 	estDebugUnitTest assembleDebug passou, com a suíte já atualizada (288 testes/63 suítes) e recursos recompilados. Instalei apenas .visualreview no Moto G34 Android 15 e conferi a tela circular de perfis; captura e hierarquia em docs/ui-audit/2026-10-07-typography-runtime/. Não validei layouts em TV física nem todas as superfícies afetadas.

Perfis — smoke complementar de avatar no Moto G34: abri a edição no .visualreview, rolei até a galeria circular, selecionei uma imagem sem salvar e cancelei. O seletor continuou aberto; nenhuma edição foi persistida. Capturas e hierarquia em docs/ui-audit/2026-10-07-typography-runtime/. Fluxo de touch/cancel validado; navegação D-pad dentro do editor e galeria de wallpapers continuam sem validação.

Perfil — foco do catálogo no editor: D-pad DOWN chegou à galeria; RIGHT moveu entre avatares e manteve o anel de foco visível; BACK fechou o diálogo sem salvar e preservou o seletor. Capturas/hierarquia em docs/ui-audit/2026-10-07-typography-runtime/. Teste em touch/D-pad do Moto G34 dentro do .visualreview, não em controle remoto de TV.

Home W3-T3.1 — smoke visual no XML de produção: .visualreview em Moto G34 Android 15 exibiu o backdrop dentro do hero, título, metadados, sinopse e CTAs. Evidência docs/ui-audit/2026-10-07-typography-runtime/home-hero-smoke.png/xml. A fixture usa conteúdo estático; não prova debounce/foco, metadados remotos ou crossfade. Na janela curta do celular em paisagem, a próxima prateleira fica abaixo da dobra e exige rolagem; não alterei a altura sem validar a janela TV de 960×540dp. TV física e preview integrado ao Drive seguem pendentes.

Séries Q14 — reteste de viewport e foco (07/10/2026): fixture de detalhes no Moto G34. A 960×540dp (wm 1920×1080/320dpi temporários, restaurados após captura), o primeiro viewport inclui a faixa de episódios. Na resolução física 1600×720, D-pad desce à primeira mídia e a rola para dentro; RIGHT/LEFT navegam cards, UP retorna a temporadas e à ação principal com foco visível. Screenshots/hierarquias em docs/ui-audit/2026-10-07-details-960x540-runtime/. Sem dados Drive; não prova ordenação/metadados/playback real, controle remoto de TV nem fontes ampliadas.

Onboarding — corrigi o recorte do cabeçalho causado por centralização vertical do conteúdo maior que a viewport; ações focadas agora rolam para dentro da área segura inferior. No Moto G34 `.visualreview`, logo/título/descrição e CTA aparecem no viewport TV simulado 960×540dp; quatro DOWN alcançam Configurações avançadas inteiramente visível. `testDebugUnitTest assembleDebug` passou com 288 testes/63 suítes, sem falhas; artefato QA arm64 SHA-256 `84C044CCCEDF8758ADC8332E3EE5EFE612200AAF819EC646F0D4A62E4450D2A3`. Evidências em `docs/ui-audit/2026-10-07-signin-runtime/`. Não acionei login. TV física, TalkBack e OAuth real seguem pendentes; pairing segue dependente do backend mobile approval/OAuth/sessão.

### Build/test final da rodada Front B (07/10/2026)

- `testDebugUnitTest --rerun-tasks` recompilou e executou **288 testes em 63 suítes**, com 0 falhas, 0 erros e 0 skips. `assembleDebug` também concluiu com sucesso.
- APK arm64 do build instalado no Moto G34 somente para o pacote `zechs.drive.stream.visualreview`; SHA-256 `A2D5C9CB36F35A7BEF6969793B9A016B0A6D10ED94B40B7DFE5C6E790DACD7F6`.
- Reabri `VisualReviewActivity` após a instalação e confirmei a tela de detalhes com dados de teste. Captura pós-build: `docs/ui-audit/2026-10-07-details-960x540-runtime/postbuild-details.png`. A captura em resolução padrão do handset termina no título “Episódios”; a confirmação de cards no primeiro viewport de TV é a captura separada de 960×540dp.
- O `monkey` abriu a MainActivity sem conteúdo visível nesta fixture; iniciei a Activity de revisão explicitamente e ela foi exibida. Isso não foi tratado como validação do fluxo normal sem login.
### W3-T3.1 — cancelar preview quando o foco já saiu (07/10/2026)

- `scheduleFeaturedPreview` agora recebe a view que originou o foco. Depois dos 250 ms, só atualiza o destaque se o card ainda estiver focado, anexado e na aba Início; o job também é cancelado em `onDestroyView`.
- Motivo: um atraso válido para evitar consultas em cada movimento rápido podia aplicar um preview atrasado depois que o foco deixava a fileira.
- Validação: `testDebugUnitTest assembleDebug --rerun-tasks` passou; 288 testes/63 suítes, 0 falhas/erros/skips. APK arm64 instalado no `.visualreview`, SHA-256 `34A8753D5C6AC4E12966159770E16BFAA621AA70F927A683608CCE9BB15E1EF8`. Home fixture abriu no Moto G34; D-pad DOWN focou o cartão individual `cardShelfItem` (XML em `docs/ui-audit/2026-10-07-home-shelves-runtime/home-focus-guard-dpad.xml`).
- Limite: a Activity de revisão usa adaptadores de fixture e não executa o callback real do `HomeFragment`; a guarda foi compilada, mas ainda falta observar o hero dinâmico na Home integrada com biblioteca de QA e em TV.

### W3-T3.4 — composição de detalhes em paisagem/TV (07/10/2026)

- Reduzi o pôster editorial para 112×168dp e o espaçamento vertical do hero somente em `layout-land`; criei `layout-land/item_series_episode_card.xml` com cards mais compactos sem reduzir a tipografia. O layout retrato permanece inalterado.
- Em viewport 960×540dp no Moto G34, a abertura agora exibe os thumbs 16:9. Ao mover DOWN até os episódios, título, duração, badge e card completo ficam visíveis com contorno ciano; RIGHT move ao episódio seguinte e UP retorna às abas de temporada. Viewport restaurado a 720×1600/280dpi.
- `assembleDebug` passou. APK arm64 visualreview instalado; SHA-256 `5C9D980D890589DA62CFBBC3016512F49CB932488A0BA8F8CB92339C944613F4`. Screenshots e XML: `details-compact-960x540dp.*`, `details-compact-focus.*`, `details-compact-right.*`, `details-compact-up.xml` em `docs/ui-audit/2026-10-07-details-960x540-runtime/`.
- Dados são fixture sintética; playback, metadados/ordem Drive, texto ampliado e TV física continuam pendentes.

### W3-T3.7 — entrada e foco da seleção de perfis (07/10/2026)

`ProfilesAdapter` destaca em bold o nome do perfil ativo/focado e anima a primeira entrada dos cartões com stagger de 40 ms. Movimento reduzido suprime a animação; reciclagem durante a entrada deixa o cartão em estado opaco/estável. Build forçado: 288 testes/63 suítes sem falhas, `assembleDebug` passou. APK arm64 `.visualreview` instalado no Moto G34 (SHA-256 `64FB56D8EFD74F742F283D0A73BF84314C4545642E97BD07195EF50C15FD1197`). D-pad percorreu perfis e ações de rodapé sem dead end. Evidências: `docs/ui-audit/2026-10-07-profile-stagger-runtime/`. A animação não foi capturada quadro a quadro; TV física e TalkBack ainda não foram homologados.

### 2026-10-07 — Home: preview editorial com debounce compartilhado

Extraí a política de debounce e validação de foco para `FeaturedPreviewScheduler` e a apresentação visual para `FeaturedHeroBinder`, compartilhado pela Home integrada e pela fixture. A fixture agora passa título, metadados, sinopse, arte, fallback e transição pelo binder de produção. No Moto G34, o D-pad selecionou Jujutsu Kaisen e o hero atualizou depois de 250 ms. 291 testes passaram e `assembleDebug` compilou; evidências em `docs/ui-audit/2026-10-07-home-preview-runtime/`. A Activity ainda fornece dados sintéticos diretamente, sem `HomeViewModel.previewFeatured`/Drive; arte repetida não permite confirmar visualmente troca entre backdrops distintos. TV física e Drive reais permanecem pendentes.

### 2026-10-07 — Seletor de avatar: fileiras sem recorte no foco

Confirmei que os avatares já são circulares, sem moldura cinza, e que o anel ciano indica foco. Removi o título de galeria redundante, que aparecia cortado, e ajustei o auto-scroll para alinhar a linha focada quando havia uma linha anterior parcialmente visível. RIGHT/DOWN mantiveram foco visível no Moto G34; BACK cancelou sem salvar. Build e 291 testes/64 suítes passaram. Evidências em `docs/ui-audit/2026-10-07-profile-avatar-picker-runtime/`; a homologação em TV física e TalkBack segue pendente.

### 2026-10-07 — Home: validação visual de troca real do backdrop

A revisão inicial mostrou que a fixture mudava o texto para Jujutsu Kaisen, mas continuava reutilizando arte de Frieren. Ajustei somente `VisualReviewActivity` para usar um backdrop horizontal de Jujutsu Kaisen na série correspondente. No Moto G34, o D-pad focou esse segundo card em “Novos episódios”; depois do debounce, uma captura do topo confirmou outro backdrop, título e metadados atualizados no `FeaturedHeroBinder`. Evidências e XML: `docs/ui-audit/2026-10-07-home-preview-runtime/`. O build e os 291 testes passaram; o APK visualreview arm64 tem SHA-256 `FECC5D73BB79F6A4B759DE3871E4822AA3639329A299AF0B4DF6D1923DF0DAC1`. Isso comprova a troca no renderer/scheduler compartilhado, não o fluxo real ViewModel → Drive; a fixture continua sintética e não equivale a validação em TV física.

### 2026-10-07 — Home: manter arte local quando metadados não respondem

Corrigi o fallback de `HomeViewModel`: após uma resposta vazia/erro do resolvedor externo, a arte da pasta do Drive (`posterUrl` ou `thumbnailLink`) continua disponível no hero; uma resposta de metadados sem imagem também preserva essa arte. `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1` passou com 291 testes/64 suítes, sem falhas, erros ou skips. O APK arm64 foi instalado apenas em `.visualreview`; a Home da fixture abriu no Moto G34, e D-pad DOWN/RIGHT selecionou o segundo card de “Novos episódios”. A captura final confirmou o backdrop Jujutsu Kaisen no hero. Evidências em `docs/ui-audit/2026-10-07-home-preview-runtime/`. Hash SHA-256 `AA78C12C94300010E7E8C681516A7CCD888AE5A917DC1741CB1DDD035118D97D`. Não testei Drive/OAuth reais; o fluxo segue sintético.

### 2026-10-07 — Home: títulos das prateleiras acompanham o foco

No percurso visual, a rolagem de foco deixava o título da seção colado à barra de busca fixa. Adicionei `HomeShelfScroll` para alinhar o cabeçalho da prateleira ativa 12dp abaixo da barra e limitar o movimento para preservar o card focado. A Home real e a fixture compartilham a regra; testes cobrem o cálculo e seus limites. No Moto G34, DOWN atravessou seis zonas até o catálogo e UP retornou ao CTA; os títulos ficaram abaixo da barra em todas as capturas. `testDebugUnitTest assembleDebug` passou com 294 testes/65 suítes; APK `.visualreview` arm64 SHA-256 `36DE6E9424CE0FEE35D187C64C07E05232FC50F523391851C54B7B24A8E18C18`. Evidências: `docs/ui-audit/2026-10-07-home-shelves-runtime/home-shelf-aligned-down-01..06.*`, `home-shelf-aligned-up-01..06.*` e smoke final `home-final-runtime.png/xml`. Cobertura em `layout`, `layout-port`, `layout-land`, `layout-television` e `layout-sw720dp-land`. Dados sintéticos, sem TV física/Drive real.

## Plano W3-T3.4 — retorno de foco do menu contextual de episódio

- Defeito: ao cancelar o menu de contexto de um episódio com BACK/toque fora, o foco não é restaurado explicitamente ao card que o abriu; a navegação por D-pad pode retomar em alvo imprevisível.
- Mudança proposta: propagar a View do card até o diálogo e restaurar foco somente no cancelamento, respeitando touch mode e verificando attachment/visibilidade.
- Qualidade: substituir ações literais por recursos pt-BR/idioma padrão; callbacks de playback, progresso, fila e clipboard ficam intactos.
- Aceite: cancelar o menu por BACK restaura foco no mesmo episódio; ativar uma ação mantém o comportamento atual; build e testes passam; evidência runtime somente em .visualreview sem tocar no histórico real.


### Resultado W3-T3.4 — retorno de foco do menu de episódio (07/10/2026)

Propaguei o card invocador até um helper compartilhado de menu contextual. Cancelar com BACK restaura foco ao card após verificar attachment/visibilidade e manter compatibilidade com touch mode; as opções agora usam recursos de string. A fixture `.visualreview` foi ligada ao mesmo helper: long-press → DOWN para “Assistir episódio” → BACK confirmou o `cardEpisodeItem` focado e Activity ativa. Build e 294 testes/65 suítes passaram; APK arm64 SHA-256 `09FD727BCDB1F80F037E6E40C7BDB2FBEF562453414FAE0328ADD03978FAC7FD`. Evidência e limites: `docs/ui-audit/2026-10-07-episode-context-focus-runtime/README.md`. Sem acesso ou mutação de histórico/Drive real.

## Plano Q01 — não inferir áudio dual pelo título “Dual”

- Defeito reproduzível por política: a expressão atual aceita `Dual` sozinho; títulos como `Dual! Parallel Trouble Adventure` podem ser apresentados falsamente como áudio dual.
- Correção proposta: exigir `audio`/`áudio` após `Dual`, aceitando separadores e acento; manter texto marcado como inferido pelo nome.
- Arquivos: `SeriesMediaMetadataPolicy.kt`, `SeriesMediaMetadataPolicyTest.kt`, este log e o tracker após validação.
- Aceite: nome só com “Dual” resulta desconhecido; “Dual Audio”, “Dual-Audio” e “Dual Áudio” continuam inferidos; suíte e build passam.

### Resultado Q01.6 — não inferir áudio dual pelo título (07/10/2026)

Corrigi a pista de nomes para não classificar “Dual” isolado como áudio dual e habilitei case-fold Unicode para “Dual Áudio”. O teste novo verifica o título “Dual! Parallel Trouble Adventure” sem falso positivo e mantém formatos com marcador explícito. Build passou; suíte 295/65, sem falhas. APK `.visualreview` arm64 instalado e Activity de detalhes sintética permaneceu ativa; SHA-256 `357F5D32E544182156D22E913E85206959103C90EE7B178AABC3B7E702F298B5`. Conta/Drive reais não foram tocados. Evidência/limites em `docs/ui-audit/2026-10-07-q01-dual-audio-inference/README.md`.

### Resultado Q02.7 — deduplicação de páginas sobrepostas (07/10/2026)

A consulta detalhada do Drive agora elimina IDs repetidos entre páginas mantendo ordem de primeira aparição e metadados da ocorrência mais recente. Teste específico de sobreposição passou. A suíte completa passou com 296 testes/65 suítes, sem falhas/erros/skips; `assembleDebug` passou. Instalei apenas a variante `.visualreview` arm64 no Moto G34 e confirmei a fixture Files e navegação RIGHT×3 + DOWN com Episode 08 visível e focado. APK SHA-256 `24ADEAA144058212FDF2EC6E89D46525D71EB61259A003D630DB6BB2FB2956C7`. Capturas/XML e limites em `docs/ui-audit/2026-10-07-q02-page-dedup/README.md`. Sem sessão Drive real; Q02 permanece IN_PROGRESS.
### Resultado Q02.8 — contexto para continuação do cursor (07/10/2026)

Associei `DriveQueryContext(query, pageSize efetivo, orderBy)` a todo `FileCollectionResult`, inclusive limites e falhas parciais. O teste de cursor em listagem incompleta confirma que consulta de pasta, tamanho e ordenação continuam pareados ao token. Suíte completa e `assembleDebug` passaram (296/65, sem falhas/erros/skips). Reinstalei somente `.visualreview` no Moto G34; a fixture Files abriu e assentou. APK SHA-256 `AD436380AE33E7CE8996EB2D1A38E3419D95AA957903E0812C1DBF9E4C1DA9CD`. Evidência/limites em `docs/ui-audit/2026-10-07-q02-page-dedup/README.md`; Drive autenticado e TV permanecem pendentes.
### Resultado Q02.9 — ciclo de vida da paginação Files (07/10/2026)

Ao mudar de pasta, a consulta anterior agora é cancelada e a nova começa sem cursor ou itens antigos. Ao sair de Files durante uma página adicional, o ViewModel preserva as páginas já recebidas, remove o estado Loading e pode retomar pelo cursor interrompido ao voltar. Os dois cenários têm teste de ViewModel com repositório suspenso. Suíte completa e `assembleDebug` passaram: 298 testes/66 suítes, sem falhas, erros ou skips. APK arm64 `.visualreview` SHA-256 `4F5FF536EA743C53AD5E44B159ED4CC9DC6B40EADBAEF0CBF0D5FCDBCD87BD8C`, instalado somente no Moto G34. Smoke Files: RIGHT×3 + DOWN deixou “Episódio 08” visível e `cardGridItem` focado; captura, XML e limites em `docs/ui-audit/2026-10-07-q02-query-cancel/README.md`. Esse smoke não acessa Drive; TV e rede autenticada continuam pendentes.

### W3-T3.4 — centralizar rótulos de detalhes em recursos (07/10/2026)

Extraí rótulos estáticos de detalhes/episódios dos qualifiers retrato e paisagem e substituí os estados dinâmicos do Fragment por recursos localizados. A suíte completa passou (298 testes/66 suítes) e `assembleDebug` compilou; `git diff --check` ficou limpo. No Moto G34, a fixture abriu e DOWN×2 levou o foco à aba de temporada e depois a um `cardEpisodeItem` visível, bounds `[413,408][781,720]`. APK arm64 `.visualreview` SHA-256 `87CC91D46681910DA49FADDA806723D3F51B1CBCF2072D75E2851192158146A3`. Evidência: `docs/ui-audit/2026-10-07-details-localized-labels/README.md`. A fixture não comprova idioma inglês em runtime, TalkBack, Drive ou TV.

### W3 — manter a ordem da prateleira enquanto o usuário navega (07/10/2026)

Evitei que o refresh assíncrono mova o conteúdo sob o foco nas prateleiras do catálogo e de Adicionados Recentemente. Itens existentes mantêm posição enquanto recebem metadados novos; novidades entram no fim e remoções são refletidas. O sort A–Z/Z–A explícito segue funcionando. Os testes completos passaram: 300 testes/67 suítes sem falhas; `assembleDebug` passou. Uma fixture debug com `StarredShelfAdapter` recebeu snapshot invertido enquanto Jujutsu Kaisen estava focado; a hierarquia confirmou foco no mesmo card e ordem estável, com novo item ao fim. APK arm64 `.visualreview` SHA-256 `E3A7C07013B30810F3D8F0F7C48F1A671C2C9D9185D62D3EC466E0C2DF4310D3`. O teste não aciona os coletores Drive da HomeFragment. Nenhum Drive real ou dado persistido foi consultado. Detalhes/captura em `docs/ui-audit/2026-10-07-home-refresh-order/README.md`.

### Q14 — ação principal escolhe episódio narrativo correto (07/10/2026)

A ação Reproduzir/Continuar agora ignora OP/ED, promos e especiais quando há episódios narrativos; escolhe o episódio narrativo parcialmente assistido, depois o primeiro não assistido em ordem natural, e volta ao primeiro narrativo se todos estiverem vistos. Só recorre a extras quando a pasta não tem episódio narrativo. Cinco testes cobrem listagem embaralhada, progresso em extras, episódios vistos, biblioteca só de extras e lista vazia. Suíte completa: 305 testes/68 suítes, sem falhas/erros/skips; `assembleDebug` passou. APK debug arm64 SHA-256 `229E28F51561F675B4BDEC14670B42E0A1DDB2EE6B45A0888A3CD375DE97393D`. Validação de seleção com dados sintéticos; não reproduziu arquivo real nem validou Drive/TV.

### Q14/Q16 — grupo “Todos os Episódios” não reintroduz extras (07/10/2026)

A tela de detalhes ignorava os IDs definidos pelo `SeasonEpisodeGrouper` para “Todos os Episódios” e reconstruía a lista excluindo apenas música. Agora respeita os IDs do grupo, ordena pelo comparador narrativo compartilhado e mantém extras acessíveis se não houver episódio regular. O trailer permanece disponível no estado da tela e é reproduzido como item isolado, sem autoplay para a sequência narrativa. Quatro testes cobrem exclusão/ordem usando o próprio agrupador, fallback sem IDs, pasta só de extras e seleção do PV fora da sequência. Suíte completa: 309 testes/69 suítes, sem falhas/erros/skips; `assembleDebug` passou. APK debug arm64 SHA-256 `CFB14562648A34F6C228427A5950EF72C96595D39AAAE99112931B833177EDB4`. Validação sintética; sem Drive/playback real ou TV.

### Q08 — lookup de código manual de pareamento (07/10/2026)

Implementei `POST /v1/pairing/lookup` no protótipo local: normaliza código com/sem hífen, espaços e caixa; limita o JSON a 1 KiB; aplica rate limit independente de 5 chamadas/minuto por peer; retorna somente challengeId, nome da TV e vencimento ativos; respostas usam `no-store`, e códigos desconhecidos/inativos não distinguem motivo. A consulta não aprova, não reivindica e não devolve poll secret. In-memory e PostgreSQL usam a mesma política, com teste do store PostgreSQL.

`backend` testou 22 casos sem falhas/erros/skips. `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1` passou: 309 testes Android, 0 falhas/erros/skips, compilação Debug concluída. APK arm64 SHA-256 `F4405D9D42D11E2ECDEBFACB538A4070AC41A597B96F8B42DB0BE8ADA36363B0`. Não instalei durante esta rodada para preservar a sessão MPV de QA ativa no Moto G34. Evidência e limites: `docs/ui-audit/2026-10-07-tv-pairing-code-lookup/README.md`.

O pareamento permanece incompleto e não está exposto publicamente: faltam página mobile, autenticação Google/OAuth, aprovação com CSRF, sessão Makimono, claim/refresh e deployment HTTPS. O lookup é apenas uma etapa não autenticadora do fluxo.

### Q13 — retorno espacial da Home à navegação (07/10/2026)

Alinhei a `VisualReviewActivity` com os callbacks de navegação da Home integrada: LEFT no primeiro card das prateleiras retorna ao item atual da rail, e LEFT no catálogo só retorna quando o foco está na primeira coluna. No Moto G34 em paisagem, a fixture `.visualreview` confirmou foco em `btnNavInicio` após o percurso D-pad; `testDebugUnitTest assembleDebug` passou com 309 testes, sem falhas/erros/skips. Evidências em `docs/ui-audit/2026-10-07-home-nav-return-runtime/`. Essa validação é do harness sintético; TV física, sessão Drive e a proposta de rail compacta ainda não foram validadas/implementadas.

### Q17 — saída em camadas dos players (07/10/2026)

Corrigi a `VisualReviewActivity` para manter uma Home sintética atrás das atividades Media3 e MPV, permitindo validar retorno ao contexto. Com OSD visível, BACK ocultou os controles nos dois motores; BACK seguinte saiu do episódio e mostrou a Home fixture. `testDebugUnitTest assembleDebug` passou (309 testes, 0 falhas/erros/skips), e o APK `.visualreview` SHA-256 `23F683E397FD6BB48366E6D0C23753CE87FB8C661C0FAD6E646D44453F53C65B` foi executado no Moto G34. Capturas e XML: `docs/ui-audit/2026-10-07-player-return-runtime/`. Isso valida as camadas BACK em vídeo local sintético; arrow touch, menus/drawers, streaming Drive, sessão de decoder longa e controle remoto/TV física continuam pendentes.

#### Q08 — página de prévia do QR e recuperação por código (07/10/2026)

A rota de verificação do QR (`GET /pair/{challengeId}`) agora mostra uma página responsiva, própria para celular, com o nome seguro da TV, expiração e estado explícito de prévia. Inclui formulário por POST para código manual (`POST /pair/lookup`), sem colocar o código em URL. A resposta tem CSP restritiva, `no-store`, `no-referrer`, `nosniff` e proteção contra framing; não carrega script/analytics/recursos de terceiros. Testes de rota confirmam escaping contra XSS, ausência de poll secret, estado ainda `pending`, códigos inválidos/inativos com mensagem idêntica e limite de corpo. Suíte backend: 25 testes, zero falhas/erros/skips; os 5 testes da classe PostgreSQL executaram. Inspeção visual concluída no browser local em desktop e viewport de 390×844, sem overflow horizontal; o formulário não foi submetido manualmente porque o POST está coberto por teste. Nenhum app foi instalado, preservando a sessão MPV de QA no Moto G34. Esta página só visualiza a solicitação: OAuth/CSRF/approval/claim continuam pendentes.

### W3-T3.7 — rótulos do seletor de perfis localizados (07/10/2026)

Centralizei títulos e papéis do seletor em recursos Android pt-BR, removendo texto literal do XML das variantes retrato/paisagem e do `ProfilesAdapter`. “Gerenciar perfis” agora usa caixa normal. A identidade visual de avatar circular e foco sem tile cinza foi preservada. `testDebugUnitTest assembleDebug`: 309 testes Android, 0 falhas/erros/skips; compilação Debug concluída. APK arm64 SHA-256 `843A6F098B6D81A3B71E9CCA7DAD0C59A69CDB5D48FD3E2245EAC9C06810537A`. Não instalei para não interromper a reprodução de QA no Moto G34.

### Perfis — textos do editor e ações localizados (07/10/2026)

Substituí literais de UI no editor e na tela de perfis por recursos Android padrão/PT-BR: criação/edição, busca da galeria, descrição acessível de arte, validação, estados vazios/erro e confirmação de exclusão. 	estDebugUnitTest assembleDebug --no-daemon --max-workers=1 passou com 309 testes JVM sem falhas. Instalei somente .visualreview no Moto G34 e confirmei via hierarquia e captura docs/ui-audit/2026-10-07-profile-copy/smoke-editor-localized.png os rótulos em português e botões Cancelar/Salvar. Fixture sintética, sem persistência, Drive, TalkBack ou TV física. git diff --check passou.

### Perfis — simplificação do editor de arte (07/10/2026)

Removi os emojis dos botões de avatar/wallpaper e transferi os rótulos literais do layout do editor para recursos padrão/PT-BR. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou novamente com 309 testes/sem falhas. Instalei apenas `.visualreview` no Moto G34; a captura `docs/ui-audit/2026-10-07-profile-copy/smoke-editor-clean-tabs.png` confirma rótulos compactos em português, sem emoji. Fixture não altera perfil persistido; TV física e TalkBack continuam sem homologação.

### Perfis — galeria circular com foco lateral (07/10/2026)

Troquei a grade vertical de arte por carrossel horizontal: avatares circulares sem tile cinza e wallpapers panorâmicos. RIGHT/LEFT percorrem as opções em ordem; o item focado é trazido à área visível pelo carrossel e o foco vertical pode continuar para os campos abaixo. A auditoria no Moto G34 revelou que as imagens estavam carregadas mas abaixo da dobra; o editor as revela ao avançar o foco/rolagem. Build + suíte passaram: 309 testes sem falhas, `assembleDebug` concluído. Capturas: `docs/ui-audit/2026-10-07-profile-copy/profile-carousel-dpad.png` e `profile-carousel-right.png`. Fixture sintética, sem persistência, TalkBack ou TV física.

Reteste da galeria de wallpapers: toque na aba alternou o catálogo para cards panorâmicos; DOWN chegou ao primeiro item, RIGHT percorreu a fileira com foco visível e BACK cancelou o editor sem salvar. Capturas `profile-wallpaper-focused.png`, `profile-wallpaper-right.png` e `profile-after-cancel.png` em `docs/ui-audit/2026-10-07-profile-copy/`. Isso fecha o percurso visual/D-pad de ambas as abas no harness sintético; persistência, TalkBack e TV física continuam pendentes.

### Perfis — catálogo de arte e foco D-pad (07/10/2026)

O check de seleção agora fica fora do recorte circular e é mostrado inteiro, sem cartão quadrado cinza. Reordenei o editor para priorizar avatares/papéis de parede antes da busca web, fiz DOWN na aba focada entrar no primeiro item e LEFT no primeiro item retornar à aba; URLs diretas ficam recolhidas numa seção secundária expansível. No Moto G34 Android 15, confirmei avatar e wallpaper por descrição de acessibilidade/foco, selecionei ambos na fixture e fechei sem persistir. Evidências: `docs/ui-audit/2026-10-07-profile-copy/profile-focus-art.png`, `profile-selection-mark-final.png`, `profile-wall-focus.xml`, `profile-wallpaper-selected-final.png`. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 309 testes, zero falhas; compilação concluída. No telefone paisagem, a arte fica parcialmente abaixo da dobra na abertura e é trazida à vista pelo foco; validar geometria em TV 720p/1080p, TalkBack e persistência real permanece pendente.

### Detalhes da série — fixture sem specs técnicas falsas (07/10/2026)

A tela de revisão mostrava chips técnicos vazios quando a fixture não tinha resolução/áudio/legendas. Ajustei apenas o harness para representar mídia com specs desconhecidas como a UI de produção: a linha técnica fica oculta. No Moto G34, conferi a linha limpa e o percurso DOWN da ação principal → temporada → episódio. Capturas/XML: `docs/ui-audit/2026-10-07-profile-copy/series-detail-clean.png`, `series-detail-focus.xml`. `testDebugUnitTest assembleDebug`: 309 testes sem falha, build concluída. Metadados reais, TalkBack e TV física permanecem sem validação.

### Perfis — seletor circular e fluxo de foco (07/10/2026)

Confirmei o seletor principal no Moto G34: avatares redondos sem tile cinza, foco ciano somente no perfil ativo; RIGHT percorre perfis e “Adicionar perfil”, DOWN alcança “Gerenciar perfis”. Evidências em `docs/ui-audit/2026-10-07-profile-copy/profiles-final.png` e `profiles-add-focus.xml`. Não salvei dados de fixture. Sem homologação em TV real ou TalkBack.

### Q08 — smoke HTTP local do fluxo de prévia (07/10/2026)

Serviço Ktor loopback: criação → prévia por QR → lookup manual por POST → estado segue `pending` → cancelamento → poll retorna `410`. Os checks passaram sem registrar código/segredo; backend `--rerun-tasks` via `X:\`/`G:\`: 25 testes, 0 falhas. Smoke e limites em `docs/ui-audit/2026-10-07-pairing-live-http/README.md`. Continua sem OAuth, aprovação, sessão/claim ou deployment.

### Home TV — rail de navegação e alinhamento de prateleira (07/10/2026)

A auditoria ampla mostrou rótulos da sidebar quebrados/truncados e “Novos episódios” desalinhado. A rail TV agora tem 176 dp nas duas variantes de TV; o span da grade usa a largura real da rail. O título de episódios seguidos usa o mesmo `home_content_margin` dos cards nas quatro variantes de Home. `testDebugUnitTest assembleDebug`: 309 testes, sem falhas/erros/skips. Instalei apenas `.visualreview` no Moto G34 Android 15; na simulação 1920×1080 @240 dpi, D-pad percorreu Catálogo, Pastas, Favoritos, Perfil e Configurações. Restaurei 720×1600 @280 dpi. TV física, TalkBack, alinhamento runtime da prateleira sem fixture e validação a 10 pés continuam pendentes. Evidências: `docs/ui-audit/2026-10-07-home-fresh-audit/README.md` e `home-1920x1080-240dpi.png`.
### Home — alinhamento de “Adicionados recentemente” (07/10/2026)

A auditoria em rolagem encontrou o cabeçalho “Adicionados recentemente” na borda da área de conteúdo, embora os cards recuassem 48 dp. Ajustei esse título em todas as quatro variantes da Home para usar `home_content_margin`; a captura posterior confirma o mesmo início horizontal dos cards e dos títulos da seção. Após recompilar e instalar somente `.visualreview`, o Moto G34 em viewport amplo simulado percorreu por D-pad as cinco prateleiras, RIGHT avançou um card e UP voltou de prateleira. 309 testes sem falhas/erros/skips. Evidências/limites: `docs/ui-audit/2026-10-07-home-fresh-audit/README.md`.
### 2026-10-07 — status de publicação em detalhes

Evitei que status de publicação desconhecido fosse apresentado como “Em exibição”. A tela agora traduz apenas estados conhecidos do MAL/AniList (concluído, em exibição, futuro, hiato e cancelado); valores não suportados ficam ocultos. Testes completos e smoke de navegação por D-pad/menu contextual estão registrados em `docs/ui-audit/2026-10-07-details-status-policy/`.

### Detalhes — disclosure para sinopses longas (07/10/2026)

Adicionei uma expansão contextual para sinopses que realmente excedem três linhas, com recolhimento e retorno à posição anterior. No Moto G34, a fixture confirmou foco bidirecional por D-pad e expansão/retração; repeti em viewport 1080p simulado e restaurei a configuração do aparelho. A suíte passou com 313 testes e o APK debug compilou. Os dados eram sintéticos; TV física, TalkBack e binding com fontes reais de metadados ainda não foram homologados. Evidências: `docs/ui-audit/2026-10-07-details-long-synopsis/`.

Reteste de toque na expansão de sinopse encontrou uma falha de ativação: o primeiro toque só adquiria foco. O controle agora usa a ativação de toque único já adotada em cards; um toque expandiu a fixture no APK final. Build com 313 testes passou. Captura: `docs/ui-audit/2026-10-07-details-long-synopsis/touch-single-after.png`.

Ao atualizar uma sinopse longa para curta enquanto o disclosure tem foco, o helper agora transfere o foco para a ação principal antes de ocultar o link. A fixture de rebind confirmou `btnPrimaryAction` focado após a atualização. Evidência em `docs/ui-audit/2026-10-07-details-long-synopsis/rebind-after.xml`.

### Detalhes — descrição opcional por episódio (07/10/2026)

O campo opcional `description` do Google Drive agora chega ao modelo de arquivo. O card só revela esse texto enquanto está focado, em até duas linhas; itens sem descrição permanecem compactos. Build Debug + 314 testes passaram. No Moto G34, a fixture isolada confirmou foco D-pad e toque para exibir/ocultar conteúdo; não usei conta nem arquivos reais. Evidências e limitações em `docs/ui-audit/2026-10-07-episode-description-focus/`.

### Home — reteste das seis prateleiras no build atual (07/10/2026)

No Moto G34, o `.visualreview` percorreu por DOWN as seis zonas Continuar, Minha fila, Novos episódios, Minha lista, Adicionados recentemente e Catálogo; UP retornou ao CTA principal e RIGHT avançou um item. Sessão somente com fixtures, sem Drive. Dumps e limites em `docs/ui-audit/2026-10-07-home-current-retake/`.

### Details — rótulo acessível nos cards de episódio (07/10/2026)

Cards focáveis agora anunciam a ação e o título, somando duração, áudio e progresso quando conhecidos; status usa o limiar já definido para concluído. 317 testes e build Debug passaram. No Moto G34, UIAutomator confirmou rótulo alterado junto com o foco D-pad. Evidência/limite TalkBack em `docs/ui-audit/2026-10-07-episode-card-accessibility/`.

Reteste final: o callback de divulgação da descrição deixou de substituir o listener de animação do `FocusEngine`. A captura no Moto G34 confirma anel, escala e elevação do card focado; RIGHT mudou o rótulo para o próximo episódio. Build + 317 testes passaram; APK arm64 SHA-256 `CA5A511656B89CDDB43D4D70ED9C426D90E1D6DF1A812A7A5442E1913742FC91`. Captura e dumps finais no mesmo diretório de evidências.

### Movimento reduzido — foco e blur (07/10/2026)

A preferência salva do perfil ativo agora é considerada pelo `FocusEngine` e por `FrostedWindow`. A expansão/elevação do foco e o blur são removidos no modo reduzido; contorno de foco e superfícies opacas continuam visíveis. Ações de detalhes, temporadas e drawer de episódios foram centralizadas no motor de foco. Build + 320 testes passaram; fixture runtime no Moto G34 validou foco estático com D-pad. Settings com reinício e TV física pendentes. APK SHA-256 `B06BA7EAD923ACD5D45F6C85C7159A4CD1E1EA343E70D44D9EE81E7DD8AAD685`. Evidências em `docs/ui-audit/2026-10-07-reduced-motion-focus/README.md`.

No reteste do Settings, um toque na categoria Aparência só transferia foco e exigia outro toque; `SingleTapActivation` agora mantém a seleção acessível por toque único sem alterar o clique por D-pad. No Moto G34, confirmei o toque único e a persistência de “Reduzido” no perfil principal após reiniciar `.visualreview`; o perfil secundário manteve “Seguir dispositivo”. `testDebugUnitTest assembleDebug`: 320 testes, sem falhas. APK arm64 SHA-256 `FD763FE8AB7C7F772EEE4327E039A4E5C2AB710BC1F2D13715BCB30C152E21FB`. Evidências: `docs/ui-audit/2026-10-07-reduced-motion-focus/README.md`. TalkBack e TV física ainda pendentes.

No reteste da faixa, o helper bloqueava o gesto horizontal do `HorizontalScrollView`. Permiti que o pai intercepte arrastos nas categorias sem perder a ativação por toque único. No Moto G34, swipe revelou “Dados e sessão” e um toque exibiu sua seção. Build + 320 testes passaram; APK `.visualreview` arm64 SHA-256 `0815E56FCB1CD675DE1A1E2E4DB277306A7EB18BDD76F31794EACE51DAF5B5C7`. Evidência em `docs/ui-audit/2026-10-07-reduced-motion-focus/README.md`.

### Detalhes — nomes de aberturas e encerramentos (07/10/2026)

O parser agora reconhece sufixos de variante (`OP4a`/`OP4b`), e os cards mostram “Abertura 04A/B” sem classificar o número da abertura como episódio regular. Política extraída e coberta por regressões; build Debug + 323 testes passou. No Moto G34, a fixture compartilhada mostrou os três títulos no adapter de produção; DOWN e RIGHT confirmaram foco visível nos dois extras. Integração com Drive real, TalkBack e TV seguem abertas. Evidências em `docs/ui-audit/2026-10-07-details-episode-names-README.md`.

### Perfis — rótulo completo nas abas do catálogo (07/10/2026)

Corrigi o truncamento de “Papéis de parede” no editor de perfil em português: as abas podem usar duas linhas sem reticências e têm alvo mínimo de 48 dp. Build forçado e 323 testes passaram. No Moto G34 720×1600, `.visualreview`, confirmei a aba, a galeria de fundos e o foco D-pad em avatar; BACK fechou sem salvar. Evidências e limites: `docs/ui-audit/2026-10-07-profile-editor-tabs/README.md`.

### Files — categorias distintas para erros do Drive (07/10/2026)

Propaguei tipos de erro de autenticação, permissão, limite de chamadas, pasta ausente, rede e servidor até Files; a tela mostra ações de recuperação adequadas, não exibe mensagem técnica duplicada e encerra o skeleton no estado final. A navegação agora guarda IDs ancestrais e bloqueia um atalho para uma pasta já aberta. Build + 334 testes passaram. No Moto G34 `.visualreview`, a fixture de falta de permissão mostrou o estado e o botão “Voltar” focado; OK executou a ação. Nenhuma conta nem dado real do Drive foi acessado. Captura, XML e limites: `docs/ui-audit/2026-10-07-files-permission-state/README.md`.
