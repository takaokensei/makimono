# Evolução e Aperfeiçoamento do Makimono

Base: [itszechs/DriveStream](https://github.com/itszechs/DriveStream) (Apache-2.0), profundamente auditado, refatorado e expandido para uma experiência de alta performance em Android e Android TV.

---

## 1. Segurança e Privacidade (Fase 0)

- **Armazenamento Criptografado no Keystore (`EncryptedSessionStore`)**: Credenciais de sessão e tokens OAuth do Google Drive e MyAnimeList são protegidos por criptografia simétrica AES-256-GCM via Android Keystore, com migração transparente dos dados legados.
- **Redação de Logs (`RedactingLoggingInterceptor`)**: Interceptor customizado de rede oculta cabeçalhos `Authorization`, client secrets e tokens nos registros do Logcat.
- **OAuth Least Privilege (`drive.readonly`)**: O escopo do Google Drive é restrito exclusivamente a leitura (`drive.readonly`). Favoritos e marcadores de progresso foram desacoplados do Drive e persistidos localmente no banco Room.
- **Servidor de Emparelhamento TV Seguro (`TvLoginServer`)**: Pareamento na LAN utilizando nonces criptográficos de uso único, expiração curta de sessão, proteção contra CSRF e fechamento automático após autenticação.
- **Migrações Não-Destrutivas no Room (`DATA-01`)**: Migrações incrementais explícitas (v1 -> v6) garantindo zero perda de histórico, progresso ou favoritos em atualizações.
- **CI/CD com Verificação de PR e Checagem de Assinatura (`ci.yml`)**: Validação automatizada de builds de pull request e verificação obrigatória de keystore em releases.

---

## 2. Fundação de UI TV/Mobile e Acessibilidade (Fase 1)

- **Navegação TV 10-foot com Rail Persistente**: Rail lateral dedicado para D-pad em landscape (`values-land/bools.xml`), remoção de overlay falso, restauração de foco em `onResume` e safe-insets de overscan.
- **Bottom Navigation Mobile Fluida**: Barra de navegação inferior nativa para uso em toque vertical em celulares, botão de menu hambúrguer 48dp e alternância fluida entre telas.
- **Biblioteca de Arquivos Landscape (`layout-land/fragment_files.xml`)**: Layout dedicado de arquivos para telas de TV e tablets com grid de alta densidade.
- **Modelo Uniforme de Estados (`ScreenState`)**: Estados desacoplados e tipados (`Loading`, `Content`, `Empty`, `Offline`, `Error`) em todas as telas principais.
- **Tradução Completa para Português do Brasil (`values-pt/strings.xml`)**: Mais de 100 chaves de interface traduzidas e revisadas rigorosamente.
- **Acessibilidade e Alvos de Toque (A11Y-01)**: Todos os alvos de interação e clique expandidos para >= 48dp, fontes escaláveis e foco com `doOnPreDraw` eliminando delays de renderização.

---

## 3. Recursos de Retenção e Produto (Fase 2)

- **Suporte a Múltiplos Perfis (`FEAT-01`)**: Isolamento completo de WatchList, progresso de episódios, fila de reprodução e preferências por perfil ativo.
- **Busca Global no Google Drive (`FEAT-02`)**: Pesquisa em tempo real com debounce (300ms), paginação, filtros e agrupamento automático.
- **Fila de Reprodução / Assistir Depois (`FEAT-03`)**: Prateleira ordenável de reprodução integrada à tela inicial e detalhes de séries.
- **Cache Offline de Catálogo (`FEAT-04`)**: Armazenamento em Room dos catálogos do Drive e metadados de anime para navegação instantânea e offline.
- **Notificação Periódica de Novos Episódios (`FEAT-05`)**: Tarefa agendada via WorkManager que monitora pastas seguidas e notifica lançamentos.
- **Política Unificada de Progresso e Autoplay (`PlaybackProgressPolicy`)**: Cálculo com base na duração real de vídeo, limiar de 10% para salvar, 95% para marcar como assistido, e transição com contagem regressiva para o próximo episódio.
- **Backup e Restauração de Perfil (`FEAT-07`)**: Exportação e importação de dados de perfil em JSON sanitizado (sem tokens ou credenciais sensíveis).
- **MediaSession e Picture-in-Picture (PiP) (`FEAT-08`)**: Controle multimídia nativo via notificações de sistema, teclas de headset/controle remoto e suporte a PiP (Android 8.0+) com proporção dinâmica.

---

## 4. Player, Drive e Performance (Fase 3)

- **Arquitetura Comum de Player (`ARCH-01`)**: Interface abstrata `PlayerEngine`, adaptadores `ExoPlayerEngine` e `MpvPlayerEngine`, e orquestrador unificado `PlaybackCoordinator`.
- **Token Provider Assíncrono sem `runBlocking` (`ARCH-02`)**: Pré-aquecimento de tokens OAuth fora da thread do player, eliminação total de `runBlocking` no `AuthenticatingDataSource` e coalescência de requisições concorrentes de renovação.
- **Paginação e Retry com Backoff no Drive (`DRV-01`)**: Recuperação de irmãos de episódios e legendas em múltiplas páginas para séries com mais de 100 episódios, com backoff exponencial contra HTTP 429/5xx.
- **Política Centralizada de Imagens (`PERF-01`)**: Eliminação de chamadas arbitrárias do Glide através de `MediaImageLoader` e `ThumbnailUrl`.
- **Atualizador Fail-Closed com SHA-256 e Assinatura (`UPD-01`)**: Verificação rigorosa do digest SHA-256 da release e conferência da impressão digital do certificado criptográfico contra o app instalado. Downloads sem verificação são bloqueados e descartados por segurança.

---

## 5. Esclarecimentos Importantes

- **Google Services / Firebase**: O projeto **não** depende de Firebase Crashlytics ou Analytics; nenhum arquivo `google-services.json` é requerido.
- **AdMob**: Anúncios e bibliotecas AdMob foram 100% expurgados do código-fonte e manifesto.
- **Credenciais OAuth**: Para conectar ao Google Drive, utilize seu próprio Client ID e Client Secret gerados no Google Cloud Console, configuráveis na tela de login do app.

---

## 6. Qualidade e Cobertura Automatizada

- **Testes Unitários**: 122 testes automatizados executando com 100% de sucesso (`./gradlew testDebugUnitTest`).
- **Verificação Estática**: `lintDebug` limpo (0 erros).
- **Build de Produção**: `assembleRelease` funcional e validado com otimização R8/ProGuard.
