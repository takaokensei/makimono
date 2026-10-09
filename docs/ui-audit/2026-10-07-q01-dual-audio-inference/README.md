# Q01 — inferência explícita de áudio dual

## Defeito

A expressão usada nos nomes de arquivo aceitava `Dual` sem a palavra `Audio`. Um título de anime como `Dual! Parallel Trouble Adventure - 01.mkv` podia ser tratado como evidência de trilha dual.

## Correção

A pista agora exige `Dual Audio` ou `Dual Áudio`, com separador opcional e comparação Unicode sem distinção de caixa. A saída continua identificada como inferida pelo nome.

## Verificação

- Teste unitário novo: o nome do anime sem marcador de áudio não produz badge; `Dual Audio`, `Dual-Audio` e `Dual Áudio` são reconhecidos.
- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL, 295 testes em 65 suítes, 0 falhas/erros/skips.
- APK arm64 instalado somente no pacote `.visualreview` do Moto G34 Android 15; Activity de detalhes da fixture permaneceu em primeiro plano.
- APK: `app/build/outputs/apk/debug/makimono-v1.6.4.3-arm64-v8a-debug.apk`
- SHA-256: `357F5D32E544182156D22E913E85206959103C90EE7B178AABC3B7E702F298B5`

## Limites

O smoke visual usa conteúdo sintético e não prova os badges de uma pasta Drive real. A correção de semântica é provada pelo teste unitário da política, que é a mesma chamada pelo ViewModel de detalhes. Sem conta, arquivo ou progresso real; TV física e demais pistas de metadados continuam pendentes.
