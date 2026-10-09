# Menu contextual de episódios — retorno do foco

## Cenário

- Dispositivo: Moto G34, Android 15, 1600×720 em landscape, 280 dpi.
- Pacote: `zechs.drive.stream.visualreview`, `VisualReviewActivity` com a tela sintética `details_episode_focus`.
- A fixture de QA chama o mesmo `SeriesEpisodeContextMenu` compartilhado pela tela real; nenhuma conta, progresso ou arquivo do Drive foi lido ou alterado.

## Resultado observado

1. Long-press no primeiro card abre o diálogo de ações; `menu-open.png/xml`.
2. D-pad DOWN destaca “Assistir episódio”; `menu-dpad.png/xml`.
3. BACK fecha o diálogo, mantém a Activity e restaura foco ao `cardEpisodeItem` que o invocou; `repro-back.xml` registra `focused=true` no card `[413,408][781,720]`.

A saída para a launcher observada no primeiro teste ocorreu após reiniciar a Activity com `am start -S`; não foi reproduzida no reteste isolado sem forçar o stop.

## Build e testes

`testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL; 294 testes em 65 suítes, 0 falhas, 0 erros, 0 ignorados. `git diff --check` sem erros de whitespace após normalizar a linha final do log.

APK debug arm64: `app/build/outputs/apk/debug/makimono-v1.6.4.3-arm64-v8a-debug.apk`
SHA-256: `09FD727BCDB1F80F037E6E40C7BDB2FBEF562453414FAE0328ADD03978FAC7FD`

## Limites

A prova cobre abertura por long-press e cancelamento por BACK em fixture no telefone. Não cobre popup em TV física, TalkBack, seleção de ações reais, playback ou persistência do progresso.
