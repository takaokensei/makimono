# Makimono TV — Roadmap de produto e qualidade de longo prazo

**Avaliação atualizada — 20/10/2026:** [Plano de qualidade visual](MAKIMONO_VISUAL_QUALITY_PLAN_2026-10-20.md) prioriza direção de arte, tipografia, foco e apresentação dos estados vazios, com observações feitas no emulador TV atual. O [plano de lacunas funcionais](MAKIMONO_REFERENCE_GAP_PLAN_2026-10-19.md) cobre dados, jornadas, downloads, reprodução e validação. As evidências Netflix disponíveis são móveis; não comprovam paridade visual com a versão TV. As observações históricas abaixo permanecem datadas.

**Plano complementar — 15/10/2026:** [Reconstrução abrangente da experiência de referência](PLANO_RECONSTRUCAO_ABRANGENTE_CRUNCHYROLL_TV.md) detalha R00–R14, o inventário de funcionalidades, a investigação dirigida por REA/JADX, a rastreabilidade e os critérios de integração/homologação. Deve ser executado junto ao plano Q00–Q10, começando pela correção dos resultados artificiais de teste e das lacunas de raiz/foco. A inclusão do plano não declara essas tarefas concluídas.

## Objetivo

Evoluir o Makimono para um produto de entretenimento doméstico polido, confiável e fácil de usar com controle remoto, com qualidade percebida comparável à de serviços comerciais maduros. “Nível Netflix/Crunchyroll/Apple” é uma ambição de qualidade, não uma promessa de equivalência ou de reprodução de implementação proprietária. O resultado deve ser um produto Makimono, com identidade visual própria, baseado em problemas observados, requisitos verificáveis e código desenvolvido para este projeto.

O plano cobre experiência, comportamento, arquitetura, integração com Drive, reprodução, acessibilidade, segurança, testes e operação. Ele é incremental: nenhuma onda é considerada concluída por ter código escrito ou uma compilação verde; cada gate exige evidência no dispositivo de destino.

**Revisão de direção — 14/10/2026:** o objetivo técnico da reconstrução é aproximar a experiência observável: composição das telas, hierarquia, proporções, transições, regras de foco e fluxos. Recuperar o projeto original da Crunchyroll, recompilar o APK decompilado ou reproduzir sua infraestrutura de serviço não é uma dependência deste plano. A investigação deve gerar decisões e componentes executáveis no Makimono. O padrão de chegada é uma jornada completa, polida e confiável no escopo da biblioteca pessoal.

**Próxima auditoria delimitada:** executar `makimono-dossie/PLANO_VEREDITO_TECNICO_CRUNCHYROLL.md` para obter um veredito por componente sobre recuperação, reprodução e integração, com três provas prioritárias. Esse plano responde o que o APK e a execução realmente oferecem antes de ampliar o investimento em investigação. O roadmap abaixo organiza a transformação dos resultados em produto.

**Após a entrega V0–V5:** ler `makimono-dossie/observacoes/REVISAO_INDEPENDENTE_V0_V5.md` e executar `docs/ui-modernization/MAKIMONO_PRODUCTION_EXECUTION_PLAN.md`. Foram conferidos doze dumps e compiladas as provas: 15 testes originais passaram, mas quatro novos contratos de integração falharam. As provas ainda não são UI integrada nem validação do player. O plano sucessor organiza Q00–Q10, corrigindo essas lacunas antes de declarar qualidade comercial.

## Diagnóstico atual: o que temos e o que ainda não temos

Esta revisão examinou o dossiê, o registro de execução fornecido e trechos do código-fonte local. Não executou uma nova sessão completa de TV, build, benchmark ou suíte de testes. Nenhum problema histórico de runtime é declarado resolvido ou ainda reproduzido apenas com base nesta leitura.

### Evidência já disponível

- Existem três APKs em `makimono-dossie/apk/`; seus tamanhos e SHA-256 conferem com `datas.md`. Isso confirma a identidade dos arquivos locais comparados ao registro, não comprova por si só assinatura, procedência oficial ou comportamentos de UI.
- Existem seis notas por subsistema. O registro colado mostra extração via ADB, metadados com AAPT e uma busca por padrões de nomes nos bytes DEX. Nesse registro não há chamadas de análise de classes/métodos por JADX/REA nem resultados originais que sustentem todas as conclusões descritas nas notas.
- `app/build.gradle` habilita ViewBinding e declara Media3 1.4.1. `PlayerActivity.kt` importa `androidx.media3.exoplayer.ExoPlayer`. A mudança para Media3 já existe no código; o próximo trabalho é validar integração, comportamento e compatibilidade.
- A Home usa Fragment/ViewBinding e layouts XML com RecyclerViews. Há variantes `layout`, `layout-land`, `layout-port`, `layout-television` e `layout-sw720dp-land`. Isso não impõe um teto de qualidade; impõe disciplina de componentes e de atualização das variantes.
- Já existem `FocusMemory`, `FocusEngine`, `FocusCoordinator` e políticas de navegação, além de testes associados. O trabalho deve corrigir lacunas observadas e integrar essas peças antes de criar outro sistema de foco.
- Há um conflito concreto: `RailNavigationPolicy.isOverlaySidebarActive()` retorna falso para TV, e `RailNavigationFocusTest` exige rail permanente sem overlay, enquanto este roadmap pede expansão/retração. Implementar a proposta exige decidir o contrato novo e atualizar implementação e testes em conjunto.
- `backend/README.md` descreve o pareamento como prova local de arquitetura. Declara ausentes aprovação móvel autenticada, Google OAuth real, grants protegidos, sessões revogáveis e claim/refresh. QR exibido e polling local não equivalem a login completo na TV.
- A listagem atual contém 113 arquivos em `app/src/test` e sete em `app/src/androidTest`; são contagens de arquivos, não quantidade de testes, cobertura ou evidência de aprovação. Há também módulo `benchmark`.
- O build tem caminhos que podem injetar credenciais em BuildConfig. O modo `distributionBuild` suprime alguns campos, mas o token GitHub tem tratamento separado. Auditar os artefatos efetivamente gerados e as condições de build antes de considerar a sessão e a distribuição prontas; não ler nem publicar valores de segredos na auditoria.

