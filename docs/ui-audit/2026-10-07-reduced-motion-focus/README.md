# Movimento reduzido — foco de cards e superfícies (07/10/2026)

## Alteração

- A preferência de movimento do perfil ativo agora também alimenta callbacks síncronos de Views: o `FocusEngine` usa estado reduzido quando o perfil pede, o Android desativa animações ou o aparelho reporta pouca memória.
- `FrostedWindow` só aplica blur quando a janela o suporta e o perfil/sistema/dispositivo permitem. Caso contrário, usa superfície opaca para manter contraste sem depender de transparência.
- Ações de detalhes, tabs de temporada e cards do drawer de episódios deixaram de aplicar escalas próprias e passaram a usar o mesmo `FocusEngine`, eliminando animações que ignoravam a preferência.
- O indicador estático de foco permanece no modo reduzido; só escala, elevação e blur são removidos.
- A fixture de desenvolvimento aceita `reduced_motion_preview=true` para validar a renderização real do adapter sob esta política. Esse parâmetro existe somente em `src/debug`.

## Verificação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: **320 testes em 72 suítes**, sem falhas, erros ou skips.
- APK debug arm64 instalado somente como `zechs.drive.stream.visualreview`; SHA-256 `B06BA7EAD923ACD5D45F6C85C7159A4CD1E1EA343E70D44D9EE81E7DD8AAD685`.
- No Moto G34 Android 15, a fixture de detalhes manteve o anel ciano de foco no modo reduzido e não ampliou o card. `focus-default.png` e `focus-reduced.png` mostram os dois estados; hierarquia foi salva para o estado reduzido.
- RIGHT no modo reduzido moveu o foco ao card seguinte (`focused=true`) mantendo escala neutra; o dump confirma o rótulo acessível do próximo episódio. A fixture exercitou o adapter de produção com dados sintéticos.
- `MotionPolicyTest` cobre preferência reduzida, animações de sistema desativadas e bloqueio de blur em baixa memória.
- A preferência foi selecionada em Settings no perfil sintético `profile-1`; depois de encerrar e abrir o processo `.visualreview`, voltei ao seletor, reativei o mesmo perfil e confirmei “Reduzido” na interface. O perfil secundário continuou em “Seguir dispositivo”, confirmando que a opção é isolada por perfil.
- O reteste encontrou que a categoria “Aparência” exigia dois toques em touchscreen. Instalei `SingleTapActivation` nas categorias: um toque agora seleciona e exibe a seção. A ativação por tecla D-pad continua ligada ao `OnClickListener` original.
- Capturas pós-build e de persistência: `settings-appearance-fixed.png`, `motion-dialog.png`, `motion-selected.png`, `motion-persisted-profile1.png`; dumps UIAutomator correspondentes preservam os rótulos e bounds.
- Build final após essa correção: `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou; APK arm64 SHA-256 `FD763FE8AB7C7F772EEE4327E039A4E5C2AB710BC1F2D13715BCB30C152E21FB`.
- Auditoria adicional mostrou que bloquear o intercept do pai impedia arrastar a faixa horizontal de categorias. O helper agora permite a interceptação nessa faixa: no Moto, um swipe expôs “Dados e sessão” e um toque abriu seu conteúdo; um toque na categoria continua ativando. Evidência: `category-swipe.png/xml` e `category-data-selected.png/xml`.
- Rebuild/reteste após a correção de rolagem: 320 testes, 0 falhas/erros/skips; APK arm64 SHA-256 `0815E56FCB1CD675DE1A1E2E4DB277306A7EB18BDD76F31794EACE51DAF5B5C7` instalado apenas no `.visualreview`.
- `git diff --check`: OK. Nenhum dado da conta/Drive foi usado; o pacote principal não foi atualizado.

## Limites restantes

- O fluxo de persistência foi validado em um único perfil sintético e no mesmo aparelho. Ainda faltam rotação, reinstalação, migração de versão e testes multiusuário mais amplos.
- O controle visual foi feito em telefone de QA, não em Android TV. TV física e API anterior a 31 continuam sem validação específica. Uma tentativa de abrir a prévia de modal capturou apenas a HUD do player e não foi considerada evidência de blur.
- A leitura do estado de animações do sistema e o fallback de baixa memória têm teste unitário/política; não foram simulados alterando as configurações globais do Android.

Capturas e dumps desta rodada incluem `focus-default.png`, `focus-reduced.png`, `focus-reduced-right.png`, `settings-appearance-fixed.png`, `motion-selected.png`, `motion-persisted-profile1.png`, `category-swipe.png` e `category-data-selected.png`, além das hierarquias UIAutomator correspondentes.
