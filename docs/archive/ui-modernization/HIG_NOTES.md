# Apple HIG notes for Makimono TV

## Planejamento de produto/pareamento — 04/10/2026

Leitura de trechos gerais/tvOS para o novo `APPLE_GRADE_PRODUCT_PLAN.md`, sem afirmar releitura integral do catálogo. Managing accounts (4 páginas; geral pp.1–2 e tvOS p.3), Settings (3 páginas; geral pp.1–2), Split views (4 páginas; geral e tvOS p.3), Collections (2 páginas), Loading (3 páginas; geral pp.1–2), e trechos de Top Shelf, Playing video, Searching, Menus, Toggles, Pickers e Progress indicators. Managing accounts p.3 e Split views p.3 foram rasterizados em 110 dpi e vistos; não foi extraída nova medida de tabela oculta. Nenhum PDF foi copiado para o repo.

Regras adicionais para rastrear na implementação; **ainda não conformidade validada do aplicativo**:

- HG-P01 — Managing accounts p.3 / tvOS: minimizar entrada no controle remoto e favorecer autenticação em outro aparelho. Adaptação: QR/código, OAuth Google no celular e sessão Makimono na TV (Q07–Q12).
- HG-P02 — Managing accounts pp.1–2 / geral: explicar método/benefício da conta e oferecer gestão/exclusão coerente. Adaptação: conexão Drive explícita, desconexão por aparelho versus conta, exclusão de dados Makimono sem excluir mídia Drive (Q11–Q12/Q20).
- HG-P03 — Managing accounts p.3 / tvOS: separar credencial compartilhada dos dados de cada espectador e evitar escolha repetitiva desnecessária. Adaptação: OwnerAccount/DeviceSession/Profile separados e restauração configurável de perfil (Q09/Q20–Q22).
- HG-P04 — Settings pp.1–2 / geral: defaults úteis e opções frequentes no contexto da tarefa. Adaptação: categorias de settings e seleções áudio/legenda/ordem nas telas de uso (Q06/Q18/Q21).
- HG-P05 — Split views p.3 / tvOS: panes equilibrados e uma hierarquia/título que orientem o conjunto. Adaptação: duas colunas em TV e fluxo sequencial mobile; medidas e breakpoints requerem evidência Android (Q06).
- HG-P06 — Collections p.1 / geral: fileiras/grades previsíveis e espaço para foco sem sobreposição. Adaptação: lockup focável completo, padding e scroll por identidade (Q04/Q13–Q15).
- HG-P07 — Loading p.2 / geral: progresso determinado só quando o avanço é conhecido, sem bloquear ações independentes. Adaptação: estados partial/loading/cancel, nenhuma porcentagem inventada (Q02/Q08/Q11).
- HG-P08 — Playing video pp.3–4 / integração: retomar ponto esperado e preparar saída com contexto relevante. Adaptação: deep links/playback e retorno ao episódio/seleção; integração Apple TV app não é API Android (Q17/Q19/Q23).

Esses IDs só entram como OK em HIG_CONFORMANCE depois de evidência por tela/aparelho. Valores de segurança, HTTP e performance do ADR/plano são decisões de engenharia, não exigências HIG. O pedido novo amplia a exclusão antiga de login/backend; não é desvio visual da HIG.

F0 reference register for the ten local Apple Developer Documentation PDFs. These are design references adapted to Android TV, not platform requirements or a claim of Apple-platform conformance. No PDF was copied into the repository. Page numbers below refer to the printed page number in each supplied PDF (not a web page or a platform not requested).

## F0 PDF review record

The supplied PDFs carry the print date 03/10/2026. General and tvOS material was reviewed; sections for other Apple platforms were excluded as directed. Relevant tables and illustrations were rasterized at 115 dpi and inspected where extraction omitted structure.

| PDF | Pages | General/tvOS pages reviewed | Raster pages inspected |
|---|---:|---|---|
| Focus and selection | 6 | 1–6 | 4–5 |
| Remotes | 3 | 1–3 | — |
| Lockups | 4 | 1–4 | — |
| Materials | 8 | 1–6, 8 | 2, 5 |
| Typography | 20 | 1–10, 15–16 | 2–10 (hierarchy, scaling and tvOS styles) |
| Layout | 9 | 1–6 | 6 (focus padding, 2-column grid values) |
| Motion | 4 | 1–4 | — |
| Color | 11 | 1–4, 8–9 | 2, 9 (contrast examples and system-color variants) |
| Dark Mode | 5 | 1–5 | — |
| Accessibility | 10 | 1–8 | 2, 5 (text/contrast table and tvOS control sizes) |

The Layout p. 6 two-column table and Typography pp. 9–10 tvOS styles were legible. The rasterized Typography pages also confirm that the style weights in those tables are not interchangeable with the other platforms' tables. Accessibility p. 2 confirms the tvOS 29/23 pt text sizes and its PDF contrast table; Color p. 9 shows the custom-color variants. The additional Layout grid-tab values for three through nine columns and tvOS alert anatomy remain unverified; no substitute numbers are fabricated.