### Alegações do dossiê que precisam voltar a hipótese

Até haver origem verificável, não usar como fatos: interface “100% Compose”; ausência de XML de tela; cache com TTL; isolamento reativo de perfis por um subsistema específico; escala exata de foco; `seek` de abertura para `end_ms + 100ms`; política exata de Back; origem e disparo do card de próximo episódio. Nomes de classes e presença de bibliotecas não comprovam esses comportamentos.

Identificadores citados nas notas precisam ser encontrados por ferramenta que distinga definição de classe, referência e string. Se a classe exata não existir, corrigir a nota; não insistir na busca do nome inventado ou transformá-lo em requisito de arquitetura.

### Conclusão técnica sobre a dificuldade

Ainda não há evidência suficiente para atribuir o progresso limitado a “ofuscação pesada”. Existe uma lacuna de rastreabilidade da investigação. Compose, otimização R8 e ofuscação são coisas diferentes: Compose define UI por código; o compilador transforma esse código; R8 pode remover, combinar e renomear estruturas. Nenhuma dessas condições garante recuperação do Kotlin original, e ausência de um XML equivalente à tela pode ser normal.

JADX pode recuperar manifesto, recursos e representações de código, com erros e perdas; REA organiza perguntas dirigidas e evidência. Eles não produzem automaticamente um design system reutilizável, backend, projeto compilável ou experiência de TV validada. O uso correto é responder perguntas específicas. A apresentação pode ser reconstruída por observação e medição mesmo quando o detalhe interno permanece desconhecido.

## Resultado realista: o que teremos em mãos

- **Da investigação:** um catálogo de comportamentos observados, telas acessíveis, medidas de composição e movimento, mapa parcial de componentes e incógnitas honestas. Login, flags de servidor e conteúdo acessível limitam quais estados podem ser observados.
- **Da implementação:** componentes Makimono com contratos de foco/estado, uma biblioteca Drive coerente, jornadas completas e testes de regressão. Cada comportamento depende de código, dados e integração próprios.
- **Do polimento:** composições e transições próximas da referência escolhida, verificadas em estados equivalentes, com identidade Makimono e arte de teste controlada.
- **Do backend:** serviços mínimos que o produto realmente exige, incluindo o fluxo de pareamento se aprovado. Um APK não fornece o backend operacional da Crunchyroll ou Netflix: catálogo editorial, processamento/transcodificação, CDN, direitos, recomendações e operação global são sistemas separados.
- **Da entrega:** APK reproduzível, evidência de testes e upgrade, lista explícita de capacidades e limitações. Não teremos automaticamente o fonte original, paridade global de catálogo/serviço ou todos os estados premium do app de referência.

É possível perseguir qualidade comparável nos fluxos escolhidos sem recuperar a implementação original. O risco de permanecer no estado atual aumenta quando a equipe mede progresso por notas, novas classes ou alterações de cor em vez de percursos completos funcionando na TV.

## Estratégia corrigida: investigação e produto avançam juntos

### Trilha A — Recuperar evidência útil, com limite de esforço

1. Fazer a Fase 2.5 abaixo e escolher uma pergunta concreta: por exemplo, como Back se comporta ao sair de detalhes para a Home e onde o foco retorna.
2. Observar uma sequência curta com ADB/controle remoto, gravar eventos, estado de conta, versão, resolução/densidade e capturas/tempos relevantes. Registrar restrição de acesso sem contorná-la.
3. Usar REA/JADX apenas se a resposta interna mudar uma decisão de implementação. Conferir esquema disponível; partir de manifesto/entry point, depois classe e método exatos com referências.
4. Orçamento inicial proposto: até 60 minutos por pergunta estática nesta etapa. Duas falhas equivalentes exigem diagnóstico; ao esgotar o orçamento, registrar incógnita e avançar com uma especificação própria verificável. Exceção exige justificar o benefício concreto.
5. Exportar comando, versão, hash do APK, saída relevante/Evidence ID, escopo e limitações. Descompilação estática não conta como execução observada.

