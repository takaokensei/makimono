# Makimono TV — Plano de execução para qualidade comercial

Data: 14/10/2026. Estado: plano proposto com revisão independente executada; tarefas de produto abaixo ainda não executadas por este documento.

## Resultado pretendido

Entregar uma experiência doméstica de biblioteca e reprodução com acabamento, previsibilidade e confiabilidade comparáveis aos fluxos maduros de Netflix, Crunchyroll e plataformas Apple, adaptada a Android TV e Google Drive. A referência orienta composição, navegação e qualidade percebida. O produto precisa funcionar com os dados e serviços reais do Makimono.

O sucesso é uma jornada integrada: instalar → entrar/parear → selecionar perfil → escolher raiz → encontrar série → escolher episódio → reproduzir → ajustar faixas → sair → retomar → atualizar o app sem perder dados. Um conjunto de telas bonitas ou políticas Kotlin isoladas não basta.

## Documentos e autoridade técnica

1. `makimono-dossie/observacoes/REVISAO_INDEPENDENTE_V0_V5.md`: o que foi conferido e os limites das entregas anteriores.
2. Este plano: tarefas de implementação e homologação decorrentes da revisão.
3. `MAKIMONO_LONG_TERM_PRODUCT_ROADMAP.md`: direção ampla, princípios e evolução futura.
4. Notas/evidências originais do dossiê: fontes de hipóteses e observações, respeitando as ressalvas da revisão.

Não sobrescrever relatórios originais para esconder divergências. Corrigir por adendo rastreável. Esta rodada não reabre a análise jurídica e não depende de decompilar integralmente o APK.

## Base factual e implicações

- Os dumps da referência sustentam rail com largura variável, mudança dos bounds do feed, retenção do terceiro card em uma sequência e retorno de detalhes ao card de origem. Isso oferece contratos locais úteis.
- Não foram entregues imagens/vídeos renderizados nem runtime de reprodução. Aparência fina, curvas de animação, OSD, skip e Up Next precisam de observação adicional ou decisão própria explicitamente identificada.
- As três provas são lógica Kotlin sem UI integrada. Compiladas nesta revisão com Kotlin 1.9.24/JUnit 4.13.2, passaram em seus 15 testes originais. Quatro novos contratos de integração falharam: restauração do item do rail, ID obsoleto, reordenação da lista e tick antigo após skip.
- Os testes das provas não estão no source set do app. O comando de teste de `FocusMemoryTest` do app não prova que os novos coordenadores passaram.
- O Makimono já possui Media3, MPV, controle de OSD, skip, banco de perfis, mecanismos de foco, repositórios e um backend parcial. A execução começa pela integração e correção dessas peças.
- A qualidade comercial ainda não foi demonstrada por uma nova sessão completa de Android TV. Estimativas de poucas horas e percentuais globais de aderência do relatório anterior não serão tratados como compromisso.

## Regras de execução para o agente

- Ler o estado atual do Git e instruções locais antes de alterar código; preservar alterações de outros agentes. Coordenar propriedade de arquivos se houver trabalho paralelo autorizado pelo usuário.
- Não fazer commit, push, publicação ou deploy como consequência automática deste plano. Produzir artefatos locais e relatório; ações remotas seguem autorização vigente específica.
- Usar Android TV AVD oficial e serial explícito. Não validar TV no Moto, em MuMu ou apenas mudando a resolução de um Android de telefone.
- Usar `.visualreview`/debug e fixtures isoladas; não limpar/desinstalar `zechs.drive.stream` para facilitar teste.
- Não transplantar os coordenadores de prova sem comparar os contratos e funcionalidades já existentes.
- Preservar APIs, arquitetura e capacidades suportadas quando possível. Cada refatoração precisa de problema demonstrado e teste de regressão relevante.
- Toda mudança de layout deve considerar todas as variantes existentes com o mesmo nome, incluindo `layout-port` quando aplicável. Não criar variantes desnecessárias só para copiar arquivos.
- Strings da UI em recursos pt-BR; conteúdos de metadados externos têm idioma/fallback declarados. Não confundir sinopse inglesa da fonte com app inteiramente sem localização.
- Segredos não entram em capturas, logs, relatórios ou código. Não ler valores privados para provar a existência de um caminho inseguro.
- Ao terminar uma tarefa, registrar código alterado, comando, resultado, APK e evidência do cenário; avançar quando o gate passar. Se bloqueada, marcar exatamente a dependência e continuar tarefas independentes.

