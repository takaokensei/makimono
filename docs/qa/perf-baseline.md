# Linha de Base de Desempenho e Cobertura — Fase 1

Data: 2026-10-09  
Ambiente: Android 15 (Físico) / Windows 11 / Gradle 8.7 / AGP 8.2.2  
Branch: `fase-1-medicao-desempenho`

---

## 1. Módulo Macrobenchmark (`:benchmark`)

O módulo `:benchmark` foi introduzido como um módulo de teste Android (`com.android.test`) instrumentando diretamente o aplicativo (`:app`) com regras de Macrobenchmark e geração de Baseline Profile:

- **Namespace:** `zechs.drive.stream.benchmark`
- **Métricas avaliadas:** `StartupTimingMetric`, `FrameTimingMetric`
- **Modos de inicialização:** `StartupMode.COLD`, `StartupMode.WARM`
- **Compilação:** `CompilationMode.None()`, `CompilationMode.Partial(BaselineProfileMode.Require)`, `CompilationMode.DEFAULT`

### 1.1 Fluxos Implementados (Seção 8.1)

| Fluxo | Classe / Teste | O que mede |
|---|---|---|
| **Fluxo 1** | `StartupBenchmark.kt` | Inicialização a frio até o primeiro frame (TTID) e tela inicial completa (TTFD) |
| **Fluxo 2** | `NavigationBenchmark::seriesDetailOpenCached` | Abertura de detalhes de série com metadados em cache local |
| **Fluxo 3** | `NavigationBenchmark::seriesDetailOpenClean` | Abertura e transição de série sem cache prévio |
| **Fluxo 4** | `PlayerBenchmark::playerFirstFrameLaunch` | Tempo até o primeiro frame da superfície de vídeo após clicar em Reproduzir |
| **Fluxo 5** | `NavigationBenchmark::railNavigationTrip` | Navegação bidirecional no Rail lateral (Home <-> Arquivos/Catálogo) |
| **Fluxo 6** | `NavigationBenchmark::shelfScrollJank` | Rolagem e fling horizontal em estantes para detecção de jank de quadros |

### 1.2 Gerador de Baseline Profile (Seção 8.2)

- Classe: `BaselineProfileGenerator.kt`
- Regra: `BaselineProfileRule.collect(packageName = "zechs.drive.stream", maxIterations = 5)`
- Cobertura de perfis: Cobre inicialização (Fluxo 1), navegação de abas no Rail (Fluxo 5), visualização de mídia (Fluxo 2) e entrada no reprodutor (Fluxo 4).

---

## 2. Medições de Tempo de Inicialização (Startup Timing)

Dispositivo: **Motorola moto g34 5G**  
Sistema Operacional: **Android 15 (API 35)**  
Resolução Física: `720 × 1600` (280 dpi)  
Número de iterações: **5 execuções consecutivas** por modalidade.

### 2.1 Cold Startup (TTID — Inicialização a Frio)
Comando:
```bash
adb shell am start -W -S -n zechs.drive.stream.visualreview/zechs.drive.stream.ui.main.MainActivity
```

Resultados [F]:
- **Execução 1:** 1133 ms
- **Execução 2:** 1116 ms
- **Execução 3:** 1122 ms
- **Execução 4:** 1134 ms
- **Execução 5:** 1139 ms

**Estatísticas:**
- **Mínimo:** `1116 ms`
- **Máximo:** `1139 ms`
- **Mediana:** `1133.0 ms`
- **Média:** `1128.8 ms`

### 2.2 Warm Startup (Inicialização Quente)
Comando:
```bash
adb shell am start -W -n zechs.drive.stream.visualreview/zechs.drive.stream.ui.main.MainActivity
```

Resultados [F]:
- **Execução 1:** 98 ms
- **Execução 2:** 88 ms
- **Execução 3:** 60 ms
- **Execução 4:** 85 ms
- **Execução 5:** 69 ms

**Estatísticas:**
- **Mínimo:** `60 ms`
- **Máximo:** `98 ms`
- **Mediana:** `85.0 ms`
- **Média:** `80.0 ms`

---

## 2.3 Medições em Android TV AVD (MakimonoAndroidTV1080p)

Dispositivo: **MakimonoAndroidTV1080p (emulator-5554)**  
Sistema Operacional: **Android 16 (API 36)**  
Resolução Física: `1920 × 1080` (320 dpi - xhdpi)  
Número de iterações: **5 execuções consecutivas** por modalidade.

### Cold Startup (TTID — Inicialização a Frio)
Comando:
```bash
adb -s emulator-5554 shell am start -W -S -n zechs.drive.stream.visualreview/zechs.drive.stream.ui.main.MainActivity
```

Resultados [F]:
- **Execução 1:** 6578 ms (WaitTime: 6584 ms)
- **Execução 2:** 6486 ms (WaitTime: 6498 ms)
- **Execução 3:** 7323 ms (WaitTime: 7337 ms)
- **Execução 4:** 6764 ms (WaitTime: 6808 ms)
- **Execução 5:** 7094 ms (WaitTime: 7098 ms)

**Estatísticas:**
- **Mínimo:** `6486 ms`
- **Máximo:** `7323 ms`
- **Mediana:** `6764.0 ms`
- **Média:** `6849.0 ms`

### Warm Startup (Inicialização a Quente)
Comando:
```bash
adb -s emulator-5554 shell am start -W -n zechs.drive.stream.visualreview/zechs.drive.stream.ui.main.MainActivity
```

Resultados [F] (WaitTime após retorno à Home):
- **Execução 1:** 48 ms
- **Execução 2:** 33 ms
- **Execução 3:** 28 ms
- **Execução 4:** 28 ms
- **Execução 5:** 26 ms

**Estatísticas:**
- **Mínimo:** `26 ms`
- **Máximo:** `48 ms`
- **Mediana:** `28.0 ms`
- **Média:** `32.6 ms`

---

## 3. Cobertura de Código Medida (JaCoCo)

Configuração integrada ao build Gradle:
- Ferramenta: **JaCoCo 0.8.11**
- Tarefa de geração: `./gradlew :app:jacocoTestReport`
- Base de testes: 358 testes unitários executados do zero.

Relatório medido (`app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`) [F]:

| Métrica | Cobertos | Total | Percentual de Cobertura |
|---|---|---|---|
| **Instruções (Bytecode)** | 23.241 | 165.840 | **14,01%** |
| **Linhas de Código** | 3.243 | 23.985 | **13,52%** |
| **Ramos / Branches** | 1.660 | 17.224 | **9,64%** |
| **Métodos** | 775 | 4.420 | **17,53%** |
| **Classes** | 244 | 1.231 | **19,82%** |
| **Complexidade Ciclomática** | 1.258 | 13.176 | **9,55%** |

*Nota de meta de cobertura (Seção 9.2): Conforme estabelecido no plano, o número factual foi registrado primeiro. Metas por camada serão estabelecidas na Fase 2 com prioridade para a camada pura de domínio.*