### Trilha B — Entregar jornadas verticais no Makimono

Para cada pergunta útil, produzir uma especificação e um incremento instalável que atravesse UI, estado, dados e navegação. Após a correção de rastreabilidade, reservar como orientação cerca de 20% do esforço para referência e 80% para implementação/QA; ajustar quando uma incógnita comprovadamente bloquear a entrega. Essa proporção é uma regra de execução proposta, não uma estimativa medida.

Não aguardar o mapa completo do APK para corrigir o player, a raiz ou a sidebar. Não reescrever todo o app em Compose para imitar a tecnologia presumida de outro produto. Manter Views/XML inicialmente; avaliar migração limitada somente diante de problema medido de manutenção, desempenho ou entrega que a mudança resolva.

### Fase 2.5 — Confiabilidade do dossiê, antes de ampliar a análise

- Classificar cada alegação como comprovada por pacote, comprovada por código, observada em execução, inferida, proposta Makimono ou desconhecida.
- Corroborar apenas as alegações que influenciam a primeira jornada; anexar saída original recuperável. Preservar o histórico das correções.
- Criar índice de cobertura de telas: acessível/observada; acessível/ainda não observada; bloqueada por conta/conteúdo; não encontrada.
- Fazer um inventário delimitado de recursos e de definições DEX para responder à pergunta sobre XML/Compose/ofuscação. Relatar limites da amostra; não concluir “100%” sem cobertura suficiente.
- Produzir uma lista de no máximo cinco decisões práticas que a investigação sustenta e identificar o que é escolha de produto Makimono.

**Saída:** notas confiáveis e uma primeira especificação executável. A ausência de reconstrução completa do APK não bloqueia a Trilha B.

## Primeiras entregas obrigatórias após esta revisão

### Incremento A — Entrar na biblioteca certa e conseguir assistir

**Percurso:** iniciar → selecionar perfil → conectar/recuperar sessão → escolher `oneblacki` pelo ID → ver biblioteca atualizada → abrir série → iniciar episódio → sair → retomar.

**Trabalho:** confirmar estado atual com AVD Android TV; corrigir invalidação da raiz e classificação de pasta-mãe; erro de sessão com ação para conectar; cancelar requisições antigas após trocar conta/raiz; persistir progresso no perfil certo; corrigir Back e saída nos motores suportados.

**Áreas iniciais:** `HomeViewModel.kt`, `DriveRepository.kt`, `ProfileLibrarySettingsDelegate.kt`, fluxo de sessão existente, `PlayerActivity.kt`, `MPVActivity.kt` e políticas de input/progresso. Os nomes são pontos de investigação, não uma ordem para editar todos.

**Aceite:** percurso repetido após cold start e atualização de raiz sem fechar o app; nenhuma mídia da raiz anterior reaparece; voltar do player é alcançável por controle remoto; progresso restaura corretamente. Não declarar login por QR concluído enquanto seu caminho de aprovação/claim real não existir.

### Incremento B — Uma Home profissional de ponta a ponta

**Percurso:** rail → hero → linha horizontal → outra linha → detalhes → Back ao mesmo item → rail → troca de seção.

**Trabalho:** escolher contrato de sidebar, resolver a contradição código/teste; unificar tokens de tamanho; conter backdrop; calibrar CTA/card; aplicar restauração por identidade estável do item; tratar loading, vazio, erro e remoção do item focado. Atualizar todas as variantes realmente afetadas, inclusive `layout-port`, preservando usos auxiliares.

**Áreas iniciais:** `HomeFragment.kt`, controllers da Home, `FocusMemory.kt`, `RailNavigationPolicy.kt`, layouts `fragment_home.xml`, adapters e estilos compartilhados.

**Aceite:** sequência de pelo menos 200 eventos D-pad registrada por roteiro, sem foco perdido/armadilha; 50 ciclos detalhes→Back restauram o contexto enquanto o item existir; 720p e 1080p sem clipping essencial; screenshots comparáveis de loading/erro/conteúdo. Repetições são metas propostas de regressão, não resultados atuais.

### Incremento C — Detalhes e player com sessão longa

**Percurso:** escolher temporada/episódio → reproduzir → legendas/áudio → seek → menu→Back → próximo episódio/cancelar → erro de rede/retomar → sair.

**Trabalho:** alinhar CTAs e ordem de episódios; implementar um contrato de OSD/input comum aos motores mantidos; marcar corretamente estados indisponíveis; testar vídeos próprios ou autorizados com trilhas, codecs e durações conhecidos; exercitar erro e lifecycle.

**Aceite:** cenários passam em cada motor anunciado como suportado; ao menos uma sessão contínua de 60 minutos sem ANR/crash nem crescimento persistente inexplicado de memória; saída, foco e progresso preservados. Esse é um gate inicial, não certificação de todas as mídias ou TVs.

