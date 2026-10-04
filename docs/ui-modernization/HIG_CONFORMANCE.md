# HIG conformance ledger — Makimono Android TV

F0 is a diagnostic baseline, not a passing conformance claim. “Gap” identifies an observed baseline issue; it is not an approved design deviation. Each affected rule must be rechecked in its implementing phase. Only the final gate may classify a rule `OK`, `D-xx` (one of the documented adaptations in `HIG_NOTES.md`) or `N/A-Android`.

**Evidence:** 12 user-provided screenshots in `docs/ui-audit/current-state/` and 12 fixture recaptures in `docs/ui-audit/after-v2/f0/`. Current capture dimensions: 1920×1080, 320 dpi (logical 960×540 dp). The fixture is debug-only and has the separate application ID `zechs.drive.stream.visualreview`; the signed install and its account data were left intact. **Scope note:** `AUDIT_REPORT.md` requested by the attached mission is absent; the existing review is `docs/ui-modernization/AUDIT.md`.

## F1 token verification

`python tools/check_contrast.py` measures primary and secondary text at ≥7:1 against each palette's base and elevated surfaces, and primary-action text at ≥4.5:1. All six palettes pass. Token and theme resources compile as part of `assembleDebug`; layout adoption, focused-state clearance, and reduced-motion behavior still require later phase verification.

| F1 area | Evidence | F1 status | Remaining phase |
|---|---|---|---|
| Color roles | `tools/check_contrast.py` output; six palette token definitions | Pass: checked primary/secondary text ≥7:1; action labels ≥4.5:1 | F2–F6 must replace remaining hardcoded view colors and validate on imagery |
| Safe margins | `values/dimens.xml`, `values-land/dimens.xml`, `values-sw600dp-land/dimens.xml` | Pass: shared 48dp horizontal / 30dp vertical tokens | F2–F5 visual checks for screens, clipping, and focus clearance |
| Type/shape/motion tokens | `values/typography.xml`, `values/dimens.xml`, `values/motion_spec.xml`, `ContinuousCornerOutline.kt` | Foundation added; runtime application not yet proven | F2/F4/F5/F6 |
| Theme compatibility | `AppThemeTest.kt`; original values 0–4 unchanged, Obsidian value 5 | Pass at model/resource mapping level | F5 theme selector and all surface variants |
| D-pad and theme picker | `after-v2/f1/02_home_details_focus.png`, `03_home_shelf_focus.png`, `04_theme_picker_obsidian.png` and hierarchy XML | RIGHT reaches Details; vertical navigation reaches shelf cards; five DOWN presses scroll to and focus Obsidian. The white ring/undersized action issue was addressed in F2; F3 verifies full visibility of a focused catalog card. | F4–F6 |

## F2 focus and control verification

Focus references re-opened for this phase: Buttons pp. 1–3, Collections pp. 1–2, Focus and selection pp. 1–6, Labels pp. 1–2, and Lockups pp. 1–2. Apple describes a 44×44 pt general button hit region; that unit is platform-specific. Makimono targets 48dp for the audited Android controls.

| F2 area | Evidence | F2 status | Remaining phase |
|---|---|---|---|
| Theme-aware focus | `utils/TvFocusRing.kt`; theme `colorAccentPrimary`/`colorFocusFill`; `after-v2/f2/02_home_focus_right.png`, `03_home_focus_play.png`, `06_home_focus_up.png`, `07_details_focus_primary.png` | Removed the fixed white double ring from hero/details actions; one 2dp theme-accent ring is drawn by the shared overlay. Compact controls get a subtle accent fill; poster cards stay clear. Card focus is drawn by the helper rather than an unreliable CardView foreground selector. | Verify dialogs/player/list focus in F4–F6 |
| Action target sizing | `fragment_home.xml` and its land/TV/sw720dp-land variants; `fragment_series_detail.xml` variants; both `item_series_season_tab.xml` variants | Home hero, nav/profile rail, details back/search/actions, and season tabs use 48dp bounds. Existing IDs and directional rules remain. | Audit remaining interactive targets and font-scale in F3–F6 |
| Focus vs selection | `drawable/tab_season_pill_bg.xml`, `drawable/detail_season_tab_bg.xml`; detail screenshot | Selected season uses a quiet surface/hairline. Transient focus is the accent ring, so selection does not reuse the focus outline. | Recheck favorite/watched semantics in F3/F5 |
| D-pad and touch routes | `after-v2/f2/14_home_focus_card_fixed.xml`, `15_details_focus_tab_final.xml`, `13_home_touch_details_fixed.xml` | Home D-pad focuses the individual `cardShelfItem`; touch no longer outlines the containing RecyclerView. Details DOWN reaches the first season tab. Production BACK/touch navigation remains for later route audits. | F3–F6 |

