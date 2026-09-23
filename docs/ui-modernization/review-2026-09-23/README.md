# Revisão visual — 23/09/2026

Complementa a revisão anterior e substitui o recorte quadrado do catálogo por pôsteres 2:3 com títulos externos. O destaque passa a medir a altura pelo conteúdo. O avatar deixa de receber tintura ciano. A ordenação por nome é persistida localmente.

Capturas: [Home com título longo e gêneros](home.png), [catálogo após rolagem](catalog.png). Emulador 1920×1080, densidade 320; imagens remotas repetidas e dados de teste explicitamente identificados. Não são capturas de uma biblioteca autenticada.

Validação: 145 testes JVM e 4 instrumentados passaram. O teste do destaque preenche título longo, título nativo, gêneros e sinopse e verifica que ambos os botões ficam dentro do card. O teste do player cobre 960 → 360 → 640 → 960dp. Release R8 nas quatro arquiteturas; x86_64 instalado e iniciado com sucesso. Assinatura conferida contra o APK publicado v1.5.7.

Ainda pendentes: TV física, autenticação/reprodução de ponta a ponta, fontes ampliadas e refinamento completo de detalhes, configurações e fidelidade aos mockups.