Cada incremento termina com APK local, build/commit-base identificados, capturas/vídeo curto, roteiro de input, testes executados e falhas restantes. Se o resultado visual continuar inadequado, ajustar a composição sobre o mesmo cenário antes de abrir mais funcionalidades.

## Controle visual, funcional e de robustez

- **Referências comparáveis:** fixar conteúdo de teste, perfil, resolução, densidade, fonte, idioma e estado. Comparar hierarquia, alinhamento, recorte, largura relativa de ações, raio, contraste e transições. Imagem diferente não deve mascarar divergência de layout.
- **Medição:** usar dimensões normalizadas e dp quando a densidade for conhecida. Captura isolada não revela curva de animação nem margem em dp com precisão. Registrar valores medidos separadamente de alvos de design.
- **Percurso de estados:** conteúdo, carregando, vazio, sem metadados, arte ausente, offline, erro autenticado e sessão expirada. A qualidade não é aferida apenas na Home preenchida.
- **Foco:** estado sempre distinguível, retorno por identidade estável, fallback quando item some, ausência de alvo oculto e ação principal alcançável. Confirmar na TV, não apenas em função pura testada.
- **Dados:** conta/perfil/raiz isolados; resultados antigos não sobrescrevem o estado novo; alterações do Drive se refletem sem reinício; migração não perde histórico.
- **Desempenho:** obter baseline com `benchmark` e perfilador. Alvo inicial proposto para o percurso de referência: até 5% de frames classificados como lentos pelo método declarado, sem congelamento de input atribuível a trabalho na main thread. Registrar equipamento, versão e método; recalibrar antes de adotar como SLA.
- **Qualidade percebida:** revisão humana em TV para legibilidade a distância, sensação de resposta, quantidade de ruído e compreensão de ações. Checklist verde não substitui essa revisão, e opinião isolada não substitui testes funcionais.

## Regras para evitar outra rodada de polimento superficial

1. Não marcar como implementado o que existe só em nota, XML, fixture, classe ou teste unitário. Identificar o nível: especificado, implementado, unitariamente testado, integrado, observado em AVD e validado em TV física.
2. Não tratar build verde como prova de qualidade visual, nem screenshot bonita como prova de backend, sessão ou reprodução.
3. Não criar outro `FocusEngine`, abstração de player ou sessão sem auditar as peças existentes e demonstrar a lacuna.
4. Resolver primeiro bloqueios de assistir/sair, perda de dados e sessão; em seguida entregar uma superfície visual completa, em vez de distribuir pequenos ajustes por todas as telas.
5. Novo framework, nova dependência ou atualização de SDK precisa de justificativa e teste de compatibilidade. Compose não é requisito para acabamento comercial.
6. Se duas iterações do mesmo defeito não produzirem evidência de melhora, interromper ajustes aleatórios, reproduzir com cenário mínimo e rever a causa/contrato.
7. Nova funcionalidade só entra após a jornada corrente passar seu gate ou ser explicitamente replanejada. O backlog não pode crescer mais rápido que as jornadas concluídas.
8. Handoff ao próximo agente inclui comando reprodutível, artefato, evidência e pendências. Não entregar outro relatório que rebatize hipóteses como descobertas.

## Referências técnicas desta revisão

