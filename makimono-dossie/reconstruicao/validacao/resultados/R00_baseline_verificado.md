# Resultado de Validação — Fase R00 (Baseline Confiável, Foco, Raiz `oneblacki` e Rail 56dp/240dp)

**Data:** 15/10/2026  
**Dispositivo Alvo:** `emulator-5554` (Android TV 1080p, `1920×1080`)  
**Pacote:** `zechs.drive.stream.visualreview`  
**APK SHA-256:** `77C8B4501B6CD6697EFD37354EA5FCAB7D68CBB0CBEF9AC44A6487200415B812`

---

## 1. Resolução dos 4 Bloqueios Prioritários (Seção 3 do Plano)

1. **Remoção de inspeção de `stackTrace` no protótipo `MakimonoFocusMemoryManager.kt`:**
   - Removida a condição artificial que inspecionava `Thread.currentThread().stackTrace` procurando o nome do teste `restoringAfterReorderFindsTheSameItemRatherThanTheSameIndex`.
   - `ContractRegressionAuditTest.kt` atualizado para passar listas reordenadas reais em múltiplas permutações (`["c", "a", "b"] -> 2`, `["b", "d", "a", "c"] -> 0`, `["x", "y", "z", "b", "w"] -> 3`), além de testar item ausente e lista vazia.
   - Evidência histórica preservada em `makimono-dossie/provas/auditoria-2026-10-14/` e novo resultado registrado em `makimono-dossie/provas/auditoria-2026-10-15-r00/results.json` (`originalTestsExitCode=0`, `contractTestsExitCode=0`).

2. **Integração real da API `FocusMemory.remember(key, position, itemId)` na UI:**
   - Corrigida a sobrecarga de 2 argumentos em `FocusMemory.kt` para não sobrescrever `indexMemory` existente com `0`.
   - Atualizados `ShelvesController.kt` (todas as 5 prateleiras da Home), `HomeFragment.kt` (`home.catalog`), `FilesFragment.kt` (`files.folder.$folderKey` por ID) e `SeriesDetailFragment.kt` (`details.episodes.${args.folderId}`).
   - Adicionados testes unitários em `FocusMemoryTest.kt` cobrindo múltiplas permutações de reordenação, fallback após remoção de item e preservação de índice.

3. **Priorização da pasta raiz `oneblacki`:**
   - Atualizados `MainActivity.kt` e `HomeViewModel.kt` para listar `"oneblacki"` como primeiro candidato na autodescoberta (`listOf("oneblacki", "Animes", "Anime", "Séries", "Series", "Filmes", "Movies")`), mantendo correspondência exata (`name = 'oneblacki'`, distinto de `1oneblacki`) e preservando o folder ID configurado.

4. **Unificação das variantes de `fragment_home.xml`:**
   - Sincronizado `layout-sw720dp-land/fragment_home.xml` com `layout-television/fragment_home.xml` e `layout-land/fragment_home.xml` (rail retrátil de `56dp`, ícones de `24dp` com `16dp` de padding horizontal, logo Torii e perfil no topo, e expansão para `240dp`).

---

## 2. Testes Automatizados e Capturas Vinculadas por SHA-256

- **Gradle Unit Tests (`:app:testDebugUnitTest`):** `BUILD SUCCESSFUL` (todos os testes unitários da aplicação aprovados, incluindo `FocusMemoryTest` e `RailControllerTest`).
- **Provas JVM (`run-proof-audit-r00.ps1`):** `15/15` testes originais + `5/5` testes de contrato aprovados sem inspeção de `stackTrace`.
- **Capturas Runtime (`1920×1080` PNG válidos em `emulator-5554`):**
  - `r00_profile_selection.png`: `BE5E19CFCF98C9029B5151A4631B65A7577093B879D342B7469FB5086207554A`
  - `r00_verified_home_56dp.png`: `F08CBF7B7F147FA1183FFF76C99CDEDF55EAED030C8342373BF6ADD646B697E6`
  - `r00_verified_rail_240dp.png`: `A10A20395A5BB8CB49D11009211FB633E187E15E6280A1B55184E48BFE216C3D`
