# Detalhes: status editorial e navegação contextual — 2026-10-07

## Correção

A tela de detalhes não deve interpretar todo status desconhecido como “Em exibição”. Agora `SeriesAiringStatusPolicy` reconhece valores conhecidos de MAL/AniList (completo, em exibição, futuro, hiato e cancelado). Valores vazios ou não reconhecidos não exibem badge nem criam metadado presumido.

## Verificação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: BUILD SUCCESSFUL; relatório atual contém 311 testes em 70 suítes, sem falhas/erros/skips.
- APK debug arm64 instalado somente no pacote isolado `zechs.drive.stream.visualreview` no Moto G34 Android 15.
- Fixture sintética de detalhes abriu sem crash. DOWN percorreu ação principal → temporada → primeiro episódio. Long-press abriu o menu contextual; DOWN destacou “Assistir episódio”; BACK fechou a camada e a hierarquia confirmou o foco restaurado em `cardEpisodeItem`.
- Capturas/hierarquias: `details-portrait.png/xml`, `details-focus-episode.png/xml`, `details-context-menu.png/xml`, `details-context-menu-dpad.png/xml` e `details-focus-restored.xml`.

## Limites

O status dos cartões da fixture é estático e não percorre a resposta real dos provedores; a regra de classificação e os valores suportados foram exercitados por testes unitários, e o binding Android foi compilado. Não houve login, chamada ao Drive, reprodução, persistência, teste em TV física ou TalkBack. A orientação usada pelo Moto é um viewport de QA, não homologação de hardware de TV.

## Artefato final desta rodada

- SHA-256 do APK arm64 após a segunda compilação: `39C323A0C0F7BC631FA063FD5E6C248633A1178A14F07FE406F5749AD47B8147`.
- Reteste pós-instalação: fixture reabriu; hierarquia final confirma `cardEpisodeItem` focado antes e depois do fechamento do menu por BACK.
