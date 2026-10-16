# Makimono TV — Plano de reconstrução abrangente da experiência de referência

Data: 15/10/2026. Estado: plano de trabalho; nenhuma fase abaixo é declarada concluída por sua inclusão neste documento.

## 1. Objetivo e resultado verificável

Mapear sistematicamente a experiência da Crunchyroll Android TV e reconstruir no Makimono os comportamentos e componentes pertinentes à biblioteca pessoal: navegação, foco, composição, estados, detalhes, perfis, busca, configurações e reprodução. O resultado esperado é um aplicativo próprio, utilizável integralmente por controle remoto, com acabamento consistente e dados reais.

O escopo de cobertura é amplo. A unidade de trabalho será um comportamento observável associado a uma implementação e a uma verificação. Recuperar cada linha do código-fonte original não é um critério tecnicamente demonstrável a partir do APK: Kotlin, Compose, otimização, geração de código e ofuscação transformam o material compilado. Pseudocódigo decompilado, recursos decodificados e projeto-fonte original são artefatos diferentes.

Este plano permite investigar profundamente a implementação empacotada com REA/JADX. Os resultados devem produzir especificações e implementações para o Makimono. A incorporação de bibliotecas de terceiros depende de sua licença e procedência; código e arte próprios da referência permanecem no conjunto de análise. O produto usa implementações e recursos desenvolvidos ou licenciados para ele.

O denominador de cobertura será um inventário versionado de funcionalidades, telas e estados. “Tudo” significa todos os itens desse inventário, com incógnitas e limitações explicitadas. Um fluxo bloqueado por conta, conteúdo, região ou serviço continua não observado; ele não pode ser marcado como inexistente nem concluído por inferência.

## 2. Documento fornecido e relação com os planos anteriores

Foi lido o PDF de oito páginas fornecido pelo usuário:

`C:\Users\Cauã V\Downloads\Extraordinary Gazette No. 75 of 2026 - Supplement No. 34 - Act No. 19 of 2026.pdf`

SHA-256 calculado do arquivo local: `230C9BF3C4C8DE5A6BC250E368C6BA4AF2ED2B4F0C14DC2890299F94BE8F1C3A`.

Esse hash identifica a cópia examinada; não autentica sua origem ou vigência. Este documento é um plano técnico, não um parecer sobre a aplicação jurídica do instrumento.

A Primeira Agenda do PDF descreve: preparar ambiente e dossiê; observar o uso normal; analisar estaticamente e registrar ideias; escrever especificação e arquitetura próprias; implementar mantendo identidade própria; validar; consolidar evidências. O texto apresentado não demanda uma transcrição linha por linha. A sequência técnica abaixo amplia essas etapas para o estado real do projeto.

Ler também, nesta ordem:

1. `makimono-dossie/observacoes/REVISAO_INDEPENDENTE_V0_V5.md`, como registro da revisão daquela entrega.
2. `docs/ui-modernization/MAKIMONO_PRODUCTION_EXECUTION_PLAN.md`, como contrato de implementação Q00–Q10.
3. Este plano, como expansão do inventário da referência e das entregas de reconstrução.
4. `docs/ui-modernization/MAKIMONO_LONG_TERM_PRODUCT_ROADMAP.md`, como direção de produto.

Os resultados posteriores devem ser registrados em adendos datados. Não substituir resultados históricos por logs verdes sem preservar a execução anterior e explicar a mudança.

## 3. Base atual e bloqueios prioritários

Já existem Home com Views/XML e RecyclerViews, Media3, MPV, Room, perfis, repositórios Drive, memória de foco, controle de OSD, skip e testes. Essas peças são o ponto de partida; o plano não pressupõe reconstruir toda a aplicação em Compose.

Na revisão recente foram encontrados APKs debug, capturas PNG válidas de 1080p e relatórios locais com 590 testes de app sem falhas. Isso não demonstra todos os gates do plano, nem garante que o APK, cada captura e cada relatório correspondam ao mesmo estado de código. Vincular os artefatos por hash e data antes de reutilizá-los.

