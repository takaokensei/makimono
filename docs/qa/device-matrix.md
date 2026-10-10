# Matriz de Dispositivos e Ambientes de Teste

Documento canônico especificando a matriz de validação de hardware, resolução, densidade e versões do Android suportadas.

---

## 1. Dispositivos Verificados em Laboratório Local

| Dispositivo | Identificador | Tipo | Resolução Física (`wm size`) | Densidade (`wm density`) | Versão Android | Status de Teste |
|---|---|---|---|---|---|---|
| **Motorola moto g34 5G** | `0086442783` | Físico (USB) | 720 × 1600 px | 280 dpi | Android 15 (API 35) | APK instalado (`v1.6.5.0-arm64-v8a`), OSD validado |
| **MakimonoAndroidTV1080p** | `emulator-5554` | AVD Android TV | 1920 × 1080 px | 320 dpi (xhdpi) | Android 16 (API 36) | Suíte UiAutomator (`TvFocusNavigationUiAutomatorTest`, 4/4) e suíte instrumentada total (11/11) aprovadas |

---

## 2. Perfis Alvo e Requisitos de Canvas

| Perfil | Tipo | Resolução Física | Densidade (DPI) | Canvas Base | Versão Android |
|---|---|---|---|---|---|
| **TV 1080p Padrão** | Emulador / Hardware Real | 1920 × 1080 | 320 dpi (xhdpi) | 960 × 540 dp | Android 11+ (API 30+) |
| **TV 4K UHD** | Hardware Real | 3840 × 2160 | 640 dpi (xxxhdpi) | 960 × 540 dp | Android 12+ (API 31+) |
| **TV 720p (Legado)** | Emulador | 1280 × 720 | 213 dpi (tvdpi) | 960 × 540 dp | Android 9+ (API 28+) |
| **Smartphone Portrait** | Físico (`moto g34 5G`) | 720 × 1600 | 280 dpi (hdpi/xhdpi) | 411 × 914 dp | Android 8.0+ (API 26+) |
| **Tablet / Landscape** | Emulador | 2560 × 1600 | 320 dpi (xhdpi) | 1280 × 800 dp | Android 12+ (API 31+) |

---

## 3. Critérios de Homologação

- Validação obrigatória de percurso D-pad no perfil **TV 1080p Padrão** antes de qualquer release.
- Verificação de renderização de anéis de foco e layouts sem cortes em canvas de **960 × 540 dp**.
- Validação física de controles touch e OSD no dispositivo móvel de referência.
