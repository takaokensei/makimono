# Subsistema 05 — Player, Controles de Reprodução e Pular Abertura

## 1. Identificadores Observados e Origem

- **Classes Principais:**
  - `com.crunchyroll.crunchyroid.player.exoplayer.ExoplayerComponent` (Módulo Player / Media3)
  - `com.crunchyroll.crunchyroid.player.events.SkipEvent` (Modelo de Evento de Pular)
  - `com.crunchyroll.crunchyroid.player.events.SkipEventType` (Tipo: Intro / Recap / Outro)
  - `com.crunchyroll.crunchyroid.player.ui.UpNext` (Componente de Próximo Episódio)
  - `com.crunchyroll.crunchyroid.player.bus.PlayerEventBus` (Barramento de Eventos do Player)

---

## 2. Enquadramento e Análise Estrutural

### 2.1 Arquitetura Baseada em ExoPlayer / Media3
- **Tag:** `[IDEIA]`
- **Observação Factual:** A infraestrutura de reprodução repousa sobre a biblioteca padrão da indústria `androidx.media3.exoplayer` encapsulada pela classe arquitetural `ExoplayerComponent`. O player gerencia uma camada de superfície desacoplada dos controles de sobreposição (HUD/OSD).
- **Grau de Confiança:** Forte evidência estática (presença maciça de pacotes `androidx.media3` e adaptadores de renderização).
- **Lacunas Conhecidas:** Comportamento com fluxos locais não segmentados (MKV com múltiplas faixas de áudio embutidas), uma vez que a Crunchyroll foca em HLS/DASH.

### 2.2 Sistema de Eventos de Pular (Intro / Recap / Outro)
- **Tag:** `[IDEIA]`
- **Observação Factual:** O fluxo de reprodução possui um subsistema estruturado de detecção e disparo de marcadores temporais (`SkipEvent`, `SkipEventType`). Quando o cursor de reprodução entra no intervalo `[start_ms, end_ms]`:
  1. Um botão contextual de foco prioritário ("Pular Abertura" / "Skip Intro") surge na tela.
  2. O foco do D-pad é transferido opcionalmente para esse botão sem pausar a reprodução do vídeo.
  3. O acionamento de `D-PAD CENTER` executa um seek direto para `end_ms + 100ms`.
  4. Se não acionado dentro da janela temporal, o botão desaparece automaticamente com animação de fade.
- **Grau de Confiança:** Forte evidência estática a partir dos modelos de dados e barramento de eventos `PlayerEventBus`.
- **Lacunas Conhecidas:** Se a Crunchyroll suporta detecção baseada em capítulos embutidos no container ou se depende exclusivamente de metadados remotos fornecidos pelo backend.

### 2.3 Transição Automática de Próximo Episódio ("Up Next")
- **Tag:** `[IDEIA]`
- **Observação Factual:** O componente `UpNext` é disparado durante os créditos finais (outros), apresentando um card flutuante no canto inferior direito com contagem regressiva em segundos, miniatura e botão de cancelamento. Ao término do contador, a fila de reprodução carrega o próximo episódio sem retornar à tela de detalhes.
- **Grau de Confiança:** Forte evidência estática.
- **Lacunas Conhecidas:** Integração de foco do D-pad entre o scrubber principal e o card de "Up Next".

### 2.4 Código Concreto e Constantes de Temporização
- **Tag:** `[EXPRESSÃO]`
- **Observação Factual:** Estruturas de serialização JSON de marcadores temporais internos e nomes de eventos privados do barramento.
- **Grau de Confiança:** Forte evidência estática.
- **Diretriz Clean-Room:** O Makimono deve consumir os marcadores de capítulos do próprio container MKV/MP4 via libmpv / Media3 (ou via integração externa aberta tipo AniSkip), sem replicar os nomes de classes ou estruturas de payload proprietárias da Crunchyroll.