The mission catalog also seeds rules from Buttons, Labels, Collections, Top Shelf, Menus, Lists and tables, Loading, Managing accounts, Settings, and other phase-specific guides. Their guide files are assigned to F2–F6; those rules are not recorded below as PDF-reconfirmed until those guides are read in their phase.

## F1 re-review — color, layout, typography, materials, motion, accessibility

Before token changes, the general/tvOS portions of `Accessibility`, `Color`, `Dark Mode`, `Focus and selection`, `Layout`, `Materials`, `Motion`, and `Typography` were re-opened from `C:\Work\refs\hig`. The dark-mode and accessibility contrast guidance supports strong foreground contrast on dark surfaces; Focus and selection distinguishes focus from selection and requires room around enlarged focused content; Layout emphasizes alignment and adaptive hierarchy; Materials separates controls from changing imagery; Motion should be purposeful and restrained; Typography prioritizes legibility and hierarchy. These are adaptation principles, not Android implementation rules.

- Contrast is checked locally by `tools/check_contrast.py`: primary and secondary text target ≥7:1 on both base and elevated surfaces across all six palettes; text on primary action fills targets ≥4.5:1. Tertiary is kept for disabled/unavailable states.
- Obsidian is a sixth selectable palette and the fallback only when a profile has no valid saved preference. The five original enum values and user selections remain stable; this reconciles the new default with D-02's preservation constraint without adding another appearance picker.
- TV landscape safe margins now resolve to 48dp horizontal and 30dp vertical across base, `land`, and `sw600dp-land` dimension resources. Screen-by-screen clipping and focus-clearance remain phase-specific visual checks.
- Token styles use the bundled Inter family, retain scalable `sp` sizes, and shorten old motion resource timings to the semantic 100/150/220/300/450ms scale. Runtime reduced-motion behavior remains to be verified in later implementation phases.

## Focus and selection

**Source:** `Focus and selection _ Apple Developer Documentation.pdf` (6 pages), general and tvOS sections, pp. 1–6.

- Keyboard/remote focus is a model for discrete navigation; it is not a pointer. Preserve the remote's familiar directional, select, menu/back and playback behavior.
- Visually separate unfocused, focused, highlighted/activated and selected states. A selected favorite, for example, must remain distinguishable from current D-pad focus.
- Focus should elevate and illuminate the target. Scale/animation are possible cues, but need padding so the enlarged target does not overlap content.
- On tvOS full-screen media, focus is not shown until the viewer interacts with controls. Focus groups/priorities should make the most likely action straightforward.

## Remotes

**Source:** `Remotes _ Apple Developer Documentation.pdf` (3 pages), pp. 1–3.

- Keep the standard remote's gestures and button meanings. Select activates one discrete focused item; don't replace that with pointer-like interaction.
- Back/Menu navigates to the parent level. Play/Pause toggles playback. Directional presses move among a predictable set of focus targets.
- Where a gesture is used, expose an onscreen alternative. The reference emphasizes standard tvOS remotes; Android TV should additionally follow Android's own remote conventions.

## Lockups

**Source:** `Lockups _ Apple Developer Documentation.pdf` (4 pages), pp. 1–4.

- Treat an image, title and supporting metadata as one coherent lockup. Keep spacing and relative sizing consistent across shelves.
- Reserve enough surrounding space for focused lockups to grow without obscuring their neighbors. Make focus-aware image assets sharp at the larger size.
- Profile imagery is circular in the monogram guidance; use a circular crop and initials fallback instead of a hard rectangular edge.

## Buttons, collections and labels

**Sources:** `Buttons _ Apple Developer Documentation.pdf` (6 pages), pp. 1–3; `Collections _ Apple Developer Documentation.pdf` (4 pages), pp. 1–2; `Labels _ Apple Developer Documentation.pdf` (3 pages), pp. 1–2; `Lockups _ Apple Developer Documentation.pdf` (4 pages), pp. 1–2. The supplied PDFs were re-opened for F2; source files remain outside the repository.

- Buttons communicate through a coherent style, content and role. Give primary actions a clear hierarchy, use concise labels, and leave enough separation for touch and remote selection. The Buttons guide gives 44×44 pt as its general minimum hit region; this is an Apple-platform measure, so Makimono's implementation target is Android's 48dp.
- Collections should use familiar rows and grids, give browsable content room to breathe, and preserve padding around images so focus does not collide with adjacent content. A focused lockup's content, header and footer should grow as one unit; groups should use consistent sizing.
- Labels need to stay legible with custom fonts and communicate the action/context without forcing truncation of important information. Validate long strings separately from the current fixture's short titles.
- Makimono represents transient D-pad focus with one theme-accent outline and a restrained tint on compact controls. Selected seasons use a quiet surface treatment so selection does not impersonate focus. Focused cards draw through the shared overlay so poster art remains visible; focus-safe spacing and focus return still require screen-by-screen validation.

