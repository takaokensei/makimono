# ADR-001 — pareamento TV/celular e autorização Google Drive

Data: 04/10/2026. Atualizado: 09/10/2026 (Fase 3: remoção formal do `TvLoginServer.kt` do app Android).
Estado: **Aceito / Em andamento no backend**. Servidor HTTP local no app Android removido por violação de segurança e obsolescência.

## Contexto e decisão

O usuário quer autorizar pelo celular e abrir a TV posteriormente já conectada, sem deixar credenciais Drive expostas na interface ou embutidas no aplicativo. O projeto atual usa `drive.readonly` para biblioteca preexistente. Há QR generator, armazenamento local cifrado e servidor HTTP de login local não ligado à produção; não há prova de um backend de pareamento operacional.

Adotar **pareamento Makimono próprio**, separado do OAuth Google. TV solicita desafio temporário; celular autoriza Google em navegador normal; backend recebe callback e grant; celular confirma a TV; somente o aparelho que criou o desafio recebe uma sessão Makimono. Na abertura seguinte ele renova essa sessão, sem repetir consentimento quando o grant ainda é válido.

### Prova local iniciada em 05/10/2026

O sub-build independente `backend/` usa Kotlin 2.2.20 e Ktor 3.6.0; a versão Android não herda esse toolchain. A primeira fatia implementa criação, polling autenticado e cancelamento de desafios, com locator de 128 bits, código manual de oito caracteres, poll secret de 256 bits, somente hash persistido em memória, expiração de cinco minutos e cancelamento irreversível. O processo de desenvolvimento vincula-se a `127.0.0.1` e usa domínio `.invalid` para deixar o QR não roteável. Os testes Ktor/Protocol cobrem estados, concorrência de criação, segredo incorreto e ausência de cache.

Esta prova **não é um servidor de produção**. A primeira fatia usava armazenamento volátil/single-process. Em 05/10/2026 foi adicionada uma implementação PostgreSQL opcional em `PostgresPairingChallengeStore`, migração Flyway e testes Testcontainers de reinício do pool, cancelamento concorrente e hash. O host segue restrito a loopback e a URL `.invalid`; rate limit distribuído, política de retenção, aprovação mobile, página web, OAuth, sessão Makimono, TLS/domínio real, gestão operacional e transporte de mídia continuam ausentes. A API fica explicitamente fora do app Android até essas garantias e operações serem implementadas.

Essa composição não é o Google Device Grant nem deve anunciar conformidade automática ao RFC 8628. A especificação abaixo é proposta do projeto, com valores ajustáveis após testes.

O Google não suporta o escopo atual no fluxo de entrada limitada. O catálogo de scopes oferecido nesse fluxo não permite tratá-lo como substituição transparente da navegação existente. A prova Q07 precisa manter leitura/listagem/download da biblioteca sem ampliar para escrita. Fontes e data de pesquisa estão no plano principal.

## Arquitetura proposta

Componentes separados por responsabilidade, em um backend pequeno, sem microservices:

1. Android Kotlin/Views: PairingViewModel, DeviceSessionRepository, DriveAccessGateway e engines atuais.
2. Backend Kotlin/Ktor modular: PairingService, GoogleGrantService, DeviceSessionService, DriveGateway, StreamingProxy, SyncService posterior.
3. PostgreSQL proposto para desafios/grants/sessões; expiração e consumo atômicos por transação. Uma instância local/SQLite pode servir ao spike, nunca ser chamada de arquitetura multi-instância aprovada.
4. Página mobile pequena, TypeScript estrito ou HTML servido por backend com JS mínimo tipado; usar biblioteca OAuth/crypto mantida. A escolha de versão/toolchain é registrada antes de codificar.
5. HTTPS/domínio, armazenamento de segredos e chave de cifragem separados do banco. Hosting e orçamento ainda não selecionados.

UI não depende diretamente de forma de token. Interfaces de domínio preservam DriveRepository, perfil, catálogo, playback e cache. Adapter legado continua durante migração opt-in; adapter BFF usa a sessão própria. Não migrar todos os repositórios e ambos players em uma única alteração.

### Caminho dos dados de vídeo

**Padrão proposto BFF:** Google access/refresh tokens ficam no servidor; metadata/list e bytes usam sessão própria do aparelho. `/v1/media/{fileId}` transmite bytes incrementalmente. A sessão Makimono também é credencial e precisa de proteção; “sem token Google na TV” não significa “sem credencial alguma”.

