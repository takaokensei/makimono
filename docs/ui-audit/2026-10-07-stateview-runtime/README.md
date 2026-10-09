# StateView runtime smoke — 2026-10-07

- Corrigi o limite de confiança das mensagens: `StateView.showState` exibe cópia localizada fornecida pelo chamador sem reclassificá-la; mensagens dinâmicas de `HomeState.Error` e erro do navegador de pastas passam por `sanitizeErrorMessage` antes de chegar à UI.
- O sanitizador agora converte detalhes de provedor não reconhecidos para uma mensagem genérica. Uma primeira execução do teste mostrou que a palavra literal `access_token` disparava incorretamente a mensagem de sessão expirada; a regra foi estreitada para sinais de autenticação (`auth`, HTTP 401/403 e `expired`), mantendo o conteúdo oculto sem diagnóstico falso.
- `testDebugUnitTest assembleDebug`: **288 testes, 63 suítes, 0 falhas/erros/skips**; build concluído.
- Instalei somente `zechs.drive.stream.visualreview` 1.6.4.3 (versionCode 55) no Moto G34 Android 15. A busca fixture sem resultados exibiu `Nenhum resultado`, o texto PT-BR e CTA focada `Limpar busca` após a mudança do componente.
- Evidência: `empty-search.png` e `empty-search.xml` nesta pasta. O APK gerado é debug-signed (não distribuir).
- Limite: a tela fixture valida texto estático e integração do StateView na busca; não injetei um erro real de Drive na Home/Picker. Não usei conta/arquivos Drive reais, não fiz release/commit/push. TV física e matrizes de resolução continuam pendentes.