## F2 measured visual evidence

| Capture and hierarchy | Measured value | Interpretation |
|---|---|---|
| `after-v2/f2/06_home_focus_up.png` + `home-up.xml` | `cardShelfItem` focused at `[742,267][1148,496]` (203×114.5dp). | Single cyan perimeter is visible without a white inner ring; D-pad returns from catalog to the shelf. |
| `after-v2/f2/09_details_final.png` + `details-final.xml` | Final `btnPrimaryAction` bounds `[336,469][658,553]` (322×84 px); XML target is 48dp high. | Primary action has the final target size and uses the shared theme-accent focus overlay. |
| `after-v2/f2/15_details_focus_tab_final.png` + `15_details_focus_tab_final.xml` | DOWN from the primary action focuses `tabPillContainer` `[336,588][506,672]` (170×84 px); XML target is 48dp high. | Cyan ring identifies transient focus; the selected tab also has a quiet filled surface. Episode navigation continues through the next focus step. |
| `after-v2/f2/13_home_touch_details_fixed.png` | Tap at `[700,528]`; no focused item or collection outline appears. | Touch on the hero background does not decorate the entire shelf. |
| `after-v2/f2/14_home_focus_card_fixed.png` + `14_home_focus_card_fixed.xml` | `cardShelfItem` focused at `[308,759][714,988]` (406×229 px). | The individual card retains a single cyan outline after collection containers were excluded. |

## F3 viewport and surface verification

| F3 area | Evidence | F3 status | Remaining phase |
|---|---|---|---|
| Collection clipping | Home `contentScrollView` in base/land/TV/sw720dp-land and Details `scrollView` in base/land set `clipChildren`/`clipToPadding` true; shelf/grid/episode rows retain local clipping behavior. | Scroll content is contained inside its viewport while card-level focus treatment remains visible. | Audit other screens and orientation changes in F4–F6 |
| Focused Home item visibility | `after-v2/f3/05_home_focus_scrolled.png` + `05_home_focus_scrolled.xml` | `cardGridItem` fully visible at `[314,162][520,604]`; after DOWN from hero through the shelf, the page scrolls the complete focused tile into view. | Continue checking end-of-list, search and async updates in F4/F6 |
| Details backdrop boundary | `after-v2/f3/02_details_after_clip.png` + `02_details_after_clip.xml` | In the fixture, `ivHeroBackdrop` ends at y=574 px and `rvSeasonTabs` begins at y=581 px. The user screenshot's art bleed did not reproduce in this layout; scroll viewport clipping is now explicit. | Recheck on production metadata and other themes in F4/F5 |

## F1 measured visual evidence

| Capture and hierarchy | Measured value | Interpretation |
|---|---|---|
| `after-v2/f1/home-focus-hierarchy.xml` | Focused `btnFeaturedInfo` bounds `[605,486][823,570]` = 109×42dp. | Historical F1 baseline: the target was below the 48dp standard control target and had a double white ring; both were addressed in F2. |
| `after-v2/f1/home-down-hierarchy.xml` | Focused `cardShelfItem` bounds `[308,759][714,988]` = 203×114.5dp. `tvHomeCatalogTitle` is at `[308,1077][1836,1080]` (3 px visible). | Historical baseline showed the catalog below the initial fold; F3 now scrolls the full focused grid card into the viewport. |
| `after-v2/f1/theme-picker-focus-hierarchy.xml` | Focused `cardTheme` bounds `[516,839][1403,1003]`; its label is Obsidian. | The new sixth theme can be reached by D-pad scrolling; the original five remain in place. |