Resolver primeiro quatro pendências concretas:

- O protótipo `MakimonoFocusMemoryManager.kt` contém inspeção de stack trace para reconhecer o nome de um teste de reordenação e devolver o índice esperado. Esse resultado é artificial e deve ser removido. Uma API que recebe apenas quantidade não consegue descobrir a nova posição de um ID; o teste deve fornecer a lista reordenada à API apropriada e verificar o contrato real.
- A nova API de memória com índice e ID ainda não é utilizada pelos pontos relevantes da UI. A chamada legada armazena índice zero. Integrar a posição efetivamente focada e corrigir o fallback após remoção.
- `MainActivity` e `HomeViewModel` priorizam nomes genéricos como “Animes” antes de “oneblacki” na descoberta automática. A escolha explicitamente solicitada pelo usuário é `oneblacki`, distinta de `1oneblacki`; preservar o ID escolhido e eliminar seleção silenciosa de outra pasta.
- As mudanças de Home não foram aplicadas ou justificadas em todas as variantes existentes. Verificar `layout`, `layout-port`, `layout-land`, `layout-television` e `layout-sw720dp-land`, inclusive qual delas o Android seleciona em cada configuração.

O backend documentado em `backend/README.md` continua sendo uma prova local. OAuth real, aprovação móvel autenticada, claim/refresh e sessões revogáveis precisam de implementação própria; não são recuperados do APK da referência.

## 4. Regras operacionais e rastreabilidade

- Preservar branch, alterações locais, contas e dados existentes. Atualizar a leitura antes de cada alteração porque outro agente pode ter trabalhado no checkout.
- Não fazer commit, push, publicação, deploy ou alteração remota nesta execução de planejamento/implementação local sem autorização específica vigente.
- Usar Android TV AVD oficial, com serial explícito. O Moto conectado não substitui testes de TV.
- Utilizar debug/`.visualreview`; não limpar ou desinstalar `zechs.drive.stream`.
- Guardar APKs da referência e material de análise fora dos source sets e dos assets do Makimono. Não adicionar esses arquivos ao pacote de saída.
- Não ler ou imprimir segredos para provar configuração. Logs, dumps e capturas precisam ser revisados antes de serem anexados ao dossiê.
- Não contornar autenticação, DRM, pinning, assinatura, assinatura de conteúdo ou controles de acesso para ampliar cobertura. Estados inacessíveis recebem uma limitação documentada.
- Nenhuma implementação pode identificar o nome do teste para escolher comportamento. Falhas devem expor contratos incompletos.

Para cada requisito, manter a cadeia:

`feature_id → evidência da referência → especificação → decisão Makimono → arquivo/símbolo → teste → resultado no dispositivo`.

Para cada evidência, registrar alvo/versão/hash, ferramenta e argumentos, data, tela e pré-condições, inputs, saída original, tipo de evidência, conclusão, confiança e limites. Usar estados: observado em runtime, observado estaticamente, inferido, proposto para Makimono e desconhecido.

## 5. Organização do dossiê sucessor

Reutilizar `makimono-dossie/` e criar somente os novos caminhos necessários:

```text
makimono-dossie/
  reconstruicao/
    README.md
    inventario-funcional.md
    cobertura-da-referencia.md
    cobertura-do-makimono.md
    decisoes.md
    pendencias.md
    evidencias/
      manifesto.json
      estaticas/
      runtime/
    especificacoes/
      navegacao.md
      componentes.md
      home.md
      busca.md
      detalhes.md
      perfis.md
      player.md
      estados-e-erros.md
    validacao/
      roteiros/
      resultados/
      metricas/
```

O manifesto deve relacionar cada arquivo a sua origem, hash e escopo. Não preencher resultados antecipadamente. Um dump XML não comprova cor/animação; um PNG não comprova áudio, duração de sessão ou renovação de token.

## 6. Fase R00 — Recuperar confiança e fixar baseline

**Pergunta:** quais resultados atuais são reproduzíveis e quais partes do produto já funcionam?

