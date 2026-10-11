# Relatório de Conclusão da Fase 3 — Guardas de CI & Evidências Empíricas

- **Branch:** `v5/fase-3-guardas-evidencia`
- **Base:** `v5/fase-2-lint-categorias` (`4e7d988`)
- **Data:** 2026-10-10

---

## 1. Resumo Executivo das Tarefas

| Tarefa | Descrição | Arquivos Modificados | Commit | Status |
|---|---|---|---|---|
| **T-301** | Script de verificação estrita `check_no_direct_log.py` | `tools/checks/check_no_direct_log.py` | `96f2dbc` | Concluído |
| **T-302** | Integração dos verificadores no fluxo do CI GitHub Actions | `.github/workflows/ci.yml` | `54fa0b8` | Concluído |
| **T-303** | Medição empírica de cobertura JaCoCo por camada | Relatório JaCoCo gerado via `jacocoTestReport` | Atual | Concluído |
| **T-304** | Validação instrumentada do OSD no emulador de TV 1080p | Execução no `emulator-5554` (Android 16 TV) | Atual | Concluído |
| **T-305** | Relatório de evidência e validação da Fase 3 | `docs/qa/v5/fase-3-relatorio.md` | Atual | Concluído |

---

## 2. Evidência Canônica: Guardas de CI

### A. Verificação de Logging Direto (`check_no_direct_log.py`)
```
$ python tools/checks/check_no_direct_log.py
[OK] Zero direct android.util.Log calls outside AppLog.kt.
```

### B. Integração no `.github/workflows/ci.yml`
```yaml
      - name: Run static quality guards
        run: |
          python3 tools/checks/check_strings.py
          python3 tools/checks/check_no_direct_log.py
          python3 tools/checks/baseline_diff.py app/lint-baseline.xml
```

---

## 3. Evidência Canônica: Cobertura JaCoCo por Camada (T-303)

Comando executado:
```
gradlew.bat jacocoTestReport
```
Métricas consolidadas do XML `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`:
- **Cobertura Global de Instruções:** 18.51% (32.104 / 173.396)
- **Cobertura Global de Branches:** 12.19% (2.122 / 17.410)
- **Cobertura Global de Linhas:** 18.25% (4.562 / 24.994)

### Cobertura de Instruções por Pacote / Camada
| Pacote / Camada | Instruções Cobertas | Total de Instruções | % Cobertura |
|---|---|---|---|
| `domain/playback` | 128 | 131 | **97.71%** |
| `playback/session` | 1.934 | 2.361 | **81.91%** |
| `playback/engine` | 479 | 591 | **81.05%** |
| `utils/state` | 245 | 298 | **82.21%** |
| `domain/library` | 4.158 | 5.526 | **75.24%** |
| `ui/player/predictive` | 370 | 544 | **68.01%** |
| `playback/display` | 27 | 40 | **67.50%** |
| `data/model` | 2.248 | 3.806 | **59.06%** |
| `ui/common` | 322 | 564 | **57.09%** |
| `domain/common` | 163 | 345 | **47.25%** |
| `data/repository` | 2.567 | 6.741 | **38.08%** |
| `ui/series` | 3.022 | 10.232 | **29.53%** |
| `utils` | 6.189 | 21.961 | **28.18%** |
| `playback/osd` | 270 | 1.084 | **24.91%** |
| `ui/files` | 1.766 | 7.383 | **23.92%** |
| `ui/home` | 2.792 | 12.373 | **22.57%** |
| `worker` | 102 | 495 | **20.61%** |
| `ui/main` | 671 | 4.295 | **15.62%** |
| `ui/settings` | 1.244 | 8.033 | **15.49%** |
| `data/remote` | 741 | 7.199 | **10.29%** |
| `ui/player` | 1.057 | 25.259 | **4.18%** |
| `data/local` | 381 | 10.022 | **3.80%** |
| `ui/player2` | 479 | 13.972 | **3.43%** |

---

## 4. Evidência Canônica: Instrumentação no Emulador Android TV (T-304)

Dispositivo validado: **MakimonoAndroidTV1080p (`emulator-5554`)** — Android 16 (API 36).
*Nenhum teste foi executado no dispositivo móvel, em estrito respeito à instrução do usuário.*

### A. PlayerOsdLayoutTest
Resultado do XML `TEST-MakimonoAndroidTV1080p(AVD) - 16-_app-.xml`:
- Testes: 1
- Falhas: 0
- Erros: 0
- Tempo: 3.133s

### B. TvFocusNavigationUiAutomatorTest
Resultado do XML `TEST-MakimonoAndroidTV1080p(AVD) - 16-_app-.xml`:
- Testes: 4
- Falhas: 0
- Erros: 0
- Tempo: 30.122s
- Cenários aprovados:
  1. `playerScreenShowsOsdWithFocusAndHidesOnBackKey`
  2. `seriesDetailScreenEstablishesInitialFocusOnPrimaryAction`
  3. `settingsScreenAllowsVerticalCategoryNavigation`
  4. `homeScreenHasInitialFocusAndRespondsToDpad`

### C. Logcat Empírico do OSD na TV
```
10-11 00:02:35.252 D OsdController: [OSD_CONTROLLER] show() called -> action=StartShow, state=SHOWING
10-11 00:02:36.945 D OsdController: [OSD_CONTROLLER] hide() called -> action=StartHide, state=HIDING
10-11 00:02:37.570 I TestRunner: finished: playerScreenShowsOsdWithFocusAndHidesOnBackKey
```
