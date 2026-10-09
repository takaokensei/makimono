# Q02 — cancelamento e retomada de consultas Files

## Alteração coberta

Ao trocar a consulta de pasta, `FilesViewModel` cancela o pedido anterior, limpa cursor e lista e inicia a nova pasta desde a primeira página. Ao sair de Files com paginação ativa, cancela o pedido em andamento, remove o indicador de carregamento pendente e preserva os arquivos/cursor já recebidos. Ao voltar, a tela retoma a página interrompida com o mesmo cursor.

## Verificação automatizada

- `FilesViewModelQueryTest.changingFolderQueryCancelsOldRequestAndStartsAtFirstPage`: comprovou cancelamento do pedido da pasta antiga, primeira página sem cursor para a nova pasta e ausência de item antigo no resultado.
- `FilesViewModelQueryTest.leavingDuringNextPageKeepsLoadedFilesAndRetryUsesSameCursor`: comprovou preservação dos arquivos da primeira página, cancelamento da próxima, remoção do estado Loading e retomada pelo cursor `page-2`, sem duplicar o primeiro episódio.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: `BUILD SUCCESSFUL`; 298 testes em 66 suítes, sem falhas, erros ou skips.
- `git diff --check` nos arquivos desta tarefa: sem erros de whitespace.

## Smoke no dispositivo de QA

- Moto G34 Android 15, pacote isolado `zechs.drive.stream.visualreview`; APK arm64 `makimono-v1.6.4.3-arm64-v8a-debug.apk`, SHA-256 `4F5FF536EA743C53AD5E44B159ED4CC9DC6B40EADBAEF0CBF0D5FCDBCD87BD8C`.
- Abri a fixture Files e enviei RIGHT×3 + DOWN. UIAutomator encontrou `cardGridItem` focado em `[1223,382][1484,678]`; “Episódio 08” estava visível em `[1231,544][1476,620]`. A Activity `.visualreview` permaneceu em primeiro plano.
- Evidências: [tela após navegação D-pad](files-dpad.png), [hierarquia UI](files-dpad.xml), [tela inicial da fixture](files-start.png).

## Limites

A fixture Files não aciona o `FilesViewModel` de produção nem o Google Drive. Cancelamento e retomada são demonstrados por testes unitários com repositório suspenso; não foram exercitados com OAuth, rede, resposta real paginada ou back stack real no dispositivo. Android TV/controle remoto físico e retomada em modo de economia de energia seguem pendentes.
