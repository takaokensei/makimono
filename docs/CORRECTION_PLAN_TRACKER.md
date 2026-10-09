# 📋 Makimono: Plano de Correção e Execução Contínua

Documento de acompanhamento da execução das Fases 0 a 8 do plano de correção Makimono.
Base: `main` @ `460ac1b` (v1.6.4.8, versionCode 60).

---

## 📊 Linha de Base (Fase 0)

* **Data do Baseline:** 2026-10-08 / 2026-10-09
* **Suíte de Testes Unitários:** 347 testes automatizados executados e passando (100% de sucesso).
* **Lint:** Configurado com verificação em builds de release (`checkReleaseBuilds true`).
* **Tamanhos de Artefato (v1.6.4.8 Release):**
  * `arm64-v8a`: ~24.27 MB
  * `armeabi-v7a`: ~24.22 MB
  * `x86`: ~24.61 MB
  * `x86_64`: ~25.94 MB
* **Tamanhos de Artefato (Debug):**
  * `arm64-v8a`: ~37.54 MB
  * `x86_64`: ~39.67 MB
* **Ambiente de Referência:**
  * Emulador Android TV 1080p (320dpi) e TV 1080p física (213dpi - tvdpi)
  * Resolução de referência tvOS/Android TV: 960x540dp base / 1920x1080 físico

---

## 🗺️ Mapa de Execução por Fases

### [ ] Fase 0: Linha de Base e Setup
- [x] P0-1: Registro do estado inicial (347 testes, tamanhos de APK anotados)
- [x] P0-2: Definição da matriz de densidades (320dpi vs 213dpi)
- [x] P0-3: Verificação de ferramentas de segurança de debug (LeakCanary, StrictMode)
- [x] P0-4 & P0-5: Matriz de auditoria documentada em `docs/ui-audit/`

### [x] Fase 1: Texto, Rótulos e Coerência (UI-01 a UI-08)
- [x] **UI-01**: Remover sufixo `" · nome"` de `SeriesMediaMetadataPolicy.kt` (retornar dados/enum de cobertura, não string crua).
- [x] **UI-02**: Unificar rótulo de filme ("Filme Completo" vs "Episódio 01") através de `PlaybackLabel` compartilhado puro e checagem da fonte da biblioteca.
- [x] **UI-03**: Encurtar hint truncado de busca ("Pesquisar neste diretório…" -> "Pesquisar aqui…") com `singleLine="true"` e `ellipsize="end"`.
- [x] **UI-04**: Padronizar strings de perfis em recursos (`values-pt/` e `values/`), eliminando strings literais em inglês na interface pt-BR.
- [x] **UI-05**: Evitar quebra no meio da palavra em títulos de cards com `maxLines="2"`, `ellipsize="end"`, e marquee apenas sob foco.
- [x] **UI-06**: Validação da escolha de raiz ("1oneblacki") no diálogo de seleção de pasta e logging estruturado.
- [x] **UI-07**: Esconder seção "Episódios" para filmes e evitar quebras de navegação D-pad (`downTarget = View.NO_ID`).
- [x] **UI-08**: Padronizar menu de navegação (rail, drawer e mobile bottom bar) unificado com strings `@string/nav_*`.

### [x] Fase 2: Foco, Zona Segura e Layout (FOC-01 a FOC-07)
- [x] **FOC-01**: Token unificado de foco (anel 3dp `#22D3EE`, escala 1.06 em cards, respeitando `ANIMATOR_DURATION_SCALE == 0`).
- [x] **FOC-02**: Correção de contorno cortado no Hero (`paddingBottom >= 8dp`, `clipToPadding="false"`).
- [x] **FOC-03**: Zona segura de overscan (48dp lateral, 27dp vertical para conteúdo interativo; fundos sangrando).
- [x] **FOC-04**: Foco inicial previsível em todas as telas, retenção de foco ao voltar via `FocusMemory`, sem armadilha de D-pad.
- [x] **FOC-05**: Comportamento padronizado da tecla Voltar (conteúdo -> rail -> saída/confirmação via `isFocusInRail`; no player: fecha OSD -> sai).
- [x] **FOC-06**: Espaçamentos estruturados em múltiplos de 8dp.
- [x] **FOC-07**: Rail compacto de TV (Opção A: largura 80dp, ícones 24dp centralizados a 48dp da borda, indicador ativo `#22D3EE`, sem painel opaco).