- [Android — Jetpack Compose](https://developer.android.com/develop/ui/compose/documentation): modelo de UI definido por código. A adoção específica no APK continua dependendo de evidência do artefato.
- [Android — otimização com R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization): transformações de otimização e nomes; não comprova quais foram usadas pelo alvo.
- [JADX — README oficial](https://github.com/skylot/jadx/blob/master/README.md): decodificação e decompilação têm limites e podem produzir erros.
- [REA — análise Android](https://github.com/morluto/rea/blob/main/docs/android-analysis.md): inspeções dirigidas de pacote/classe/método/referências. Os comandos devem seguir o schema da versão instalada, que pode diferir de `main`.

Estas referências foram consultadas apenas para questões técnicas; esta revisão não reabre a discussão jurídica.

## Princípios de execução

1. **TV é o alvo primário.** O percurso principal deve funcionar integralmente por D-pad, OK, Back e controles de reprodução. Telefone e tablet continuam sendo superfícies auxiliares, nunca substitutos para validação de TV.
2. **Evidência antes de implementação.** Separar observação do app de referência, inferência, requisito de produto e decisão própria do Makimono. Não converter nomes de classes ou símbolos isolados em afirmações de comportamento.
3. **Implementação independente.** Usar padrões funcionais e aprendizados abstratos; manter a identidade, o código, os textos e os ativos do Makimono. Não importar ativos ou trechos literais de terceiros.
4. **Estado previsível.** Foco, sessão, pasta raiz, progresso, filtros e preferências precisam sobreviver às transições apropriadas e ter regras explícitas para restauração e erro.
5. **Segurança por desenho.** Segredos e tokens não entram no APK, no repositório, nos logs, em capturas ou nos relatórios. Pareamento concede apenas a autorização necessária e pode ser revogado.
6. **Mudanças pequenas e reversíveis.** Auditar a implementação atual antes de cada subsistema, preservar alterações locais, evitar refatorações amplas sem hipótese e critério de aceite.
7. **Sem release por suposição.** Builds, testes, assinatura, instalação e atualização são etapas distintas e precisam de resultados registrados.

## Estado de entrada e dependências

As Fases 0–2 do plano de investigação são o ponto de entrada: inventário do ambiente, identificação verificável do APK, e mapa estático inicial limitado. Elas produzem evidências e não constituem uma cópia nem uma implementação no Makimono.

Se o APK não estiver acessível, continuar o trabalho independente sobre o Makimono com os dados existentes, marcar os pontos dependentes da referência como desconhecidos e não inventar achados. Antes da primeira mudança de código, atualizar o estado do Git, ler as instruções locais e examinar a documentação de arquitetura e o estado atual do subsistema.

## Fase 3 — Especificação comportamental e backlog com evidências

**Objetivo:** converter notas de investigação em requisitos úteis para o Makimono sem tratar estrutura estática como prova de comportamento.

**Trabalho:**

- Construir mapas de percurso para início, seleção/troca/edição de perfil, onboarding, conexão do Drive, definição da raiz, Home, catálogo, busca, pastas, detalhes, favoritos, configurações e player.
- Registrar para cada fluxo: gatilho, estado inicial, sequência de foco, ação principal, ação de retorno, loading, sucesso, erro, cancelamento, persistência e recuperação após reinício.
- Marcar a evidência como runtime observado, pacote/manifesto/recurso, símbolo/código estático, teste Makimono ou hipótese; anotar confiança e perguntas abertas.
- Converter padrões úteis em critérios abstratos (“Back fecha primeiro a superfície modal ativa e restaura foco ao acionador”), sem afirmar que o produto de referência implementa isso se não foi observado.
- Fazer uma auditoria equivalente no Makimono e associar cada requisito a um bug ou oportunidade, prioridade, dependências, risco de regressão e teste de aceite.

**Entregáveis:** catálogo de fluxos; matriz de foco por tela; backlog priorizado; registro de evidência e incógnitas; critérios de aceite para os primeiros incrementos.

**Gate:** todo item P0/P1 tem reprodução ou justificativa clara, evidência classificada e verificação planejada. Itens inferidos permanecem explicitamente inferidos.

## Fase 4 — Fundação de produto e arquitetura

**Objetivo:** garantir que as telas compartilhem estado e regras coerentes antes de polir superfícies isoladas.

**Trabalho:**

- Mapear navegação, fontes de verdade, ViewModels, repositórios, persistência Room/DataStore, eventos de player e limites entre UI, domínio e integração com Drive.
- Definir contratos para sessão, perfil ativo, raiz da biblioteca, itens de mídia, temporadas/episódios, progresso, favoritos, busca, metadados e preferências.
- Documentar políticas de erro e retry: sem rede, token expirado, acesso revogado, item removido, paginação interrompida, cache indisponível, metadados ausentes e player incapaz de abrir mídia.
- Formalizar invariantes: raiz configurada não vira item de catálogo; selecionar a raiz atualiza as telas dependentes imediatamente; uma pasta não é classificada como anime só pelo nome do diretório pai; progresso pertence ao perfil e ao item corretos.
- Introduzir telemetria local de diagnóstico sem dados sensíveis, IDs de usuário, nomes privados de arquivos ou tokens.
- Identificar contratos que merecem testes unitários antes de mudanças de UI.

**Entregáveis:** mapa de arquitetura atual e alvo, decisões registradas, modelos e fluxos de estado revisados, matriz de erros e invariantes.

**Gate:** principais estados e transições têm proprietário claro; erros conhecidos são representáveis; não há segredo nos fluxos de log/build.

## Fase 5 — Sistema visual e interação para TV

**Objetivo:** substituir ajustes isolados por linguagem visual consistente e confortável a distância.

**Trabalho:**

- Definir tokens de cor, tipografia, espaçamento, raio, elevação, scrims, bordas, duração/easing e estados enabled/focused/pressed/selected/disabled/loading/error.
- Desenhar componentes próprios reutilizáveis: botões primários/secundários, ícones, chips, cards de mídia, cards de episódio, linhas horizontais, tabs/segmentos, diálogos, folhas de ação, avisos e skeletons.
- Projetar foco para 10-foot UI: indicação nítida sem brilho excessivo, escala moderada, clipping controlado, contraste e ausência de movimento obrigatório. Respeitar efeitos reduzidos e dispositivos de menor capacidade.
- Resolver a sidebar como comportamento responsivo: recolhida fora do foco com ícones reconhecíveis e área clicável estável; expandida com rótulos ao receber foco; conteúdo adjacente pode ganhar scrim/blur leve se não prejudicar leitura ou desempenho; entrada/saída previsíveis por foco e Back. Antes da mudança, reconciliar `RailNavigationPolicy` e seu teste, que atualmente exigem rail fixo na TV; não manter contratos opostos.
- Rever proporção e hierarquia dos CTAs, tamanho dos cards, densidade de conteúdo, recortes de hero, bleed de backdrop e truncamento de rótulos.
- Adaptar layouts para 720p, 1080p, diferentes overscan/insets e variações de densidade.

**Entregáveis:** especificação do sistema visual, catálogo de estados, layouts de referência e componentes migrados por tela.

**Gate:** foco sempre visível; texto essencial nunca é cortado; contraste e escala são legíveis no televisor; animações podem ser reduzidas; nenhuma tela depende de hover ou toque.

## Fase 6 — Perfis, onboarding e sessão segura

**Objetivo:** tornar a primeira execução compreensível e a troca de perfil segura e simples.

**Trabalho:**

- Seleção de perfil com avatares circulares sem moldura retangular externa desnecessária; estado focado claro; criação, edição, remoção e confirmação com navegação D-pad.
- Suportar perfil infantil/restrito com controles reais, classificação e isolamento de histórico/favoritos, se isso fizer parte do escopo funcional aprovado.
- Criar onboarding em passos curtos: boas-vindas, conexão de conta, seleção da pasta raiz, confirmação do que será indexado e estado inicial útil.
- Completar o pareamento a partir do estado real de `backend/README.md`: a prova local não possui aprovação móvel autenticada, OAuth real e claim/refresh. Investigar e escolher fluxo Google OAuth compatível com TV e políticas vigentes. O QR inicia um fluxo seguro no telefone: código curto de uso único, expiração, aprovação explícita, validação do dispositivo e confirmação na TV. Exigir teste de integração real antes de declarar o recurso concluído.
- Persistir credenciais pelo mecanismo seguro suportado; nunca embutir token de acesso pessoal, token de GitHub ou segredo de assinatura no cliente.
- Implementar renovação/expiração, logout, troca de conta, revogação, rede ausente e sessão parcial com mensagens acionáveis.

**Entregáveis:** fluxos completos de conta e perfil, contratos de pareamento, armazenamento seguro e casos de teste de sessão.

**Gate:** primeiro uso pode ser concluído apenas com controle remoto na TV e telefone apenas onde o fluxo explicitamente exigir; cancelar/expirar/retomar funciona; nenhum segredo aparece em logs, APK ou Git.

## Fase 7 — Biblioteca Drive, raiz e navegação de pastas

**Objetivo:** tornar a biblioteca pessoal inteligível e evitar confundir a pasta raiz com uma série.

**Trabalho:**

- Seletor de raiz em configurações e onboarding, com breadcrumb completo, busca dentro do seletor, expansão por D-pad e atualização imediata após salvar.
- Tratar nomes distintos como `oneblacki` e `1oneblacki` sem heurística que os confunda. A pasta escolhida explicitamente é a raiz; os caminhos da UI começam, por exemplo, em `oneblacki/Bakemonogatari`.
- Navegação profunda com breadcrumb navegável, estado vazio/erro/retry, paginação, cancelamento e foco restaurado após voltar de um item.
- Grade/lista acessível, ordenação persistente e filtro com estado visível; distinguir pastas, séries, temporadas, arquivos de episódio, especiais, aberturas, encerramentos e trailers somente com regras justificadas e corrigíveis pelo usuário.
- Exibir detalhes técnicos quando úteis (container, resolução, áudio, legendas, tamanho), sem inventar valor quando indisponível.
- Detectar alterações, itens movidos/removidos e dados parciais, com atualização controlada e cache coerente.

**Entregáveis:** navegação da raiz até arquivo reproduzível, regra de classificação documentada e recuperação de estado.

**Gate:** alterar a raiz reflete imediatamente em Home, busca e pastas; navegação por pastas profundas é operável só com D-pad; nenhum item é rotulado como anime por ser apenas a pasta-mãe.

## Fase 8 — Home editorial e descoberta

**Objetivo:** criar uma Home que ajude a retomar e encontrar conteúdo, com ritmo visual e carregamento resiliente.

**Trabalho:**

- Hero cinematográfico responsivo, backdrop contido dentro dos limites da UI, scrim funcional, título e metadados hierárquicos, sinopse curta e ações proporcionais.
- Prateleiras alimentadas por dados reais: Continuar assistindo, novos episódios, Minha lista, adicionados recentemente e biblioteca geral, conforme os dados disponíveis.
- Cards com proporção adequada ao tipo de mídia; favoritos compactos e acessíveis; progresso sem excesso de badges; foco move o hero com debounce/crossfade sem disparar mudança a cada repetição rápida.
- Busca global com teclado virtual, filtros úteis, sugestões/histórico local opcional e estado vazio com ação clara.
- Skeletons dimensionados para o layout final; falha em um poster não bloqueia a prateleira; arte ausente usa fallback consistente.
- Menus de contexto por tecla Menu/long-press onde suportado: continuar, recomeçar, abrir série, remover da lista com desfazer.

**Entregáveis:** Home, busca e catálogo completos, com estados carregando/vazio/erro/offline.

**Gate:** hero não vaza nem corta áreas essenciais; conteúdo é navegável em fileiras; mudança de foco não causa flashes ou reposicionamento inesperado; todos os estados têm feedback útil.

## Fase 9 — Detalhes de série, temporadas e episódios

**Objetivo:** responder rapidamente o que é a série, onde continuar e o que será reproduzido.

**Trabalho:**

- Detalhes com título/localização, sinopse expansível, metadados consolidados, créditos/classificação disponíveis e arte carregada sem fazer o fundo competir com o texto.
- No máximo uma ação primária destacada; ações secundárias agrupadas; estado favorito/seguir/assistido inequívoco e persistente por perfil.
- Temporadas em controle segmentado adequado a foco remoto; episódios em cards de proporção coerente, duração, progresso e indicação “em andamento”.
- Episódio em foco pode revelar sinopse adicional sem deslocar a grade abruptamente.
- Especiais, OVAs, aberturas, encerramentos e trailers em grupos claros, sem misturar clipes curtos com a ordem dos episódios normais.
- Ordenação de arquivos verificável e corrigível para numeração irregular, nomes longos, especiais e idiomas.

**Gate:** abrir, retomar, selecionar temporada, escolher episódio e voltar funcionam por D-pad; não há duplicação, título inacessível ou ordenação implausível sem feedback.

## Fase 10 — Reprodução de nível comercial

**Objetivo:** garantir sessão de vídeo previsível antes de polir detalhes de overlay.

**Trabalho:**

- Partir da integração Media3 já existente (`media3-exoplayer` 1.4.1 no build inspecionado), auditar compatibilidade e APIs antes de escolher atualização e decidir, com dados de mídia/dispositivos, se MPV permanece como alternativa explícita. Não abrir uma tarefa fictícia de migração de ExoPlayer legado quando o código já importa AndroidX Media3. A escolha não pode deixar dois caminhos com controles divergentes.
- Definir máquina de estados do player: preparando, bufferizando, tocando, pausado, seek, erro recuperável, fim, próximo episódio e encerramento.
- Resolver Back: primeiro dispensa diálogos/menus; depois esconde o OSD se essa for a interação ativa; então retorna à tela anterior ou encerra playback conforme o fluxo, sem colapsar a UI para uma reprodução impossível de sair.
- D-pad: OK alterna controles/pausa com semântica única; esquerda/direita com OSD oculto executam seeks acumulativos e silenciosos com HUD temporário; foco não desaparece em overlay.
- OSD em ilha visual compacta: barra de progresso precisa, tempo decorrido/restante, capítulo quando disponível, controles grandes para TV, estado de foco e timeout previsível.
- Seleção de áudio/legenda real; sincronização e estilo legível; suporte fiel às capacidades do formato, com indicação quando uma opção não pode ser aplicada.
- AniSkip configurável por perfil/episódio, com opção de aceitar/recusar e feedback temporal; autoplay com preview, contagem regressiva cancelável e sequência de playlist validada.
- Lifecycle: pausa/retoma ao perder foco, tela, áudio, rede ou processo; restauração de posição sem marcar progresso indevidamente.
- Diagnóstico de codec/resolução/áudio/buffer sem expor credenciais; mensagens de erro distinguem arquivo inválido, rede, formato não suportado e bloqueio real.

**Gate:** reprodução, saída, retomada, trilhas, seek, erros e próximo episódio passam em mídia de teste nos motores suportados, usando toque e D-pad; nenhum caminho deixa o usuário preso no player.

## Fase 11 — Configurações, acessibilidade e privacidade

**Objetivo:** transformar configurações em controles reais, coerentes e reversíveis.

**Trabalho:**

- Agrupar conta/biblioteca, reprodução, legendas/áudio, aparência/acessibilidade, perfil, cache e diagnóstico.
- Cada opção exibe valor atual e efeito, persiste corretamente e oferece restaurar padrão quando apropriado.
- Cache com tamanho/categorias, limpeza confirmada e feedback; não apagar biblioteca ou progresso acidentalmente.
- Opções de legenda: tamanho, fonte disponível, fundo/contorno, sincronização e prévia; oferecer padrão seguro para distância de TV.
- Acessibilidade: ordem de foco, semântica, contraste, tamanho de fonte, alternativas a cor, redução de movimento e tempos ajustáveis.
- Privacidade: minimização de dados, apagar histórico/progresso/favoritos por perfil, encerrar sessões e revogar dispositivos pareados.

**Gate:** cada ação configurável foi exercitada após reiniciar; ações destrutivas explicam o efeito e podem ser canceladas antes de confirmar.

## Fase 12 — Robustez, desempenho e operação

**Objetivo:** suportar bibliotecas reais e sessões longas sem travamentos nem surpresas.

**Trabalho:**

- Medir tempo de início, primeira arte, primeira reprodução, scroll, memória, jank e uso de rede em perfil de referência declarado.
- Testar bibliotecas pequenas/grandes, posters lentos/ausentes, nomes extremos, rede lenta/intermitente, sessão expirada, disco/cache cheio, rotação quando suportada, processo encerrado e cold start.
- Otimizar por perfilador e comparação antes/depois; priorizar causa observada em vez de micro-otimizações preventivas.
- Auditar lifecycle, cancelamento de coroutines/requests, imagens recicladas, listeners do player, leaks de janela/Activity e migrações Room.
- Adicionar logs diagnósticos redigidos e exportação voluntária que exclui tokens e dados privados por padrão.
- Verificar tamanho do APK, R8/minificação, compatibilidade de ABI, inicialização em TVs de capacidade modesta e compatibilidade de upgrade.

**Gate:** metas numéricas de desempenho definidas a partir de baseline real; sem ANR/crash nos percursos de aceite; sem regressão não explicada de memória, tempo ou fluidez.

## Fase 13 — Homologação de TV e beta privada

**Objetivo:** testar o produto em ambiente representativo antes de ampliar distribuição.

**Matriz:** Android TV Emulator 720p/1080p e versões suportadas; TV/box físico de baixo e médio desempenho; controle remoto com D-pad/Back/Menu/play-pause; instalação limpa e atualização; sessão autenticada e não autenticada; biblioteca pequena e extensa.

**Suíte:** smoke test de instalação e onboarding; percurso completo por todas as telas; matriz de foco; estados de erro; player e interrupções; preferências e persistência; regressões de screenshot com tolerância; testes unitários/instrumentados e verificação de release assinado.

**Processo:** beta privada com versão identificável, changelog, canal de rollback e coleta de feedback voluntária. Separar problema reproduzido de opinião e priorizar bloqueio, perda de dados, falha de foco, leitura e refinamento visual nessa ordem.

**Gate:** nenhum P0/P1 aberto; todo fluxo crítico tem resultado documentado; versão anterior pode ser recuperada ou atualizada sem perder dados; distribuição e credenciais seguem o modelo de privacidade aprovado.

## Fase 14 — Polimento contínuo e diferenciação Makimono

**Objetivo:** manter a qualidade e evoluir com base no uso, sem copiar produto de referência.

**Ciclo contínuo:** observar sessões e feedback consentido → reproduzir → medir impacto → escrever hipótese e aceite → priorizar → implementar em fatia pequena → testar em TV → revisar acessibilidade/performance → registrar resultado → atualizar backlog.

Possíveis áreas de evolução, somente após validação de necessidade: perfis adicionais, controles parentais, sincronização multi-dispositivo, downloads locais se o modelo de conteúdo permitir, recomendações privadas, widgets/atalhos de TV, controles por telefone, favoritos avançados, tags e regras de organização por pasta.

**Gate contínuo:** toda nova função tem problema de usuário, regra de dados, comportamento de foco, fallback de erro, custo de manutenção e plano de teste. Itens sem evidência ou sem uso claro não entram apenas para igualar uma lista de funcionalidades alheia.

## Ordem sugerida e paralelismo

1. Reconciliar as evidências das Fases 0–2 na Fase 2.5 e iniciar Fase 3 com os incrementos A–C acima.
2. Executar Fase 4 antes de alterações que cruzem múltiplos subsistemas.
3. Construir Fase 5 em paralelo com protótipos não invasivos, mas migrar as telas gradualmente.
4. Entregar Fases 6 e 7 como jornada inicial de valor; raiz e sessão são pré-requisitos para catálogo confiável.
5. Entregar Fases 8 e 9 sobre dados reais e contratos estáveis.
6. Tratar Fase 10 como trilha prioritária independente, começando pelos bugs que prendem o usuário ou perdem progresso.
7. Aplicar Fases 11 e 12 em todas as ondas, não apenas no fim.
8. Fazer Fase 13 antes de releases amplas; manter Fase 14 como rotina de produto.

Não fixar estimativa total antes de inventário de código, número de telas/fluxos ainda ativos, estado real de Drive/OAuth e baseline do player. Estimar cada incremento após dividir o trabalho e registrar bloqueios.

## Template para cada incremento

```text
ID / prioridade:
Problema observado e usuário afetado:
Evidência e grau de confiança:
Estado atual no Makimono:
Comportamento desejado:
Estados de loading / vazio / erro / cancelamento:
Matriz de foco D-pad e Back:
Dados persistidos e perfil proprietário:
Arquivos/subsistemas prováveis:
Riscos e compatibilidade:
Testes e dispositivo-alvo:
Critério objetivo de aceite:
Resultado, evidência e pendências:
```

## Critério final de produto

O objetivo de longo prazo será considerado atingido quando o Makimono puder ser instalado e atualizado de forma reproduzível; concluir onboarding e selecionar a raiz sem reinício manual; navegar catálogo, pastas, detalhes e configurações inteiramente por controle remoto; reproduzir e retomar conteúdo sem ficar preso; manter foco, preferências e progresso coerentes; tratar falhas de rede e dados ausentes com ações claras; apresentar identidade visual consistente e legível em TV; e passar pela matriz automatizada e manual de regressão sem defeitos P0/P1 conhecidos.

Isso estabelece um padrão comercial de engenharia e experiência. “Apple/Netflix/Crunchyroll” permanece uma referência aspiracional; qualidade é demonstrada por critérios, evidência e uso real, não por uma nota subjetiva nem por alegar equivalência de produto.
