
## Reteste dos players em fixture local — 07/10/2026

Dispositivo: Moto G34 Android 15, 1600×720, pacote isolado `zechs.drive.stream.visualreview`. Os testes usaram somente `review_multitrack.mkv`; nenhuma conta, biblioteca Drive ou dado de produção foi acionado.

- **MPV:** a hierarquia confirma foco inicial em Play/Pause e navegação à direita até Episódios. O menu Mais opções abre com foco inicial em Velocidade, mostra Velocidade, Proporção de tela, Capítulos, Informações técnicas e Girar tela; BACK fecha e restaura o foco em `btnMoreOptions`. BACK no player oculta a OSD e mostra “Pressione Voltar novamente para sair do vídeo”; outra pressão encerra a Activity. Evidências: `player-mpv-retest.*`, `player-mpv-right-focus.xml`, `player-mpv-menu.*`, `player-mpv-menu-closed.xml`, `player-mpv-osd-hidden.*`.
- **Exo/Media3:** a OSD é exibida e Play/Pause recebe foco. A sequência OK → cima foca `btnBack`; OK encerra a Activity. BACK mantém a Activity na primeira pressão e a segunda retorna à tela chamadora. O toque em `btnBack` (126,95 na captura 1600×720) também encerrou a Activity. Evidências: `player-exo-retest.*`, `player-exo-center-up.xml`, `player-exo-after-exit.xml`, `player-exo-back1.*`.
- **Observação de foco:** enviar apenas CIMA logo após abrir Exo não moveu o foco; após OK → CIMA, a seta recebeu foco como esperado. O fluxo de seta está validado com a sequência de ativação da OSD, mas a dependência de OK no primeiro percurso precisa ser explicada/retetada em TV antes de fechar C1.
- **Limites:** no ramo Exo da fixture, a posição ficou em 00:00; isto valida OSD e navegação, não reprodução contínua. O MPV usa mídia curta local, portanto não prova streaming/seek no Drive, buffering de rede, continuidade, formatos/decoders, TV física nem tela 10-foot. Menu completo visível e foco de entrada/retorno foram verificados; percurso por todas as cinco linhas ainda não foi homologado nesta rodada.
- **Isolamento:** ao terminar um ciclo de BACK, a MainActivity de produção ficou brevemente em primeiro plano por estar no histórico da tarefa; nenhum controle foi acionado. Relancei explicitamente `VisualReviewActivity` e confirmei o pacote seguro no topo.

