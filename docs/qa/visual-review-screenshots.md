# 📸 Procedimento de Captura de Telas com VisualReviewActivity (Android TV)

Este documento descreve o procedimento operacional padrão para captura automatizada ou manual de telas do Makimono em ambiente Android TV (1080p @320dpi) utilizando a fixture isolada `VisualReviewActivity`.

---

## 🎯 Por Que Usar a VisualReviewActivity?
A `VisualReviewActivity` (`zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity`):
- Existe exclusivamente na variante `debug` (`applicationIdSuffix ".visualreview"`), podendo ser instalada lado a lado com a versão de produção sem sobrescrever sessões ativas do Google Drive ou banco Room.
- Renderiza os layouts XML reais de produção com injeção de dados sintéticos estáveis e reproducíveis.
- Possui anéis de foco automáticos (`TvFocusRing`) e marca d'água de segurança ("PRÉVIA · DADOS DE TESTE") para auditoria visual rastreável.

---

## 🛠️ Passo a Passo com Comandos ADB

### 1. Compilar e Instalar o APK Debug
Compile o pacote de debug e instale no dispositivo ou emulador:

```bash
# Compilar o APK debug
./gradlew assembleDebug

# Instalar no emulador padrão (Android TV 1080p)
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
# Ou o APK universal:
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

---

### 2. Iniciar a VisualReviewActivity na Tela Desejada

Utilize `am start` especificando a tela através do parâmetro `--es screen <nome_da_tela>`:

```bash
# 1. Tela de Detalhes da Franquia / Série (v4 - Foco na Ação Primária)
adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen details

# 2. Detalhes com Foco na Lista de Episódios
adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen details_episode_focus

# 3. Detalhes com Sinopse Expandida
adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen details_long_synopsis

# 4. Tela Inicial (Home com prateleiras e Hero)
adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen home

# 5. Seletor de Perfis
adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen profiles

# 6. Reprodutor de Vídeo (OSD e HUD)
adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen player

# 7. Configurações
adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen settings
```

> [!TIP]
> **Modais e Diálogos:** É possível exibir diálogos automaticamente após abrir a tela adicionando `--es dialog <tipo>` (opções: `subtitles`, `rating`, `theme`, `glass_menu`, `profile_editor`).
> Exemplo: `adb shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.debug.VisualReviewActivity --es screen player --es dialog subtitles`

---

### 3. Tirar a Screenshot

Aguarde 1 a 2 segundos para o carregamento dos assets e renderização completa antes da captura.

#### No Linux / macOS:
```bash
adb exec-out screencap -p > docs/qa/screenshots/tela-detalhes-v4.png
```

#### No Windows (PowerShell):
Para evitar corrupção de quebra de linha (CR/LF) no pipeline do PowerShell:
```powershell
# Método Seguro 1: Gravar no dispositivo e puxar via adb pull
adb shell screencap -p /sdcard/review_screen.png
adb pull /sdcard/review_screen.png ./docs/qa/screenshots/tela-detalhes-v4.png
adb shell rm /sdcard/review_screen.png

# Método 2 (Direto em memória sem arquivo intermediário):
$bytes = adb exec-out screencap -p
[System.IO.File]::WriteAllBytes("docs/qa/screenshots/tela-detalhes-v4.png", $bytes)
```

---

### 4. Fechar a Activity

Após a captura, encerre a fixture para liberar recursos do emulador:

```bash
adb shell am force-stop zechs.drive.stream.visualreview
```