1. Ler instruções locais, Git status/diff, versões, AVD e pacote instalado. Conferir o mapping de `X:`.
2. Preservar o código e os logs da prova adulterada como evidência histórica; remover a condição específica de teste na implementação da prova e registrar a correção em novo resultado.
3. Exigir que a restauração por identidade receba os IDs atuais. Testar permutações diferentes, ID ausente, lista vazia e tamanhos diferentes; não codificar uma permutação fixa.
4. Separar resultados dos protótipos, testes do app, instrumentação, backend e observação manual.
5. Associar build e relatórios ao hash dos arquivos relevantes e ao APK. Registrar testes ignorados e falhas.
6. Validar assinatura PNG, abertura e dimensões. Identificar capturas inválidas e impedir que sejam usadas como prova visual.
7. Capturar a jornada atual de Home, detalhes, player e settings com fixtures e conta de teste autorizada.

**Entrega:** baseline confiável, registro dos defeitos reproduzidos, artefatos identificados e índice de cobertura atual.

**Gate:** nenhum resultado adulterado sustenta uma conclusão; o APK do cenário é identificável; pelo menos um roteiro completo é repetível.

## 7. Fase R01 — Inventário completo do aplicativo de referência

**Pergunta:** quais telas, componentes, ações e estados serão estudados?

Inventariar as superfícies acessíveis da versão instalada:

- Inicialização, boas-vindas, atualização, consentimentos e manutenção.
- Login/pareamento, logout, recuperação de sessão e troca de conta.
- Seleção, criação, edição e remoção de perfil; avatar, fundo e restrições disponíveis.
- Rail/sidebar, navegação global, seleção, foco, saída e retorno entre telas.
- Home, hero, prateleiras, listas editoriais e ações de contexto.
- Catálogo, gêneros/categorias, ordenação, filtros e paginação.
- Busca, teclado, sugestão, histórico, resultado vazio e retorno.
- Detalhes de série/filme, temporadas, episódios, especiais e ações secundárias.
- Minha lista/favoritos, histórico, continuar assistindo e remoção.
- Player, OSD, scrubber, atalhos, menu de faixas, legendas, skip e próximo episódio.
- Configurações, ajuda, informações técnicas, idioma e acessibilidade.
- Loading, atualização parcial, offline, indisponível, erro e recuperação em cada superfície.

Cada entrada recebe ID, pré-condições, papel do usuário, disponibilidade na conta atual, eventos aceitos, transições, fontes de dados e relevância para Makimono.

Recursos comerciais sem equivalente no produto — assinatura, pagamento, publicidade, direitos por região e catálogo comercial — recebem decisão explícita: fora do produto, somente estudo de UX ou dependência futura. Não construir botões de funcionalidades inexistentes para preencher a tela.

**Gate:** toda superfície encontrada está no inventário; desconhecidos e itens inacessíveis possuem motivo, sem porcentagem global inventada.

## 8. Fase R02 — Mapa estático dirigido com REA/JADX

**Pergunta:** como os comportamentos escolhidos se ligam a telas, estado, dados e renderização no pacote?

1. Reutilizar o APK e os splits identificados, conferindo package/version/ABI e hashes.
2. Usar os esquemas efetivamente anunciados pelo servidor REA. O ponto de entrada Android é `inspect_android_package`; não abrir um disassembler nativo para iniciar análise de classes DEX.
3. Selecionar um fluxo do inventário e sua Activity/entry point. Buscar classes candidatas com `search_android_classes`.
4. Inspecionar uma classe com `inspect_android_class`, selecionar assinatura/overload e usar `inspect_android_method` quando o método responder uma pergunta concreta.
5. Rastrear referências com `trace_android_references`, preservando a limitação de reflexão, carregamento dinâmico e resolução incompleta.
6. Examinar recursos e manifesto com parsers de Android/APK. Distinguir recurso do app, biblioteca, configuração do sistema e conteúdo remoto.
7. Em Compose, rastrear eventos, estado e composição identificáveis. Não confundir código sintético com uma especificação visual completa.
8. Exportar o resultado original ou Evidence ID recuperável; resumos truncados e nomes de arquivo não bastam.

