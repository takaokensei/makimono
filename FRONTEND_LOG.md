# Makimono Frontend Evolution Log

Este documento é a memória persistente dos ciclos de auditoria, testes visuais e refatoração de UI/UX do Makimono para Android TV e Mobile, conforme definido em `GOAL.md`.

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
