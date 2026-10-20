# Makimono TV — plano de qualidade visual

Data: 20/10/2026. Escopo: UI, direção de arte e acabamento visual do app Android TV. Avaliação ao vivo no AVD `MakimonoTVAscii`, API 36, resolução virtual 1920×1080, build `1.9.2` (`zechs.drive.stream.visualreview`, instalada em 19/10/2026 19:12). As imagens usadas nesta leitura vieram da janela do emulador atual, depois de iniciá-lo pela skill `android-tv-emulator`; capturas antigas não foram tratadas como estado presente.

## Veredito visual

O Makimono já tem uma direção visual reconhecível: fundo Obsidian, arte em tela cheia, hero editorial, rail lateral, ações grandes e telas com navegação por foco. A Home atual já transmite um produto de mídia e tem hierarquia básica clara. A distância para um acabamento de streaming premium está menos na falta de componentes e mais na consistência: os metadados chamam atenção com separadores vermelhos que não pertencem ao sistema Obsidian, estados sem dados continuam ocupando a composição como se fossem conteúdo editorial, e a mesma linguagem visual ainda precisa ser comprovada em perfis, detalhes e estados vazios.

Na captura atual de Detalhes, o título `Downloads_Diretos` aparece como um hero de obra, sem arte, sem valores de metadados e com placeholders visuais vazios. A lista abaixo também está vazia. O problema é visual e de apresentação de estado: o usuário vê uma tela incompleta em vez de uma resposta deliberada para uma pasta sem episódios. A regra deve ser não renderizar informação vazia, apresentar um estado vazio com hierarquia própria e oferecer uma ação útil para voltar ou escolher outro conteúdo.

O seletor de perfil atual tem composição simples e focalizada, com um perfil, criação de perfil e fundo imersivo. A Home atual tem título grande, sinopse em três linhas, duas ações e início de prateleira. Esses fundamentos são bons. O próximo nível depende de refinar tipografia, espaçamento, contraste e estados de foco sem acumular ornamentos, além de tratar conteúdo real imprevisível com elegância.

## Base de comparação e limites

- As evidências de Crunchyroll TV 3.61.2 contêm capturas de Home, rail, busca, perfis e detalhes. Elas permitem comparar hierarquia, escala, composição e navegação observáveis. São referência de decisões de produto, não um molde de assets, texto ou código.
- A instalação da Netflix analisada no dossiê é móvel, versão 9.88. Ela não sustenta conclusões sobre a estética, foco ou navegação da Netflix para TV. O plano não atribui esses padrões à Netflix TV.
- As capturas desta revisão atual são de seletor de perfis, Home e Detalhes. A navegação até Detalhes foi feita por D-pad. O ADB rejeitou `screencap` com `hasReadColorBufferDma`; a captura foi feita da janela do emulador em primeiro plano. Não foi uma inspeção completa de todas as telas.
- A pasta aberta em Detalhes não tinha dados de episódios, então esta observação não prova que todos os detalhes apresentam o mesmo problema. Ela comprova que o estado observado não tinha fallback visual adequado.

## Lacunas visuais observadas

| Área | Estado observado na build atual | Lacuna de qualidade | Resultado desejado |
|---|---|---|---|
| Home e hero | Arte imersiva, título amplo, sinopse, duas ações e primeira prateleira | A linha de metadados usa losangos vermelhos repetidos, competindo com o título e destoando do acento ciano do Obsidian; ainda falta validar títulos e sinopses longos | Uma linha tipográfica calma, com separadores discretos; título e sinopse sempre legíveis sobre artes claras e escuras; ações com uma prioridade visual inequívoca |
| Rail | Ícones e estado ativo visíveis | A captura mostra o item ativo com bloco cinza e filete branco, diferente do indicador ciano e da superfície leve descritos pelo Design System; a expansão e o foco não foram auditados nesta sessão | Estado selecionado persistente distinto do foco D-pad, um único foco visível, labels sem quebra/truncamento e transição curta e estável |
| Detalhes sem conteúdo | Hero preto, nome bruto `Downloads_Diretos`, metadados vazios, ações de série e “Nada por aqui” | A tela trata uma pasta sem metadados como obra editorial; mostra placeholders e ações sem contexto | Classificar pasta/filme/série antes de escolher o template; ocultar metadados inexistentes; fallback tipográfico de título; estado vazio que explica o que ocorreu e dá saída clara |
| Perfis | Fundo em tela cheia, pergunta central, avatar, criar perfil | A composição geral é focada; falta confirmar foco, legibilidade a distância e equilíbrio com muitos perfis ou nomes extensos | Avatares com escala generosa, foco ciano coerente, nome legível e grade que reequilibra sem apertar o usuário |
| Linguagem visual global | Base escura, superfícies e imagens fortes | O contraste entre o ciano canônico, cinzas, branco de botões e acentos vermelhos precisa ser normalizado; não aprovar cores por tela isoladamente | Tokens Obsidian aplicados de ponta a ponta, cor reservada para ação/foco/estado semântico, tipografia e raios compartilhados |

As observações são classificadas como **vistas na captura atual**. A associação do rail com o contrato do Design System é uma comparação direta entre pixels atuais e a especificação canônica. Problemas em telas que não foram capturadas são **itens a verificar**, não defeitos afirmados.

## Plano de execução

### V0 — Fixar uma baseline visual confiável

