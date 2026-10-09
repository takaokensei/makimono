# Perfil — entrada e hierarquia de foco

Data: 07/10/2026. Validação feita no Moto G34 Android 15, em `zechs.drive.stream.visualreview` com fixture local; nenhuma conta, perfil ou dado Room foi editado.

`ProfilesAdapter` agora anima cada cartão uma única vez na primeira entrada, com deslocamento vertical curto, fade de 180 ms e atraso incremental de 40 ms por posição. A preferência de movimento reduzido mantém os cartões visíveis sem animação. Se um cartão sair da janela durante a animação, ele é normalizado para evitar reaparecer parcialmente transparente. O nome do perfil ativo ou focado usa peso bold; os demais permanecem em peso normal. A linha de nome no XML usa peso normal como base.

Em runtime, a fixture confirmou avatar circular com anel de foco ciano sem fundo retangular no cartão. D-pad RIGHT percorreu Cauã → Visitante → Kids/Shounen; DOWN alcançou `btnManageProfiles`; UP voltou ao perfil focado. A hierarquia registra foco no `cardProfileRoot` e depois no botão de gerenciamento. Capturas e árvores: `profiles-final.png/xml`, `profiles-right.png/xml`, `profiles-right2.xml`, `profiles-down.png/xml` e `profiles-up.xml`.

A duração/ordem do stagger está implementada no adapter, mas a captura foi feita depois da animação e não serve como prova visual frame a frame. Não foi homologado em TV física nem com TalkBack; a fixture usa perfis e imagens de teste.

Build forçado: `testDebugUnitTest assembleDebug --rerun-tasks --no-daemon --max-workers=1`, 288 testes em 63 suítes, 0 falhas/erros/skips. APK arm64 instalado somente no `.visualreview`; SHA-256 `64FB56D8EFD74F742F283D0A73BF84314C4545642E97BD07195EF50C15FD1197`.