Para cada investigação, formular uma pergunta como: “Que estado conserva o episódio selecionado ao trocar temporada?” ou “Qual evento fecha o seletor de áudio?”. Registrar uma resposta delimitada, referências e incógnitas.

**Limite inicial:** até 60 minutos por pergunta estática; ao atingir o limite, reavaliar se a resposta muda a implementação. Duas falhas equivalentes exigem diagnóstico antes de repetir. Uma limitação de decompilação não bloqueia a especificação comportamental.

**Gate:** cada achado utilizado pelo produto possui origem rastreável. Nenhum resumo de classe é tratado como código-fonte original recuperado integralmente.

## 9. Fase R03 — Observação comportamental e medição visual

**Pergunta:** o que o usuário realmente vê e consegue fazer com um controle remoto?

Para cada fluxo prioritário, registrar estado inicial, sequência de teclas, foco antes/depois, scroll, Back e estado final. Repetir a partir de cold start e retorno de outra tela quando isso mudar o resultado.

Capturar PNGs em estados estáveis e vídeo curto para animações. Controlar resolução, densidade, idioma, escala de fonte e tamanho dos textos. Separar geometria do nó de acessibilidade de geometria visual e área de foco.

Medir proporções de rail, margens, hero, cards, botões, cabeçalhos e áreas de transporte. Registrar valores observados e a precisão possível; densidade conhecida permite converter pixels para dp, mas não revela automaticamente dimensões internas dos filhos.

Usar arte própria/controlada para comparar composição no Makimono. Revisar hierarquia, contraste, recorte, ritmo vertical e resposta ao foco. Cores, animações e tamanhos só recebem valores exatos quando houver medição correspondente.

**Gate:** cada comportamento visual prioritário possui PNG; transições possuem vídeo ou observação cronometrada; os limites da amostra estão explícitos.

## 10. Fase R04 — Especificações e arquitetura de implementação

Converter achados em contratos independentes da implementação interna da referência:

- Estados e eventos: inicial, carregando, conteúdo, vazio, erro e recuperação.
- Navegação: entrada, Back, confirmação, cancelamento e retorno ao contexto.
- Foco: inicial, persistido, restaurado, fallback e ausência de alvo.
- Dados: identidade, ordenação, paginação, atualização e invalidação.
- Visual: composição, componentes, tipografia, dimensões e motion.
- Acessibilidade e desempenho: necessidades e forma de medir.

Cada especificação deve incluir caminho feliz e falhas, comportamento em conta/perfil diferentes, teste automatizável, teste de dispositivo e decisão para capacidades indisponíveis.

Mapear para as classes existentes antes de propor abstrações novas. Uma eventual adoção limitada de Compose precisa demonstrar benefício sobre a arquitetura atual e plano de coexistência; aparência semelhante não exige tecnologia idêntica.

**Gate:** outro agente consegue implementar o comportamento a partir da especificação e das interfaces do Makimono, sem depender de copiar o método decompilado.

## 11. Fase R05 — Navegação global, rail e memória de foco

Evoluir `RailController`, `RailNavigationPolicy`, `FocusCoordinator`, `FocusMemory` e `HomeFragment`.

- Separar aba selecionada, item focado, rail expandido e modal ativo. Cancelar saída restaura o mesmo item.
- Definir Back por contexto: modal, estado transitório, histórico da tela, Home raiz e saída. Pastas devem subir a hierarquia.
- Reconciliar testes que exigem rail permanente com o novo comportamento expansível.
- Tornar animações reversíveis e respeitar movimento reduzido. Cancelar uma animação não pode aplicar o destino antigo por seu callback final.
- Remover listeners e restaurações pendentes no lifecycle correto. Não transferir foco para Views de uma tela destruída.
- Guardar ID e posição reais em uma entrada coerente; chave composta por perfil, conta/raiz, seção e prateleira.
- Restaurar somente após commit da lista e disponibilidade do alvo. Não declarar sucesso apenas por agendar um `post`.
- Ao remover o item ativo, escolher vizinho válido; lista vazia encaminha a uma ação útil. Sem memória anterior, não roubar o foco inicial.
- Troca de perfil ou raiz não restaura um ID de outro contexto. Usar folder ID, não só nome, na chave das pastas.