## Materials

**Source:** `Materials _ Apple Developer Documentation.pdf` (8 pages), general and tvOS sections, pp. 1–6 and 8. The supplied guide describes Liquid Glass as a tvOS navigation/control material; that visual system is not directly equivalent to Android blur.

- Separate the functional/navigation layer from the content layer. Use the material only where controls must remain legible over changing imagery.
- Choose material thickness by the amount of content that must remain visible. The tvOS table distinguishes ultra-thin, thin, regular and thick materials (p. 5); dark overlays that cover content call for a dark-scheme material.
- Test translucency against bright and dark artwork. Apply a dimming treatment when bright imagery weakens labels; don't use glass as decoration on every card.

## Typography

**Source:** `Typography _ Apple Developer Documentation.pdf` (20 pages), general and tvOS sections, pp. 1–10 and 15–16.

- The tvOS reference uses 29 pt as its standard text size and 23 pt as the smallest style (p. 9); avoid thin weights. Its style table on p. 10 ranges from Title 1 (76/96 pt) through Body (29/36 pt) and Caption 2 (23/30 pt), with paired weights/line heights.
- Preserve hierarchy, limit type families, and adapt layout when text grows. Do not truncate important labels or metadata merely to keep a fixed composition.
- Makimono uses Android `sp`/Inter; values need platform-specific conversion and device testing, not literal copying of Apple point sizes.

## Layout

**Source:** `Layout _ Apple Developer Documentation.pdf` (9 pages), general and tvOS sections, pp. 1–6.

- Group related content and establish hierarchy. Disclose secondary controls progressively; visually distinguish controls from browsable content.
- tvOS safe areas are 80 pt at left/right and 60 pt at top/bottom (p. 5). Map these conservatively to Android TV and account for overscan/device variance.
- Leave padding for focused elements that scale. The two-column grid reference specifies 860 pt unfocused content width, 40 pt horizontal spacing and 100 pt minimum vertical spacing (p. 6). Those dimensions are not directly transferable to a denser Android layout, but the emphasis on generous focus-safe spacing is applicable.
- Keep partially offscreen items symmetrical and use consistent spacing. Background art can extend behind navigation; preserve its aspect ratio using scale/crop.

## Motion

**Source:** `Motion _ Apple Developer Documentation.pdf` (4 pages), pp. 1–4.

- Motion should communicate a meaningful state change, remain brief and precise, and be cancelable where appropriate.
- Avoid gratuitous or repetitive animations on common remote interactions. Use focus transitions to reinforce, not delay, directional navigation.
- Check reduced-motion preferences and provide a restrained alternative to large scale or parallax effects.

## Color

**Source:** `Color _ Apple Developer Documentation.pdf` (11 pages), general and tvOS sections, pp. 1–4 and 8–9.

- Keep the palette purposeful and semantic; use accent colors sparingly. Never encode focus, rating, watched or warning state by color alone.
- Maintain legible foreground/background combinations over changing artwork and translucent surfaces. Color and translucency can change the appearance of nearby artwork (p. 2); validate states on varied backgrounds.
- The guide encourages testing default and increased-contrast variants (p. 2) and checking color across displays and viewing conditions.

## Dark Mode

**Source:** `Dark Mode _ Apple Developer Documentation.pdf` (5 pages), general and tvOS-relevant sections, pp. 1–5.

- Apps should generally respect system appearance; a dedicated dark-only appearance is defensible for an immersive media app, but must remain readable and consistent.
- The guide calls for at least 4.5:1 contrast for text and greater contrast for small text; the project briefing adopts 4.5:1 as a minimum audit target for all text and 7:1 as a target for small text.
- Avoid pure-white expanses in dark surfaces; keep icons distinguishable. Support increased contrast and reduced transparency where feasible.

## Accessibility

**Source:** `Accessibility _ Apple Developer Documentation.pdf` (10 pages), general and tvOS sections, pp. 1–8.

- Support intuitive, perceivable and adaptable experiences. Text should adapt to larger sizes; tvOS reference styles use 29 pt standard and 23 pt minimum (p. 3).
- Contrast guidance varies by size/weight: 4.5:1 below 18 pt regular (or below 14 pt bold), and 3:1 for larger text (p. 4). Makimono uses the stricter project-wide 4.5:1 floor.
- Don't use color alone for state. Make remote controls sufficiently large: 66×66 pt default and 56×56 pt minimum for tvOS. Allow 12 pt around beveled controls or about 24 pt for borderless controls (p. 5).
- Prefer simple gestures and expose alternative controls. Avoid time-limited interactions where possible; let viewers pause/skip autoplay; captions and audio descriptions need usable settings. Respect Reduce Motion.