## Organização das ondas

Cada onda entrega uma parte utilizável. Confiabilidade de reprodução e dados pode preceder acabamento visual quando houver bloqueio de uso. Revisão visual e performance são parte de cada onda; a homologação final amplia a cobertura.

### Onda 0 — Baseline reproduzível e rastreabilidade

**Q00.1 — Inventário de situação atual**

- Confirmar versões de build, paths SDK/JDK/ADB, AVD, dependências, arquivos modificados e último resultado real de testes.
- Exportar somente evidência REA relevante com comando/hash/classe/método. Corrigir transcrição do hash xhdpi e referências de Evidence ID que não identifiquem o resultado utilizado.
- Classificar alegações de OSD/skip/autoplay/conta como observadas, inferidas ou propostas, sem atrasar trabalho independente.

**Q00.2 — Ambiente visual controlado**

- Corrigir a captura PNG: validar magic bytes, tamanho e abertura do arquivo depois de `screencap`/`adb pull`; no PowerShell, evitar redirecionamento de bytes que corrompa PNG.
- Registrar densidade real e configurar fixtures: biblioteca preenchida, vazia, lenta, sem arte/metadados, nome longo, raiz A/B, perfis distintos e série com especiais.
- Reutilizar a fixture visual existente somente após conferir o que ela exercita; não chamá-la de integração Drive real.
- Criar roteiro curto da jornada principal com identificação de build e input, e um índice de resultados por tela.

**Áreas:** `app/src/androidTest`, fixture existente, `benchmark`, `makimono-dossie/observacoes` e `capturas`.

**Gate:** build/testes de baseline com saída preservada ou bloqueio diagnosticado; primeira captura PNG legível do Makimono em AVD TV; mapa de fluxos ainda não testados. Não inventar aprovação onde só houve leitura de código.

### Onda 1 — Modelo de navegação, sidebar e Back

**Q01.1 — Contrato único de navegação**

- Delimitar responsabilidades de `MainActivity`, `HomeFragment`, NavController e controllers da Home. Evitar dois handlers consumindo o mesmo Back.
- Definir ordem por contexto: modal/diálogo → estado transitório da tela → backstack da tela → política da Home raiz → saída. Back em pastas deve subir a hierarquia, não mandar invariavelmente para a sidebar.
- Na Home raiz, adotar como proposta: conteúdo → rail/aba ativa; rail → confirmação de saída; cancelar → mesmo item do rail. A transição Back do conteúdo ainda não foi comprovada pelo material entregue da referência; é uma escolha Makimono até confirmação.
- Proposta de segurança do diálogo: foco inicial em Cancelar; confirmar saída exige ação explícita. Registrar essa decisão, pois o dump da referência foca Confirmar.

**Q01.2 — Sidebar expansível para TV**

- Resolver o conflito do rail fixo em `RailNavigationPolicy`/testes. Usar 80dp/264dp como ponto de partida condicionado ao viewport medido; ajustar para tipografia pt-BR e densidades reais.
- Separar aba selecionada, item focado, estado expandido e diálogo. Preservar índice/ID anterior ao abrir modal e ao retornar do conteúdo.
- Base proposta: expansão por foco com labels, recolhimento ao voltar ao conteúdo e deslocamento estável do feed. Scrim/blur é variação visual opcional, não bloqueio funcional nem propriedade comprovada da referência.
- Animação cancelável e reduzível; não recarregar arte/listas a cada frame nem roubar foco ao receber resultados da rede. Não ativar navegação de aba apenas por passar o foco se isso causar carregamentos e saltos inesperados.

**Áreas:** `ui/home/controller/RailController.kt`, `RailNavigationPolicy.kt`, `FocusCoordinator.kt`, `HomeFragment.kt`, `ui/main/MainActivity.kt`, variantes de `fragment_home.xml`, recursos compartilhados.

