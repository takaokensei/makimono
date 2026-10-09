# Pareamento — smoke HTTP local (07/10/2026)

## Ambiente

- Serviço Ktor iniciado pelo Gradle no host de desenvolvimento, vinculado a `127.0.0.1:8097` e configurado com URL pública reservada `.invalid`.
- Store em memória; desafio sintético e descartável. Nenhuma conta Google, dispositivo do proprietário ou dado do Drive foi usado.
- Servidor encerrado após o smoke; a porta 8097 não permaneceu em estado `Listen`.

## Fluxo observado

1. `POST /v1/pairing/challenges` criou um desafio e retornou locator, código, segredo independente e expiração.
2. `GET /pair/{challengeId}` exibiu a prévia com o nome seguro do dispositivo e não incluiu o segredo de polling.
3. `POST /pair/lookup` com `application/x-www-form-urlencoded` encontrou o mesmo dispositivo sem ecoar o código nem o segredo.
4. Poll autenticado continuou em `pending` depois das duas prévias; nenhuma delas autorizou a TV.
5. `DELETE /v1/pairing/challenges/{id}` cancelou o desafio e o poll seguinte retornou `410 Gone`.

O script de smoke validou cada condição com assertions e imprimiu somente os resultados booleanos; locator, código e segredo não foram registrados na evidência.

## Build e teste

- `gradlew.bat -p backend test --rerun-tasks --no-daemon --max-workers=1`: repetido via `X:\` com `GRADLE_USER_HOME=G:\` após o runner falhar pelo caminho com acento. Resultado: **25 testes em 4 suítes, 0 falhas/erros/skips**; os 5 testes PostgreSQL Testcontainers executaram.
- A tentativa inicial com checkout no caminho acentuado falhou ao carregar `GradleWorkerMain`/classe de teste. Não foi contabilizada como falha funcional.
- `git diff --check` passou nos arquivos documentais desta atualização; Git reportou somente a normalização LF/CRLF configurada no checkout Windows.

## Limites

Isso valida o contrato local de criação, prévia, lookup, estado pendente e cancelamento. Não existe aprovação, OAuth, sessão Makimono, claim, refresh nem deployment; o serviço segue loopback-only e não deve ser exposto. Não valida navegador/celular real, fluxo de celular fora da rede, nem o cliente Android/TV.