## F0 rule register (traceable IDs)

IDs below are from the mission briefing's confirmed catalog. Each record is a paraphrase of the cited general/tvOS section; applicability is Android TV adaptation, not tvOS code reuse.

| ID | Source and PDF page(s) | Recorded rule |
|---|---|---|
| H-L1 | Layout, p. 5 | Keep primary content inside 80 pt horizontal / 60 pt vertical safe areas; artwork may bleed to screen edges. |
| H-L2 | Layout, p. 6; Focus and selection, p. 4 | Allow focused enlargement without covering neighbors or important information. |
| H-L3 | Layout, p. 6 | Use consistent grid spacing; 2-column reference is 860/40/100 pt and partial offscreen items should be symmetric. |
| H-L4 | Layout, pp. 1–4 | Establish hierarchy, align/group related items, and disclose secondary actions progressively. |
| H-L5 | Layout, pp. 2, 5 | Distinguish controls from content; full-screen background may extend under navigation; crop without changing aspect ratio. |
| H-K1 | Lockups, pp. 1–3 | Artwork, title and supporting content form a consistent unit and grow/contract coherently on focus. |
| H-K2 | Lockups, pp. 3–4 | Profile monograms use circular images and names; use initials as a fallback. |
| H-A1 | Accessibility, p. 3; Typography, pp. 9–10 | TvOS reference text is 29 pt standard and 23 pt minimum; avoid thin weights. |
| H-A2 | Accessibility, p. 4; Dark Mode, pp. 2–3 | Contrast threshold depends on size/weight; this project applies its stricter 4.5:1 floor and 7:1 small-text target. |
| H-A3 | Accessibility, pp. 3–4; Color, pp. 1–3 | Never encode state only through color. |
| H-A4 | Accessibility, p. 5 | TvOS controls are 66×66 pt default / 56×56 pt minimum, with padding around them. |
| H-A5 | Accessibility, p. 3; Typography, pp. 8–10 | Adapt useful text to larger sizes without overlap or loss of essential content. |
| H-A6 | Accessibility, p. 7; Motion, pp. 1–4 | Minimize timed dismissal and make necessary timed UI understandable and controllable. |
| H-A7 | Accessibility, pp. 7–8 | Playback and autoplay must remain user-controllable. |
| H-A8 | Accessibility, pp. 7–8; Motion, pp. 2–4 | Respect reduced-motion settings and avoid motion as the only state signal. |
| H-A9 | Accessibility, pp. 6–8 | Preserve legible, adjustable captions/subtitles and audio accessibility. |
| H-A10 | Accessibility, pp. 2, 6 | Expose meaningful interface descriptions to assistive technologies. |
| H-D1–D5 | Dark Mode, pp. 1–5 | A dark-only immersive media UI can be appropriate; maintain contrast, distinguish surfaces, and support increased contrast/reduced transparency. |
| H-CO1–CO6 | Color, pp. 1–4, 8–9 | Use a restrained semantic palette, don't rely on color for focus, test contrast and validate across displays. |
| H-Y1–Y8 | Typography, pp. 8–10, 15–16 | Preserve readable tvOS hierarchy, weight, tracking and leading; adapt layouts to larger text. |
| H-F1 | Focus and selection, pp. 1–5 | Keep unfocused/focused/activated/selected/disabled states distinct; focus elevates and illuminates. |
| H-F2–F4 | Focus and selection, pp. 2–5 | Make all actions reachable, don't steal focus, and reserve space for focused assets. |
| H-F5–F7 | Focus and selection, pp. 3–5; Remotes, pp. 1–3 | Hide focus when full-screen playback controls are hidden; use directional focus and set a sensible initial target. |
| H-F8–F9 | Focus and selection, pp. 4–5; Lockups, pp. 1–4 | Keep hierarchical selection persistent and ensure focused items don't crowd neighbors. |
| H-R1–R5 | Remotes, pp. 1–3 | Preserve standard remote meanings, parent/back behavior, directional navigation, select-vs-touch distinction and familiar playback toggles. |
| H-M1–M5 | Materials, pp. 2, 5–6 | Reserve glass-like material for navigation/control layers, select opacity semantically and protect text over rich media. |
| H-O1–O5 | Motion, pp. 1–4 | Motion should be purposeful, brief, coherent, cancelable and subordinate to input. |

**Catalog-only pending reconfirmation:** H-C1, H-F10, H-T1, H-B1–B6, H-Q1–Q5, H-N1, H-S1, H-G1, H-U1, H-NV1–NV8, H-V1, H-PV1–PV6, H-SE1, H-PG1, H-RV1–RV2, and H-CO/Y extensions not covered by the selected page ranges. These remain in the ledger as pending source review, not as verified HIG statements.

## Conflitos (C-01–C-08)