Consequências: dependência do backend, custo e latência de banda, necessidade de Range/backpressure e limites compatíveis com hosting. Antes de contratar, medir bitrate/horas/aparelhos e custo de egress/compute do provedor. Estimativa de volume: `GB ≈ Mbps × segundos ÷ 8 ÷ 1000`; 8 Mbps por 3 h equivale aproximadamente a 10,8 GB, sem overhead. Esse é exemplo de volume, não preço de serviço.

**Alternativa condicionada à decisão do proprietário:** broker com refresh Google server-side e access token curto apenas em memória na TV, para bytes diretamente do Drive. É mais simples e evita tráfego do proxy, mas o access token ainda permite acesso no seu escopo OAuth e não é limitado a uma pasta só por esconder pastas na UI. Não trocar silenciosamente para esse modo por custo/performance.

O padrão BFF aplica autorização de owner/root no servidor. A pasta escolhida no Makimono limita a aplicação, não reduz magicamente o escopo consentido do grant Google. Shortcuts e ancestors precisam de validação e proteção contra ciclo; `fileId` fornecido pelo cliente não comprova autorização.

## Fluxo completo

1. TV cria desafio com nome de aparelho e protocolo/capabilities; recebe ID opaco, código, URL de verificação, vencimento, intervalo de polling e poll secret.
2. QR contém somente `https://dominio-controlado/pair/<locator>`. Código manual abre página equivalente. Nenhum client secret, poll secret ou token permanente vai nessa URL.
3. Celular vê nome da TV, código e pedido de conexão. Backend cria sessão de navegador e transação OAuth com state aleatório de uso único, vinculados ao desafio. Google login/consentimento ocorrem no domínio Google.
4. Callback HTTPS registrado recebe código; valida state/transação/expiração e troca server-side. Verifica identidade via biblioteca OIDC, audiência, issuer e validade; não confiar em e-mail enviado pelo Android.
5. Conta proprietária permitida e scopes necessários são verificados. Grant cifrado é armazenado sem sobrescrever refresh token anterior por null. Se houver grant válido e sessão web do owner, futuro pareamento pode reutilizar autorização, ainda exigindo confirmação da TV.
6. Celular mostra confirmação específica: aparelho/código/conta. POST protegido por CSRF confirma. Apenas obter grant Google não aprova automaticamente um aparelho que o usuário não confirmou.
7. Backend marca Approved. TV apresenta poll secret pelo canal HTTPS em header, nunca URL. Consome aprovação atomicamente e recebe credenciais Makimono. Código/QR deixam de funcionar.
8. TV persiste sessão própria protegida; limpa desafio transitório. Seleção de root e perfil concluem onboarding. Reabrir usa sessão válida/renovação single-flight.
9. Revogar um aparelho invalida sua sessão. Desconectar conta revoga/retira grant Google conforme ação explícita e desconecta aparelhos relacionados; não apagar mídias do Drive.

Após cancelamento da TV, callback tardio não revive desafio. Não vincular contas por refresh token fornecido no POST do celular, não transferir token por LAN, não usar redirect `127.0.0.1` do celular como callback de um servidor rodando na TV.

## Estados e invariantes

Desafio: `Pending → GoogleAuthorized → Approved → Claimed` ou `Denied/Cancelled/Expired`. Estados finais são irreversíveis. Grant pode existir após usuário cancelar pareamento; tratá-lo conforme consentimento/retention explícitos, não criar sessão de TV com ele.

Sessão: `Active → Refreshing → Active` ou `Revoked/Expired`; erro de rede não significa Revoked. Grant: `Valid`, `RefreshRequired`, `ReconsentRequired`, `Revoked`, `TemporarilyUnavailable`.

Invariantes:

- Só quem possui poll secret original reivindica aquela aprovação; locator/código não são credenciais para polling/claim.
- Aprovar exige usuário web autenticado permitido e confirmação com proteção CSRF.
- Grant nunca viaja ao APK no modo BFF; nenhum segredo aparece em logs/QR/HTML/cache/browser localStorage.
- Um grant pode servir a vários aparelhos do mesmo owner; sessão rotativa e revogação por aparelho são independentes.
- ProfileId/role do cliente não alteram owner/root/permission. Admin tem autoridade verificada server-side.
- Atualização Android não apaga grant/sessão existente; migração só depois da conexão nova aprovada.