| Rule | Source | Diagnostic evidence / baseline finding | F0 status | Planned phase |
|---|---|---|---|---|
| H-F1–F2 | Focus and selection, pp. 2–5 | Cards have prominent rings, but selected/favorite and focused states are not consistently distinguishable and every action path is not proven. | Gap | F1 focus |
| H-F3, H-F7 | Focus and selection, pp. 2–5 | Home/details choose initial targets; async list updates and full screen need interaction verification to ensure focus is not stolen. | Not verified | F1 |
| H-F4, H-L2, H-K1, H-F10 | Layout, p. 6; Focus and selection, p. 4; Lockups, pp. 1–4 | Some cards visibly clip/collide under focus, and shelf card treatments differ. F0 episode-focus route now reproduces an episode card state. | Gap | F1/F2 |
| H-F5, H-R1–R5 | Focus and selection, p. 4; Remotes, pp. 1–3 | User report confirms BACK can strand player playback. D-pad direction, central-key single activation, player parity and return hierarchy need real key testing. | Gap | F1 player |
| H-L1, H-L3–L6, H-C1 | Layout, pp. 1–6; Collections, tvOS pages | Details episode region leaks hero art through its content surface; home bottom rows and settings rows crop; grid rhythm and responsive variants need comparison. | Gap | F2/F3 |
| H-Y1–Y8, H-A1, H-A5 | Typography, pp. 5–10, 15–16; Accessibility, pp. 3–4 | Font scale 1.0/1.3/2.0 was exercised on Home, Details, Files, Settings, Profiles and Player. Home hero/actions now scale at >1.0 and focused card remains reachable, but 2.0 still clips/truncates essential content on multiple screens. | Verified gap; partial Home improvement | F8 |
| H-A2, H-A3, H-CO1–CO6, H-D2–D4 | Accessibility, p. 4; Color, pp. 1–4, 8–9; Dark Mode, pp. 2–5 | Gray metadata overlays art without consistent scrim; oversized favorites stars make semantic focus/selection ambiguous; cyan is overused. Contrast across themes is not yet measured. | Gap | F2 |
| H-A4 | Accessibility, p. 5 | Icon controls and action affordances vary in focus/hit bounds; details action group is visually dense. | Gap | F1/F2 |
| H-A6–A10, H-G1, H-U1 | Accessibility, pp. 2, 6–8; Loading/Managing accounts, tvOS pages | Global animation scales set to 0 in MuMu; focus remained visible and D-pad navigation worked. This does not prove Android Reduced Motion integration. Content descriptions, live captions, real account flow and timed playback remain unverified. | Partial; remaining gaps explicit | F8 / authenticated runtime |
| H-D1, H-D5 | Dark Mode, pp. 1–5 | Dark-only shell is appropriate to media, but reduced-transparency/high-contrast behavior is not demonstrated. | D-02 / not verified | F2/F5 |
| H-M1–M5, H-O1–O5 | Materials, pp. 2, 5–6; Motion, pp. 1–4 | Image leaks through detail shelf and player menu; focus-motion timing/cancelability is not evidenced by still captures. | Gap / not verified | F1/F2/F6 |
| H-Q1–Q5, H-N1 | Alerts/Action sheets/Modality/Menus PDFs assigned to F6; not yet source-checked | Theme dialog nearly fills the display; player menu layers expose artwork through a content-heavy panel; Back/dismiss behavior needs validation. | Pending source review | F6 |
| H-V1, H-PV1–PV6 | Going full screen/Playing video PDFs assigned to F6; not yet source-checked | User report identifies failed player exit path. OSD and playback parity, aspect fill, loading, resume and overlay timing need runtime validation. | Pending source review | F6 |
| H-S1, H-NV1–NV8, H-SE1, H-PG1 | Lists/Search/Settings/Sidebars/Collections/Loading PDFs assigned to F3–F5; not yet source-checked | File list/search empty states, rail behavior, settings organization and progress semantics need an interactive audit. | Pending source review | F3–F5 |
| H-RV1–RV2 | Ratings and reviews/Rating indicators PDFs assigned to F6; not yet source-checked | Rating prompt is shown by fixture; engagement timing, dismissal and rating representation need checking. | Pending source review | F6 |

## F0 measured evidence