**Testes:** casos de cancelar saída na aba diferente da primeira; Back repetido/long press; modal sobre conteúdo; loading/erro sem cards; labels longos; retorno de seção e de detalhes; movimento reduzido.

**Gate:** 50 ciclos de abrir/cancelar diálogo e retornar de detalhes sem perder contexto; todo item de rail legível em 720p/1080p; navegação de pastas/settings preservada. Quantidades são metas de QA propostas.

### Onda 2 — Foco por identidade e listas assíncronas

**Q02.1 — Memória de foco coerente**

- Evoluir `FocusMemory`/estado já existente; não instalar um segundo mapa global concorrente.
- Identificar contexto por perfil, conta/raiz, seção e prateleira; lembrar ID estável do item e posição/offset como fallback.
- Após reordenação, localizar o ID atual. Após remoção, escolher vizinho válido; lista vazia retorna ausência de alvo e encaminha para ação útil. Limpar identidades obsoletas.
- Definir comportamento em recriação/process death com estado apropriado, sem confundir recuperação de foco com persistência de credenciais.

**Q02.2 — Restauração ligada ao ciclo da lista**

- Aguardar commit da lista e disponibilidade do ViewHolder com callbacks/lifecycle. Reduzir dependência de atrasos mágicos de 64/100ms; cancelar tentativas de telas antigas.
- Aplicar restauração após retorno de detalhes, troca de aba, hero→linha→hero, paginação e retorno de modal.
- Definir navegação vertical entre linhas com tamanhos diferentes e sem grid ainda carregado. IDs estáveis devem orientar a regra antes do índice.

**Áreas:** `utils/FocusMemory.kt`, `FocusEngine.kt`, `HomeFragment.kt`, `FocusCoordinator.kt`, adapters e policies existentes.

**Testes:** reordenação, exclusão do item ativo, troca de perfil/raiz, lista vazia, resultado antigo após nova busca, paginação, criação tardia de ViewHolder e cancelamento da restauração.

**Gate:** quatro classes de cenários (estável/reordenado/removido/vazio) passam em unidade e em UI; roteiro de 200 eventos D-pad sem foco perdido; nenhum alvo oculto ou posição fora do adapter.

### Onda 3 — Sistema visual e Home cinematográfica

**Q03.1 — Tokens e componentes**

- Consolidar estilos existentes antes de criar novos: cores semânticas, escala de tipos, espaçamento, raio, botões, badges, foco e scrims.
- Aplicar leitura a distância, ações proporcionais e seleção distinta de foco. Sem glow/bordas em excesso como substituto de hierarquia.
- Consultar os PDFs HIG de Layout, Typography, Focus and selection, Motion, Materials e Accessibility fornecidos pelo usuário. Registrar princípio e decisão Android TV; não fingir leitura de documento indisponível.

**Q03.2 — Hero, prateleiras e dados**

- Contenção real do backdrop e scrims; título com comprimento variável; metadados sem duplicação; sinopse curta; uma ação primária e secundárias enxutas.
- Arte/título só mudam conforme regra de foco estável. Debounce/crossfade são parâmetros Makimono ajustáveis, não números extraídos da referência sem evidência.
- Prateleiras relevantes com dados reais: continuar, novidades de seguidos, minha lista/fila, recentes e catálogo. Omitir prateleira sem conteúdo quando houver outra ação útil.
- Definir remoção de continuar com desfazer, recomeçar, abrir série e fallback de mídia removida.
- Investigar virtualização da Home atual com NestedScrollView/RecyclerViews e grid `wrap_content`. Medir quantidade de Views e jank; alterar container somente se houver problema demonstrado.

**Q03.3 — Revisão visual efetiva**

- Obter PNG/vídeo em estados comparáveis. Comparar alinhamento, proporções, CTA, recorte, legibilidade e ritmo, usando arte controlada própria.
- Distinguir bounds semânticos de imagem/card visível. Não fixar espaçamentos ou altura do hero apenas por um dump.
- Fazer a revisão em 720p e 1080p e verificar labels em pt-BR e texto ampliado suportado.