Antes de redesenhar, capturar a build instalada com resolução, densidade, versão e estado dos dados registrados. Preparar fixtures próprias para: Home preenchida, arte clara, título/sinopse longos, perfil único e múltiplos perfis, pasta vazia, filme sem duração conhecida, série com temporadas, histórico vazio/preenchido e falha de imagem. Guardar capturas da `VisualReviewActivity` e também percorrer os fluxos no AVD por D-pad. Usar a skill de Android TV para iniciar/verificar o AVD; cada tela deve ser identificada como fixture isolada ou jornada real.

**Aceite:** as capturas são da build atual e repetíveis; comparação visual não mistura telefone, mockup, screenshot histórico e execução presente.

### V1 — Unificar a gramática visual

Auditar Home, rail, seletor/editor de perfil, detalhes, histórico, busca, Downloads, Settings e player contra `docs/design/DESIGN.md`. Aplicar apenas fontes existentes (Outfit/Space Grotesk), tokens semânticos, margens seguras, escala tipográfica e raios de superfície consistentes. Remover separadores decorativos redundantes da linha de metadados e padronizar chips, ícones e badges. Distinguir visualmente foco, seleção persistente, pressionado e desabilitado.

**Aceite:** nenhuma tela inventa uma paleta ou estado de foco; nenhum metadado ausente gera símbolo ou chip vazio; texto de UI fica acima do piso tipográfico de 12sp e mantém contraste AA quando aplicável.

### V2 — Dar acabamento à Home e à navegação

Refinar composição do hero: proporção do título, largura máxima da sinopse, comprimento de linha, respiro entre metadados e ações e scrim direcional adaptável à arte. Fazer o rail retrátil parecer parte da mesma superfície, com largura e rótulos estáveis; desfocar/escurecer conteúdo ao abrir apenas se isso puder ser feito sem blur real pesado e sem esconder contexto. Refinar cards e prateleiras para ter ritmo visual consistente e foco com escala/sombra sutis.

**Aceite:** foco atual é sempre identificável; a arte continua sendo o ponto visual principal; primeira prateleira aparece com transição natural abaixo do hero; nenhum texto da rail quebra ou fica cortado em 1080p e 720p.

### V3 — Perfis e primeiro uso com intenção visual

Harmonizar seletor, criação e edição de perfil: entrada dos avatares em stagger sutil, expansão do avatar focado, nomes longos e ação “Novo perfil” equilibrados. Estados sem perfil e com um perfil devem parecer telas completas, não grades vazias. Preservar uma ação de saída acessível sem deixá-la competir com a escolha principal.

**Aceite:** controle remoto deixa evidente qual perfil está focado; foco não é confundido com edição; avatar e nome permanecem legíveis a 10-foot distance, inclusive com 6+ perfis.

### V4 — Refazer a composição de Detalhes por tipo de mídia

Criar modelos de apresentação próprios para filme, série/franquia, pasta sem mídia e erro de metadados. O hero de série deve usar arte e uma fita curta de dados; o de filme deve mostrar duração apenas quando conhecida; pastas vazias devem omitir nota, gêneros, badges e botões de série e apresentar uma explicação útil. Separar temporada, episódio e franquia sem repetir badges em cada card. Expandir sinopse no foco, com limite e animação controlados.

**Aceite:** nenhum placeholder vazio; nenhum nome de pasta cru com underscores em contexto editorial sem fallback; nota e duração correspondem ao item selecionado; cards de episódios têm alinhamento e proporção consistentes e foco único.

### V5 — Acabamento das telas secundárias e feedback

Aplicar o mesmo sistema a histórico, busca, downloads e settings. Revisar densidade e ritmo de cards no histórico, hierarquia de filtros e ações de remoção; criar estados vazios, carregamento, erro e confirmação com o mesmo vocabulário. Refinar OSD do player sem competir com a imagem.

**Aceite:** telas secundárias parecem partes do mesmo produto; mensagens explicam o próximo passo; nenhuma tela fica visualmente morta ou confusa em loading/erro/vazio.

### V6 — Movimento e homologação visual

Calibrar animações de foco e mudança do hero: resposta imediata ao D-pad, transições curtas, sem layout shift e respeitando redução de movimento. Validar com screenshots de estados focado/não focado, 1080p e 720p, overscan e sessão de controle remoto. Fazer revisão lado a lado com evidência identificada da Crunchyroll TV; usar o material Netflix apenas para princípios gerais comprovados em telas móveis, nunca como especificação TV.

**Aceite:** mudança de foco responde em até 100 ms; crossfade não deixa texto ou card tremendo; foco nunca se perde; todas as telas principais são utilizáveis e legíveis à distância.

## Ordem recomendada

V0 → V1 → V2 → V3 → V4 → V5 → V6. V1–V3 podem avançar enquanto os problemas de metadados do plano de lacunas são corrigidos; V4 depende da classificação confiável de tipo de mídia e metadados para que o refinamento visual não esconda informação errada. Falhas críticas de sessão, isolamento de perfil e reprodução continuam acompanhando o plano funcional, mas não substituem esta trilha de UI.

## Critério de qualidade percebida

O alvo não é reproduzir pixels ou ativos de um serviço comercial. O Makimono deve parecer deliberado e coeso em cada estado: conteúdo em primeiro plano, tipografia editorial, foco claro, movimentos discretos e respostas bonitas também quando faltam dados. A comparação com Crunchyroll serve para apontar oportunidades de composição e fluxo; a identidade Obsidian permanece própria. A comparação visual com Netflix TV fica pendente até existir evidência de uma variante TV.

