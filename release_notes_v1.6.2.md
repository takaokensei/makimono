## Makimono v1.6.2

### Destaques e Novidades

- **Autoplay e Transição de Episódios Reformulados**:
  - **Redesign do Card de Próximo Episódio**: Interface profissional no estilo frosted glass/glassmorphism escuro, cantos arredondados, miniatura de prévia estilizada com badge de reprodução, tipografia limpa, barra de progresso suave de contagem regressiva e botão de ação primária com foco intuitivo para Android TV.
  - **Correção da Sequência de Episódios**: Implementada ordenação natural alfanumérica robusta e agrupamento por temporada. O player agora identifica fielmente o próximo episódio cronológico da história, filtrando arquivos de resoluções duplicadas e extras/promos e eliminando saltos fora de ordem.

- **Navegação e Controles D-pad no Controle Remoto**:
  - **Prevenção de Duplo Acionamento do Botão Central**: Adicionada proteção contra repetição de tecla e consumo de evento no `ACTION_UP` do botão de seleção do D-pad, evitando que segurar ou clicar no centro acione comandos indesejados no botão Play/Pause.
  - **Pulo Rápido Acumulativo Discreto (+10s, +20s, +30s...)**: Pressionar Direita ou Esquerda no D-pad com os controles recolhidos não abre mais a barra inteira do player; agora exibe um indicador sutil e moderno com a soma acumulada dos segundos saltados.

- **Pular Encerramentos (Ending Skip) via AniSkip e Capítulos**:
  - Suporte completo à detecção de `ENDING` via AniSkip e capítulos incorporados de arquivos Matroska (MKV).
  - Pílula flutuante e botão dedicados "Pular Encerramento", com suporte a pulo automático caso a opção de Auto-Skip esteja ativada no app.

- **Polimento Geral da UI**:
  - Substituição de caracteres genéricos por ícones vetoriais modernos (`ic_more_vert_24`) com estilo glassmorphism.
  - Paridade total de comportamento e recursos entre os motores ExoPlayer e MPV.
