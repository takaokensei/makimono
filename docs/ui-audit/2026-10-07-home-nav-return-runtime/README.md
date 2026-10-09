# Home: retorno do D-pad à navegação (07/10/2026)

## Cenário

APK Debug `.visualreview`, pacote `zechs.drive.stream.visualreview`, executado no Moto G34 com a tela em paisagem (1600×720 px). A `VisualReviewActivity` usa conteúdo sintético identificado pela marca d'água; não leu nem alterou conta Google, Drive ou histórico real.

## Resultado

Na fixture, DOWN percorreu da ação principal até a prateleira “Minha fila”; LEFT a partir do primeiro cartão retornou o foco a `btnNavInicio` (Início). A captura mostra o foco visual no item selecionado e a hierarquia XML confirma `focused="true"` em `btnNavInicio`. O foco de catálogo segue a regra geométrica de só retornar à rail quando LEFT parte da primeira coluna.

Foi corrigida a fixture para conectar os mesmos callbacks de LEFT usados pela Home integrada. A inspeção anterior que apontava foco em “Catálogo” vinha da ausência dos callbacks no harness; não era evidência de falha da navegação de produção.

## Evidências e validação

- `home-left-return.png` — estado após LEFT, rail Início focada.
- `home-left-return.xml` — dump UIAutomator correspondente.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` — BUILD SUCCESSFUL; 309 testes JVM, 0 falhas, 0 erros, 0 ignorados.

## Limites

Esta evidência valida o fixture e o desenho/foco no aparelho em paisagem. Não substitui controle remoto de Android TV física, TV 720p/1080p, nem teste da navegação com uma sessão Drive real. A compactação/expansão da sidebar sugerida pelo proprietário ainda não foi implementada nem aprovada visualmente.
