## Makimono v1.9.8 — Instant TTFF Playback, Fast-Start Cloud Streaming & Next-Episode Focus Engine

Esta versão refina a experiência de reprodução instantânea no Android TV e Mobile, alinhando o comportamento ao padrão Crunchyroll/Netflix com tempo reduzido de carregamento de vídeo (TTFF), alocação imediata de pastas para streaming sob demanda e melhorias ergonômicas no OSD e gerenciador de downloads.

### ⚡ Reprodução Instantânea & Buffer Otimizado (TTFF)
- **Startup Buffer Otimizado:** Redução do buffer mínimo inicial (`MIN_PLAYBACK_START_BUFFER`) de 4.000ms para 1.800ms e retomada (`MIN_PLAYBACK_RESUME_BUFFER`) para 3.000ms, proporcionando carregamento até 55% mais ágil.
- **Priorização de Tempo sobre Tamanho:** Ativação de `setPrioritizeTimeOverSizeThresholds(true)` no `DefaultLoadControl` do ExoPlayer, liberando a renderização do primeiro frame assim que o buffer mínimo é atingido.
- **Telemetria de TTFF:** Rastreamento do tempo decorrido até a renderização do primeiro frame via `AnalyticsListener.onRenderedFirstFrame`.

### 🚀 Fast-Start Cloud Streaming Sob Demanda
- **Vinculação Imediata no Google Drive:** O daemon do Seedbox no Colab aloca e comunica o ID da pasta do anime no Google Drive logo nos primeiros 2 segundos de execução do job (`readyFolderId`).
- **Prioridade de Download (Episódio 1):** Ordenação prioritária garantida para que o episódio 1 seja baixado e disponibilizado de imediato enquanto os demais episódios prosseguem em segundo plano.

### 🎮 FocusEngine no Card de Próximo Episódio (AniSkip & UpNext)
- **Navegação D-pad Fluida:** Integração do `FocusEngine` com `FocusKind.BUTTON` nos botões "Assistir Próximo Agora" e "Cancelar", eliminando armadilhas de foco e garantindo realce Obsidian responsivo no controle remoto.

### 🛡️ Gerenciador de Downloads & Resiliência
- **Cancelamento Idempotente:** Tratamento de respostas HTTP 404 em `SeedboxRepository.cancelJob` como sucesso idempotente, evitando falsos alertas de erro quando um job já foi cancelado ou removido pelo servidor.

### 🧪 Integridade e Qualidade
- **Testes Unitários:** 717 testes unitários aprovados com 100% de sucesso.
- **Worker Backend:** 17 testes Vitest no Cloudflare Worker 100% verdes.
- **Qualidade de Código:** Android Lint em conformidade estrita com a baseline e Design System Obsidian (zero hexadecimais brutos).
