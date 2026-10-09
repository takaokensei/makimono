# Revisão visual — 22/09/2026

Base relida antes de continuar: `7163362` (v1.5.7), com árvore de trabalho limpa. Foram preservadas as alterações de galeria/editor, foco direto nos cartões, autenticação e expiração de tokens feitas entre as sessões.

Esta rodada corrige o cabeçalho duplicado do catálogo, a altura ocupada pela Home, o texto de Configurações na barra lateral e as alturas dos cartões de perfil sem selo. O catálogo horizontal usa recorte compacto das mesmas imagens remotas; Pastas mantém vídeos em 16:9. Os destinos verticais de foco da Home acompanham a visibilidade das prateleiras. O anel de foco também reconhece o foco inicial e evita instalação duplicada na mesma raiz.

O teste do player encontrou sobreposição em 360dp após a introdução das margens do painel. A distribuição agora considera a largura interna: três faixas em larguras estreitas, duas em intermediárias e uma em TV. O teste reutiliza a mesma View em 960 → 360 → 640 → 960dp para cobrir mudanças de configuração e verifica sobreposição e cortes horizontais.

Validação: 145 testes JVM passaram; 3 testes instrumentados passaram (Room e layout do player). A configuração de assets dos testes foi corrigida para incluir os schemas Room existentes. APKs debug e de instrumentação compilados.

## Capturas

- [Home](home.png)
- [Perfis](profiles.png)
- [Detalhes](details.png)
- [Pastas/arquivos](files.png)
- [Player](player.png)

Capturas do emulador Android em 1920×1080, densidade 320, com XML de produção. A Activity `VisualReviewActivity` existe apenas em debug e usa dados de teste, sem gravar no Room ou alterar a biblioteca do usuário. As imagens de teste são carregadas por URL e repetidas propositalmente; não representam diversidade do catálogo nem autenticação no Drive. O player dessa prévia não reproduz vídeo.

Ainda falta validar em TV física, com biblioteca autenticada, títulos longos, fontes ampliadas, capítulos/trilhas reais e reprodução contínua. Estas capturas não comprovam equivalência completa aos mockups nem paridade funcional de todos os menus dos dois motores de vídeo.
