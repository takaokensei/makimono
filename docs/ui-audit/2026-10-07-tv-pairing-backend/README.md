# Pareamento TV — auditoria da fatia backend (07/10/2026)

## Resultado atual

- `backend/` compila e `gradlew.bat -p backend test --no-daemon --max-workers=1` concluiu com `BUILD SUCCESSFUL`.
- Relatórios: 4 suítes, 18 testes, 0 falhas, 0 erros, 0 skips. Inclui testes PostgreSQL/Testcontainers.
- Rotas verificadas em `backend/src/main/kotlin/zechs/drive/stream/backend/Application.kt`: `POST /v1/pairing/challenges`, `GET /v1/pairing/challenges/{id}/status` e `DELETE /v1/pairing/challenges/{id}`.
- O processo padrão continua amarrado a `127.0.0.1`; URL de verificação `https://pairing.invalid`. PostgreSQL persistente é opção de desenvolvimento, não implantação autenticada pública.

## O que a prova não faz

Não há endpoint/site para aprovar via celular ou claim da TV; não existe OAuth Google server-side, grant cifrado, sessão Makimono/refresh/revogação, API de biblioteca Drive nem proxy de bytes. Portanto uma TV não consegue receber sessão e o QR não pode autenticar. O app Android ainda não deve apontar para essa API. Estes limites também constam na ADR-001 e no README do backend.

## Gate externo para o fluxo real

Antes de integrar o frontend de TV/celular, faltam projeto OAuth controlado pelo proprietário e redirect allowlist, domínio/HTTPS/hosting, PostgreSQL operacional com secrets isolados, política de owner/grant, sessão e armazenamento cifrado, fluxo web mobile com CSRF/state, endpoints de confirmar/claim/refresh/revoke e validações/observabilidade. Após isso, ainda é necessário o E2E celular↔TV em redes distintas e stream seekable. Não use credenciais reais em fixtures.

## Escopo executado

Somente testes locais do backend; nenhum servidor foi exposto/iniciado, nenhuma conta Google ou dado Drive foi acessado, nenhuma credencial solicitada e nenhuma alteração de plano/arquitetura feita.
