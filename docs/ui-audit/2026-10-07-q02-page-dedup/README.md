# Q02 — deduplicação em consultas paginadas do Drive

Em 07/10/2026, corrigi a acumulação de arquivos em `DriveRepository.getAllFilesDetailed`: páginas sobrepostas agora são deduplicadas por ID, mantendo a posição da primeira aparição e usando os metadados mais recentes recebidos. Isso evita que IDs repetidos consumam o limite de 2.500 entradas e que dados atualizados apareçam como duplicatas.

## Evidências

- `getAllFilesDetailed_overlappingPagesDeduplicatesAndKeepsLatestMetadata` passou: duas páginas sintéticas repetem `f2` com nome atualizado; o resultado contém `f1, f2, f3`, sem duplicatas, na ordem estável e completo.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL; 296 testes em 65 suítes, 0 falhas, 0 erros e 0 skips.
- APK arm64 instalado somente no pacote `zechs.drive.stream.visualreview` do Moto G34 (Android 15). Activity `VisualReviewActivity` abriu a fixture `files`; RIGHT×3 + DOWN deixou `Episode 08` com foco e visível após rolagem. O app permaneceu como Activity retomada.
- SHA-256 do APK: `24ADEAA144058212FDF2EC6E89D46525D71EB61259A003D630DB6BB2FB2956C7`.
- Capturas e hierarquias: [tela inicial Files](moto-files-fixture.png), [D-pad/rolagem](moto-files-dpad.png), [XML inicial](moto-files-fixture.xml), [XML após D-pad](moto-files-dpad.xml).

## Limites

A fixture é sintética e não chama o Drive; não foi usada conta OAuth, biblioteca real ou dado do usuário. Portanto, deduplicação com resposta real, recuperação após erro de rede, troca de pasta em requisição ativa e validação em Android TV/controle físico continuam pendentes. O smoke comprova apenas instalação isolada, apresentação da tela Files e foco/rolagem básica por D-pad no telefone.
## Q02 — contexto do cursor (mesma rodada)

Cada `FileCollectionResult` agora inclui `DriveQueryContext` não nulo com a consulta literal, page size efetivo (após o limite da API) e ordenação. Isso mantém os parâmetros associados ao `nextPageToken` tanto em resultado completo quanto parcial/erro; um consumidor pode continuar a enumeração sem reconstruir a consulta a partir de estado visual.

O teste de limite de páginas agora usa uma consulta de pasta, page size 12 e ordenação `name desc`, e confirma que o resultado parcial preserva exatamente os três parâmetros junto ao cursor. Suíte completa e build passaram novamente: 296 testes/65 suítes, 0 falhas/erros/skips. O APK atualizado foi reinstalado somente em `.visualreview`; a fixture Files abriu novamente no Moto G34 e a tela assentou após a animação de início.

APK atualizado SHA-256: `AD436380AE33E7CE8996EB2D1A38E3419D95AA957903E0812C1DBF9E4C1DA9CD`. Capturas finais: [Files após recompilação](moto-files-context-settled.png) e [hierarquia UI](moto-files-context-settled.xml).

Após essa reinstalação, RIGHT×3 + DOWN foi repetido no APK atualizado. UIAutomator confirmou `cardGridItem` focado em `[686,382][947,678]` e a Activity `.visualreview` permaneceu em primeiro plano; evidências: [captura após D-pad](moto-files-context-dpad.png) e [hierarquia](moto-files-context-dpad.xml).
