# Plano de Implementação: Kodi & Apple Grade Features para Makimono

**Data:** 2026-10-06
**Objetivo:** Implementar as 4 features mais relevantes e de maior impacto prático do Kodi no Makimono com padrão de engenharia Apple HIG / Android TV, sem quebrar testes existentes (269/269) e mantendo integridade total do app.

---

## 1. Seleção das Features Mais Relevantes

Com base no levantamento técnico do repositório do Kodi (`xbmc/xbmc`), selecionamos as 4 features que trazem o maior ganho de usabilidade na sala de estar (10-foot UI):

1. **Feature 1 — Additive Skip Steps (Pulos Progressivos Kodi no D-pad Esquerda/Direita)**
   - *Por que:* Atualmente, cada toque pula fixos 10 segundos. Se o usuário quer pular 2 a 3 minutos de abertura ou resumo, precisa apertar o botão 18 vezes. O padrão Kodi permite:
     - 1º toque rápido = `10s`
     - 2º toque rápido = `30s`
     - 3º toque rápido = `60s (1 min)`
     - 4º toque rápido = `180s (3 min)`
     - 5º toque rápido = `300s (5 min)`
     - Se pausar por 1.2s, o seek é executado e o acumulador reseta para 10s.
   - *Impacto:* Sensação imediata de controle ágil e moderno de media center de luxo.

2. **Feature 2 — Modo Noturno / Realce de Diálogos (Night Mode / Dynamic Audio DRC)**
   - *Por que:* O maior incômodo ao assistir filmes e animes na TV são explosões ensurdecedoras com falas inaudíveis.
   - *Implementação:*
     - **No MPV:** Toggle no menu de áudio aplicando o filtro FFmpeg `lavfi=[dynaudnorm=f=250:g=31:p=0.5]`.
     - **No ExoPlayer:** Toggle ativando `DynamicsProcessing` da API de áudio do Android ou equalização vocal.
   - *Impacto:* Conforto acústico sem precisar regular o volume físico da TV o tempo todo.

3. **Feature 3 — Persistência de Sincronia de Áudio e Legenda por Mídia**
   - *Por que:* Se o usuário calibra `+250ms` em um episódio ou filme devido a atraso no arquivo ou soundbar/Bluetooth, hoje ao fechar o player a calibração é esquecida.
   - *Implementação:*
     - Adicionar campos `audioDelayMs: Long = 0L` e `subtitleDelayMs: Long = 0L` à entidade Room `WatchList`.
     - Ao iniciar a reprodução, restaurar automaticamente o offset ajustado para aquela mídia.
   - *Impacto:* Zero frustração de recalibração repetida.

4. **Feature 4 — Abertura com Vídeo & Paralell Preload Engine**
   - *Por que:* Solicitação do usuário para integrar a nova vinheta (`Animating_logo_and_brand_identity_20261006174150.mp4`) com carregamento paralelo de catálogo/banners em segundo plano enquanto a animação roda suavemente na TV.
   - *Implementação:*
     - Substituir o arquivo em `res/raw/vinheta.mp4`.
     - Disparar `PreloadCatalogWorker` em corrotina paralela no início da vinheta para pré-aquecer o cache do Glide com os metadados dos animes reconhecidos.
   - *Impacto:* A tela de perfis e a Home abrem instantaneamente preenchidas, sem telas cinzas.

---

## 2. Roteiro de Execução Passo a Passo

### Fase 1: Implementação do Additive Skip Steps (Progressive Seeking)
- **Arquivos:**
  - `app/src/main/java/zechs/drive/stream/utils/SeekStepPolicy.kt` (novo utilitário com testes unitários puros).
  - `app/src/main/java/zechs/drive/stream/ui/player/PlayerActivity.kt` (integrar no ExoPlayer).
  - `app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt` (integrar no MPV).
- **Testes Unitários:**
  - `SeekStepPolicyTest.kt` validando progressão sequencial: 10s -> 30s -> 60s -> 180s -> 300s e reset de timeout.

### Fase 2: Implementação do Modo Noturno (Dynamic Audio Normalizer)
- **Arquivos:**
  - `app/src/main/java/zechs/drive/stream/ui/player2/MPVActivity.kt` (comando `af` no MPV).
  - `app/src/main/java/zechs/drive/stream/ui/player/PlayerActivity.kt` (integração ExoPlayer).
  - `app/src/main/java/zechs/drive/stream/ui/player/PlayerGlassMenuDialog.kt` (ação no menu de áudio).

### Fase 3: Persistência de Sincronia de Áudio & Legenda
- **Arquivos:**
  - `app/src/main/java/zechs/drive/stream/data/model/WatchList.kt` (migration ou campos opcionais no Room).
  - `app/src/main/java/zechs/drive/stream/ui/player/PlayerViewModel.kt`.

### Fase 4: Nova Abertura & Preload Paralelo
- **Arquivos:**
  - Cópia do vídeo para `app/src/main/res/raw/vinheta.mp4`.
  - `app/src/main/java/zechs/drive/stream/ui/main/MainActivity.kt` (disparo de corrotina paralela de preload durante a reprodução da vinheta).

### Fase 5: Compilação, Testes & Validação em Runtime
- Executar `./gradlew testDebugUnitTest` e assegurar 269+ testes verdes.
- Executar `assembleDebug` e instalar no Moto G34 5G (`0086442783`).
- Coletar capturas de tela e validar D-pad no aparelho físico.
