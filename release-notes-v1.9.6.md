## Makimono v1.9.6 — Kodi & VLC OSD Standard, Clean Search Filtration & Audio DSP

Esta versão aprimora a experiência de reprodução e catálogo do Makimono no Android TV e Mobile, adotando a arquitetura de OSD de reprodutores de referência (Kodi e VLC) e eliminando a poluição visual de episódios individuais nos resultados de busca.

### 🎛️ OSD do Player no Padrão Kodi e VLC
- **Painel de Legendas em Abas Segmentadas:**
  - *Faixas:* Nomes humanizados e limpos (ex: "Português (Brasil)", "Japonês", "Inglês") através do `PlaybackTrackFormatter`.
  - *Sincronia:* Stepper em tempo real com ajustes finos e grosseiros (`[-500ms]`, `[-100ms]`, `[0 ms / Padrão]`, `[+100ms]`, `[+500ms]`).
  - *Aparência:* Seletor de tamanho de texto (16sp a 28sp) e paleta de cores (Branco, Kodi Amarelo, Obsidian Ciano).
  - *Buscar Online:* Acesso direto ao catálogo de legendas oficiais.
- **Painel de Áudio em Abas Segmentadas:**
  - *Faixas:* Identificação precisa de idiomas, faixas originais/dubladas e formatos (ex: "Japonês — Áudio Original • Mono 1.0 • AAC").
  - *Sincronia:* Stepper de atraso/adiantamento em milissegundos (`[-250ms]`, `[-50ms]`, `[0 ms]`, `[+50ms]`, `[+250ms]`).
  - *Processamento de Áudio:* Modo Noturno (Dynamic Range Compression via `LoudnessEnhancer`) e Dialogue Boost (+0 dB, +4 dB, +8 dB, +12 dB).
- **Notificações e HUD:**
  - Pílula de status centralizada no topo (`top|center_horizontal`), eliminando qualquer colisão com o relógio.
  - Eliminação de notificações duplicadas de pulo de abertura.
  - Substituição de `Snackbar` por banners OSD integrados ao design Obsidian.
- **Sleep Timer:**
  - Timer de suspensão integrado com presets de 15m, 30m, 45m, 60m e Fim do Episódio.

### 🔍 Busca Limpa e Inteligente (Sem Episódios Avulsos)
- **Filtro de granularidade de catálogo:** Arquivos de vídeo que representam episódios isolados (padrões de releases como `S01E02`, ` - 02`, `02 ts`, etc.) são filtrados da visualização de busca.
- **Foco em Séries e Filmes:** A busca apresenta exclusivamente pastas consolidadas de animes/temporadas, pacotes do catálogo Seedbox e filmes independentes.

### 🧪 Integridade e Qualidade
- **Testes Unitários:** 709 anotações `@Test` com 100% de aprovação (0 falhas).
- **Worker Backend:** 17 testes Vitest aprovados.
- **I18n:** 631 chaves de strings traduzidas com paridade estrita entre inglês e português.
