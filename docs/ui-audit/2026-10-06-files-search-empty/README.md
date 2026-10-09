# Busca sem resultados — QA local (06/10/2026)

A busca vazia em Arquivos usa o `StateView` compartilhado, com título, explicação e ação contextual **Limpar busca**. Em D-pad, DOWN a partir da busca chega à ação; UP retorna à busca; CENTER limpa a consulta, restaura o foco ao campo e volta a exibir a grade. O mesmo botão funciona por toque.

O `VisualReviewActivity` foi atualizado para reproduzir esse estado com arquivos sintéticos, em vez de usar a mensagem legada do `error_layout`. A produção e o fixture compartilham layout e strings, mas o fixture não executa `FilesFragment` nem acessa o Google Drive.

## Validação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: sucesso; 273 testes, 0 falhas, 0 erros, 0 ignorados.
- APK debug instalado no Moto G34 (`0086442783`, Android 15, tela 1600×720 em paisagem), pacote isolado `zechs.drive.stream.visualreview`.
- D-pad: consulta `ZZZ_NO_RESULT_91` → BACK fecha o teclado → DOWN foca `btnStateAction` → UP retorna a `etSearch` → DOWN/CENTER limpa a busca. XML confirma o foco em cada ponto e a grade restaurada.
- Toque: a ação também limpa a consulta e restaura a grade.
- `git diff --check`: sem erros de whitespace nos arquivos alterados; Git pode emitir avisos de normalização LF/CRLF do checkout Windows.

## Evidências

- `final-empty.png`: estado vazio com CTA.
- `action-final.xml`: DOWN foca `btnStateAction`; `search-final.xml`: UP retorna a `etSearch`.
- `final-cleared.png` / `final-cleared.xml`: grade restaurada e foco no campo após D-pad.
- `cleared-touch.png` / `cleared-touch.xml`: grade restaurada após toque.
- SHA-256 de `final-cleared.png`: `1DCD6A1122FBFBB02E77693359F7C3DEC8CCC92EE2B51D3704F44FD94F54BE95`.

## Limites

Este QA confirma compilação, hierarquia visual, ação e navegação do componente de estado em fixture local. Não confirma sessão/rede/árvore real do Drive, TalkBack, TV física ou a matriz 720p/1080p. Nenhum dado de conta foi acessado ou alterado; sem release, commit ou push.