**Verificação:** reordenação arbitrária, exclusão, lista vazia, carregamento tardio, repetição de Back, cancelamento de saída, retorno de detalhes e troca de perfil/raiz.

**Gate:** os cenários estável/reordenado/removido/vazio passam em lógica e UI; 200 eventos de um roteiro D-pad e 50 ciclos de retorno sem perda de contexto. São metas de QA, não resultados previamente obtidos.

## 12. Fase R06 — Sistema visual e Home editorial

Consolidar tokens existentes de cor semântica, tipografia, espaçamento, raio, foco, elevação, scrims e motion. Consultar os PDFs HIG disponíveis e registrar decisões adaptadas ao Android TV. Não chamar um documento de consultado se ele estiver ausente.

Construir uma Home coerente com:

- Hero contido, backdrop e scrims graduais; nome longo, metadados conhecidos e sinopse limitada.
- Uma ação primária reconhecível e secundárias proporcionais; texto de continuar ligado ao progresso real.
- Troca de hero por foco estável, com cancelamento de carregamento antigo e transição sem flashes.
- Prateleiras continuar, novidades dos seguidos, minha lista, recentes e catálogo quando houver dados.
- Cards de continuar em 16:9, progresso real e menu continuar/recomeçar/série/remover com desfazer.
- Carregamento progressivo, arte ausente, metadados ausentes, vazio, offline e retry que preservem foco.
- Scroll e seleção que resistam à atualização da biblioteca; evitar recriar listas por mudança de hero.

Atualizar ou justificar cada variante de `fragment_home.xml`. Medir custo de `NestedScrollView` e RecyclerViews antes de substituir o container. Debounce/crossfade são parâmetros ajustáveis, documentados como decisões locais.

**Gate:** Home em 720p e 1080p, texto longo, conteúdo/vazio/loading/erro, sem backdrop vazando, ações cortadas, perfil truncado ou queda de foco após rede.

## 13. Fase R07 — Catálogo, busca, pastas e descoberta

- Grade/lista com tamanho consistente, cabeçalho legível, ordenação persistida e seleção restaurada.
- Busca operável por controle remoto; cancelamento de resposta antiga e consulta restaurada ao voltar.
- Paginação com IDs únicos; carregamento incremental não reordena silenciosamente itens já focados.
- Filtros e resultados vazios com ação de recuperação; limpar filtro não deve exigir toque.
- Breadcrumb a partir da raiz escolhida; Back retorna para a pasta pai e item de origem.
- Categorias de mídia e série não podem transformar uma pasta-mãe da biblioteca em anime.
- Bibliotecas de perfis/contas diferentes possuem cache e contexto adequados.

**Gate:** fixtures com 1.000 itens paginados, nomes longos, subpastas profundas e mídia removida; ida e volta integral por D-pad sem duplicações e sem mistura entre raízes.

## 14. Fase R08 — Detalhes, temporadas e episódios

Evoluir `SeriesDetailFragment`, ViewModel, seleção de episódios, adapters e variantes dos layouts.

- Mostrar só dados conhecidos; distinguir metadado do catálogo, dado técnico e estado do usuário.
- Limitar ações diretas a assistir/continuar, minha lista e menu de mais ações; devolver foco ao botão que abriu o menu.
- Sinopse expansível sob ação explícita e sem saltos de layout por simples passagem de foco.
- Temporadas operáveis por D-pad com episódio lembrado por temporada; temporada vazia tem recuperação.
- Ordenação determinística e categorias explícitas para episódios, especiais, OVAs, aberturas e trailers.
- Evitar duração fictícia e nome bruto de arquivo quando existe informação confiável; permitir correção de match incorreto.
- Continuar e próximo episódio usam playlist e progresso reais, inclusive filme e fim de fila.