| ID | Reconciliation |
|---|---|
| C-01 | Earlier 3:1 floor is overridden by Dark Mode's 4.5:1 floor: apply H-A2. |
| C-02 | `text.tertiary` is reserved for unavailable content; ordinary metadata/eyebrows use secondary label treatment (H-T1). |
| C-03 | Earlier SemiBold-only title guidance conflicts with tvOS table's Medium base / Bold emphasis (H-Y1); project uses Inter, validated visually. |
| C-04 | Earlier 1.06 focus scale is superseded by reference ratios 1.09/1.14 where the component type applies (H-F10). |
| C-05 | “Never indicate focus only by color” is grounded in Color/Accessibility/Toggles, not Focus and selection. |
| C-06 | Top Shelf poster's printed unfocused 333×570 size conflicts with the 2:3 poster ratio; use 333×500 for that calculation. |
| C-07 | Some PDF tab/table visuals are absent from text extraction; rasterized Layout p. 6 confirms only the two-column measurements. Other 3–9 column measurements and alert anatomy remain unverified and are not invented. |
| C-08 | Tracking values in Typography are SF Pro metrics; Inter values require visual validation (D-06). |

## Desvios pré-aprovados (D-01–D-08)

- **D-01 — H-M2 / motivo (c):** MPV `SurfaceView` cannot blur. Alternative: ≥88% opaque dark surface + hairline, and a 35% dim layer over bright artwork. F6 `FrostedWindow` uses 0xE8 (91%) when blur is available, 0xF2 (95%) otherwise, and 35% dim in both states; see `after-v2/f6/menu_final.png`.
- **D-02 — Dark Mode / motivo (b):** Preserve the five existing dark themes; don't add a light theme or another appearance picker. Evidence: existing theme list and theme dialog.
- **D-03 — H-K1 / motivo (b):** The v1.5.8 invariant keeps poster titles visible at rest. Alternative: secondary label at rest, primary emphasis on focus, title grows with card. Evidence: catalog/details captures.
- **D-04 — Focus / motivo (a):** tvOS parallax/tilt assumes Siri remote swipe and layered image assets, with no Android D-pad equivalent. Alternative: restrained scale, elevation and fill; don't add parallax.
- **D-05 — H-B5/H-N1/H-Q2 / motivo (a):** English title-style capitalization has no PT-BR equivalent. Alternative: sentence case consistently across UI.
- **D-06 — H-Y3/H-Y5 / motivo (b):** Inter is the established project identity. Alternative: preserve Inter, validate its tracking/readability, and don't copy SF Pro metrics verbatim.
- **D-07 — H-PG1 / motivo (a):** tvOS has no equivalent seek slider. Alternative: retain Android `SeekBar` with strong focus treatment and test remote access.
- **D-08 — H-NV3 / motivo (b):** Season changes can't steal focus from asynchronous metadata/episode updates (v1.4.23 invariant). Alternative: keep explicit OK confirmation if focus-driven debounce would violate the invariant.

Apple HIG pages describe Apple platforms. The tvOS sizing and interaction metrics above are used as the user's chosen adaptation reference; they are not statements about Android's native APIs. Exact 3–9 column grids and alert anatomy not legible in the supplied source remain explicitly unverified.

## F6 PDF re-review — Player e diálogos (2026-10-03)

Todos os PDFs obrigatórios da fase e os cinco PDFs transversais F0 foram relidos antes da implementação. As notas abaixo resumem as seções gerais/tvOS; orientações exclusivas de iOS, iPadOS, macOS, visionOS e watchOS não foram portadas.

| PDF | Páginas | Regras reconfirmadas / aplicadas |
|---|---:|---|
| Playing video | 6 | pp. 1–4: controles de reprodução familiares, proporção original, overlays breves e retorno contextual ao sair. |
| Going full screen | 3 | pp. 1–2: conteúdo prioriza tela cheia, controles essenciais fáceis de revelar e saída por escolha da pessoa. |
| Sliders | 3 | pp. 1–3: direção e semântica familiares, feedback do valor; tvOS não oferece slider nativo. `SeekBar` Android permanece por D-07. |
| Menus | 7 | pp. 2–4: rótulos sucintos, ações frequentes primeiro, grupos consistentes e listas dinâmicas roláveis. |
| Sheets | 8 | pp. 1–3: tarefa curta ligada ao contexto, Cancelar/Concluído explícitos e saída acessível; sheet Apple não foi copiada como componente iOS. |
| Alerts | 6 | pp. 1–3: alertas raros, título direto, mensagem necessária e até três ações. |
| Action sheets | 3 | pp. 1–2: escolha ligada a uma ação intencional, poucas opções e Cancelar explícito. |
| Modality | 3 | p. 2: modal com tarefa clara, caminho simples e uma rota evidente para dispensar. |
| Ratings and reviews | 3 | pp. 1–2: solicitar avaliação após engajamento e em pausa natural; pedido dispensável e sem insistência. |
| Rating indicators | 2 | pp. 1–2: nota textual clara; estrelas só quando comunicam classificação. |
| Focus and selection (releitura transversal) | 6 | pp. 2–5: foco distinto, destino previsível e sem roubar foco. |
| Layout (releitura transversal) | 9 | pp. 2–6: hierarquia, safe areas e espaço para estados focados. |
| Accessibility (releitura transversal) | 10 | pp. 3–7: controles legíveis, alternativas ao gesto e movimento reduzido. |
| Materials (releitura transversal) | 8 | pp. 2–6: material adequado à densidade do conteúdo e contraste sobre mídia. |
| Motion (releitura transversal) | 4 | pp. 1–4: movimento funcional, curto e subordinado à entrada. |