| Capture and hierarchy | Measured value | Interpretation |
|---|---|---|
| `after-v2/f0/02_home_shelf_focus.png` + `after-v2/f0/home-hierarchy.xml` | Focused `cardShelfItem` bounds `[308,759][714,988]` = 406×229 px. `tvHomeCatalogTitle` bounds `[308,1077][1836,1080]` = 3 px visible at bottom edge. | Historical first-fold view showed only the top of the next section; F3 confirms D-pad scrolling reveals the focused catalog tile. |
| `after-v2/f0/06_details_episode_focus.png` + `after-v2/f0/details-episode-hierarchy.xml` | Focused `cardEpisodeItem` bounds `[336,718][651,988]` = 315×270 px (157.5×135 dp); hierarchy confirms `focused=true`. | Confirms the previously mislabeled state now actually focuses episode 1; used as reproducibility evidence, not a claim that details layout conforms. |

## F0 rubric (0–10)

Ratings judge current screenshots and code evidence, not future quality. They are not averaged into a single score because that obscures critical TV navigation failures.

| Area | Score | Evidence |
|---|---:|---|
| Home visual hierarchy | 5.5 | Hero consumes most of the viewport; shelf begins close to the fold; multiple prominent button shapes and cropped lower row in provided capture. |
| Catalog/card system | 4.5 | Cards are dense and inconsistent; favorite stars dominate artwork; labels clip; category pills compete with titles. |
| Files and folder navigation | 5.0 | Breadcrumb/search affordances exist, but sparse content has weak empty/error hierarchy and the root session error is easy to confuse with a transient failure. |
| Media details | 4.5 | The supplied screenshot shows image bleed through the episode region, overly dense outlined actions and clipped episode titles/metadata. |
| Player behavior | 3.0 | User-reported Back behavior can strand full-screen playback; this is a functional blocker until D-pad and touch paths are reproduced and fixed. |
| Settings/dialogs | 5.0 | Large setting rows are readable, but screenshots show header/content cropping and dialog backdrop/shape consistency problems. |
| Focus and accessibility | 4.0 | Focus rings exist, yet card/action hit area, contrast, focus-vs-selected semantics and 10-foot movement are unverified or visibly inconsistent. |

## F0 checks and limitations

- The repository has 152 JVM unit tests in the current checkout; all 152 passed with `--rerun-tasks`. This is the observed suite count; older project notes reference 145 or 122 tests.
- `assembleDebug` passed before the fixture-only route adjustment. F0's final gate must rebuild after that adjustment.
- The user-provided `current-state/` screenshots remain byte-for-byte preserved. They are evidence supplied with the task; don't overwrite them with emulator output.
- OAuth/Drive login, expired-token behavior, network failure recovery, MPV/ExoPlayer key behavior, and subfolder traversal require runtime fixtures/accounts beyond the current visual fixture. Do not infer that these paths pass from screenshots or JVM tests.


## F4 — Coleções, busca e preferências

| Área | Evidência | Resultado desta fase | Pendente |
|---|---|---|---|
| Hierarquia e foco de Files | `after-v2/f4/files_rail.xml`, `files_dpad_final.xml` | D-pad parte da navegação lateral, alcança o primeiro card e segue para a linha seguinte. Listener de paginação agora compõe com o callback de foco do card em vez de substituí-lo. | Paginação remota e ordem/listagem de dados do Drive real. |
| Busca contextual | `fragment_files.xml`, `after-v2/f4/files_search_test.xml` | Placeholder explicita “neste diretório”; busca alcançável por D-pad após mover para a coluna direita. | Sugestões/histórico de busca não são implementados; testar resultados, teclado Android TV e limpar em dados reais. |
| Estado visual de foco | `settings_after.png`, `settings_first_row_focus.png` | Configurações usa um único anel ciano temático; contorno branco duplicado removido do drawable compartilhado. | Validar dialogs e todos os temas em execução. |
| Título/voltar em Settings | `layout-land/fragment_settings.xml`, `settings_first_row_focus.png` | Barra fixa mostra “Configurações”; seta possui descrição “Voltar”; foco de primeira linha tem alvo de retorno explícito. | BACK do sistema e tela completa em device build de produção. |
| Preferências condicionais | `SettingsFragment.kt` | Ordem de foco ignora linhas ocultas e é refeita quando muda a visibilidade de sincronização MAL. | Estado de conta e callback OAuth real. |

