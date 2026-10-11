# Baseline de Partida — Plano de Implementação v5

- **Commit Base:** `e4fa901`
- **Data / Sessão:** 2026-10-10
- **Branch:** `v5/fase-0-baseline`

---

## 1. Tabela Comparativa de Métricas

| Item | Valor no Plano v5 | Valor Medido Localmente | Comando Utilizado | Status |
|---|---|---|---|---|
| Testes unitários (`@Test`) | 498 | 498 | `python tools/checks/count_tests.py` | Idêntico |
| Execuções JUnit (XML) | 505 | 505 | `python tools/checks/count_tests.py` | Idêntico |
| Testes instrumentados (`@Test`) | 11 (4 UiAutomator) | 11 (4 UiAutomator) | `git grep -c "@Test" -- app/src/androidTest` | Idêntico |
| Itens na baseline do lint | 939 | 939 | `python tools/checks/count_lint_categories.py` | Idêntico |
| `HardcodedText` | 286 | 286 | `python tools/checks/count_lint_categories.py` | Idêntico |
| `UnsafeOptInUsageError` | 180 | 180 | `python tools/checks/count_lint_categories.py` | Idêntico |
| `UnusedResources` | 178 | 178 | `python tools/checks/count_lint_categories.py` | Idêntico |
| `SetTextI18n` | 102 | 102 | `python tools/checks/count_lint_categories.py` | Idêntico |
| `NewApi` | 20 | 20 | `python tools/checks/count_lint_categories.py` | Idêntico |
| Strings `values` e `values-pt` (total) | 390 e 390 | 390 e 390 | Script ElementTree inline | Idêntico |
| Strings traduzíveis (`translatable!=false`) | 387 | 387 | `python tools/checks/check_strings.py` | Idêntico |
| `Log.*` fora de `AppLog.kt` | 0 | 0 | `git grep -n "Log\.[vdiwe]" -- "app/src/main/java/**/*.kt"` | Idêntico |
| `catch (_: Exception) {}` vazios | 3 | 3 | `git grep -n "catch.*Exception.*{\s*}" -- "app/src/main/java/**/*.kt"` | Idêntico |
| Durações literais de animação | 0 | 0 | `git grep -n "\.duration\s*=\s*[0-9]" -- "app/src/main/java/**/*.kt"` | Idêntico |
| Compose (`@Composable`) | 0 arquivos | 0 arquivos | `git grep -l "@Composable" -- "app/src/main/java"` | Idêntico |
| `PlayerActivity.kt` | 3285 linhas | 2960 linhas | `git show HEAD:app/src/main/java/zechs/drive/stream/ui/player/PlayerActivity.kt \| Measure-Object -Line` | Divergência registrada* |
| `MPVActivity.kt` | 2791 linhas | 2497 linhas | `git show HEAD:app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt \| Measure-Object -Line` | Divergência registrada* |
| Módulo `benchmark/` | 4 classes | 4 classes (6 arquivos) | `git ls-tree -r --name-only HEAD benchmark/` | Idêntico |

*\*Nota sobre as linhas de `PlayerActivity.kt` e `MPVActivity.kt`: no commit `e4fa901`, extrações e refatorações prévias da Fase 7 (como `OsdStateMachine`, `PillVisibilityPolicy`, `MatroskaChapterParser`) reduziram o tamanho dos arquivos em relação ao rascunho inicial do plano.*

---

## 2. Saídas Brutas dos Comandos Canônicos

### A. Testes Unitários
```
$ python tools/checks/count_tests.py
Anotações @Test no código-fonte: 498
Execuções de teste (XML JUnit):   505
Falhas: 0, Erros: 0, Ignorados: 0

Explicação da diferença:
O framework de testes de arquitetura ArchUnit (DomainRulesArchitectureTest) executa
múltiplas asserções de regras dinâmicas sob cada anotação, resultando em mais
execuções reportadas no JUnit (505) do que anotações estáticas (498).
```

### B. Baseline do Lint
```
$ python tools/checks/count_lint_categories.py
Total issues in baseline: 939

| Categoria (Issue ID) | Ocorrências |
|---|---|
| `HardcodedText` | 286 |
| `UnsafeOptInUsageError` | 180 |
| `UnusedResources` | 178 |
| `SetTextI18n` | 102 |
| `NewApi` | 20 |
| `InconsistentLayout` | 18 |
| `UnusedAttribute` | 15 |
| `Overdraw` | 15 |
| `Autofill` | 12 |
| `ContentDescription` | 12 |
| `CutPasteId` | 10 |
| `VectorPath` | 10 |
| `DisableBaselineAlignment` | 9 |
| `UseCompoundDrawables` | 7 |
| `UselessParent` | 7 |
| `TooManyViews` | 6 |
| `IconLocation` | 6 |
| `NestedWeights` | 5 |
| `UseAppTint` | 4 |
| `TooDeepLayout` | 4 |
| `RtlSymmetry` | 4 |
| `EllipsizeMaxLines` | 3 |
| `Untranslatable` | 3 |
| `NotifyDataSetChanged` | 3 |
| `KeyboardInaccessibleWidget` | 3 |
| `CheckResult` | 2 |
| `InlinedApi` | 2 |
| `VectorRaster` | 2 |
| `MissingQuantity` | 2 |
| `SmallSp` | 2 |
| `ClickableViewAccessibility` | 2 |
| `InflateParams` | 1 |
| `PluralsCandidate` | 1 |
| `MergeRootFrame` | 1 |
| `IconXmlAndPng` | 1 |
| `IconDuplicates` | 1 |
| **Total** | **939** |
```

### C. Paridade de Strings
```
$ python tools/checks/check_strings.py
[OK] String parity verified: 387 keys match between values and values-pt.

Contagem bruta ElementTree:
values/strings.xml: total = 390 (387 translatable, 3 untranslatable)
values-pt/strings.xml: total = 390
```

### D. Testes Instrumentados
```
$ git grep -c "@Test" -- app/src/androidTest
app/src/androidTest/java/zechs/drive/stream/ExampleInstrumentedTest.kt:1
app/src/androidTest/java/zechs/drive/stream/FavoriteDaoBatchTest.kt:1
app/src/androidTest/java/zechs/drive/stream/HomeHeroLayoutTest.kt:1
app/src/androidTest/java/zechs/drive/stream/PlayerOsdLayoutTest.kt:1
app/src/androidTest/java/zechs/drive/stream/ProfileMigrationTest.kt:2
app/src/androidTest/java/zechs/drive/stream/SeriesDetailLayoutTest.kt:1
app/src/androidTest/java/zechs/drive/stream/TvFocusNavigationUiAutomatorTest.kt:4
Total de anotações @Test: 11 (4 na classe UiAutomator)
```

### E. Verificação de Código Limpo
- `Log.*` direto fora de `AppLog.kt`: 0
- Durações literais de animação (`.duration = <número>`): 0
- Arquivos com `@Composable`: 0
- Blocos `catch (_: Exception) {}` vazios:
  1. `app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt:300`
  2. `app/src/main/java/zechs/drive/stream/ui/settings/MalAuthDialog.kt:421`
  3. `app/src/main/java/zechs/drive/stream/ui/settings/MalAuthDialog.kt:710`

### F. Compilação e Testes Gradle
```
$ gradlew.bat testDebugUnitTest lintDebug assembleDebug
BUILD SUCCESSFUL in 2m 4s
136 actionable tasks: 19 executed, 117 up-to-date
Unit tests result: 505 tests, 0 failures, 0 errors, 0 skipped.
```