**Conflitos desta fase:** nenhum novo conflito entre PDF e catálogo. A limitação de blur sobre `SurfaceView`/MPV permanece coberta por D-01: a janela é `0xE8` (91% opaca) quando blur está disponível e `0xF2` (95%) no fallback, com dim de fundo de 35%; a captura final do menu confirma texto legível sobre arte clara.

## F7 PDF re-review — escalabilidade, movimento e foco (2026-10-04)

Os PDFs Motion, Layout, Accessibility, Materials, Focus and selection e Typography foram relidos nas seções gerais e tvOS antes da rodada. Princípios exclusivos de outras plataformas Apple não foram tratados como requisitos nativos Android.

| PDF | Páginas revisadas | Regras usadas nesta rodada |
|---|---:|---|
| Motion | 1–4 | Animação deve comunicar mudança, ser curta, cancelável e não bloquear a entrada; minimizar movimento redundante. |
| Layout | 2–6 | Hierarquia estável, adaptação a conteúdo maior, safe area e espaço para foco ampliado sem colisão. |
| Accessibility | 2–7 | Texto ampliável, contraste, controles alcançáveis, estado não indicado só por cor e movimento configurável. |
| Materials | 2–6 | Superfícies translúcidas preservam legibilidade sobre mídia; contexto pode permanecer visível sem competir com texto. |
| Focus and selection | 2–5 | Foco D-pad visível, previsível e alcançável; foco transitório não deve ser confundido com seleção persistente. |
| Typography | 1–6, 8–10, 15–16 | Hierarquia e peso legíveis a distância, layout adaptável e redução de truncamento em tamanhos ampliados. |

**Adaptação Android verificada:** com animações globais do MuMu em zero, o foco D-pad continuou visível e navegável; o teste não afirma suporte específico ao recurso de acessibilidade Reduced Motion do Android. Home passou a aumentar a altura do hero e dos botões proporcionalmente à escala de fonte para manter as ações visíveis em 1,3×. A validação em 2,0× e nas demais telas encontrou truncamento e conteúdo cortado em Home, Details, Files, Settings e Profiles; isso permanece um defeito aberto para F8, não uma conformidade presumida. Evidência reduzida: `docs/ui-audit/after-v2/f7/reduced-motion-home.png` e `reduced-motion-home.xml`; comparativos 1,0×/1,3×/2,0× e captura corrigida estão em `docs/ui-audit/after-v2/f7/`.

**Conflitos desta fase:** nenhum novo. O alvo de 200% do material Apple é um critério de referência; layouts Android ainda precisam preservar conteúdo útil e não se declaram aprovados enquanto as capturas 2,0× exibirem cortes. As escalas globais de animação foram restauradas após o teste.

## F8 PDF re-review — Lists, Collections, Buttons e responsividade (2026-10-04)

Layout pp. 2–6, Typography pp. 1–2/6/9–10, Accessibility pp. 2–5, Lists and tables pp. 1–3, Collections pp. 1–2, Buttons pp. 1–3/7–8 e Focus and selection pp. 1–5 foram relidos. Aplicação prática: listas de texto precisam permitir leitura em mais de uma linha e rolagem; grids permanecem adequados para arte; ações primárias mantêm hierarquia e padding; foco D-pad continua distinto da seleção. Para escala ampliada, conteúdo pode reflow e crescer, preservando a ordem e a rota de navegação. Os números e componentes Apple são referência de design, não especificações de Android.

**Aplicado nesta rodada:** linhas de Settings na variante landscape passam de altura fixa para `wrap_content` com mínimo/padding de controle, preservando summaries em 200%; cards de perfil permitem nome/role em duas linhas e os botões inferiores crescem conforme a tipografia. Capturas pré/pós e dumps UIAutomator, incluindo foco nos caminhos Settings e Profiles a 2.0×, ficam em `docs/ui-audit/after-v2/f8-*`. Home/Details/Files/Player foram auditados e os defeitos registrados para o ciclo seguinte; a conclusão de F8 não significa conformidade a 200% nessas telas.