**Áreas:** `FeaturedHeroBinder.kt`, `controller/HeroController.kt`, `HomeViewModel.kt`, adapters, `StateView.kt`, dimens/styles e todas as variantes de `fragment_home.xml` afetadas.

**Gate:** Home aprovada visualmente em conteúdo/loading/vazio/erro; hero sem bleed; ações essenciais visíveis; rede não rouba foco; carga de biblioteca grande mantém fluidez medida.

### Onda 4 — Detalhes, episódios e descoberta

**Q04.1 — Detalhes completos**

- Separar mídia técnica, metadados de catálogo e estados do usuário. Mostrar somente valores conhecidos; escolher fonte/localização e corrigir match errado.
- Temporadas focáveis e episódios ordenados por regra documentada, preservando especiais, OVAs, OP/ED e trailers em categorias corretas.
- Continuar deve apontar para episódio/duração reais; próximo depende da playlist ordenada, não apenas da posição visual de um card.
- Limitar ações expostas; agrupar trailer/seguir/marcar assistido em menu com foco de retorno. Sinopse expansível sem layout saltando a cada foco.

**Q04.2 — Busca e descoberta**

- Consulta/teclado/filtros por TV; debounce somente onde evita trabalho; cancelamento de respostas antigas; foco e consulta restaurados ao voltar.
- Busca local tem loading/vazio/erro claros e não depende de endpoints privados da referência.
- Priorizar metadados em pt-BR quando suportados; sinopse sem tradução disponível deve ter fallback consistente, sem fabricar informação.

**Áreas:** `ui/series/*`, `SeriesDetailEpisodeSelection.kt`, adapters e layouts, `CatalogRepository.kt`, policies de biblioteca e controllers de catálogo.

**Gate:** série com temporada vazia, metadados ausentes, nomes longos, especiais e progresso parcial funciona integralmente por D-pad; iniciar episódio e voltar preserva foco e seleção.

### Onda 5 — Player: confiabilidade antes de aparência fina

**Q05.1 — Unificar contratos existentes**

- Mapear os dois `PlaybackCoordinator` existentes em namespaces diferentes; esclarecer responsabilidades antes de unificar ou renomear.
- Preservar `OsdStateMachine` com transições/interruptibilidade. Não substituí-lo por um booleano de controles visíveis.
- Preservar capacidades de `playback/session/SkipController`: merge de capítulos/AniSkip, auto-skip, grace period e fallback; corrigir contratos identificados sem remover recursos.

**Q05.2 — Input, foco e lifecycle**

- Um evento físico gera uma ação; tratar DOWN/UP/repeat e long press explicitamente. OK, media play/pause e clique não disparam duplo toggle.
- Back fecha superfície ativa/OSD conforme contrato; seta visível de sair encerra o player e retorna de forma inequívoca. Não criar caminho que esconda a UI e prenda a reprodução.
- Seek oculto com delta acumulativo e HUD discreto; OSD visível usa foco/scrubber; modo bloqueado tem saída acessível.
- Timeout com relógio monotônico e scheduler ligado ao lifecycle; suspender quando modal, scrub/seleção ou interação demandar permanência. Separar posição de mídia do relógio de inatividade.

**Q05.3 — Skip, próximo episódio e dados confiáveis**

- Intervalos validados contra ordem/duração; origem do marcador identificada; labels por tipo em recursos.
- Após skip, manter estado de comando pendente até confirmação/erro/timeout. Tick com posição antiga não deve reabrir botão nem reenviar seek.
- Limitar destino à duração conhecida. Offset de 100ms é uma opção de implementação a justificar; não uma regra comprovada da referência.
- Up Next com playlist determinística, uma navegação por evento, cancelamento, itens removidos, fim de fila e gravação correta de progresso. Contagem de 15s não é exigida por uma observação que não ocorreu.

**Q05.4 — OSD e faixas com acabamento**

- Hierarquia de transporte, tempo decorrido/restante, scrubber, quick actions, overlays e estados de foco. Aplicar assets/tokens do Makimono e validar visualmente.
- Seleção real de áudio/legenda com idioma, nome, codec/canais quando disponíveis; sincronização e estilos respeitam capacidades de ASS/SSA e do motor; mostrar limitação de modo útil.
- Testar com mídia própria/autorizada de duração, faixas e capítulos conhecidos. Conta da referência não é necessária para exercitar o player próprio.