**Fontes locais consultadas:** Collections pp. 1–2 (grades/linhas padrão, escolha fácil); Lists and tables pp. 2–3 (texto sucinto e comportamento de foco em tvOS); Search fields p. 6 e Searching pp. 1–2 (escopo, sugestões e privacidade); Settings p. 2 (opções gerais, evitar configuração redundante); Sidebars pp. 1–2 (hierarquia enxuta); Pickers pp. 1–2; Focus and selection pp. 2–5; Typography pp. 1–2 (legibilidade à distância e contraste). Adaptações Android TV estão descritas em `FRONTEND_LOG.md`; as diretrizes Apple são referência, não requisito nativo Android.

## F5 — Player e controle remoto

| Área | Evidência | Resultado desta fase | Pendente |
|---|---|---|---|
| Estado da HUD e retorno | `PlayerControllerVisibility.kt`, `PlayerControllerVisibilityTest.kt`, `after-v2/f5/player_back_focus.xml`, `touch_back_result.png` | A Activity deixa de inferir a HUD visível a partir de subviews residuais após o controller fechar. A fixture valida seta por OK/toque; D-pad sobe ao retorno e o ativa. | Confirmar sequência OSD→player com playback real e timeout no ExoPlayer/MPV. |
| Foco e navegação no OSD | `player_initial.xml`, `player_left_focus.xml`, `player_right_focus.xml`, `player_up.xml` | Foco inicia em Play/Pause e percorre seek bar, controles de transporte, retorno e Mais opções no MuMu. | Navegação e single activation com teclas físicas em reprodução real; teste no hardware Android TV. |
| Opções secundárias | `player_quick_menu.png`, `player_quick_menu_down.png`, `player_after_menu_back.xml` | Dialog abre na primeira linha focada, DOWN avança e BACK fecha restaurando foco no botão de origem. Velocidade, proporção, capítulos e dados técnicos preservados. | Validar cada ação com mídia compatível e leitura técnica real. |
| Paridade e playback | inspeção de `PlayerActivity`/`MPVActivity` e fixture | Sem recurso existente removido; labels do fechar localizadas. A fixture testa apresentação/retorno do drawer e navegação, não reprodução. | Drive autenticado é necessário para decoder, streams, legendas, AniSkip, autoplay e ambos os engines. |

**Referências locais consultadas:** Playing video pp. 1–2 (ações sucintas, transporte previsível e proporção de vídeo) e Remotes pp. 1–2 (comportamento padrão de Play/Pause/Back e foco direcional), adaptadas à plataforma Android TV.

## F6 — Player e diálogos

