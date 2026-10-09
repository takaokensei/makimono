# Rótulos acessíveis nos cards de episódio

Data: 07/10/2026  
Dispositivo: Moto G34 Android 15, pacote isolado `.visualreview`.  
APK final: Debug arm64; SHA-256 `CA5A511656B89CDDB43D4D70ED9C426D90E1D6DF1A812A7A5442E1913742FC91`.

## Implementação

O card clicável anuncia “Reproduzir [título]” e acrescenta duração, áudio e progresso apenas quando esses valores são conhecidos. Progresso entre 1 e 94% anuncia “Em andamento”; 95% ou mais anuncia “Assistido”, seguindo o limiar já usado no fluxo de detalhes. Valores vazios não deixam separadores soltos. A lógica de composição tem testes unitários e os rótulos estão em inglês base e pt-BR.

## Verificação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: build passou; 317 testes em 71 suítes, sem falhas, erros ou skips.
- Instalei somente o APK `.visualreview`. A fixture real do `SeriesDetailEpisodeAdapter` expôs no dump `Reproduzir O fim da viagem, 24 min`; RIGHT moveu o foco e anunciou `Reproduzir A magia da memória, 24 min`. A descrição longa continuou visível apenas no primeiro card focado.
- A captura final mostra o anel de foco e a escala/elevação aplicados pelo `FocusEngine`. Corrigi o listener da descrição que sobrescrevia a animação do mecanismo central de foco. Captura: `focused-final.png`; hierarquias: `focused-final.xml` e `next-focused-final.xml`.
- Capturas e hierarquias anteriores: `../2026-10-07-episode-card-accessibility-focused.png`, `../2026-10-07-episode-card-accessibility-focused.xml` e `../2026-10-07-episode-card-accessibility-next.xml`.

O dump confirma o conteúdo exposto ao serviço de acessibilidade, não substitui teste com TalkBack nem TV física. Os dados da tela são sintéticos; nenhum Drive real foi acessado.
