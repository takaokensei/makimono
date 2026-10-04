# Makimono — plano contínuo de qualidade orientado pela HIG

Data: 04/10/2026. Base revisada: `bb71c27`. Status: C0.1 corrigido, build e 171 testes unitários aprovados; revalidação funcional isolada no fixture concluída.

## 1. Objetivo e autoridade

Chegar a uma experiência comercial refinada e continuar melhorando com evidências: conteúdo em primeiro plano, hierarquia clara, navegação previsível, acessibilidade, movimento preciso e recuperação confiável de falhas.

A referência vinculante é a missão HIG v2 enviada pelo usuário, com seus PDFs locais e desvios registrados em `HIG_NOTES.md`. Ela substituiu a direção anterior de glow permanente. Valores de escala, margens e durações daquele documento são decisões de adaptação do projeto; não devem ser apresentados como especificações universais da Apple para Android.

Preservar Kotlin/Views/XML, layouts mobile, Room, Glide, Google Drive, MAL, AniSkip, OpenSubtitles, atualizador, ExoPlayer e MPV. Nenhuma funcionalidade é removida para simplificar uma tela. HIG orienta a experiência; integridade dos dados e operação das integrações têm critérios adicionais neste plano.

As restrições vigentes continuam valendo: commits locais por ciclo; sem push, tags de release ou alteração de workflows. Preservar o pacote principal, sessão Drive e dados. Testes destrutivos ou gravações de teste usam pacote, conta e banco isolados. Credenciais ausentes exigem informação do proprietário; nunca presumir ou registrar seus valores em evidências.

Fontes de trabalho: `HIG_CONFORMANCE.md`, `HIG_NOTES.md`, `DESIGN_SYSTEM.md`, `AUDIT.md`, `FRONTEND_LOG.md`, missão HIG v2 e código atual. Consultas posteriores à HIG devem registrar data e eventual divergência, sem trocar silenciosamente a referência aprovada.

## 2. Dois horizontes

**Horizonte A — fechar a versão comercial:** resolver bloqueadores conhecidos e cumprir os gates dos ciclos C0–C10. Existe um critério de conclusão verificável; não depender de uma impressão de “1000/10”.

**Horizonte B — melhoria contínua:** repetir auditorias por área, incorporar problemas do uso real e evoluir o backlog. Uma área aprovada continua sujeita a regressões e novas evidências. Não inventar defeitos nem alterar uma tela aprovada apenas para manter atividade.

“Ad infinitum” descreve o processo de evolução. Este documento não agenda execuções em segundo plano; cada continuação retoma o próximo item do backlog.

## 3. Estado de partida

- Existem tokens semânticos, seis temas, persistência de perfis Room, catálogo remoto de arte e OSD com opções secundárias.
- Home, Details, Files, perfis e diálogos receberam correções pontuais de layout e D-pad.
- A última validação registrada tem 171 testes unitários passando. É uma referência histórica, não prova de que todos os fluxos funcionam.
- Muitas capturas usam `zechs.drive.stream.visualreview`, sem autenticação ou playback real.
- Configurações ainda não entrega o split-pane da missão; foco/motion continuam heterogêneos; modo de efeitos reduzidos ainda falta.
- Reprodução real nos dois motores, conta Drive, persistência exercitada, performance medida e release final compatível continuam como gates abertos.

### C0.1 — fidelidade do fixture Home

**Problema observado:** a fixture sintética de revisão visual mostrava os títulos da prateleira no campo destinado ao nome da série, deixava o badge superior vazio e ocultava o episódio na linha inferior. Isso distorcia a avaliação visual da Home e não correspondia ao binder de produção `ContinueWatchingAdapter`.

**Alteração limitada a `src/debug`:** os cards sintéticos agora mostram o nome da série, badge `Ep. NN`, episódio e tempo restante. A Home real, integração de playback e dados da conta não foram alterados.