| ID da regra | Tela/componente | Evidência (captura + valor medido + arquivo) | Status |
|---|---|---|---|
| H-Q1–Q3 | Avaliação MAL | `after-v2/f6/rating_before.png`: modal com duas ações claras; `rating_cancel.xml` e `rating_back.xml`: Cancelar e BACK dispensam sem enviar; `menu_final_back.png` mostra OSD e foco em `btnPlayPause`. | OK (fixture) |
| H-Q4 | Menu contextual de opções | O player usa menu de lista contextual Android, não action sheet; a ação é acessada deliberadamente em “Mais opções” e BACK/× dispensam. | N/A-Android |
| H-Q5 | Avaliação, editor de legendas e menu | `rating_before.png`, `subtitles_before.png`, `menu_before.png`: uma tarefa por modal; menus/drawers fecham por BACK. Modal aninhado de cor não revela o OSD antes do modal pai fechar. | OK (fixture/código) |
| H-N1 | Mais opções do OSD | `menu_before.png` / `menu_before.xml`: 5 ações secundárias, primeiro item focado; DOWN move para o próximo; BACK fecha e restaura Play/Pause. Ações de velocidade, proporção, capítulos e informação técnica continuam presentes. | OK (fixture) |
| H-B1–B5 | Avaliação MAL | `rating_before.png`: nenhum score pré-selecionado (`?`); botão de envio inicialmente desabilitado; `rating_selected.xml`: após 3 RIGHT, valor `8`, envio habilitado; labels em PT-BR. | OK (fixture) |
| H-M1–M5 / D-01 | OSD e diálogos | `menu_final.png`, `subtitles_before.png`: `FrostedWindow.kt` usa alpha `0xE8` (91%) com blur e `0xF2` (95%) sem blur; dim 35%; conteúdo continua perceptível sem competir com os rótulos. | D-01 |
| H-V1 | Player visual | `menu_final_back.png`, `rating_back.xml`, `subtitles_back.xml`: modal fechado com OSD visível e foco restituído; fixture não executa mídia real. | OK (fixture) |
| H-PV1–PV6 | ExoPlayer e MPV | Inspeção de `PlayerActivity.kt` e `MPVActivity.kt`; esta fase não removeu handlers. Buffering, timers, saída/retomada de stream, tracks, AniSkip e autoplay exigem fonte autenticada e não foram simulados. | Pendente (sem playback real) |
| H-RV1–RV2 | Pedido de avaliação | `MalRatingDialog.kt`: pedido continua acionado no ponto de conclusão existente; nota só é enviada após seleção explícita; botão Cancelar e BACK dispensam. Uma avaliação não é submetida pela nota inicial. | OK (código/fixture) |
| H-A3, H-A4, H-A10 | Menu, rating e editor de legendas | Dumps UIAutomator confirmam elementos acionáveis e foco; `SeekBar` e botões são selecionáveis por D-pad/toque. Escalas 1.0/1.3/2.0 exercitadas; cortes permanecem em várias telas em 2.0. A descrição semântica dos ícones ainda requer auditoria dedicada. | Parcial; F8 |

## F8 — Texto ampliado em Configurações e Perfis

| Regra | Evidência | Status |
|---|---|---|
| H-A5, H-Y1–Y8 | `after-v2/f8-settings-2.png` antes: summaries cortados dentro de rows landscape fixas; `f8-settings-2-after.png` depois: “Meu Drive › Animes” e tema têm altura suficiente e não se sobrepõem. FontScale 2.0. | Melhoria confirmada; Settings passa a rolar verticalmente |
| H-A5, H-Y1–Y8, H-L4 | `after-v2/f8-profiles-2.png` antes: nome/role cortados; `f8-profiles-dpad.png` depois: nomes e badges quebram em duas linhas, botões inteiros. | Melhoria confirmada; título ainda domina a tela em 2.0 |
| H-F2–F4, H-R3 | `f8-profile-down.xml`: três RIGHT alcançam Add Profile e DOWN foca `btnManageProfiles` `[730,854][1191,952]`; `f8-settings-down.xml`: DOWN foca `settingLibraryRoot` `[284,409][1635,594]`. | D-pad sem dead-end nos caminhos testados |
| H-A5, H-Y1–Y8 | Escalas 1.3/2.0 inspecionadas em Home, Details, Files e Player (`f7/scale-*`). Home CTA parcialmente corrigida em F7; Details actions empilhadas e Files grid reduzido em F9. Details synopsis/title, Files nav/search e Player OSD continuam truncando/cortando em 2.0. | Parcial; gap para F10 |