**Áreas:** `ui/player/PlayerActivity.kt`, `ui/player2/MPVActivity.kt`, `BasePlayerActivity`, `playback/osd`, `playback/session`, UI de controles e adaptadores dos motores.

**Gate:** ambos os motores anunciados passam no mesmo contrato de saída/foco/progresso; seek/skip e eventos duplicados testados; ao menos uma sessão de 60min por motor selecionado, incluindo pausa e falha de rede, sem crash/ANR. Sem prometer suporte a formato não exercitado.

### Onda 6 — Drive, raiz e consistência dos dados

**Q06.1 — Raiz e invalidação imediata**

- Persistir a pasta pelo ID escolhido e nome para exibição; distinguir `oneblacki` de `1oneblacki`; nenhuma heurística deve trocar a escolha explícita.
- Ao mudar conta/raiz, invalidar estados e cache no escopo correto; cancelar carregamentos anteriores e impedir que a resposta antiga substitua a biblioteca nova.
- Atualizar Home, busca e pastas por fluxo de estado, sem fechar/reabrir o app. Breadcrumb começa na raiz selecionada; pasta-mãe não vira anime.

**Q06.2 — Biblioteca grande e rede**

- Paginação, deduplicação, IDs estáveis, classificação corrigível e metadados carregados progressivamente.
- Retry limitado com backoff/jitter para erros transitórios conforme semântica da API; cancelamento; não repetir indefinidamente 401/403/404.
- Coalescer renovação concorrente de token; mídia removida, permissão revogada e sessão expirada produzem recuperação útil.
- Testar lista parcial/offline e restauração do último contexto sem apresentar cache de outra conta/perfil.

**Áreas:** `DriveRepository.kt`, `CatalogRepository.kt`, `TokenProvider.kt`, `TokenAuthenticator.kt`, `HomeViewModel.kt`, `ProfileLibrarySettingsDelegate.kt`, Files e persistência da biblioteca.

**Gate:** troca A→B→A com requisições em voo sem vazamento de resultados; 1.000 itens de fixture paginada sem duplicação; pastas profundas navegáveis; alteração de raiz reflete sem reinício.

### Onda 7 — Perfis, onboarding e pareamento realmente funcional

**Q07.1 — Perfis existentes, refinados**

- Usar `ProfileEntity`, `ProfileDao` e `ProfileManager` existentes; não recriar banco por supor que falta suporte local.
- Avatares circulares sem caixa cinza externa, foco por anel/escala discreta e nome legível; galeria e fundo próprios; salvar/cancelar com estado previsível.
- Isolar progresso/favoritos/fila, inclusive com callback tardio do player ou troca durante trabalho em background. Destruição de perfil tem confirmação e escopo claros.

**Q07.2 — Primeira execução**

- Boas-vindas → conectar/parear → confirmar conta → selecionar raiz → biblioteca útil. Não pular passo obrigatório com um spinner sem diagnóstico.
- Todos os passos possuem Back/cancelamento/retry, restauração do estado e nenhuma exigência de digitação extensa por controle remoto.

**Q07.3 — Concluir o backend parcial**

- Partir do estado declarado em `backend/README.md`; não expor a prova loopback `.invalid` como serviço pronto.
- Validar os fluxos Google OAuth suportados e os escopos Drive requeridos na documentação oficial durante a implementação; não presumir que qualquer Device Flow permite qualquer escopo.
- Implementar aprovação móvel autenticada, associação ao desafio correto, entrega/claim de uso único, expiração/cancelamento concorrentes, grant protegido, sessões revogáveis e refresh conforme contrato escolhido.
- QR/código identifica um desafio, não substitui autenticação no telefone. Aprovação precisa estar ligada ao usuário e dispositivo corretos; segredo de polling/claim não aparece em URLs/capturas.
- Testar replay, brute force, polling, estado após reboot/redeploy, concorrência, token revogado, perda de rede e cleanup. Configurar domínio/TLS/hospedagem somente após prova local e decisão de operação.

