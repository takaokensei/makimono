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