**Verificação:** `assembleDebug testDebugUnitTest --no-daemon -PdistributionBuild --max-workers=1` concluído com sucesso (171 testes; 0 falhas, erros ou ignorados). Para este host, `TEMP/TMP=C:\temp`, `GRADLE_USER_HOME=G:\` (atalho temporário para o cache existente) e checkout em `X:\` contornaram a falha de loopback e o classpath sob o perfil com acento. O APK x86_64 foi instalado somente em `zechs.drive.stream.visualreview` no MuMu `127.0.0.1:16384`. Capturas antes/depois e hierarquia estão em `docs/ui-audit/after-v2/c0/`. No percurso D-pad RIGHT → DOWN → UP, a hierarquia confirmou foco em `btnFeaturedInfo` e retorno a `btnFeaturedPlay`; o screenshot confirma o anel visual de foco.

**Limite:** a primeira tentativa Windows falhou ao resolver o classpath do worker. Repetir com cache e checkout acessados por unidades ASCII confirmou a execução correta. A tentativa WSL/Linux foi descartada porque o SDK Android deste host só contém ferramentas Windows. Pacote principal e dados permaneceram intactos; sem releases, tags ou push.

## 4. Prioridade e seleção

**P0 — funcionamento e dados:** saída/navegação travada, crash/ANR, perda ou mistura de dados, sessão, reprodução, progresso e atualização incompatível. Interrompe polimento da área afetada.

**P1 — ergonomia e acessibilidade:** foco perdido, ação inacessível, texto essencial cortado, contraste insuficiente, estados ambíguos, informação técnica incorreta e fluxos principais difíceis.

**P2 — linguagem visual e descoberta:** composição, consistência entre telas, busca, loading, arte, diálogos, hierarquia e movimento.

**P3 — refinamento e evolução:** microinterações, ajustes de desempenho após o gate mínimo, recursos adicionais e manutenção da matriz.

Dentro de uma prioridade, ordenar por frequência no percurso real, impacto e capacidade de reprodução. Dependências estruturais podem antecipar trabalho de menor severidade; registrar o motivo. Cada ciclo tem um problema principal e correções necessárias a ele.

## 5. Sequência inicial de execução

### C0 — baseline operacional e backlog único

Prioridade: P0/P1. Dependência: nenhuma. Correspondência original: reconciliação F0 e gates F1–F7.

1. Atualizar estado Git, reler as regras da área e separar defeito atual de problema histórico já corrigido.
2. Inventariar telas, diálogos, opções e os dois motores; confrontar com todas as funções originais.
3. Reproduzir os bloqueadores conhecidos e transformar cada um em item com ID, cenário, esperado, observado, arquivo, regra HIG e evidência.
4. Preparar dados isolados: mídia de teste com trilhas/legendas/capítulos, biblioteca paginada e perfis distintos. Usar conteúdo local/servidor de teste para testar players sem depender de OAuth; Drive precisa de percurso próprio autenticado.
5. Reconciliar a matriz atual: distinguir IMPLEMENTADO, VALIDADO e ACEITO. F22 não equivale à conclusão da F7 original.

Arquivos: documentos de auditoria, `VisualReviewActivity` e `app/src/androidTest/` conforme necessário.

Aceite: backlog ordenado, mapa de paridade e cenários reproduzíveis. Nenhum item marcado aprovado só por compilação ou fixture.

### C1 — contrato de reprodução e navegação nos dois motores

Prioridade: P0. Dependência: C0. Correspondência: F6; H-R*, H-F5, H-PV*, H-V1.

- Testar ExoPlayer e MPV com mídia real e os mesmos cenários de entrada/saída.
- Seta visível encerra o player; BACK dispensa modal, depois OSD, depois sai; CENTER não dispara dupla ativação; Play/Pause e Espaço alternam reprodução uma vez.
- Direita/esquerda com OSD oculto fazem seek com feedback discreto e acumulação coerente; foco não aparece no vídeo com OSD oculto.
- Verificar buffering, falha recuperável, rede restabelecida, retomada, troca de episódio e liberação do engine ao sair.
- Preservar áudio, legendas avançadas, velocidade, proporção, capítulos, info técnica, episódios, skip, PiP, bloqueio e gestos.
- Testar AniSkip, autoplay, cancelar contagem, último episódio e pedido de avaliação em pausa apropriada.
- Compartilhar políticas entre motores onde já houver abstração útil, preservando diferenças necessárias de decodificação e lifecycle.

Arquivos: `PlayerActivity.kt`, `MPVActivity.kt`, `PlayerViewModel.kt`, `PlaybackCoordinator.kt`, `PlayerControllerVisibility.kt`, helpers de input/modal e testes relacionados.

Aceite: repetir os percursos por toque e D-pad em ambos os engines; saída sempre alcançável, progresso recuperado, sem dupla ação, foco oculto ou bloqueio. Evidência separada de fixture, mídia local e Drive.

### C2 — sessão, persistência e integridade dos metadados

Prioridade: P0/P1. Dependência: C0; pode alimentar C1. Correspondência: invariantes funcionais e H-U*.

- Exercitar refresh antes da expiração e após 401, chamadas concorrentes, 403/429/5xx, timeout, cancelamento e ausência de rede. Preservar sessão válida; reconectar só quando necessário.
- Verificar paginação, ordenação natural de episódios, duplicações, limites de listagem e arquivos removidos. Limites devem ficar claros, sem sugerir lista completa quando truncada.
- Testar Room/migrações, CRUD de perfis, perfil ativo, isolamento de histórico/favoritos/fila, exclusão transacional e backup/restauração após reiniciar.
- Confirmar progresso com duração desconhecida, seek, conclusão, replay e troca de perfil durante atualizações assíncronas.
- Remover afirmações técnicas não confirmadas: resolução, áudio e legendas devem vir de informação real ou aparecer como desconhecidos/ausentes. Rever `subtitleBadge = "Multi Subs"`.
- Admin/Kids permanecem status conforme escopo atual; controle parental/PIN constitui uma feature separada, com política explícita antes de implementação.

Arquivos: `DriveRepository.kt`, `TokenProvider.kt`, `TokenAuthenticator.kt`, `SessionManager.kt`, `ProfileManager.kt`, DAOs/Room, `BackupSerializer.kt`, `SeriesDetailViewModel.kt` e repositórios de histórico/favoritos/fila.

Aceite: falhas simuladas e fluxo autenticado documentados, sem perda de dados, mistura de perfis, loop de autenticação ou rótulos técnicos falsos. Sucesso de testes unitários não substitui persistência e rede reais.

### C3 — componentes, tipografia, foco e motion como sistema

Prioridade: P1. Dependência: C0. Correspondência: F1/F2; H-F*, H-B*, H-K*, H-Y*, H-O*.

- Consolidar tamanhos, espaçamento, cores, pesos, leading, raios e durações em tokens semânticos; migrar aliases de compatibilidade nas telas tocadas.
- Substituir a dependência de contorno uniforme pelo tratamento por componente previsto na missão: escala/elevação/preenchimento, com estados distinguíveis.
- Aplicar foco ao conjunto imagem+título do card; reservar espaço para expansão sem clipping; dimensionar decode da arte para o tamanho focado.
- Unificar animações canceláveis; evitar animação de layout, bounce excessivo e roubo de foco por metadados assíncronos.
- Padronizar primário/secundário/ícone/destrutivo, com pressionado e indisponível; manter alvos Android adequados e descrições semânticas.
- Validar todos os estados nos seis temas; não mudar silenciosamente a linguagem HIG v2 para a dos mockups anteriores.

Arquivos: `TvFocusRing.kt`, adaptadores, `MediaImageLoader.kt`, `values/`, temas, seletores e `DESIGN_SYSTEM.md`.

Aceite: galeria de componentes com todos os estados, valores medidos e navegação rápida sem cortes ou animação presa. Registrar adaptações Android antes de declarar conformidade.

### C4 — acessibilidade e efeitos reduzidos

Prioridade: P1. Dependência: C3. Correspondência: F7; H-A*, H-D*, H-O*.

- Implementar Efeitos visuais Completo/Reduzido persistido em `AppSettings`, com padrão compatível com `isLowRamDevice()`.
- Respeitar animações desativadas e suportar contraste aumentado quando disponível; reduzido usa superfícies opacas e foco imediato evidente, sem zoom/bounce excessivo.
- Validar fonte 1.0/1.3/2.0, leitura a distância, TalkBack/descrições, estado selecionado, sliders e feedback temporizado.
- Medir contraste de texto/ícones sobre arte clara/escura e vídeo, além das cores-base. Preferir scrims/superfícies adequadas a escurecer a arte inteira indiscriminadamente.
- Cobrir TV, landscape, tablet e portrait; reflow deve preservar ações e funções, sem esconder texto essencial.

Arquivos: `AppSettings.kt`, `TvAccessibilityLayout.kt`, `FrostedWindow.kt`, layouts/estilos e componentes do C3.

Aceite: matriz de acessibilidade sem bloqueadores nas combinações avaliadas; preferências sobrevivem a reinício; fallback legível em API abaixo de 31 e dispositivo sem blur.

### C5 — Configurações em duas colunas

Prioridade: P1. Dependência: C3/C4. Correspondência: F5; H-NV2/H-NV6/H-NV7/H-F8.

- Inventariar todas as opções existentes e agrupá-las em Perfil, Biblioteca, Aparência, Reprodução, Armazenamento e Atualizações, incluindo integrações de conta.
- TV/landscape: categoria à esquerda, conteúdo à direita, título único e seleção persistente. Portrait mantém organização adequada a toque.
- Direita entra nas opções, esquerda retorna à categoria, BACK segue a hierarquia; mudança de categoria não perde valores em edição.
- Ações abrem diálogos consistentes; limpeza, logout e restauração explicam o efeito e oferecem recuperação/confirmam quando necessário.

Arquivos: variantes de `fragment_settings.xml`, `SettingsFragment.kt`, `AppSettings.kt` e diálogos envolvidos.

Aceite: nenhuma opção desaparece; foco inicial/restauração corretos; 2× legível; tema, player e efeitos persistem; ações reais verificadas em dados isolados.

### C6 — composição cinematográfica de Home, Details e perfis

Prioridade: P2; defeitos de acesso são P1. Dependência: C2/C3/C4. Correspondência: F3/F4.

- Home: primeira dobra equilibrada, hero adaptável, scrims contínuos, CTA proporcional e prateleiras coerentes; skeleton imediato e estados vazios úteis. Não introduzir rotação automática de destaque.
- Details: poster/título alinhados, metadados compactos e verdadeiros, ações secundárias subordinadas, capítulos/episódios com progresso legível; corrigir bleed em todas as variantes reais.
- Perfis: avatar/nome/status consistentes, wallpaper completo, foco inicial no último usado, editor/galeria navegáveis e URL customizada com cache/fallback.
- Arte e metadados continuam vindo das fontes existentes; testar nomes longos, falta de poster/banner, URLs quebradas e catálogo sem rede.
- Comparar os enquadramentos com as referências, sem hardcode de títulos, gêneros ou imagens de demonstração na experiência real.

Arquivos: Home/SeriesDetail/Profile fragments, layouts e adapters, `MediaImageLoader.kt`, catálogo de arte e drawables de scrim.

Aceite: capturas 1×/1.3×/2× e caminhos de volta ao item original; sem cortes essenciais, fundo vazando, metadados fabricados ou placeholders permanentes. Salvar e reiniciar perfis em ambiente isolado.

### C7 — biblioteca, busca e descoberta

Prioridade: P1/P2. Dependência: C2/C3/C4. Correspondência: F4; H-S*/H-SE*/H-G*.

- Breadcrumbs utilizáveis em profundidade, busca com escopo reconhecível e troca grade/lista preservando posição/foco.
- Pesquisas recentes e sugestões antes da digitação, com limpar histórico; consulta explícita/debounce apropriado, cancelamento e resultados sem salto de seleção.
- Grade/lista com miniatura e metadados corretos, ordenação de temporada/episódio e feedback de paginação.
- Separar vazio, sem resultado, falta de permissão, sessão expirada e offline; cada estado oferece ação coerente.

Arquivos: `FilesFragment.kt`, `FilesViewModel.kt`, layouts/adapters, política de estado e persistência de busca conforme arquitetura existente.

Aceite: navegar por pastas profundas e biblioteca grande, pesquisar/limpar/voltar, alternar modos e recuperar rede sem duplicações ou dead-ends.

### C8 — família de OSD, menus e diálogos

Prioridade: P1/P2. Dependência: C1/C3/C4. Correspondência: F6; H-M*/H-Q*/H-N*/H-PV*.

- Finalizar ilha OSD e Play/Pause dominante; tempo restante, scrubber e ações secundárias conforme missão.
- Unificar temas, proporções, títulos de tarefa, seleção das trilhas e saídas dos diálogos. Não empilhar fundos ou modais concorrentes.
- Abrir modal oculta OSD; dispensar restaura contexto/foco; título e disponibilidade vêm das trilhas reais.
- Validar tamanhos extremos de listas, 2×, vídeo claro/escuro, blur suportado/desativado e mobile.

Arquivos: controles XML dos players, `PlayerGlassMenuDialog.kt`, `PlayerModalHost.kt`, `PlayerEpisodeDrawerDialog.kt`, `SubtitleStyleDialog.kt`, `MalRatingDialog.kt` e `FrostedWindow.kt`.

Aceite: todas as funções acessíveis nos dois motores, uma tarefa por modal, nenhuma costura visual/foco ambíguo e nenhuma interação que bloqueie a saída.

### C9 — desempenho e durabilidade da experiência

Prioridade: P0 se ANR/crash; P1 para navegação severamente lenta; restante P3. Dependência: componentes/telas entregues.

- Medir `gfxinfo` em varredura rápida de Home/Files e preservar o orçamento original: no máximo 5% de frames lentos no percurso documentado.
- Registrar startup, carregamento de imagens, memória, GC, requisições repetidas e tempo de recuperação; definir orçamentos adicionais após medir o baseline, sem números fictícios.
- Ajustar tamanho de decode/cache, lifecycle de observers, trabalho fora da UI e pedidos canceláveis; blur conhecido reduzido/cacheado conforme missão.
- Testar sessão prolongada, repetidas entradas/saídas dos players e mudança de configurações; executar em MuMu e TV física/aparelho limitado quando disponível.

Arquivos: loaders/cache, ViewModels/adapters, players e somente hotspots identificados por medição.

Aceite: evidência comparável antes/depois, orçamento de frames cumprido, sem crescimento contínuo inexplicado de memória nem regressão de navegação. Limitação de hardware registrada quando não houver aparelho real.

### C10 — regressão completa e candidato a release

Prioridade: gate de entrega. Dependência: C1–C9.

- Executar suíte unitária atual e testes instrumentados de hero, Details, OSD e migração, além dos novos testes de integração necessários.
- Repetir o percurso completo por toque/D-pad, nos dois players, incluindo erro/recuperação, reinício e atualização sobre versão anterior.
- Compilar Debug e Release com R8; verificar ABIs, assinatura compatível, versão, assets/checksums e comportamento do atualizador.
- Atualizar as 12 comparações-base, rubrica por tela e matriz HIG. Não substituir teste real por screenshot de fixture.
- Preparar notas locais, limitações conhecidas e rollback. Publicação segue a autorização/restrições vigentes do proprietário.

Aceite: nenhum P0/P1 aberto no escopo de entrega; todas as regras aplicáveis com OK ou desvio justificado/evidenciado; dimensões da rubrica ≥9 conforme missão; Debug/Release e testes verdes. Release com chave debug não atende compatibilidade de atualização.

## 6. Loop de cada ciclo

1. **Observar:** estado atual dos arquivos, regra/PDF da área, captura e percurso real. Selecionar o próximo problema reproduzível; incorporar feedback recente.
2. **Planejar:** 5–10 linhas em `FRONTEND_LOG.md` com problema, severidade, arquivos, hipótese, aceite e risco de regressão.
3. **Implementar:** mudança pequena e completa; manter paridade, padrões existentes e cancelamento de trabalho assíncrono.
4. **Verificar:** build/checks/testes pertinentes; instalar no pacote adequado; navegar e medir antes/depois. Ampliar cobertura quando houver risco novo.
5. **Auditar novamente:** procurar regressão na área vizinha e cenário de borda. Se falhar, reparar e repetir o gate.
6. **Registrar:** ID/regra, valor medido, captura/dump, ambiente, arquivos e status de evidência; atualizar matriz e backlog; commit local com rollback.
7. **Continuar:** escolher o próximo item. Ao esgotar o backlog conhecido, iniciar a próxima rotação de auditoria; ausência de defeito mantém a área aprovada, sem refatoração artificial.

## 7. Evidência e critérios de encerramento

Cada item registra separadamente: inspeção de código, testes unitários, fixture visual, integração com mídia/rede, TV física e build Release. “Não exercitado” nunca vira OK. Não calcular porcentagem de conformidade sem inventário completo das regras aplicáveis.

Evidências novas: `docs/ui-audit/after-v2/continuous/<ciclo>-<item>/`, com capturas, UI dumps, passos, ambiente e medições. Redigir dados de sessão, nomes sensíveis e tokens antes de versionar.

Ficha mínima de backlog: ID; prioridade; regra HIG/critério operacional; tela; cenário; esperado/observado; arquivo; dependência; aceite; evidência; estado (aberto, em andamento, implementado, validado, aceito, bloqueado por condição externa).

Bloqueio de conta/hardware não impede trabalho independente. Registrar exatamente o que falta e pedir só a informação necessária, sem assumir credenciais. Dados de teste permanecem isolados do uso pessoal.

## 8. Rotação permanente após C10

1. **Percurso cotidiano:** abrir → retomar → mudar episódio → voltar → continuar no mesmo item. Medir passos, hesitações e foco recuperado.
2. **Descoberta:** arte, biblioteca grande, busca, nomes longos, conteúdo sem metadados e diferentes preferências.
3. **Reprodução:** outro codec/container, tracks, ASS/SSA, capítulos, OP/ED, fim de série, erro e retomada, nos dois motores.
4. **Acessibilidade:** seis temas, fontes ampliadas, contraste, efeitos reduzidos, leitura a distância e controles alternativos.
5. **Resiliência:** offline/latência, token expirado, CDN indisponível, arquivos removidos, reinício, perfil e migração.
6. **Desempenho:** repetir percursos de medição, sessão prolongada, hardware limitado e atualização.

Após a sexta rodada, retornar à primeira com o feedback acumulado. Regressões retomam P0/P1 imediatamente. Novas features só entram quando resolvem uma necessidade observada e têm aceite, ergonomia e custo de manutenção definidos.

Cadência sugerida, acionada por trabalho/uso: em cada mudança, regressão da área; em cada candidato a release, percurso completo; após feedback real, reproduzir e priorizar; após mudanças de Android/engines/APIs, revisão de compatibilidade. Revisão periódica mensal é uma proposta, não uma automação criada.

## 9. Resultado esperado por rodada

Um problema resolvido e comprovado, uma experiência mais coerente, nenhuma função perdida, dados preservados e o próximo conjunto de hipóteses priorizado. O acabamento visual evolui junto da confiabilidade; o backlog permanece vivo depois de atingir o gate comercial.

Referências oficiais complementares: [Focus and selection](https://developer.apple.com/design/human-interface-guidelines/focus-and-selection/), [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility), [Materials](https://developer.apple.com/design/human-interface-guidelines/materials) e [Designing for tvOS](https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos). A aplicação no Android deve preservar controles e convenções da plataforma.
