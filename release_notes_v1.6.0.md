## Makimono v1.6.0

### Correções e Melhorias

- **Bordas Selecionadas no Player (TvFocusRing)**:
  - Corrigido o bug onde as bordas de foco em ciano (`TvFocusRing`) ficavam desenhadas ao redor de toda a tela do player ou sobre views ocultas durante a reprodução em tela cheia.
  - O `TvFocusRing` agora ignora o container raiz (`root`), superfícies de vídeo (`PlayerView`, `MPVView`, `SurfaceView`) e qualquer view que não esteja visível (`isShown == false`, `alpha < 0.1f`).
  - Adicionado `TvFocusRing.clear()` explícito ao ocultar controles e ao alternar modos de proporção de tela / tela cheia.

- **Autenticação do Google Drive (Access Token)**:
  - Resolvido o erro genérico `"Access token can not be null"`.
  - Tratamento aprimorado de erro no `DriveRepository`: agora propaga a causa raiz detalhada (sessão ausente, erro de rede, ou refresh token revogado/expirado com código HTTP 400 `invalid_grant`).
  - Mensagens amigáveis em português orientando o usuário a fazer login em Configurações.
  - Atualizado o workflow do GitHub Actions para carregar credenciais opcionais do Drive (`DRIVE_CLIENT_ID`, `DRIVE_CLIENT_SECRET`, `DRIVE_REFRESH_TOKEN`) a partir dos secrets do repositório.

- **Interface de TV e Backdrops**:
  - Implementado `BackdropImageView` para evitar expansão indevida da arte de fundo nas telas de TV e manter proporções fluidas no cabeçalho.
