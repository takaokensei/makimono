# W3-T3.4 — textos da tela de detalhes em recursos localizados

A tela de detalhes e os cards de episódios mantinham rótulos estáticos diretamente no XML/Kotlin. Extraí breadcrumb, botões, título da seção, vazio/retry e badge de áudio para recursos, usando a mesma referência nos layouts retrato/paisagem e os estados dinâmicos de reproduzir/continuar, favorito/favoritado e seguir/seguindo no Fragment. O recurso existente de trailer e os rótulos de ação já disponíveis foram reaproveitados.

## Validação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: `BUILD SUCCESSFUL`; 298 testes em 66 suítes, sem falhas, erros ou skips.
- `git diff --check` nos layouts, Fragment e arquivos de strings: sem erros.
- Moto G34 Android 15, pacote `.visualreview`, APK arm64 SHA-256 `87CC91D46681910DA49FADDA806723D3F51B1CBCF2072D75E2851192158146A3`.
- A fixture de detalhes abriu com dados sintéticos. DOWN alcançou a aba de temporada e outro DOWN moveu o foco a `cardEpisodeItem`, bounds `[413,408][781,720]`; a Activity de revisão permaneceu em primeiro plano.
- Evidências: [detalhes ao abrir](details-start.png), [temporada focada](details-focus.png), [episódio focado](details-episode-focus.png) e [hierarquia após foco](details-episode-focus.xml).

## Limites

A fixture não valida a troca de idioma em um aparelho configurado em inglês, dados/metadados do Drive, retorno real de playback, TalkBack nem Android TV. A estrutura de strings e os dois qualifiers de layout compilam; ainda falta homologação em 720p/1080p de TV e escala de fonte ampliada.

### Complemento após a auditoria de rótulos

Também passei para recursos pt-BR as strings de estado da série, as descrições acessíveis que identificam metadados inferidos pelo nome do arquivo e os textos do diálogo de busca de episódio/número curto. A logo na variante paisagem agora referencia `@string/app_name`. Recompilei e reinstalei o novo APK no `.visualreview`; 298/66 testes continuam verdes e D-pad DOWN×2 deixou `cardEpisodeItem` visível e focado em `[413,408][781,720]`. Captura pós-build: [details-final-qa.png](details-final-qa.png), hierarquia: [details-final-qa.xml](details-final-qa.xml). SHA-256 atualizado: `87CC91D46681910DA49FADDA806723D3F51B1CBCF2072D75E2851192158146A3`.
