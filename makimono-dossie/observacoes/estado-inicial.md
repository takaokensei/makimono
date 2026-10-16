# Relatório de Estado Inicial e Congelamento de Ambiente (Etapa V0)

**Data:** 14 de Outubro de 2026  
**Projeto:** Makimono (`intelligent-mendel`)  
**Branch Git:** `main`  
**Estado da Árvore:** Limpa (arquivos não rastreados de auditoria pré-existentes preservados intactos).

---

## 1. Verificação Criptográfica e Integridade dos APKs

Os hashes e tamanhos foram recalculados via `Get-FileHash` e conferidos contra o registro da Fase 1:

| Arquivo | Tamanho (Bytes) | Hash SHA-256 | Status de Integridade |
| :--- | :--- | :--- | :--- |
| `base.apk` | 96.610.909 | `901ECDAADE4720C44763BF655409BCF69977A6B9AB203102F66738981378976E` | **Idêntico / Preservado** |
| `split_config.x86_64.apk` | 41.394 | `E4B9F8823C7FB7551DCAD7F4519F7B38EA069237BB894C5DF5B6EBDCB85BB9F8` | **Idêntico / Preservado** |
| `split_config.xhdpi.apk` | 1.051.222 | `2CD35241AC53E9F79911FCD0279BEEFEEF2C2951C24953EE7E11EC717EE8669F` | **Idêntico / Preservado** |

### Validação Oficial de Assinatura (`apksigner`)
A assinatura digital foi inspecionada via `apksigner.bat verify --verbose --print-certs`:
- **Esquemas Validados:** v1 (JAR signing): `true`, v2: `true`, v3: `true`.
- **Signatário Principal (#1):**
  - **DN do Certificado:** `CN="Crunchyroll, Inc.", OU="Crunchyroll, Inc.", O="Crunchyroll, Inc.", L=San Francisco, ST=CA, C=CA`
  - **SHA-256 do Certificado:** `1c2dbdd136487d30a37ee0ee380e615e8a2341c0f2ac91165199e54b744c7ec7`
  - **Algoritmo de Chave:** RSA 2048 bits
- **Source Stamp Signer (Distribuição Oficial):**
  - **DN:** `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US`
  - **SHA-256:** `3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d`
- **Diagnóstico:** Comprovada formalmente a procedência do pacote a partir da Google Play Store oficial, sem modificações ou injeções de terceiros.

---

## 2. Confirmação do Ambiente de Execução Android TV

- **Identificador ADB:** `emulator-5554` (Target Exclusivo).
- **Modelo:** `sdk_google_atv64_x86_64` (AVD `MakimonoTVAscii`).
- **Versão do SO:** Android 16 (API 36).
- **Resolução de Tela:** `1920 x 1080` (Full HD, proporção 16:9).
- **Densidade:** `320 dpi` (fator de escala de tela: 2.0x, onde 1dp = 2px; viewport equivalente = 960 x 540 dp).
- **Recursos de TV Ativos:**
  - `android.software.leanback` presente no sistema.
  - Entry-point verificado: `SplashActivity` com intent-filter `android.intent.category.LEANBACK_LAUNCHER`.
- **Contenção Estrita:** O dispositivo físico conectado via USB (`0086442783`) foi mantido estritamente isolado. Todas as invocações de ferramenta utilizam a chave `-s emulator-5554`.

---

## 3. Matriz de Ferramentas Operacionais

| Ferramenta | Caminho do Executável | Versão Reportada | Operação de Validação Realizada |
| :--- | :--- | :--- | :--- |
| **Node.js** | `C:\Users\CAUV~1\node24\v24.21.0\node-v24.21.0-win-x64\node.exe` | v24.21.0 | Suporte nativo a `URL.parse` comprovado. |
| **Java JDK** | `C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot\bin\java.exe` | 21.0.7+6-LTS | Heap operacional de 8 GB ativo. |
| **ADB** | `C:\Android\Sdk\platform-tools\adb.exe` | v1.0.41 (37.0.0) | `getprop`, `screencap`, `uiautomator` e `input`. |
| **AAPT** | `C:\Android\Sdk\build-tools\34.0.0\aapt.exe` | v0.2-10229193 | `dump badging` com caminho relativo. |
| **apksigner**| `C:\Android\Sdk\build-tools\34.0.0\apksigner.bat` | Build-Tools 34.0.0 | Verificação v1/v2/v3 e carimbo Google Play. |
| **JADX Bridge** | `C:\Users\Cauã V\AppData\Local\Programs\REA\jadx-headless-mcp-0.7.1-all.jar` | v0.7.1 | Carga de 46.932 classes do APK base. |
| **REA CLI** | `...\node_modules\rea-agents\scripts\rea.mjs` | v6.4.0 | `inspect-android-package`, `inspect-android-class`, `inspect-android-method`. |