## Contrato HTTP v1 proposto

Manter `backend/openapi.yaml` como contrato executável quando Q07 iniciar. Isso não é endpoint Google, nem API já disponível. IDs opacos; timestamps UTC RFC3339; duração em segundos; erros tipados com correlation ID, mensagem pública e retryAfter quando aplicável.

### POST /v1/pairing/challenges

Sem sessão prévia; rate limit por origem/instalação e limite global. Body contém `deviceName`, `clientVersion`, `protocolVersion`, `capabilities`; campos limitados, sem PII necessária. Não confiar em deviceName como identidade.

Resposta 201: `challengeId`, `userCode`, `verificationUrl`, `expiresAt`, `pollIntervalSeconds`, `pollSecret`. `Cache-Control: no-store`. Proposta inicial: locator ≥128 bits aleatórios; poll secret ≥256 bits; código 8 caracteres sem ambíguos; TTL 5 min; intervalo 3 s. Esses números não vêm da HIG/Google. Código curto tem limite de tentativa e não autentica claim.

Backend guarda hash do poll secret, não valor em texto; resposta enviada uma vez. Chave do desafio transitório pode usar storage local protegido para sobreviver a process death até TTL; não incluir em backup.

### GET /v1/pairing/challenges/{id}/status

Header dedicado `Authorization: Pairing <pollSecret>`. Retorna estado/expiry/pollInterval e instrução pública. 401 segredo inválido; 404 ID desconhecido; 410 final expirado/cancelado; 429 Retry-After. Nunca devolve tokens Google ou e-mail completo. Poll não consome Approval; claim é POST explícito.

### DELETE /v1/pairing/challenges/{id}

Mesmo header de pairing; cancelamento idempotente. Solicitação antiga inválida depois de renovar QR. Assincronia/callback não reabre estado final.

### Rotas de navegador /pair/{locator}, /oauth/google/start, /oauth/google/callback

Cookie de transação Secure/HttpOnly/SameSite apropriado ao redirect (normalmente Lax), validade curta, state não previsível e comparação com valor persistido de uso único. Redirect URI fixo no allowlist do Google, sem open redirect. Não refletir deviceName/código em HTML sem escaping. Política referrer evita vazamento de locator para terceiros; sem scripts/trackers terceiros na página de pareamento.

Authorization Code com client de servidor, `state` e pedido offline. PKCE S256 recomendado e testado com client/provider/library escolhidos; registrar capability/prova, não inventar parâmetros de uma biblioteca. RFC 9700 recomenda PKCE também para clientes confidenciais; bibliotecas/verificação são preferíveis a crypto/OAuth artesanal. Fonte: [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html).

### POST /v1/pairing/confirm (navegador)

Sessão web autenticada e CSRF obrigatório; body challenge/code/approve. Conferir owner autorizado, desafio e código apresentados. 409 para estado incompatível; operação idempotente para mesmo owner/resultado. Deny encerra desafio; não cria device session.

### POST /v1/pairing/challenges/{id}/claim

Header Pairing + chave de idempotência aleatória da TV. Atomicamente cria uma DeviceSession para Approval e devolve `sessionAccessToken`, `sessionRefreshToken`, `accessExpiresAt`, `deviceId`. Sem tokens Google. Proposed access lifetime 10 min, refresh inactivity 90 dias, sempre revogável; configurar e testar sem promessa “para sempre”.

Retry após resposta perdida deve recuperar a mesma entrega apenas para o mesmo pairing secret/idempotency key, em janela limitada, com payload de replay cifrado server-side ou desenho equivalente documentado. Não emitir múltiplas sessões nem bloquear a TV para sempre porque o HTTP se perdeu. Confirmar recepção e expirar o replay; testes de concorrência obrigatórios.

### POST /v1/sessions/refresh

Refresh próprio em corpo HTTPS; sem logs do body. Rotação com version/family e chave de idempotência por operação. Serializar no cliente, tratar retry de resposta perdida e replay malicioso de forma distinta. Persistir nova credencial atomicamente; invalidar antiga segundo política definida. Implementação com testes, não JWT próprio sem biblioteca.

### GET /v1/devices e DELETE /v1/devices/{id}

