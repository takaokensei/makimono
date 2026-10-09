# Perfil — textos do editor e ações localizados

## Mudança

Os textos exibidos pelo editor e pelo menu de perfis agora vêm de recursos Android nos idiomas padrão e pt-BR. Isso inclui título e ações do editor, descrição acessível dos avatares, estados de busca da galeria, validação dos campos, confirmação/exclusão e erro de carregamento dos perfis.

## Validação

- `testDebugUnitTest assembleDebug --no-daemon --max-workers=1`: sucesso; 309 testes JVM, sem falhas.
- Instalei apenas `zechs.drive.stream.visualreview` no Moto G34 Android 15.
- Abri a fixture `screen=profiles` com `dialog=profile_editor`; a captura confirma rótulos em português, foco ciano no campo de busca e ações visíveis.
- Hierarquia UI confirma `Novo perfil`, `Escolha um avatar`, `Cancelar` e `Salvar`.
- `git diff --check` passou.

## Limites

A fixture não salva alterações e não valida persistência de perfil real. A galeria remota não foi usada como critério de aceite; sem seleção, não houve mudança de avatar ou wallpaper. Não testei TalkBack, idioma inglês em runtime nem TV física.

## Refinamento visual/localização do editor

Removi os emojis dos seletores de avatar e papel de parede; os botões agora usam rótulos curtos e recursos Android. Hints, papéis e estado inicial da galeria também vêm dos recursos padrão/PT-BR.

Na build seguinte, `testDebugUnitTest assembleDebug` passou novamente com 309 testes JVM e zero falhas. A tela atualizada foi instalada somente no `.visualreview` e inspecionada no Moto G34: “Avatares”, “Papéis de parede”, “Infantil”, busca, status e ações aparecem em português, sem emoji nos seletores. Captura: `smoke-editor-clean-tabs.png`; hierarquia: `smoke-editor-clean-tabs.xml`.

## Carrossel de avatar e navegação por D-pad

A inspeção no aparelho encontrou a causa do catálogo aparentemente vazio: os avatares eram carregados, mas a galeria era uma grade vertical longa no fim do conteúdo rolável. Mudei a apresentação para uma fileira horizontal de círculos, com wallpaper em formato panorâmico, e configurei o foco lateral para percorrer as opções em ordem e revelar o item focado. A identidade dos avatares não recebe mais um tile cinza ao redor.

Validei a nova build no Moto G34 Android 15 pela fixture `.visualreview`: no estado inicial, o diálogo mantém os campos no topo; ao navegar para baixo, a rolagem revela imagens reais do catálogo, e RIGHT percorre a fileira enquanto desloca o carrossel. Capturas do estado rolado e navegação: `profile-carousel-dpad.png` e `profile-carousel-right.png`; árvore: `profile-carousel-right.xml`. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 309 testes, sem falhas, e `git diff --check` passou.

Também validei a aba de wallpapers: a troca da categoria atualiza o rótulo e exibe cards panorâmicos; DOWN chega à fileira, RIGHT percorre e rola o item focado, e BACK cancela o diálogo sem alterar a lista sintética. Capturas: `profile-wallpaper-focused.png`, `profile-wallpaper-right.png` e `profile-after-cancel.png`.

Limites: essa fixture sintética não grava perfil, não testa TalkBack nem imagens sem rede; a galeria está abaixo da dobra ao abrir o diálogo e aparece ao avançar o foco/rolar. A validação é em um telefone usado em modo paisagem, não em TV física.

## Refinamento final do catálogo de arte (07/10/2026)

O badge ciano de seleção saiu da área circular recortada e agora fica em uma camada irmã do card; avatar e wallpaper exibem o check completo sem tile quadrado cinza. Reordenei o editor para mostrar as opções predefinidas antes da busca online e explicitei a navegação vertical: DOWN da aba focada entra no primeiro item; LEFT no primeiro item volta à aba; LEFT/RIGHT percorrem a fileira. A busca por catálogo fica logo depois dos cards. URLs diretas foram agrupadas em uma seção opcional expansível, com rótulos pt-BR.

No Moto G34 Android 15, reinstalei apenas `zechs.drive.stream.visualreview`. A árvore de acessibilidade confirmou foco e descrição “Avatar: Luffy (One Piece), selecionado”, e DOWN na aba de wallpapers focou “Papel de parede: Wallpaper · Frieren Beyond Journey's End”. Selecionei um avatar e um wallpaper na fixture e confirmei o check inteiro em `profile-selection-mark-final.png` e `profile-wallpaper-selected-final.png`; fechei sem salvar. A captura `profile-focus-art.png` registra a fileira limpa em foco. `testDebugUnitTest assembleDebug --no-daemon --max-workers=1` passou com 309 testes e `git diff --check` passou.

Limites: em telefone paisagem, a fileira pode começar parcialmente abaixo da dobra; o primeiro foco D-pad a revela e mantém título e opções visíveis. A validação é do harness sintético, não mede TV física em 720p/1080p. TalkBack e persistência real não foram testados.

## Revisão da tela de detalhes (07/10/2026)

A fixture de detalhes exibia pílulas técnicas apenas com ícones mesmo sem dados de mídia. Isso não representava o estado `Unknown` da tela de produção; o harness agora oculta a segunda linha técnica quando não possui especificações confirmadas ou inferidas. Retestei no Moto G34 Android 15: as pílulas vazias não aparecem (`series-detail-clean.png`) e a sequência DOWN alcança a temporada e depois o primeiro episódio (`series-detail-focus.xml`). Suíte JVM (309 testes) e `assembleDebug` passaram.

Limites: a tela de detalhes foi testada no harness com metadados estáticos; não usei Drive real, TalkBack nem TV física. O foco por D-pad foi validado na viewport do celular em paisagem, não em 720p/1080p.

## Seletor de perfis — verificação final (07/10/2026)

No seletor sem diálogo, os três avatares continuam circulares, sem moldura quadrada cinza; apenas o perfil em foco recebe contorno ciano. Na fixture do Moto G34, RIGHT percorreu Cauã → Visitante → Kids/Shounen → Adicionar perfil, e DOWN chegou a “Gerenciar perfis”. Captura e árvore: `profiles-final.png`, `profiles-final.xml`, `profiles-add-focus.xml` e `profiles-manage-focus.xml`. Não confirmei criação/edição para evitar persistir alterações da fixture.