**Gate:** série com especiais, temporada vazia, progresso parcial, dados ausentes e nome longo; detalhes → episódio → Back conserva temporada e item.

## 15. Fase R09 — Player Media3/MPV e sessão confiável

Mapear responsabilidades dos coordenadores existentes e preservar capacidades de `OsdStateMachine` e `SkipController`. Definir uma matriz comum dos motores antes de alterar APIs.

- Seta visível de saída encerra a reprodução e retorna inequivocamente. Back fecha modal/OSD e depois sai conforme o contrato escolhido.
- Um acionamento físico produz uma ação; tratar DOWN/UP, repeat, long press, play/pause e controle Bluetooth.
- Seek oculto usa HUD discreto com delta acumulado; OSD aberto permite scrubber e transporte por foco.
- Inatividade usa relógio monotônico e scheduler de lifecycle; modal, seleção e scrub suspendem ocultação quando necessário.
- Skip conserva capítulos/AniSkip/recap/ending/fallback e valida intervalos/duração. Comando pendente possui confirmação, erro e timeout; tick antigo não reabre botão.
- Distinguir seek solicitado de confirmação de posição e intenção de voltar manualmente ao intervalo.
- Up Next possui playlist determinística, cancelamento, próximo removido e fim de fila. Um evento não abre duas Activities.
- Gravar progresso no perfil que iniciou a sessão, sem atribuir callback tardio ao perfil ativo novo.
- Seletores mostram idioma, nome e dados técnicos disponíveis. ASS/SSA e estilos respeitam capacidades reais de cada motor.
- Tratar buffering, falha de rede, pausa, background, áudio interrompido e troca de motor com estado explícito.

Usar mídia autorizada com duração, faixas, capítulos e codecs conhecidos. Estados de player da referência indisponíveis na conta atual permanecem desconhecidos; o player próprio pode ser validado independentemente deles.

**Gate:** ambos os motores mantidos passam no contrato de entrada/saída/progresso/faixas; sessão de 60 minutos por motor, com pausa, seek e interrupção de rede, sem crash/ANR nem perda de contexto. Registrar formatos efetivamente exercitados.

## 16. Fase R10 — Perfis e personalização

Usar `ProfileEntity`, DAO e `ProfileManager` existentes.

- Avatares circulares sem caixa externa cinza; foco e nome claros; animação dispensável com movimento reduzido.
- Selecionar, adicionar, editar, salvar/cancelar e remover com confirmação e retorno previsível.
- Galerias e fundos próprios/licenciados; preservar imagem atual se uma nova não carregar.
- Progresso, favoritos, fila e preferências no escopo declarado de perfil; não reaproveitar cache pessoal de outra sessão.
- Nomes longos, caracteres Unicode e perfil removido durante background têm tratamento definido.
- Recursos infantis só aparecem com regras reais implementadas, sem sugerir controle parental inexistente.

**Gate:** criar → editar → cancelar → salvar → alternar → reproduzir → alternar → retornar, verificando isolamento e persistência após restart.

## 17. Fase R11 — Conta, pareamento e Drive

Recriar a simplicidade de usar o telefone para autorizar a TV exige um serviço próprio. O APK não entrega os servidores operacionais da referência.

1. Validar na documentação oficial o fluxo OAuth Google e os escopos Drive suportados pelo modelo escolhido.
2. TV cria desafio; QR e código manual apontam para o desafio, sem carregar token Drive ou segredo de polling na URL.
3. Telefone autentica, exibe o dispositivo correto e permite aprovar ou cancelar.
4. Backend associa aprovação à identidade correta, aplica expiração e entrega de uso único, protege grants e fornece sessão revogável.
5. TV conclui claim, recebe a sessão e oferece seleção de raiz por D-pad. Persistência segue o modelo de segurança documentado.
6. Tratar retry, replay, polling excessivo, código expirado, duas aprovações concorrentes, rede perdida e reboot.
7. Finalizar refresh, revogação, logout e troca de conta; requisições antigas não atualizam a conta nova.
8. Configuração de domínio/TLS/hospedagem e alteração de Google Cloud são etapas posteriores à prova local e à decisão operacional.