**Áreas:** `SessionManager.kt`, `EncryptedSessionStore.kt`, fluxo de sign-in existente, `ui/profile`, Room e `backend/src/main`/`src/test`.

**Gate:** login real chega à listagem Drive e persiste após restart; aprovar/cancelar/expirar/revogar funcionam ponta a ponta. Se dependência de Google Cloud/domínio impedir esse gate, manter explicitamente pendente e continuar outras ondas; não declarar login pronto por renderizar QR.

### Onda 8 — Configurações, localização e acessibilidade

- Categorias claras para conta/biblioteca, reprodução, áudio/legendas, aparência, perfis, cache e diagnóstico; valor atual e resultado de cada ação visíveis.
- Persistência após reiniciar; preview útil de legenda; efeitos reduzidos sem depender de blur; limpeza de cache distingue arte, dados e histórico.
- Revisar recursos e literais em todas as variantes. Menus e erros em pt-BR; metadados externos respeitam fallback. Rótulos não podem truncar justamente a ação necessária.
- Foco visível sem depender só de cor, contraste medido, fonte ampliada, descrições úteis, ações com tempo suficiente e ausência de animação obrigatória.
- UI móvel de apoio preservada, com validação própria; não usar seu sucesso para marcar TV aprovada.

**Áreas:** settings/delegates, `AppSettings`, strings/styles/dimens e componentes/modais compartilhados.

**Gate:** todos os ajustes ativos exercitados após restart; nenhum controle requer toque; textos principais legíveis a distância e com ampliação suportada; sem preferências que exibem efeito inexistente.

### Onda 9 — Desempenho, segurança e estabilidade

- Medir com `benchmark` e perfiladores: cold start, primeiro conteúdo útil, primeira imagem, tempo até playback, scroll, frames lentos, heap e rede. Especificar dispositivo, cenário, amostra e build.
- Baseline e meta por métrica; alvo inicial de até 5% de frames lentos no percurso definido é proposta a calibrar, não resultado obtido.
- Comparar conta, perfil e listas em sessões prolongadas; observar coroutines/listeners, leaks de Activity/View, decode de arte e callbacks de telas descartadas.
- Testar migrações e atualização sem perda de progresso, favoritos, raiz ou perfil; recuperação de interrupção e cache cheio.
- Auditar BuildConfig e APK gerado sem imprimir valores de credenciais. Remover dependência de tokens privados embutidos do caminho de distribuição; escolher atualização e sessão compatíveis com o modelo operacional.
- Revisar erros redigidos, backup/restauração e log exportável opt-in. Telemetria não deve coletar dados pessoais por conveniência.

**Gate:** sem P0/P1 conhecido, sem crescimento de memória persistente inexplicado nos cenários, artefato sem segredo privado identificável e upgrade testado. Performance em AVD não substitui TV física modesta.

### Onda 10 — Homologação e candidato de entrega

Executar matriz declarada: AVD Android TV 720p/1080p; instalação limpa/upgrade; conta válida/expirada; biblioteca pequena/grande; rede normal/lenta/interrompida; ambos os motores suportados; movimento reduzido e idioma pt-BR. Acrescentar TV/box físico antes de classificar como homologado para esse hardware.

Percursos críticos obrigatórios:

1. Primeiro uso e recuperação de sessão.
2. Seleção/troca da raiz e pastas profundas.
3. Perfil, Home e retenção de foco.
4. Busca → detalhes → episódio → Back.
5. Áudio/legenda → seek/skip → pausa → retomada.
6. Próximo episódio/fim de fila/cancelamento.
7. Erro de rede/permissão e retry.
8. Configurações/cache → restart → upgrade preservando dados.

**Artefatos de saída:** APK identificado e hash, resultado de build/testes/lint, roteiro de D-pad, capturas reais, métricas, matriz de dispositivos e falhas residuais com severidade. Release assinado e atualização instalada são verificações separadas. Publicação remota não é automática.

**Gate final:** todos os percursos críticos passam no ambiente declarado, zero P0/P1, ressalvas conhecidas e revisão visual humana aprovada. Não usar “90%” ou “Apple grade” como substituto desse conjunto de resultados.

