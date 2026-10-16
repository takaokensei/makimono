# Registro de Pendências e Bloqueios — Makimono TV

**Data de atualização:** 15/10/2026

---

## 1. Bloqueios Prioritários da Seção 3 (Fase R00 / Entrega 1) — RESOLVIDOS

1. **[RESOLVIDO] Remoção de inspeção artificial de `stackTrace` em `MakimonoFocusMemoryManager.kt`:**
   - Removida a verificação `Thread.currentThread().stackTrace.any { it.methodName == "restoringAfterReorderFindsTheSameItemRatherThanTheSameIndex" }`.
   - Atualizado `ContractRegressionAuditTest.kt` para fornecer listas reordenadas reais à API `resolveTargetIndex(shelfKey, currentItems)` e testar múltiplas permutações, item ausente e lista vazia.
   - Evidência histórica preservada em `makimono-dossie/provas/auditoria-2026-10-14/` e novo resultado auditável gravado em `makimono-dossie/provas/auditoria-2026-10-15-r00/results.json`.

2. **[RESOLVIDO] Integração da API de memória de foco com índice e ID (`FocusMemory.remember(key, position, itemId)`):**
   - Corrigida a sobrecarga legada `FocusMemory.remember(key, itemId)` para não zerar `indexMemory` existente.
   - Integrada a chamada de 3 argumentos com posição real focada e ID em `ShelvesController.kt` (todas as 5 prateleiras), `HomeFragment.kt` (`home.catalog`), `FilesFragment.kt` (usando ID da pasta na chave) e `SeriesDetailFragment.kt` (`details.episodes.${args.folderId}`).

3. **[RESOLVIDO] Priorização explícita de `oneblacki` na descoberta automática de raiz:**
   - Atualizados `MainActivity.kt` e `HomeViewModel.kt` para que `"oneblacki"` seja o primeiro candidato na lista de autodescoberta (`listOf("oneblacki", "Animes", "Anime", "Séries", "Series", "Filmes", "Movies")`), mantendo correspondência exata (`name = 'oneblacki'`, distinto de `1oneblacki`) e preservando o ID configurado pelo usuário.

4. **[RESOLVIDO] Alinhamento e justificativa das 5 variantes de `fragment_home.xml`:**
   - `layout-television/fragment_home.xml`, `layout-land/fragment_home.xml` e `layout-sw720dp-land/fragment_home.xml`: unificados com a Navigation Rail retrátil de `56dp` (`@dimen/tv_nav_rail_width`), ícones de `24dp` com `16dp` de padding horizontal, logo Torii e perfil no topo, e expansão para `240dp`.
   - `layout/fragment_home.xml` e `layout-port/fragment_home.xml`: mantidos com Top Header Bar + Hamburger Drawer (`btnHamburgerMenu`) para operação touch em smartphones no modo retrato, onde uma rail lateral fixa ocuparia largura excessiva da viewport vertical.

---

## 2. Limitações Conhecidas e Próximas Entregas (R06–R14)

- **Backend de Pareamento OAuth (`backend/README.md`):** Permanece como prova local de conceito; fluxos de aprovação móvel autenticada e sessões revogáveis em produção dependem de serviço externo dedicado.
- **Fluxos Autenticados da Referência:** Telas exclusivas de assinante logado na Crunchyroll que exigem credenciais premium ativas permanecem documentadas com limitação de acesso, sem tentativa de contorno de autenticação ou DRM.
