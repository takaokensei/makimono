# Detalhes — expansão acessível de sinopses longas (07/10/2026)

**Status:** implementado; compilação, testes unitários e fixture de revisão validados. A integração com dados reais e TV física segue pendente.

Quando a sinopse é realmente truncada em três linhas, a tela mostra “Ler sinopse completa”. O estado expandido troca o rótulo para “Recolher sinopse”; recolher restaura a posição anterior da rolagem. Textos curtos não exibem controle adicional, textos vazios ocultam a sinopse, e o foco D-pad liga o botão primário ao disclosure (UP/DOWN). A expansão rola o controle para uma área visível com margem inferior segura.

A implementação fica em `app/src/main/java/zechs/drive/stream/ui/series/SeriesSynopsisDisclosure.kt`; a política de truncamento tem teste unitário. Os layouts base e paisagem têm o controle localizado em pt-BR/valores padrão. A fixture `details_long_synopsis` usa texto sintético e não acessa Drive.

## Validação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: 313 testes, 0 falhas/erros/skips; build concluído.
- Moto G34 Android 15, pacote isolado `.visualreview`: UP a partir da ação principal foca “Ler sinopse completa”; CENTER expande mantendo o controle visível e focado; CENTER recolhe e restaura a área inicial; DOWN retorna à ação principal.
- Repeti a expansão/retração simulando 1920×1080 a 240 dpi (configuração restaurada ao final para 720×1600 a 280 dpi). Captura expandida: `tv1080-expanded.png`; recolhida: `tv1080-collapsed.png`. A 720p, o título comprido ocupa duas linhas e o botão de recolher permanece totalmente na área visível.
- APK debug arm64 SHA-256: `21110312978CE652DDF9B62ECEF4E2C59D411EE26770FB548FA8B8B622788F0A`.
- `git diff --check` sem erros; somente avisos de conversão de fim de linha em arquivos já modificados.
- O smoke inicial revelou que um toque apenas focava o disclosure. `SingleTapActivation` foi aplicado e, no APK final, um toque único expandiu a sinopse no Moto G34; evidência: `touch-single-after.png` e `touch-single-after.xml`.
- A fixture `details_synopsis_rebind` substitui a sinopse longa por curta enquanto o link está focado. Após o rebind, o link some e `btnPrimaryAction` está focado; XML/captura: `rebind-after.xml` e `rebind-after.png`.

## Limites

A tela de revisão reutiliza o layout e o helper reais, mas injeta conteúdo sintético; ainda falta verificar sinopses recebidas de MAL/AniList/Drive e TalkBack. A resolução 1080p foi simulada no telefone, não é homologação de TV física, controle remoto real, distância de 10 pés nem overscan.