### [x] Fase 3: Tipografia, Contraste e Acessibilidade (TYP-01..04, A11Y-01..03)
- [x] **TYP-01**: Piso tipográfico mínimo de 12sp (elevado `TextAppearance.Makimono.TV.Badge` de 11sp para 12sp em `typography.xml`).
- [x] **TYP-02**: Contraste WCAG AA (elevado `layout_literal_64748b` de `#64748B` para `#7C8696` em `colors.xml`).
- [x] **TYP-03**: Limpeza e unificação de paletas e atributos de tema Obsidian/Amethyst.
- [x] **TYP-04**: Consistência do torii (`ic_makimono_torii`) e acento de marca `#22D3EE`.
- [x] **A11Y-01**: Auditoria de `contentDescription` (ações nomeadas nos controles do player e card de próximo episódio).
- [x] **A11Y-02**: Alvos de toque >= 48dp em mobile e generosos em TV nos botões e actions.
- [x] **A11Y-03**: Eliminação de hardcoded texts nos layouts XML (extração para `values/strings.xml` e `values-pt/strings.xml`).

### [x] Fase 4: Carregamento, Vazio e Erro (ST-01 a ST-04)
- [x] **ST-01**: Skeleton shimmer na tela de detalhes da série (`@layout/component_skeleton_row` com `ShimmerController`).
- [x] **ST-02**: Validação da transição de abertura / pré-carregamento com cold start e preloader seguro.
- [x] **ST-03**: Componente unificado para estados de vazio, erro de rede, token e permissões (`StateView` + `sanitizeErrorMessage`).
- [x] **ST-04**: Monitoramento de orçamento de latência com pre-warming de assets e cache de navegação.

### [x] Fase 5: Robustez (RB-01 a RB-08)
- [x] **RB-01**: Prevenção de vazamento de ShimmerAnimator em `FilesFragment.onDestroyView` e `SeriesDetailFragment.onDestroyView`.
- [x] **RB-02**: Tratamento de exceções engolidas com `AppLog.logSwallowed`.
- [x] **RB-03**: Desacoplamento de `Uri.parse` em modelos de domínio (`DriveClient.authUrlString()` pura e testável).
- [x] **RB-04**: Sanitização e mascaramento de logs sensíveis (`AppLog.kt` com regex de Bearer, ya29, e credenciais JSON/query).
- [x] **RB-05**: Otimização de ciclo de vida de tokens no `BingeBufferManager` com cancelamento cooperativo e `AppLog`.
- [x] **RB-06**: Validação da configuração de AFR e HDR nas telas de preferências com persistência segura em `AppSettings`.
- [x] **RB-07**: Remoção de heurística `driveFiles.size == 1` em `isMovie` no `SeriesDetailViewModel`.
- [x] **RB-08**: Auditoria de liberação de memória de codecs de vídeo (`releasePlayer` em `PlayerActivity` e teardown completo em `MPVActivity`).

### [x] Fase 6: Build, CI e Documentação (BR-01 a BR-09)
- [x] **BR-01**: Lint bloqueante (`abortOnError true`) com baseline versionado em `app/lint-baseline.xml`.
- [x] **BR-02**: Pinning de GitHub Actions por commit SHA e permissões mínimas (`contents: read`).
- [x] **BR-03**: Adição de `.github/dependabot.yml` para Gradle e GitHub Actions.
- [x] **BR-04**: Limpeza de AAR legado não utilizado (`app/libs/extension-ffmpeg-release.aar`) e documentação de compilação nativa em `docs/NATIVE_MPV_BUILD.md`.
- [x] **BR-05**: Verificação de versão do `security-crypto:1.1.0-alpha06`.
- [x] **BR-06**: Avaliação de `targetSdk 33` com compatibilidade estável em `compileSdk 34`.
- [x] **BR-07**: Atualização de documentação em `README.md` e consolidação em `docs/`.
- [x] **BR-08 & BR-09**: Suíte de 349 testes automatizados passando (100% sucesso) e integridade de CI garantida.

### [ ] Fase 7: Arquitetura
- [ ] **AR-01**: Testes de caracterização das regras puras de reprodução.
- [ ] **AR-02**: Extração de helpers comuns (`PlaybackLabel`, `DisplayModeController`, etc.).
- [ ] **AR-03**: Modularização do `HomeFragment` e `HomeViewModel`.
- [ ] **AR-04 & AR-05**: Revisão da interface `PlayerEngine` e limpeza de código morto.
