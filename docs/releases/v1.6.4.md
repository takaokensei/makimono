# Makimono v1.6.4

## Correções

- Sair pelo botão do player encerra a reprodução; o botão Voltar do controle fecha o OSD primeiro e sai quando ele já está oculto, nas engines ExoPlayer e MPV.
- A posição salva só é retomada depois que o MPV carrega o arquivo correto, e episódios concluídos começam do início em vez de saltarem para o fim e avançarem automaticamente.
- A página de detalhes mantém a arte de destaque no hero e aplica uma superfície escura contínua atrás das temporadas e episódios.
- Intents recebidos por uma Activity de player reutilizada atualizam arquivo, título e miniatura antes de preparar a reprodução.
