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

### [ ] Fase 2: Foco, Zona Segura e Layout (FOC-01 a FOC-07)
- [ ] **FOC-01**: Token unificado de foco (anel 3dp `#22D3EE`, escala 1.06 em cards, respeitando `ANIMATOR_DURATION_SCALE == 0`).
- [ ] **FOC-02**: Correção de contorno cortado no Hero (`paddingBottom >= 8dp`, `clipToPadding="false"`).
- [ ] **FOC-03**: Zona segura de overscan (48dp lateral, 30dp vertical para conteúdo interativo; fundos sangrando).
- [ ] **FOC-04**: Foco inicial previsível em todas as telas, retenção de foco ao voltar, sem armadilha de D-pad.
- [ ] **FOC-05**: Comportamento padronizado da tecla Voltar (conteúdo -> rail -> saída/confirmação; no player: fecha OSD -> sai).
- [ ] **FOC-06**: Espaçamentos estruturados em múltiplos de 8dp.
- [ ] **FOC-07**: Rail compacto de TV (Opção A: largura 80dp, ícones 24dp centralizados a 48dp da borda, indicador ativo `#22D3EE` 16x2dp, sem painel opaco).

### [ ] Fase 3: Tipografia, Contraste e Acessibilidade (TYP-01..04, A11Y-01..03)
- [ ] **TYP-01**: Piso tipográfico mínimo de 12sp (Body 16sp, Callout 14sp, Badges 12sp).
- [ ] **TYP-02**: Contraste WCAG AA (trocar `#64748B` onde for texto por `obsidian_text_tertiary` `#7C8696` ou superior).
- [ ] **TYP-03**: Limpeza e unificação de paletas e atributos de tema.
- [ ] **TYP-04**: Consistência do torii e acento de marca `#22D3EE`.
- [ ] **A11Y-01**: Auditoria de `contentDescription` (manter `@null` apenas em puramente decorativos; nomear ações interativas).
- [ ] **A11Y-02**: Alvos de toque >= 48dp em mobile e generosos em TV.
- [ ] **A11Y-03**: Eliminação de hardcoded texts nos layouts XML.

### [ ] Fase 4: Carregamento, Vazio e Erro (ST-01 a ST-04)
- [ ] **ST-01**: Skeleton shimmer na tela de detalhes da série em vez de spinner centralizado.
- [ ] **ST-02**: Validação da transição de abertura / pré-carregamento com cold start.
- [ ] **ST-03**: Componente unificado para estados de vazio, erro de rede, token e permissões.
- [ ] **ST-04**: Monitoramento de orçamento de latência.

### [ ] Fase 5: Robustez (RB-01 a RB-08)
- [ ] **RB-01**: Prevenção de vazamento de ShimmerAnimator em `FilesFragment.onDestroyView`.
- [ ] **RB-02**: Tratamento de exceções engolidas com `logSwallowed`.
- [ ] **RB-03**: Desacoplamento de `Uri.parse` em modelos de domínio (uso de `String` pura).
- [ ] **RB-04**: Sanitização e mascaramento de logs sensíveis (`AppLog`).
- [ ] **RB-05**: Otimização de ciclo de vida de tokens no `BingeBufferManager`.
- [ ] **RB-06**: Validação da configuração de AFR e HDR nas telas de preferências.
- [ ] **RB-07**: Remoção de heurística `playlist.size <= 1` em `isMovie`.
- [ ] **RB-08**: Auditoria de liberação de memória de codecs de vídeo.

### [ ] Fase 6: Build, CI e Documentação (BR-01 a BR-09)
- [ ] **BR-01**: Lint bloqueante (`abortOnError true`) com baseline versionado.
- [ ] **BR-02**: Pinning de GitHub Actions por commit SHA e permissões mínimas.
- [ ] **BR-03**: Adição de `.github/dependabot.yml`.
- [ ] **BR-04**: Limpeza de AAR não utilizado e documentação de compilação nativa do MPV.
- [ ] **BR-05**: Verificação de versão do `security-crypto`.
- [ ] **BR-06**: Avaliação de `targetSdk`.
- [ ] **BR-07**: Atualização de documentação (`README.md`, migração de `FRONTEND_LOG.md` para `docs/`).
- [ ] **BR-08 & BR-09**: Medição de cobertura e testes instrumentados de foco.

### [ ] Fase 7: Arquitetura
- [ ] **AR-01**: Testes de caracterização das regras puras de reprodução.
- [ ] **AR-02**: Extração de helpers comuns (`PlaybackLabel`, `DisplayModeController`, etc.).
- [ ] **AR-03**: Modularização do `HomeFragment` e `HomeViewModel`.
- [ ] **AR-04 & AR-05**: Revisão da interface `PlayerEngine` e limpeza de código morto.
