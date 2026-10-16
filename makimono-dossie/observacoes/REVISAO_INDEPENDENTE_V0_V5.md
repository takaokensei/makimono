# Revisão independente das entregas V0–V5

Data: 14/10/2026. Escopo: registro fornecido, documentos criados, doze dumps XML, seis arquivos Kotlin das provas e confronto com partes relevantes do Makimono. Não houve nova sessão de navegação do app, build Android, reprodução ou benchmark nesta revisão.

## Veredito da revisão

Há evidência nova e útil de execução da Home/detalhes da referência e há protótipos Kotlin que compilam. Isso justifica avançar para integração no Makimono. A declaração de V0–V5 integralmente concluídas, “90%+ de aderência”, integração “perfeita” e player completamente mapeado excede a evidência preservada.

Os documentos originais foram preservados. Esta revisão é o complemento de confiança que orienta o plano de implementação; não pressupõe que o app instalado apresente os mesmos defeitos dos protótipos isolados.

## Arquivos examinados

- `observacoes/`: estado inicial, inventário, roteiro runtime, matriz de evidências, matriz de aproveitamento e veredito.
- `jadx-notes/correcoes-do-dossie.md` e as três notas em `jadx-notes/evidencias/`.
- Todos os doze XMLs em `capturas/`.
- As três classes de prova e seus três arquivos de teste em `provas/prova_01_*`, `prova_02_*` e `prova_03_*`.
- Contratos e integração existentes: build Android, Home/rail/foco, `OsdStateMachine`, `SkipController` e documentação do backend.

## O que os dumps sustentam diretamente

- `cr_tv_live_ui.xml`: foco em `startWatching`; `homeFeedComponent` inicia em x=160px; `homeNavDrawer` tem bounds `[0,386][160,490]`.
- `step3_right2.xml`: foco em `panelCard_1_3`.
- `step4_up.xml`: foco volta a `startWatching`.
- `step5_down_test_memory.xml`: foco em `panelCard_1_3`. A sequência preservada sustenta o retorno ao terceiro card nesse cenário.
- `step6_rail.xml`: item Home possui bounds até x=528px e o feed inicia em x=528px. Sustenta alteração da geometria do conteúdo nessa captura; não determina curva de animação.
- `step7_back.xml`: foco em `exitDialogConfirm`.
- `step8_dismiss.xml`: nó focado no rail, com bounds `[0,386][528,490]`.
- `step9_right_to_content.xml`: foco em `panelCard_1_1`, rail até x=160px e feed a partir de x=160px.
- `details.xml`: foco em `showDetailsHeroWatchlistButton`; `return_from_details.xml`: foco em `panelCard_1_1`. Sustenta o retorno ao item de origem nessa sequência.
- A Home e detalhes capturados contêm `ComposeView`. A afirmação sobre toda a aplicação e todos os estados requer cobertura adicional.

As conversões para 80dp/264dp dependem da densidade de 320dpi registrada pelo agente. Esta revisão analisou os arquivos offline e não revalidou a configuração atual do AVD. Medidas de semântica podem incluir áreas de hit target e filhos agregados, não apenas a imagem ou card visível.

## O que permanece sem comprovação suficiente

1. Não há screenshot PNG ou vídeo preservado na pasta `capturas/` desta entrega; os doze arquivos são XMLs. Eles não mostram sombras, cores, gradientes, tipografia renderizada, recorte de imagens ou fluidez. As tentativas de screenshot no registro não produziram arquivo visual entregue.
2. Não há dump/gravação de reprodução, trilhas, skip ou Up Next. Timers exatos, foco desses controles e contador de 15s não podem ser chamados de comportamento observado com base nesse conjunto.
3. O roteiro preservado entra no rail por LEFT e testa Back no rail/diálogo; não contém uma captura específica da transição Back do conteúdo para o rail. Essa regra deve ser confirmada ou adotada como escolha Makimono.
4. As três notas estáticas repetem o mesmo Evidence ID e apresentam excertos/sínteses, sem exportação do registro canônico que associe cada resultado ao método/argumentos. Não é evidência suficiente para atribuir esse ID indistintamente a todas as inspeções.
5. Não foi demonstrado um transplante isolado para concluir inviabilidade geral de todo código decompilado. A recomendação de reconstruir os componentes locais é plausível, mas “custo infinitamente superior”, “0% possível” e “100% precisão” não são resultados medidos.
6. Não existe conjunto finito de requisitos/testes que sustente “90%+ de aderência”. Remover esse número dos critérios de planejamento.
7. O hash do split xhdpi transcrito em `estado-inicial.md` diverge do hash preservado em `datas.md` e da verificação anterior. Tratar como erro de transcrição até conferir; usar hash calculado do arquivo, nunca copiar o campo incorreto para nova evidência.
8. A descrição de certificado/carimbo e os comandos citados não substituem saída verificável e uma referência confiável de fingerprint. Evitar concluir procedência apenas pelo DN do certificado.

