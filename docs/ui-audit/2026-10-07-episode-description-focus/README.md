# Descrição opcional no card de episódio

Data: 07/10/2026  
Dispositivo: Moto G34, Android 15, pacote isolado `zechs.drive.stream.visualreview`  
Build: Debug arm64; SHA-256 `2774FC7986E154A35DB265F38841B5F8EFBA3DBDB83110DBDA67A8C9226FE671`

## Resultado

O Drive pode fornecer um campo `description` opcional. O app agora o preserva na resposta e no modelo de arquivo. Nos cards de episódios, o texto aparece somente quando o card está focado, com limite de duas linhas e 600 caracteres. Descrição ausente ou em branco mantém o card compacto. Não há sinopse inferida do nome do arquivo nem texto de placeholder na UI.

## Verificação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: build concluído; 314 testes em 71 suítes, sem falhas.
- Instalei somente o APK Debug `.visualreview`, sem substituir `zechs.drive.stream` e sem consultar sessão ou arquivos reais do Drive.
- No harness de revisão, D-pad iniciou no primeiro episódio e exibiu a descrição da fixture. RIGHT moveu o foco para o segundo episódio sem descrição, que permaneceu compacto; um toque no primeiro card retomou foco e revelou o texto. XML de acessibilidade/foco: `2026-10-07-episode-description-focus.xml`, `2026-10-07-episode-description-unfocused.xml` e `2026-10-07-episode-description-touch.xml`.
- Capturas: `2026-10-07-episode-description-focused.png`, `2026-10-07-episode-description-unfocused.png` e `2026-10-07-episode-description-touch.png`.
- A resolução e a densidade do aparelho permaneceram em 720×1600 / 280 dpi.

## Limites restantes

As descrições mostradas são sintéticas. A experiência com `description` real depende de o proprietário preencher esse campo no Drive; MAL/AniList não fornece sinopses de episódio pelo fluxo atualmente integrado. Continua pendente homologação em TV física, TalkBack e mudanças de altura do rail em diferentes densidades/overscan.