## Sequência imediata de trabalho

1. Q00: baseline e capturas válidas.
2. Q01/Q02: corrigir contratos das provas ao evoluir componentes existentes, integrar uma Home navegável e testar retorno/foco.
3. Q03: refinar a composição dessa mesma jornada com screenshots reais; não abrir mais features enquanto os cortes e o foco continuam ruins.
4. Q05: tratar falhas de saída/progresso como prioridade; completar um percurso de reprodução nos dois motores.
5. Q04/Q06: fechar detalhes/biblioteca e invalidação da raiz sobre o percurso funcionando.
6. Q07: concluir perfis e jornada de conta; o backend pode avançar como trilha independente depois de seus contratos estarem definidos.
7. Q08/Q09/Q10: aplicar acessibilidade/performance a cada entrega e ampliar homologação.

Nenhum bloqueio de referência deve impedir corrigir uma falha reproduzível no Makimono. Mais REA/JADX só entra quando responde uma pergunta que muda o código a entregar.

## Como medir avanço sem produzir outro relatório otimista

Manter um registro por tarefa com estados: planejada → implementada → compilada → testada em unidade → integrada → observada em AVD → homologada no hardware declarado. Nem todas as tarefas exigem cada tipo de teste; registrar por que um gate não se aplica.

Cada entrega responde: o usuário consegue fazer o quê agora, qual caminho foi exercitado, onde está a evidência e qual falha ainda impede uso? Screenshot e teste de política são evidências diferentes.

Não atribuir duração fixa à integração com base nos protótipos. Após Q00 e a primeira integração, estimar cada tarefa com dependências, faixa e confiança. Orçamento de investigação é limitado; o backlog de funcionalidades permanece subordinado aos gates de confiabilidade e legibilidade.

## Comandos e ambiente Windows

Confirmar o destino de `X:` antes de usá-lo. Se não existir, mapear para o checkout; se apontar para outro diretório, não sobrescrever silenciosamente. Os exemplos abaixo usam o mapping correto já confirmado:

```powershell
subst X: "C:\Users\Cauã V\Documents\antigravity\intelligent-mendel"
& X:\gradlew.bat -p X:\ assembleDebug
cmd /c "cd /d X:\ & set TEMP=C:\Temp& set TMP=C:\Temp& set GRADLE_USER_HOME=C:\Temp\.gradle& gradlew.bat testDebugUnitTest"
& X:\gradlew.bat -p X:\ lintDebug
& X:\gradlew.bat -p X:\backend test
```

Criar `C:\Temp` se necessário, sem apagar conteúdo existente. Não executar builds pesados concorrentes. Obter o serial real com `adb devices -l`; confirmar Android TV e boot concluído. Instalar o APK realmente gerado da variante debug `.visualreview`, informando nome e caminho existentes, não um nome de saída presumido.

```powershell
adb -s emulator-5554 install -r <apk-debug-existente>
adb -s emulator-5554 shell input keyevent KEYCODE_DPAD_RIGHT
adb -s emulator-5554 shell input keyevent KEYCODE_DPAD_CENTER
adb -s emulator-5554 shell input keyevent KEYCODE_BACK
```

`emulator-5554` é exemplo baseado no último registro; confirmar a identidade antes de executar. Testes de foco instrumentados exigem APK de teste instalado e runner correto. Testes do backend que dependem de PostgreSQL precisam declarar se foram executados ou ignorados; uma suíte parcial não valida migração/transações reais.

## Handoff pronto para o próximo agente

> Leia a revisão independente V0–V5 e este plano. Atualize o estado dos arquivos antes de editar. Comece por Q00, depois entregue Q01/Q02 integrados ao código existente, preservando recursos de foco/player já implementados. Use as três provas como referência de contrato, corrigindo os limites demonstrados; não as copie como soluções prontas. Compile, instale em Android TV AVD e valide por D-pad com capturas reais. Prossiga pelas ondas conforme dependências e gates. Registre falhas com precisão, preserve dados de produção e não publique automaticamente. A cada entrega, apresente o comportamento que passou a funcionar e sua evidência, além das pendências que restam.
