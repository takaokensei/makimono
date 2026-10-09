# ADR-002: Motor de Reprodução (Contrato Comum, Media3 Primário e MPV Especializado)

- **Status:** Proposto / Rascunho (Fase 2)
- **Data:** 2026-10-09
- **Contexto:** Makimono — Reprodução de vídeo no Android TV e Mobile

---

## 1. Contexto e Problema

O Makimono suporta reprodução de fluxos de vídeo armazenados no Google Drive, abrangendo uma diversidade de contêineres (MKV, MP4), codecs de vídeo (H.264, HEVC, AV1) e tipos de legendas (SRT em texto plano, PGS em bitmap, e ASS/SSA com estilização vetorial avançada).

Historicamente, o aplicativo manteve duas Activities gigantes e paralelas:
- `PlayerActivity.kt` (~3380 linhas): baseada no Google Media3 (ExoPlayer).
- `MPVActivity.kt` (~2880 linhas): baseada no wrapper nativo `mpv-android` (libmpv).

Ambas implementam lógicas redundantes de OSD (On-Screen Display), controle de foco D-pad, histórico de progresso (`watch_list`), avanço inteligente de abertura e gaveta de episódios.

---

## 2. Decisão

1. **Contrato de Abstração Único (`PlayerEngine`):**
   A camada de reprodução será desacoplada da interface visual através de uma interface de contrato comum (`PlayerEngine`), expondo controle de ciclo de vida (`play`, `pause`, `seekTo`, `release`), seleção de faixas e emissão de estados via `StateFlow<PlayerPlaybackState>`.

2. **Media3 como Motor Primário (Padrão):**
   O Google Media3 será o motor padrão para a grande maioria das reproduções devido a:
   - Integração nativa e eficiente com os decodificadores de hardware da plataforma Android e MediaCodec.
   - Suporte transparente a `MediaSession`, Picture-in-Picture (PiP), e menor pegada de memória em dispositivos de TV com recursos limitados.

3. **MPV como Motor Especializado (Fallback & Estilização Avançada):**
   O motor nativo MPV (`mpv-android`) será mantido e integrado ao mesmo contrato para:
   - Renderização fiel de legendas estilizadas complexas (.ass / .ssa) através da biblioteca `libass`.
   - Fallback de reprodução para fluxos de áudio multicanal não decodificados pelo hardware ou arquivos com contêineres incomuns.

4. **Sessão Unificada de Reprodução (`PlaybackSession`):**
   O gerenciamento de estado de reprodução, persistência de progresso (`ProgressRecorder`) e pulo de abertura (`SkipController`) serão centralizados na camada de sessão (`playback/session`), eliminando a duplicação de regras de negócio entre as telas de reprodução.

---

## 3. Consequências

### Positivas:
- **Redução de código:** Elimina mais de 3.000 linhas de lógica duplicada entre `PlayerActivity` e `MPVActivity`.
- **Previsibilidade:** Uma única máquina de estados gerencia progresso, buffer preditivo e avanço de episódios.
- **Flexibilidade:** Permite alternar o motor de reprodução dinamicamente ou nas preferências do usuário sem alterar a interface do player.

### A Validar (Fase 4):
- Implementação dos adaptadores de motor em `playback/engine/Media3Engine.kt` e `playback/engine/MpvEngine.kt`.
- Validação empírica de liberação de recursos de codec e superfície via LeakCanary após ciclos de reprodução.