**Conflitos desta fase:** nenhum. O foco após RIGHT/DOWN foi testado sem transições; estado selecionado e foco não foram fundidos. Configuração de fontScale do emulador retornou a 1.0.

## F9 PDF re-review — Details, Files e reprodução (2026-10-04)

Playing video pp. 1–4, Remotes pp. 1–3, Layout pp. 1–6, Typography pp. 1–2/6, Accessibility pp. 2–5, Collections pp. 1–2, Lists and tables pp. 1–3 e Focus and selection pp. 1–5 foram relidos. Na adaptação: manter proporção e ações previsíveis, hierarquia do foco D-pad, grade de imagem familiar e textos em listas sem sobreposição; usar scroll/reflow para conteúdo complementar. Não foram portadas APIs/comportamentos exclusivos de Siri Remote ou AVPlayer.

**Aplicado nesta rodada:** a partir de fontScale 1.3, as cinco ações do cabeçalho de Details empilham dentro da coluna, usam até duas linhas e mantêm a ordem Play, Trailer, Favorito, Seguir e Assistido. O grid do explorador reduz colunas gradualmente (−1 em 1.3, −2 em 1.8+) para aumentar a área dos títulos. Evidências 200% em `docs/ui-audit/after-v2/f9-*`.

**Verificação:** RIGHT/DOWN em Files focou `cardGridItem` no grid de duas colunas. Em Details, DOWN a partir da ação primária alcançou Trailer; mais quatro DOWN passaram por todas as cinco ações e chegaram à aba de temporada focada, enquanto o NestedScrollView deslocava a região. As cinco ações ficaram inteiras na captura. O player continua sem mídia real; o OSD 200% permanece assunto pendente. Side rail e busca do Files também ainda cortam rótulos/texto, para F10.

**Conflitos desta fase:** nenhum. Reduzir colunas é resposta de layout explícita ao tamanho de texto e preserva todos os itens; não remove dados ou ações.

## F10 PDF re-review — busca, sinopse e navegação (2026-10-04)

Layout pp. 2–6, Typography pp. 1–6/8–10/15–16, Accessibility pp. 2–7, Focus and selection pp. 2–5, Remotes pp. 1–3, Playing video pp. 1–4, Search fields pp. 1–4 e Searching pp. 1–2 foram relidos nas seções gerais e tvOS relevantes. Princípios aplicados: preservar o escopo reconhecível da busca, deixar alvos e rótulos crescerem com texto ampliado, permitir mais de uma linha e rolagem, e manter rotas de foco previsíveis. Nenhum comportamento específico de Apple TV/Siri Remote foi transplantado como requisito Android.

**Aplicação preparada:** rail Files larga em escalas 1.3×/2×; rótulos com até duas linhas; busca mais alta/larga e ações de toque ampliadas; layout mobile preserva a largura original quando não há rail. Sinopse Details permite mais linhas e usa o scroll já existente. O OSD foi inspecionado na captura 2×, em que as ações visíveis cabem; não se alterou o conjunto de controles.

**Validação F10:** no MuMu x86_64, Files/Details foram capturados a 1.0×/1.3×/2.0× e OSD a 1.0×/2.0×. A toolbar da fixture representa a tela real (`args.name`, nome atual da pasta), não breadcrumb sintético. A busca conserva o texto indicativo; o label “Configurações” cabe sem quebra órfã em 1.3× e 2.0×. D-pad RIGHT/DOWN em Files foca um card; cinco DOWN em Details 2× chegam à aba de temporada com scroll. A escala global foi restaurada a 1.0×.

O build do Windows falhou antes de compilar ao criar a conexão loopback Java; o build/teste foi concluído no Ubuntu WSL com JDK 21 e Android Build Tools 34. `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild` passou com 165 testes, zero falhas/erros/skips. O APK atualizado foi instalado somente no pacote fixture `zechs.drive.stream.visualreview`; `local.properties` foi restaurado e o app principal permaneceu intacto. Capturas e dumps estão em `docs/ui-audit/after-v2/f10-*`.

**Limite:** validar BACK do player, decoder, streams/Drive autenticado, trilhas, capítulos, AniSkip, autoplay e paridade efetiva dos engines exige reprodução real; os screenshots de OSD são fixture e a inspeção de BACK Exo/MPV foi estática. APKs debug usam assinatura DEBUG e não são releases distribuíveis.

## F11 PDF re-review — busca e navegação no explorador (2026-10-04)

Search fields pp. 1–2, Searching pp. 1–2, Lists and tables pp. 1–2 e Focus and selection pp. 1–3 foram relidos. Para o Files Android, princípios aplicados: placeholder identifica o escopo local; resultados refinam enquanto a pessoa digita; existe caminho claro para limpar; textos de uma lista devem permanecer legíveis; foco remoto precisa comunicar o próximo alvo e alcançar todas as ações sem depender de toque.