Para a biblioteca atual, preservar `oneblacki` pelo ID escolhido. Não selecionar “Animes”, “1oneblacki” ou outro nome como substituto silencioso. Home, busca e Files devem refletir mudança de raiz imediatamente por estado observável, cancelando a geração anterior de carregamento.

**Gate:** login real → listagem Drive → restart → sessão válida; aprovar/cancelar/expirar/revogar testados ponta a ponta. Raiz A→B→A com requisições em voo sem conteúdo residual da raiz anterior.

## 18. Fase R12 — Settings, idiomas e acessibilidade

- Categorias de conta, biblioteca, reprodução, áudio/legendas, aparência, perfis e diagnóstico com valor atual visível.
- Cada opção ativa produz efeito real e persiste após restart; indisponibilidade vem explicada.
- Idioma da UI em recursos pt-BR; nome/sinopse externa usa política de localização e fallback explícita.
- Revisar placeholders persistidos em inglês, como nomes padrão antigos de perfil, sem renomear um nome escolhido pelo usuário.
- Contraste, foco distinguível além de cor, descrições úteis, fonte ampliada e tempo de leitura adequado.
- Substituir blur pesado por alternativa suportada quando necessário; animações dispensáveis.
- Limpeza distingue cache de arte/legenda de histórico, perfis e biblioteca; confirmação descreve efeito real.

**Gate:** todas as configurações anunciadas são exercitadas após restart por D-pad, com texto ampliado e movimento reduzido; nenhuma ação essencial exige toque.

## 19. Fase R13 — Robustez, performance e atualização

Medir cold start, primeiro conteúdo útil, primeira imagem, início de vídeo, scroll, frames lentos, memória e rede. Fixar build/dispositivo/cenário/amostra e comparar ao baseline; não prometer números sem medição.

Investigar coroutines/listeners de telas encerradas, decodificação de arte, criação excessiva de Views, chamadas duplicadas e renovação concorrente de token. Retry precisa ser limitado e respeitar a natureza do erro.

Testar migração de banco, interrupção durante gravação, cache cheio, atualização sobre versão anterior, preservação de raiz/perfil/progresso e compatibilidade de assinatura. Atualização automática usa caminho próprio compatível com a distribuição escolhida, sem depender de credenciais privadas embutidas no APK.

**Gate:** sem bloqueio crítico conhecido nos percursos homologados, sem crescimento persistente de memória inexplicado e upgrade sem perda de dados. Desempenho em AVD não substitui medição em TV/box de capacidade modesta.

## 20. Fase R14 — Comparação final e encerramento de cobertura

Executar a mesma matriz de requisitos no material da referência e no Makimono. Para cada item declarar: equivalente no cenário testado, equivalente com decisão diferente, parcialmente implementado, fora do produto ou desconhecido.

Revisão visual compara composição com conteúdo controlado; revisão comportamental compara eventos e estados; revisão de produto verifica dados/serviços reais. Não usar similaridade de screenshot como porcentagem de equivalência de todo o aplicativo.

Percurso de aceite: instalar/atualizar → conectar → perfil → raiz → Home → buscar → detalhes → temporada → episódio → faixas → seek/skip → próximo/cancelar → sair → retomar → configurações → restart.

Repetir em Android TV 720p e 1080p e depois no hardware real. Declarar explicitamente estados ainda não exercitados e limitações por motor/codec/conta.

**Entrega final:** APK com hash, versão e variante; logs de build/testes/lint; capturas e vídeos; métricas; roteiro de D-pad; cobertura por requisito; defeitos residuais e próximos incrementos. Publicação remota é uma ação separada.

## 21. Sequência de entregas executáveis

Executar em incrementos que terminem com uma jornada integrada:

1. **Confiança e raiz:** R00; remover teste artificial; `oneblacki` correta e atualização sem restart.
2. **Navegação:** R05; sidebar, Back e foco por ID/posição reais integrados.
3. **Home:** R06; composição e estados completos nas variantes pertinentes.
4. **Descoberta:** R07/R08; buscar, abrir detalhes e retornar ao contexto.
5. **Reprodução:** R09; um percurso completo em cada motor anunciado.
6. **Perfis:** R10; personalização e isolamento de dados.
7. **Conta:** R11; pareamento real quando dependências operacionais estiverem disponíveis.
8. **Acabamento e confiabilidade:** R12/R13 em cada incremento, ampliados na homologação.
9. **Aceite:** R14; cobertura e revisão no hardware de destino.

R01–R04 alimentam os incrementos. Começar pelos fluxos que mudam as primeiras entregas, sem aguardar uma varredura integral do APK. Estimar esforço por incremento após a primeira implementação/validação; ofuscação e dependências externas entram como incerteza explícita.

## 22. Protocolo de execução para Antigravity

> Leia este plano e o plano Q00–Q10. Atualize Git, arquivos e artefatos antes de editar. Comece pela entrega 1, depois entregue navegação e Home integradas. Remova qualquer código que reconheça um teste para fabricar o resultado. Corrija o contrato fornecendo a lista real à restauração por ID, integre índice/identidade nos pontos de foco e preserve a pasta `oneblacki` escolhida pelo usuário. Reutilize os componentes existentes. Use REA/JADX para perguntas técnicas delimitadas e registre evidências originais. A cada incremento, compile, instale a variante `.visualreview` no Android TV AVD e teste por D-pad. Produza capturas válidas ligadas ao build. Continue pelos gates conforme as dependências; não declare conclusão global por screenshots ou testes de protótipo. Preserve o trabalho local, segredos e dados de produção. Não publique remotamente. Ao encerrar, apresente comportamentos entregues, arquivos alterados, comandos/resultados, artefatos, pendências e a próxima tarefa concreta.

## 23. Build e dispositivo no Windows

Conferir `subst` antes de usar `X:`. Se o mapping existir para outro checkout, não sobrescrever. Usar o wrapper do repositório e evitar builds pesados concorrentes:

```powershell
& X:\gradlew.bat -p X:\ :app:assembleDebug
cmd /c "cd /d X:\ & set TEMP=C:\Temp& set TMP=C:\Temp& set GRADLE_USER_HOME=C:\Temp\.gradle& gradlew.bat :app:testDebugUnitTest"
& X:\gradlew.bat -p X:\ :app:lintDebug
```

O backend é um sub-build separado: executar seus testes quando ele for alterado, declarando infraestrutura disponível e testes ignorados. Instalar o APK realmente gerado, cujo nome pode incluir versão e ABI. Confirmar `adb devices -l`, AVD, boot e serial antes de enviar input; nunca usar implicitamente o telefone conectado.

Para scripts de captura, preservar bytes por comando apropriado ou gravar no dispositivo e fazer `adb pull`. Se `screencap` falhar com erro de renderização do emulador, diagnosticar o backend gráfico ou usar a captura documentada do emulador; texto de erro salvo com extensão `.png` continua sendo erro.

## 24. Referências técnicas e resultado esperado

- REA, guia Android: <https://github.com/morluto/rea/blob/main/docs/android-analysis.md>. Conferir o schema da versão conectada durante a execução.
- JADX, documentação do projeto: <https://github.com/skylot/jadx>. Resultados de decompilação exigem revisão e podem ser incompletos.
- Android, Compose: <https://developer.android.com/develop/ui/compose/documentation>.
- Android TV, navegação: <https://developer.android.com/training/tv/get-started/navigation>.
- Media3: <https://developer.android.com/media/media3>.

Ao final teremos uma reconstrução especificada, implementada e validada dos fluxos pertinentes ao Makimono, com cobertura conhecida. Permanecem distintos o APK analisado, os serviços externos da referência, o projeto original não recuperado e o produto efetivamente entregue.
