# Files: estados de erro do Google Drive

## Mudança

Falhas de Drive agora carregam uma categoria estável (`AUTHENTICATION`, `ACCESS_DENIED`, `RATE_LIMITED`, `NOT_FOUND`, `NETWORK`, `SERVER` ou `UNKNOWN`) de `DriveRepository` até `FilesFragment`. A UI deixa de inferir login apenas pelo texto e apresenta recuperação distinta: conectar, voltar da pasta sem permissão/removida ou tentar novamente em falhas transitórias. O motivo do erro do Google é lido apenas para distinguir cota/limite; o payload bruto não é exibido. O snackbar técnico duplicado foi removido e o skeleton é encerrado ao mostrar erro.

A navegação hierárquica também preserva os IDs ancestrais. Um clique em pasta ou atalho cujo destino já está no caminho atual é interrompido com aviso, em vez de criar níveis repetidos na pilha de navegação. A entrada da Home registra a pasta raiz configurada como primeiro ancestral.

## Validação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: **334 testes em 75 suítes, 0 falhas, 0 erros, 0 skips**.
- Instalei o APK arm64 de prévia no Moto G34 (`0086442783`), pacote isolado `zechs.drive.stream.visualreview`.
- A fixture `files_access_denied` renderizou o componente de estado da tela Files em 1600×720. UIAutomator confirmou “Sem acesso a esta pasta”, a instrução sem pedido de reconexão e o botão “Voltar” focado. OK acionou a ação da fixture e fechou a Activity sem crash.
- Captura: [moto-files-access-denied.png](moto-files-access-denied.png). Hierarquia: [moto-files-access-denied.xml](moto-files-access-denied.xml).
- Política de navegação tem testes para raiz extraída da query, destino ancestral repetido, extensão de caminho e IDs escapados.
- SHA-256 do APK arm64: `33CA94EE93A4575560326294E85233C11A8033EAB6DF3685E5BDD956A5FCE97D`.

## Limites

O teste de interface usa fixture local e não simulou uma resposta 403 real da rede dentro da navegação autenticada; classificação HTTP, preservação de `errorKind` no repositório e mapeamento da tela foram cobertos por testes JVM. A prevenção de ciclos foi validada pela política unitária e pelo build, sem dirigir um Drive com atalhos reais. Sem login, leitura ou alteração de dados reais do Drive. Q02 continua IN_PROGRESS: paginação com Drive autenticado, 2.501 itens reais, TV física, TalkBack e cenários de cota/rede ao vivo ainda precisam de homologação.