**Aplicação:** uma busca sem correspondências agora tem estado vazio próprio e limpar restaura a lista. A primeira linha da coleção volta verticalmente para a busca, e busca/ações descem para conteúdo. O estado de sessão Drive separa reconexão (principal) de Retry (secundário), preserva a mensagem explicativa e inicia o foco na ação que corrige a condição. O número de página, layout e política dos componentes Apple não foram copiados para Android.

**Evidência e limite:** MuMu testou grade/lista, busca, limpar, estado vazio, foco, 200% e sequência Connect→Retry com a fixture; arquivos e fluxos foram medidos em `after-v2/f11/`. O listener de Connect aponta para SignIn no código, mas a fixture não executou OAuth ou a conta Drive real.


## F12 PDF re-review — controles de busca no D-pad (2026-10-04)

Search fields pp. 1–2, Searching pp. 1–2, Focus and selection pp. 1–3, Accessibility pp. 2–5 e Remotes pp. 1–3 foram reconsultados. Aplicação no Files: cursor do campo continua sendo edição de texto; botões adicionais seguem alcançáveis pelo controle; esconder um controle após sua ação não deve perder o foco remoto no rail.

**Aplicado e validado:** Limpar retorna foco/cursor ao campo `etSearch`. MuMu comprovou a rota de busca, Limpar, Grade/Lista e primeira linha por D-pad em 1× e 2×, com fixture e dumps/screenshot em `docs/ui-audit/after-v2/f11/f12-*`.

**Limite encontrado:** em 2× o placeholder textual ultrapassa a largura disponível e é elidido antes do fim. O campo continua funcional; a dica precisa ficar mais concisa sem reduzir tipografia. Fica planejado para F13.

## F13 PDF re-review — busca em tamanho de texto ampliado (2026-10-04)

Search fields pp. 1–2, Typography pp. 1–6 e 15–16, Accessibility pp. 2–5, Layout pp. 2–6 e Focus and selection pp. 1–5 foram reconsultados. A dica deve identificar a ação e caber no espaço responsivo sem redução do tamanho da fonte; quando a largura disponível depende da composição com rail, o texto auxiliar pode ser mais conciso nesse layout.

**Aplicação e verificação:** `TvAccessibilityLayout` escolhe “Buscar nesta pasta…” somente em 1.8× ou maior e se a tela tem rail. O MuMu mostrou a dica completa nas variantes com rail a 1.0×, 1.3× e 2.0×. O retrato mantém a dica longa independentemente do tamanho da fonte. A trilha D-pad busca → alternância → primeira linha foi repetida em 2×; screenshots/XML estão em `docs/ui-audit/after-v2/f11/f13-*`.

**Conflitos:** nenhum; placeholder curto mantém o escopo local da busca no contexto da pasta atual e não altera a query ou resultados.

## F14 PDF re-review — foco remoto em Configurações (2026-10-04)

Focus and selection pp. 1–3, Remotes pp. 1–3, Accessibility pp. 2–5, Settings pp. 1–4 e Lists and tables pp. 1–2 foram reconsultados. Na entrada de uma lista com controle remoto, o foco inicial deve apontar a primeira ação útil, continuar evidente e preservar uma rota previsível até o fim da tela.

**Aplicação:** o cartão de perfil aceita foco em touch mode no caminho TV antes do `requestFocus()` inicial; `focusedByDefault` cobre as variantes XML como fallback. O primeiro item não exige um DOWN extra para localizar o começo da tela. Em escala ampliada, o D-pad rola o cartão final até o viewport sem ocultar o conteúdo.

**Validação e limite:** MuMu confirmou foco inicial e percurso até Sair a 1×/2× usando a fixture isolada; evidência em `docs/ui-audit/after-v2/f11/f14-*`. O harness não liga os listeners de preferências nem de conta, então ações, diálogos e OAuth reais não foram acionados. Nenhuma ação de logout/cache foi usada.

## F15 PDF re-review — saída do player e uso do controle remoto (2026-10-04)

Playing video pp. 1–4, Remotes pp. 1–3, Focus and selection pp. 1–3, Buttons pp. 1–4 e Going full screen pp. 1–3 foram reconsultados. Uma ação de sair precisa continuar distinta do toque usado para revelar/ocultar a HUD; o alvo deve ser focável e a transição de tela cheia deve preservar um caminho de retorno.

**Aplicação/resultado:** na Activity ExoPlayer atual, a seta encerra a tela quando visível, CENTER segue o foco até ela, e BACK oculta OSD antes de encerrar. A Activity real foi exercitada sem stream no pacote visualreview. A reprodução relatada não ocorreu nessa versão compilada.

**Limite:** MPV recebeu inspeção estática dos mesmos handlers, mas não tem token Drive dentro da fixture isolada e encerra antes de exibir o OSD. Reprodução de arquivo real nos dois engines permanece pendente; a conta da aplicação principal não foi usada.
