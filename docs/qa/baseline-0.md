# Baseline 0 — Diagnóstico Inicial de Qualidade

Data: 2026-10-09  
Base: `main` @ `b0c4b20`  
Ambiente: Windows 11 / PowerShell / Git Bash / JDK 17 / Android SDK 34  
Branch de execução: `fase-0-higiene-baseline`

---

## 1. Métricas Medidas (Apêndice A)

Todas as medições abaixo são fatos reproduzíveis [F], obtidos executando os comandos do Apêndice A do Plano v2 na raiz do repositório.

### 1.1 Arquitetura e Tamanho de Arquivos

Comando:
```bash
find app/src/main -name "*.kt" -exec wc -l {} + | sort -rn | head -12
```

Saída:
```text
  34530 total
   3381 app/src/main/java/zechs/drive/stream/ui/player/PlayerActivity.kt
   2879 app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt
   1868 app/src/main/java/zechs/drive/stream/ui/home/HomeFragment.kt
   1193 app/src/main/java/zechs/drive/stream/ui/home/HomeViewModel.kt
   1101 app/src/main/java/zechs/drive/stream/ui/settings/SettingsFragment.kt
    884 app/src/main/java/zechs/drive/stream/ui/files/FilesFragment.kt
    840 app/src/main/java/zechs/drive/stream/utils/SeasonEpisodeGrouper.kt
    753 app/src/main/java/zechs/drive/stream/ui/main/MainActivity.kt
    714 app/src/main/java/zechs/drive/stream/ui/settings/MalAuthDialog.kt
    707 app/src/main/java/zechs/drive/stream/utils/TvLoginServer.kt
    699 app/src/main/java/zechs/drive/stream/ui/series/SeriesDetailFragment.kt
```

#### Camada de Domínio e UseCases
Comando:
```bash
find app/src/main -iname "*UseCase*" | wc -l
grep -r "UseCase" app/src/main/java 2>/dev/null | wc -l
```
Saída:
```text
0
0
```

---

### 1.2 Qualidade e Robustez

#### Lint Baseline
Comando:
```bash
grep -c '<issue' app/lint-baseline.xml
grep -o 'id="[^"]*"' app/lint-baseline.xml | sort | uniq -c | sort -rn | head -15
```
Saída:
```text
1038
    286 id="HardcodedText"
    182 id="UnsafeOptInUsageError"
    179 id="UnusedResources"
    103 id="SetTextI18n"
     88 id="MissingTranslation"
     22 id="InconsistentLayout"
     21 id="NewApi"
     15 id="UnusedAttribute"
     15 id="Overdraw"
     12 id="ContentDescription"
     12 id="Autofill"
     10 id="VectorPath"
     10 id="CutPasteId"
      9 id="DisableBaselineAlignment"
      7 id="UselessParent"
```

#### Tratamento de Exceções (`catch (_: Exception) {}`)
Comando:
```bash
grep -rn "catch (_: Exception) {}" app/src/main --include=*.kt
```
Saída (3 ocorrências):
```text
app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt:980:        } catch (_: Exception) {}
app/src/main/java/zechs/drive/stream/ui/settings/MalAuthDialog.kt:421:            } catch (_: Exception) {}
app/src/main/java/zechs/drive/stream/ui/settings/MalAuthDialog.kt:710:        } catch (_: Exception) {}
```

#### Chamadas Diretas a `android.util.Log`
Comando:
```bash
grep -rhoE "\bLog\.(d|i|w|e|v)\(" app/src/main/java --include=*.kt | wc -l
grep -rlE "\bLog\.(d|i|w|e|v)\(" app/src/main/java --include=*.kt | wc -l
grep -rl "AppLog" app/src/main/java --include=*.kt | wc -l
```
Saída:
```text
329 chamadas diretas
40 arquivos com chamadas diretas
8 arquivos utilizando AppLog
```

#### Testes
Comando:
```bash
grep -rh "@Test" app/src/test | wc -l
grep -rh "@Test" app/src/androidTest 2>/dev/null | wc -l
```
Saída:
```text
358 testes unitários (@Test em app/src/test)
7 testes instrumentados (@Test em app/src/androidTest)
```

---

### 1.3 Candidatas a Código Morto

Comando:
```bash
for cls in ContinuousCornerOutline TvLoginServer ChapterMarkersPolicy GlassMenuFocusPolicy SynopsisDisclosurePolicy TapGestureState; do
  prod_kt=$(grep -rwl "$cls" app/src/main/java --include=*.kt 2>/dev/null | wc -l)
  prod_res=$(grep -rl "$cls" app/src/main/res app/src/main/AndroidManifest.xml 2>/dev/null | wc -l)
  test_refs=$(grep -rwl "$cls" app/src/test app/src/androidTest 2>/dev/null | wc -l)
  echo "$cls: prod_kt=$prod_kt prod_res=$prod_res test_refs=$test_refs"
done
```
Saída:
```text
ContinuousCornerOutline: prod_kt=1 prod_res=0 test_refs=0
TvLoginServer: prod_kt=1 prod_res=0 test_refs=1
ChapterMarkersPolicy: prod_kt=1 prod_res=0 test_refs=1
GlassMenuFocusPolicy: prod_kt=1 prod_res=0 test_refs=1
SynopsisDisclosurePolicy: prod_kt=1 prod_res=0 test_refs=1
TapGestureState: prod_kt=1 prod_res=0 test_refs=1
```
*Nota: `prod_kt=1` corresponde apenas à própria declaração da classe/arquivo.*

---

### 1.4 Repositório e Higiene

Comando:
```bash
git ls-files | wc -l
du -sh .git 2>/dev/null
du -sh docs/ui-audit 2>/dev/null
ls -d release_notes_*.md 2>/dev/null | wc -l
```
Saída:
```text
git ls-files count: 2227
git size: 567M
docs/ui-audit size: 715M
release_notes na raiz: 6 arquivos
```

---

### 1.5 Workflows do CI/Release sem SHA

Comando:
```bash
grep -rn "uses:" .github/workflows | grep -v "@[0-9a-f]\{40\}"
```
Saída:
```text
.github/workflows/ci.yml:39:        uses: trufflesecurity/trufflehog@main
.github/workflows/release.yml:18:        uses: actions/checkout@v4
.github/workflows/release.yml:21:        uses: actions/setup-java@v4
.github/workflows/release.yml:31:        uses: trufflesecurity/trufflehog@main
.github/workflows/release.yml:132:        uses: actions/upload-artifact@v4
.github/workflows/release.yml:140:        uses: actions/upload-artifact@v4
.github/workflows/release.yml:169:        uses: softprops/action-gh-release@v2
```
