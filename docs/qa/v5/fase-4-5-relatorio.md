# Relatório de Execução e Entrega — Fases 4 e 5 (V5)

**Data:** 10/10/2026  
**Commit Base:** `ec89903`  
**Commits Entregues:**
- `054b328` — `feat(player): integrate EngineSelector into PlayerLauncher with auto ASS routing`
- `b27604a` — `feat(player): add quick audio sync dialog and delay persistence to PlayerActivity`
- Tag Anotada: `v1.6.5.2`

---

## 1. Entregas Técnicas Implementadas

### 1.1 Roteamento Inteligente de Player (`PlayerLauncher` + `EngineSelector`)
- **Integração:** `PlayerLauncher.resolvePlayerType` agora avalia dinamicamente características de mídia (`MediaInfo`) e capacidades de dispositivo (`DeviceCaps`) via `EngineSelector.selectEngine`.
- **Regra de Legendas Stylized:** Ao detectar legendas `.ass` ou `.ssa` associadas ao vídeo, o launcher roteia automaticamente para `MPVActivity` (libmpv com suporte completo a libass), prevenindo degradação visual do ExoPlayer.
- **Suporte a Hardware ASS:** Caso o dispositivo declare suporte a aceleração de hardware para ASS, o motor Media3 primário é preservado.
- **Sobrescrita do Usuário:** Escolhas explícitas do usuário (`VideoPlayer.MPV`) e flag `autoRouting = false` são rigorosamente honradas.
- **Suíte de Testes:** Criada classe `PlayerLauncherTest.kt` com 7 novos testes unitários cobrindo todos os cenários de roteamento.

### 1.2 Paridade de Sincronia de Áudio no OSD (`PlayerActivity` / ExoPlayer)
- **Diálogo OSD:** Adicionado botão de atalho no rodapé do seletor de áudio (`.setFooterSecondary("Sincronia")`) abrindo diálogo com offsets rápidos de ±100ms a ±1000ms.
- **Persistência em Banco:** Delays de áudio e legenda agora são salvos e restaurados juntos via `PlaybackViewModel.saveDelays(fileId, audioDelayMs, subtitleDelayMs)` e `getDelays(fileId)`.
- **Feedback Visual:** Toast/Snackbar exibido com confirmação do offset aplicado no player.

---

## 2. Comandos e Evidências Reais de Validação

### 2.1 Testes Unitários
Comando executado:
```powershell
python tools/checks/count_tests.py
```
Saída real:
```text
Anotações @Test no código-fonte: 505
Execuções de teste (XML JUnit):   512
Falhas: 0, Erros: 0, Ignorados: 0
```

### 2.2 Verificação de Regras Estáticas de CI
Comando executado:
```powershell
python tools/checks/check_strings.py; python tools/checks/check_no_direct_log.py; python tools/checks/baseline_diff.py app/lint-baseline.xml
```
Saída real:
```text
[OK] String parity verified: 387 keys match between values and values-pt.
[OK] Zero direct android.util.Log calls outside AppLog.kt.
Baseline HEAD:     722 issues
Baseline atual:    722 issues
Removidas:         0
Novas:             0
OK: Baseline so diminuiu ou manteve. Nenhuma issue nova.
```

### 2.3 Compilação de Artefato
Comando executado:
```powershell
gradlew.bat assembleDebug
```
Saída real:
```text
BUILD SUCCESSFUL in 22s
108 actionable tasks: 6 executed, 102 up-to-date
```

### 2.4 Testes Instrumentados no Emulador Android TV (Veto Rigoroso Respeitado)
- **Dispositivo Alvo:** `MakimonoAndroidTV1080p(AVD) - 16` (`emulator-5554`)
- **Moto G34 (`0086442783`):** Nenhum comando ou teste executado no aparelho físico.
Comando executado:
```powershell
cmd /c "T: & set TEMP=C:\temp& set TMP=C:\temp& set GRADLE_USER_HOME=C:\temp\.gradle& set ANDROID_SERIAL=emulator-5554&& gradlew.bat :app:connectedDebugAndroidTest"
```
Saída real:
```text
> Task :app:connectedDebugAndroidTest
Starting 11 tests on MakimonoAndroidTV1080p(AVD) - 16
BUILD SUCCESSFUL in 1m 20s
101 actionable tasks: 8 executed, 93 up-to-date
```
Relatório de execução (`TEST-MakimonoAndroidTV1080p(AVD) - 16-_app-.xml`):
```text
11/11 testes aprovados (0 falhas, 0 erros).
```

---

## 3. Rastreabilidade Git

- **Branch de Trabalho:** `feat/engine-routing-sync` (enviada para `origin/feat/engine-routing-sync`)
- **Merge na `main`:** Fast-forward limpo (`ec89903..b27604a`), push para `origin/main` realizado com sucesso.
- **Tag:** `v1.6.5.2` anotada e enviada para `origin`.
- **Workflow de Release:** Disparado no GitHub Actions via trigger `on: push: tags: ['v*']`.
