# Relatório de Conclusão da Fase 1 — Precisão & Guarda

- **Branch:** `v5/fase-1-precisao-guarda`
- **Base:** `main` @ `e4fa901`
- **Data:** 2026-10-10

---

## 1. Resumo Executivo das Tarefas

| Tarefa | Descrição | Arquivos Modificados | Commit | Status |
|---|---|---|---|---|
| **T-101** | Correção da contagem de strings e testes no README | `README.md` | `093fb2a` | Concluído |
| **T-102** | Criação do script de verificação `baseline_diff.py` | `tools/checks/baseline_diff.py` | `731debf` | Concluído |
| **T-110** | Eliminação de 17 issues de `NewApi` em `AutoFrameRatePolicy` | `AutoFrameRatePolicy.kt`, `app/lint-baseline.xml` | `32499f7`, `8377325` | Concluído |
| **T-111** | Eliminação de 1 issue de `NewApi` em `FeaturedHeroBinder` | `FeaturedHeroBinder.kt`, `app/lint-baseline.xml` | `c73e1a1` | Concluído |
| **T-112** | Eliminação de 2 issues de `NewApi` em `TvFocusRing` | `TvFocusRing.kt`, `app/lint-baseline.xml` | `7e747c1` | Concluído |
| **T-113** | Relatório de evidência e validação da Fase 1 | `docs/qa/v5/fase-1-relatorio.md` | Atual | Concluído |

---

## 2. Evidência Canônica: Eliminação Completa de `NewApi`

### A. Contagem de Issues por Categoria no Lint Baseline
Comando executado:
```
python tools/checks/count_lint_categories.py
```
Saída bruta:
```
Total issues in baseline: 919

| Categoria (Issue ID) | Ocorrências |
|---|---|
| `HardcodedText` | 286 |
| `UnsafeOptInUsageError` | 180 |
| `UnusedResources` | 178 |
| `SetTextI18n` | 102 |
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
| **Total** | **919** |
```

A categoria `NewApi` desapareceu inteiramente da tabela (`NewApi = 0`).

### B. Verificação Estrita de Elementos `NewApi` no XML
Comando executado:
```
python -c "import xml.etree.ElementTree as ET; r = ET.parse('app/lint-baseline.xml').getroot(); issues = [el for el in r.findall('.//issue') if el.get('id') == 'NewApi']; print('NewApi issues count:', len(issues))"
```
Saída bruta:
```
NewApi issues count: 0
```

---

## 3. Evidência Canônica: Regra P-BASE e `baseline_diff.py`
Comando executado:
```
python tools/checks/baseline_diff.py app/lint-baseline.xml
```
Saída bruta:
```
Baseline HEAD:     876 issues
Baseline atual:    876 issues
Removidas:         0
Novas:             0

OK: Baseline so diminuiu ou manteve. Nenhuma issue nova.
```

---

## 4. Evidência Canônica: Testes Unitários e Compilação
Comando executado:
```
python tools/checks/count_tests.py
```
Saída bruta:
```
Anotações @Test no código-fonte: 498
Execuções de teste (XML JUnit):   505
Falhas: 0, Erros: 0, Ignorados: 0
```

Compilação completa via Gradle:
```
cmd /c "T: & set TEMP=C:\temp& set TMP=C:\temp& set GRADLE_USER_HOME=C:\temp\.gradle&& gradlew.bat testDebugUnitTest lintDebug assembleDebug"
BUILD SUCCESSFUL in 1m 18s
136 actionable tasks: 11 executed, 125 up-to-date
```