Checks: `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou com 161 testes; foco exercitado no MuMu x86_64. Fixture exclusivamente demonstrativa; rede Drive e playback real não cobertos.

## F9 — Reflow de ações Details e grade Files

| Regra | Evidência | Status |
|---|---|---|
| H-A5, H-L4, H-B1–B3 | `after-v2/f9-details-2.png` e `f9-details-down5.png`: cinco ações em coluna em 200%; botões exibem labels inteiros. `f9-details-down1.xml`: DOWN vai a Trailer; `f9-details-down5.xml`: cinco ações percorridas até a aba, com scroll automático. | OK (fixture/d-pad); synopsis ainda elide |
| H-A5, H-Y1–Y8, H-CO1–CO2 | `after-v2/f9-files-2.png`: grid cai de 4 para 2 colunas em 200%, com cards significativamente mais largos; `f9-files-dpad.xml` foca `cardGridItem` `[322,444][1086,1080]` após RIGHT/DOWN. | Parcial (grid/foco OK); rail e busca truncam |
| H-R1–R5, H-PV1–PV6 | Operação de player escala 2× vista em `f7/scale-2.0/player.png`; esta fase não carregou stream e não validou controles reais. | Gap permanece; fixture sem mídia/Drive |

`TvAccessibilityLayoutTest`: 2 casos para preservar colunas em tamanhos padrão/moderado e reduzir sem zerar em escala extrema. Build/testes e checks da fase registrados em `FRONTEND_LOG.md`.

## F10 — Navegação Files e leitura em texto ampliado

| Regra | Evidência | Status |
|---|---|---|
| H-A5, H-L4, H-S1 | `TvAccessibilityLayout.adaptFilesChrome`: rail 168/224 dp e busca 360×44/420×52 dp a 1.3×/2×; `f10-files-final-1-3.png`, `f10-files-final-2-0.png`. Nome atual da pasta cabe na toolbar; escopo da busca segue reconhecível. | OK (fixture; 1.0×/1.3×/2×) |
| H-A5, H-L4 | `adaptDetailsSynopsis`: 5 linhas em 1.3× e 8 em 2×; `f10-details-1-3.png`, `f10-details-2-0.png`; a tela rola. | OK (visual/fixture) |
| H-F2–F4, H-R3 | `f10-files-dpad-final.xml`: RIGHT/DOWN chega a `cardGridItem`; `f10-details-dpad.xml`: cinco DOWN em 2× chega à aba de temporada. | Sem dead-end nos trajetos exercitados |
| H-R1–R5, H-PV1–PV6 | `f10-player-1-0.png`, `f10-player-2-0.png`: OSD visível e Play/Pause focado. Fixture: dois UP focam `btnBack` (`f10-player-up.xml`); OK e toque encerram a Activity. Código Exo/MPV esconde HUD em BACK antes de sair. | Fixture/código OK; stream real e handlers dos engines não exercitados |
| Build e testes | `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` via Ubuntu WSL; relatório XML da suíte. | 165 testes, 0 falhas/erros/skips |

**Limites F10:** a fixture não autentica no Drive nem reproduz mídia; login OAuth, arquivos reais, Exo/MPV com stream, faixas, AniSkip e autoplay continuam sem validação. Apenas o pacote `zechs.drive.stream.visualreview` foi instalado no MuMu; o app principal e a escala do sistema ficaram preservados.

## F11 — Estados de busca e recuperação Drive em Files

| Regra | Evidência | Status |
|---|---|---|
| H-S1–S4, H-A5 | `f11/search-empty.png`, `search-empty.xml`: busca `nomatchzz` comunica zero correspondências; `search-cleared.png` mostra itens restaurados após Limpar. | OK (fixture) |
| H-F2–F4, H-R3 | `f11/focus-card.xml` → `search-focused.xml` → `card-after-search.xml`: card ↑ busca ↓ card. Toggle por toque produz `list-mode.xml`, com file rows e sem grid rows. | OK (fixture; percurso exercitado) |
| H-A5, H-B1–B3, H-F2–F4 | `f11/auth-error.png` e `auth-error-scale-2.png`: ação Conectar em primeiro plano, Retry secundário, foco inicial na conexão; DOWN em 1×/2× vai para Retry. A busca/rail usa o adaptador de texto ampliado real. | OK (layout/foco fixture) |
| H-A3, H-R3 | `f11/auth-error.xml`: nó focado `btnConnectDrive`; `auth-error-down.xml`: nó focado `btnRetry`. | OK (fixture) |
| Fim a fim de autenticação | Handler Files navega à SignIn existente; OAuth, salvamento de refresh token e retorno à pasta exigem sessão/conta de teste e não foram executados pela fixture. | Pendente (Drive real) |
| Build e testes | `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` pelo Ubuntu WSL/JDK 21. | 167 testes, 0 falhas/erros/skips |

`check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram. APK Debug atualizado foi instalado apenas no pacote `zechs.drive.stream.visualreview`; fontScale do MuMu retornou a 1.0 e `local.properties` foi restaurado. Nenhuma publicação/release ocorreu.

## F12 — Busca Files com D-pad