Listar nomes/lastSeen e desconectar aparelho exige sessão de owner/admin verificada. Logout local remove credencial mesmo se offline; revogação remota pendente é repetida ao retornar rede. Mostrar diferença entre sair desta TV e desconectar a conta de todos os aparelhos.

### GET /v1/drive/files e GET /v1/media/{fileId}

Bearer Makimono; autorização owner/root; queries Drive construídas/escapadas server-side. List response traz items/cursor/isComplete/context, sem falso total. Stream usa header, não bearer em URL. MPV precisa prova de headers/refresh/retry para requisições futuras de seek; access token expirando no meio do playback não pode quebrar stream.

Para stream: encaminhar Range validado, preservar 200/206/416 e Content-Range quando upstream fornece; backpressure; limite de conexões; cancelar upstream; reabrir após refresh seguro sem duplicar bytes. Testar resposta upstream ignorando Range, seek não suportado, download restriction, conexões interrompidas e conteúdo binário. Não tratar Google Workspace export como vídeo blob seekable.

## Persistência e observabilidade

- Grants cifrados com authenticated encryption e nonce único; keyId/version; chave em gestão de secrets/KMS equivalente escolhida, separada do backup DB. Rotação planejada e teste de restauração.
- Sessões próprias armazenadas hashed quando apenas validação exige segredo; cipher apenas quando reentrega autorizada exige recuperar payload. Leases/transações funcionam com múltiplas instâncias.
- Android usa armazenamento protegido já existente mediante prova da faixa SDK; MasterKey/Keystore e fallback precisam ser testados em API21 e23+, sem supor hardware backing em todos aparelhos. Se proteção indisponível, falhar de modo explícito; nunca gravar refresh em prefs plaintext silenciosamente.
- Perfil/dados cacheados não contêm grant nem poll secret. Backup exporta somente dados permitidos. Logs de auth/stream redigem headers/query/cookies/secret bodies; crash reporter idem.
- Instrumentar taxa de sucesso pareamento, tempo até conectar, códigos de falha, refresh latency e stream buffering; não capturar código/QR/conta/filename por analytics. Sem porcentagem de SLA inventada.

## Testes que bloqueiam aceite

- Unit: state machine, TTL/clock, code generation, owner/profile permission e mapping errors.
- Backend integration: transações racing confirm/claim/cancel, retry de resposta perdida, rotação/replay, rejeição state/CSRF, rate limits, root/shortcut ACL.
- HTTP: 401 single-flight, invalid_grant, 403,429,5xx/offline, cursor incompleto e Range/416/interrupção.
- Android: QR/poll/BACK/foco/recreation; credencial ausente/Keystore inválido; reconnect sem limpar Room; data-source Media3 e MPV.
- End-to-end real: celular em rede móvel, TV em outra rede, conta Google do owner, autorização/refrescamento, Drive listado e episódio seekable longo; reiniciar TV; revogar grant e reconectar.
- Operação: provider budget/vazão comprovados, TLS/domínio, consent screen/escopos, restauração backup staging e rollback legado testado.

Mocks aprovam somente os mecanismos simulados. Sem projeto OAuth/hosting reais, registrar o bloqueio do gate externo e avançar UX/codecs/perfis; não publicar endpoint fake como solução pronta.

## Alternativas rejeitadas para a experiência padrão

- Embed de refresh Google/PAT/client secret de servidor no APK: instalação não pode ser tratada como armazenamento confidencial de segredo comum a todos aparelhos.
- LAN HTTP com copiar código de callback localhost: exige mesma rede, entrada manual e callback no dispositivo incorreto; não entrega o fluxo solicitado.
- Service account sem desenho de compartilhamento: não herda Drive pessoal por saber e-mail do owner.
- QR de passkey como “Drive autorizado”: identificação e consentimento de API são operações distintas.
- Trocar drive.readonly por drive.file sem prova recursiva: pode quebrar a biblioteca atual.

## Decisões ainda externas

Confirmar provedor/domínio/orçamento, projeto/client Google controlado pelo owner, política de consentimento e acesso, conta de teste, TV física, biblioteca/test assets e distribuição. Solicitar só o que for necessário no ticket correspondente; não solicitar valores secretos em texto aberto. Ainda não há deploy, banco de produção ou preço selecionado por este ADR.
