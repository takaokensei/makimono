# Registro de Ambiente e Operações (Fase 0 e Fase 1) — Dossiê Makimono

**Data de Execução:** 14 de Outubro de 2026  
**Projeto:** Makimono (`intelligent-mendel`)  
**Branch Git:** `main` (commit limpo, preservando arquivos não rastreados de auditoria pré-existentes)  
**Alvo:** `com.crunchyroll.crunchyroid` (versão Android TV instalada no AVD)

---

## 1. Premissa Jurídica Documental

- **Fonte:** `C:\Users\Cauã V\Downloads\Extraordinary Gazette No. 75 of 2026 - Supplement No. 34 - Act No. 19 of 2026.pdf` (Act No. 19 of 2026).
- **Enquadramento:** Tratado estritamente como **premissa documental fornecida pelo usuário**, sem validação externa em índices web, sem juízos jurídicos absolutos e sem controvérsias dogmáticas.
- **Diretrizes Aplicadas:**
  - Procedimento *Clean-Room*: Vedada a cópia literal de código, recursos visuais compilados, layouts, strings proprietárias ou ativos de terceiros.
  - As observações focam exclusivamente em **ideias, arquitetura, padrões estruturais e interoperabilidade funcional** para concepção e aprimoramento independente do frontend do Makimono.
  - Nenhuma modificação, commit, push ou alteração de código-fonte foi realizada no Makimono nesta etapa.

---

## 2. Inventário de Ferramentas e Ambiente

| Ferramenta | Status | Versão Observada | Caminho / Executável |
| :--- | :--- | :--- | :--- |
| **Java / JDK** | Encontrado | OpenJDK 21.0.7 LTS (build 21.0.7+6-LTS) | `C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot\bin\java.exe` |
| **Android SDK** | Encontrado | API 34/35/36 Build-Tools | `C:\Android\Sdk` e `C:\Users\Cauã V\AppData\Local\Android\Sdk` |
| **ADB** | Encontrado | Android Debug Bridge v1.0.41 (37.0.0-14910828) | `C:\Android\Sdk\platform-tools\adb.exe` |
| **AAPT** | Encontrado | Android Asset Packaging Tool v0.2-10229193 | `C:\Android\Sdk\build-tools\34.0.0\aapt.exe` |
| **Android Studio** | Ausente / Não verificável | N/A | Não detectado nos caminhos padrão do sistema |
| **apktool** | Ausente | N/A | Ausente no `PATH` do sistema |
| **JADX (CLI/GUI)** | Ausente no PATH / Integrado via REA | jadx-headless-mcp v0.7.1 | `C:\Users\Cauã V\AppData\Local\Programs\REA\jadx-headless-mcp-0.7.1-all.jar` |
| **REA** | Encontrado | REA Agents v6.4.0 (Node >= 22.1.0) | `C:\Users\Cauã V\node24\v24.21.0\node-v24.21.0-win-x64\node_modules\rea-agents\scripts\rea.mjs` |

### Limitações e Contornos Técnicos Observados
1. **Codificação de Caminho no Windows (AAPT):** A ferramenta `aapt.exe` falha com `Illegal byte sequence` ao processar caminhos contendo caracteres não-ASCII (ex.: o til em `Cauã`). O contorno técnico adotado foi a utilização do caminho curto 8.3 (`C:\Users\CAUV~1\...`) ou execução direta a partir do diretório de trabalho relativo.
2. **Dependência de Runtime Node.js para REA:** O interpretador Node.js padrão presente no `PATH` do sistema (`node v20.15.1`) não possui o método `URL.parse`, exigido pelo motor interno do REA v6.4.0. Foi empregado explicitamente o binário do Node.js v24.21.0 localizado em `C:\Users\CAUV~1\node24\v24.21.0\node-v24.21.0-win-x64\node.exe`.
3. **Dispositivo ADB Exclusivo:** O comando ADB opera sob regra de contenção estrita (`-s emulator-5554`), garantindo isolamento total do dispositivo físico secundário (`0086442783`), no qual nenhuma chamada foi realizada.

---

## 3. Registro de Operações e Aquisição de APK (Fase 1)

### Descoberta de Pacote e Caminhos
- **Comando executado:**
  ```powershell
  & "C:\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 shell pm path com.crunchyroll.crunchyroid
  ```
- **Saída obtida:**
  ```text
  package:/data/app/~~et8y1GLNLZXW6LlyYMSVeA==/com.crunchyroll.crunchyroid-dZYd_jW5qPiu0Ls3-z-ehA==/base.apk
  package:/data/app/~~et8y1GLNLZXW6LlyYMSVeA==/com.crunchyroll.crunchyroid-dZYd_jW5qPiu0Ls3-z-ehA==/split_config.x86_64.apk
  package:/data/app/~~et8y1GLNLZXW6LlyYMSVeA==/com.crunchyroll.crunchyroid-dZYd_jW5qPiu0Ls3-z-ehA==/split_config.xhdpi.apk
  ```

### Cópia de Artefatos (`adb pull`)
Os artefatos foram transferidos com sucesso para a pasta local `makimono-dossie/apk/`:
1. `adb -s emulator-5554 pull /data/app/~~et8y1GLNLZXW6LlyYMSVeA==/com.crunchyroll.crunchyroid-dZYd_jW5qPiu0Ls3-z-ehA==/base.apk "makimono-dossie/apk/base.apk"`
2. `adb -s emulator-5554 pull /data/app/~~et8y1GLNLZXW6LlyYMSVeA==/com.crunchyroll.crunchyroid-dZYd_jW5qPiu0Ls3-z-ehA==/split_config.x86_64.apk "makimono-dossie/apk/split_config.x86_64.apk"`
3. `adb -s emulator-5554 pull /data/app/~~et8y1GLNLZXW6LlyYMSVeA==/com.crunchyroll.crunchyroid-dZYd_jW5qPiu0Ls3-z-ehA==/split_config.xhdpi.apk "makimono-dossie/apk/split_config.xhdpi.apk"`

---

## 4. Identidade dos APKs e Verificação Criptográfica

| Arquivo | Tamanho (Bytes) | Hash SHA-256 | Origem Observada |
| :--- | :--- | :--- | :--- |
| `base.apk` | 96.610.909 | `901ecdaade4720c44763bf655409bcf69977a6b9ab203102f66738981378976e` | Extraído da instalação presente no AVD via ADB (`emulator-5554`) |
| `split_config.x86_64.apk` | 41.394 | `e4b9f8823c7fb7551dcad7f4519f7b38ea069237bb894c5df5b6ebdcb85bb9f8` | Extraído da instalação presente no AVD via ADB (`emulator-5554`) |
| `split_config.xhdpi.apk` | 1.051.222 | `2cd35241ac53e9f79911fcd0279be9e9eefc2951c24953ee7e11ec717ee8669f` | Extraído da instalação presente no AVD via ADB (`emulator-5554`) |

- **Identificador de Pacote:** `com.crunchyroll.crunchyroid`
- **Nome de Versão (`versionName`):** `3.75.0`
- **Código de Versão (`versionCode`):** `22365`
- **SDK Mínimo (`minSdkVersion`):** `26` (Android 8.0 Oreo)
- **SDK Alvo (`targetSdkVersion`):** `35` (Android 15)
- **Recurso de Hardware Declarado:** `android.hardware.type.television` com `android:required="false"` (híbrido compatível com Leanback e Mobile).