| Regra | Evidência | Status |
|---|---|---|
| H-S1–S4, H-A5 | `f12-1x-verified.png` e `f12-2x-verified.png`; na segunda, o placeholder longo está horizontalmente truncado, embora campo e ações estejam visíveis. | D-pad validado; dica 2× pendente F13 |
| H-F2–F4, H-R3, H-S3 | `f12-clearresult-verified.xml`: CENTER em Limpar retorna a `etSearch`; `f12-toggle-verified.xml`: RIGHT na busca vazia foca toggle; `f12-listrow-verified.xml`: CENTER e DOWN chegam à primeira linha. Repetido em 2× com `f12-clearresult2x.xml`, `f12-toggle2x-verified.xml`, `f12-row2x-verified.xml`. | OK (fixture/D-pad 1× e 2×) |
| Build e testes | `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` via Ubuntu WSL/JDK 21. | 167 testes, 0 falhas/erros/skips |

APK instalado somente em `zechs.drive.stream.visualreview`; fontScale retornou a 1.0. O app principal/dados permaneceram intactos. OAuth/Drive e reprodução real não fazem parte da fixture.

## F13 — hint da busca Files com texto ampliado

| Regra | Evidência | Status |
|---|---|---|
| H-S1–S4, H-A5 | `f13-1.0.png`, `f13-1.3.png`, `f13-2.0.png`: hint completo; em 2×, TV usa “Buscar nesta pasta…”; 1×/1.3× mantêm “Pesquisar neste diretório…”. | OK (fixture visual; 1×/1.3×/2×) |
| H-F2–F4, H-R3 | `f13-final-focus.xml`: RIGHT na busca vazia foca toggle; `f13-final-row.xml`: CENTER e DOWN chegam ao primeiro item em 2×. | OK (fixture/D-pad) |
| H-S1, H-A5 | Teste cobre a escolha compacta só com rail e mantém a dica original sem rail. | OK (unidade) |
| Build e testes | `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` pelo Ubuntu WSL/JDK 21. | 167 testes, 0 falhas/erros/skips |

Checks de cores, contraste e diff passaram. FontScale voltou a 1.0; APK aplicado apenas à fixture visualreview.

## F14 — Configurações com foco remoto previsível

| Regra | Evidência | Status |
|---|---|---|
| H-F2–F4, H-R3 | Primeiro foco `settingSelectProfile` a 1×/2× em `f14-1x-first.xml` e `f14-2x-first.xml`; depois de 16 DOWN, `settingLogOut` focado em `f14-1x-last.xml` e `f14-2x-last.xml`. | OK (fixture, navegação vertical) |
| H-A5, H-L4 | `f14-1x-first.png`, `f14-1x-last.png` e `f14-2x-last.png`: itens com texto ampliado rolam para dentro do viewport e o cartão inferior não fica cortado. | OK (visual, 1×/2×) |
| Ações de Settings | O harness visual não conecta os listeners de preferências. OAuth, sair, limpar cache, backup e troca de conta não foram acionados. | Pendente (fluxos reais preservados) |
| Build e testes | `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` via Ubuntu WSL/JDK 21. | 163 testes, 0 falhas/erros |

`check_layout_colors.py`, `check_contrast.py` e `git diff --check` passaram. Apenas `zechs.drive.stream.visualreview` foi atualizado no MuMu. `local.properties` e fontScale foram restaurados; app principal/dados mantidos intactos.

## F15 — Sair e recuperar controles no player

| Regra | Evidência | Status |
|---|---|---|
| H-PV1–PV6, H-R1–R5 | `f15-exo-actual.xml`: seta clicável; `f15-exo-dpad-focus.xml`: foco no `btnBack`; teste real da Activity confirmou que toque/CENTER saem com HUD visível. | OK (Exo sem stream) |
| H-PV2, H-R3 | BACK oculta HUD e a ação seguinte encerra. Toque no vídeo com HUD oculto revela os controles após auto-hide de 4,5 s. | OK (Exo sem stream) |
| MPV | Handlers e navegação foram inspecionados, mas o engine termina no pacote isolado sem token Drive antes de renderizar HUD. | Pendente (sem sessão de teste) |
| Build e testes | `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` pelo Ubuntu WSL/JDK 21. | Passou; suíte sem falhas |

Nenhum arquivo de mídia/Drive ou credencial foi usado. Somente o pacote visualreview foi instalado.
