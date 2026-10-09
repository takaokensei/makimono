# Matriz de Dispositivos e Ambientes de Teste

Documento canônico especificando a matriz de validação de hardware, resolução, densidade e versões do Android suportadas.

---

## 1. Dispositivos e Perfis Alvo

| Perfil | Tipo | Resolução Física | Densidade (DPI) | Canvas Base | Versão Android |
|---|---|---|---|---|---|
| **TV 1080p Padrão** | Emulador / Hardware Real | 1920 × 1080 | 320 dpi (xhdpi) | 960 × 540 dp | Android 11+ (API 30+) |
| **TV 4K UHD** | Hardware Real | 3840 × 2160 | 640 dpi (xxxhdpi) | 960 × 540 dp | Android 12+ (API 31+) |
| **TV 720p (Legado)** | Emulador | 1280 × 720 | 213 dpi (tvdpi) | 960 × 540 dp | Android 9+ (API 28+) |
| **Smartphone Portrait** | Físico / Emulador | 1080 × 2400 | 420 dpi (xxhdpi) | 411 × 914 dp | Android 8.0+ (API 26+) |
| **Tablet / Landscape** | Emulador | 2560 × 1600 | 320 dpi (xhdpi) | 1280 × 800 dp | Android 12+ (API 31+) |

---

## 2. Critérios de Homologação

- Validação obrigatória de percurso D-pad no perfil **TV 1080p Padrão** antes de qualquer release.
- Verificação de renderização de anéis de foco e layouts sem cortes em canvas de **960 × 540 dp**.
