# 🎬 Makimono: Arquitetura e Compilação Nativa do MPV (BR-04)

Este documento descreve a integração nativa da biblioteca MPV (`libmpv.so`) no ecossistema Android do Makimono.

---

## 1. Visão Geral da Arquitetura

O Makimono adota uma estratégia de reprodução de vídeo em duas camadas:

1. **ExoPlayer (AndroidX Media3 1.4.1):**
   * Mecanismo principal para streaming HTTP direto via Google Drive API.
   * Utiliza decodificação acelerada por hardware (MediaCodec) com suporte a HDR10, Dolby Vision, HLG e troca automática de taxa de quadros (AFR).
   * Renderização de legendas WebVTT/SRT na camada gráfica nativa do Android.

2. **MPV (libmpv nativo via JNI):**
   * Fallback de alta fidelidade e robustez para codecs complexos (ex.: ASS/SSA com estilização avançada, múltiplos fluxos de áudio de alta resolução, arquivos Matroska com cabeçalhos não-padrão).
   * Módulo Android Library `:mpv` embutido no projeto, integrado diretamente através de JNI (`MPVLib.kt` / `MPVView.kt`).

---

## 2. Estrutura do Módulo `:mpv`

```text
mpv/
├── build.gradle               # Plugin com.android.library, jniLibs apontando para src/main/libs
├── src/main/
│   ├── AndroidManifest.xml   # Manifest básico da biblioteca
│   ├── java/zechs/mpv/       # Wrappers Kotlin/Java (MPVLib, MPVView)
│   ├── jni/                  # Código C/C++ da ponte JNI
│   └── libs/                 # Binários compartilhados compilados por ABI
│       ├── arm64-v8a/        # libmpv.so, libc++_shared.so
│       ├── armeabi-v7a/      # libmpv.so, libc++_shared.so
│       ├── x86/              # libmpv.so, libc++_shared.so
│       └── x86_64/           # libmpv.so, libc++_shared.so
```

---

## 3. Compilação Nativa a partir do Código Fonte (mpv-android)

Para recompilar a `libmpv.so` a partir do repositório upstream [mpv-android](https://github.com/mpv-android/mpv-android):

### Pré-requisitos
* Linux x86_64 (Ubuntu 22.04 LTS ou container Docker)
* Android NDK r25c+ (`$ANDROID_NDK_HOME`)
* Meson, Ninja, Python 3, Git, Rust (para rav1e se habilitado)

### Instruções de Build
```bash
git clone https://github.com/mpv-android/mpv-android.git
cd mpv-android
git submodule update --init --recursive

# Compilar para todas as arquiteturas suportadas
./buildall.sh
```

Os artefatos compilados são gerados em `buildall/installed/{arch}/lib/` e copiados para `mpv/src/main/libs/{arch}/`.

---

## 4. Higiene de Dependências e AARs (BR-04)

* O arquivo legado `app/libs/extension-ffmpeg-release.aar` (compilado contra o namespace obsoleto `com.google.android.exoplayer2`) foi descontinuado e removido do repositório.
* A decodificação de formatos de áudio/vídeo alternativos é delegada com segurança para o motor MPV nativo em `:mpv`.