## Verificação executável das três provas

Comando reproduzível, a partir da raiz do projeto:

```powershell
& '.\makimono-dossie\provas\auditoria-2026-10-14\run-proof-audit.ps1'
```

O runner usa apenas dependências já disponíveis no cache local, Kotlin compiler embeddable 1.9.24, JUnit 4.13.2 e Java existente. Não usa o build Android, não baixa dependências e não altera os protótipos originais. Seus resultados e hashes de entrada ficam em `provas/auditoria-2026-10-14/`.

- Compilação dos protótipos e testes: passou; um aviso de variável não utilizada no rail.
- Testes originais das provas: **15 executados, 15 passaram** (`original-tests.log`).
- Auditoria de contratos de integração: **4 executados, 4 falharam** (`contract-audit.log`). São novos requisitos de robustez explicitados pela revisão, não a suíte de produção do Makimono.
- `results.json` registra separadamente os códigos de saída; o runner devolve falha quando qualquer conjunto falha. Não ocultar esses resultados em um resumo verde.

### Contratos não atendidos

1. **Restaurar item ativo do rail ao cancelar saída:** selecionar índice 3, abrir diálogo e cancelar resulta no índice 0. Estado do diálogo não preserva o índice anterior.
2. **Não manter identidade antiga ao atualizar memória sem ID:** recordar um item com ID e depois outro índice sem ID mantém o ID anterior, divergindo os mapas.
3. **Restaurar identidade após reordenação:** o item `b`, antes em índice 1, é substituído por `a` no mesmo índice após reordenar a lista. O ID armazenado não é usado por `resolveTargetIndex()`.
4. **Evitar reaparecimento do skip antes de concluir seek:** após `performSkip()`, um tick com a posição antiga torna o botão visível de novo. Não há estado pending/ack/cancel do comando.

### Outros limites de integração vistos no código

- A prova de rail não cria View, animação, scrim, diálogo ou conexão com o dispatcher de Back; é uma política pura.
- A prova intitulada hero/prateleiras contém apenas memória de foco, sem hero, RecyclerView, adapter, carregamento de arte ou renderização.
- A prova de OSD não possui View, engine, relógio/scheduler independente, suspensão por modal/seek, duração da mídia, validação de intervalos ou restauração de foco.
- As classes em `makimono-dossie/provas/` não pertencem aos source sets padrão do app; não foi encontrada inclusão desse caminho no build nem referência às classes em `app/src`. Rodar o `FocusMemoryTest` do app não executa automaticamente esses testes.
- Já há `FocusMemory`, `FocusCoordinator`, `RailNavigationPolicy`, `OsdStateMachine` e `playback/session/SkipController` no app. O controlador de skip existente possui capítulos, merge AniSkip, auto-skip e fallback. Substituí-lo pelo protótipo menor pode remover funcionalidades.
- A documentação do backend continua declarando pareamento local sem OAuth real/aprovação/claim/refresh. As provas não fecham esse fluxo.

## Decisão para o próximo plano

Usar as capturas de Home/detalhes como evidência delimitada de navegação e geometria. Usar as três provas como material de projeto e testes, não como componentes prontos para copiar para produção. Reconciliar os contratos com o código existente, construir uma jornada integrada e validá-la visualmente no AVD Android TV. Continuar análise estática apenas para perguntas que mudem uma decisão real de implementação.

Plano sucessor: `docs/ui-modernization/MAKIMONO_PRODUCTION_EXECUTION_PLAN.md`.
