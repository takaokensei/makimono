# Prompt de execução — GPT‑6 Luna, esforço alto

Copie o bloco abaixo como primeira mensagem no chat de implementação. Configure manualmente **GPT‑6 Luna / Alto** no seletor do Codex; este arquivo não muda configurações de um chat nem inicia outro agente.

---

Você é responsável por executar o plano premium do Makimono em ciclos pequenos, verificáveis, preservando funcionalidades e dados. Trabalhe no projeto `C:\Users\Cauã V\Documents\antigravity\intelligent-mendel`.

Leia primeiro:

1. `docs/ui-modernization/APPLE_GRADE_PRODUCT_PLAN.md` — objetivo, backlog Q00–Q26, dependências, gates e referências.
2. `docs/architecture/ADR-001-TV-PAIRING.md` — proposta de QR/celular/backend e contratos; não é infraestrutura já disponível.
3. `docs/ui-modernization/REAUDIT_2026-10-04.md`, `CONTINUOUS_IMPROVEMENT_PLAN.md`, `HIG_NOTES.md`, `HIG_CONFORMANCE.md`, `DESIGN_SYSTEM.md` e `FRONTEND_LOG.md`.
4. `README.md`, `GOAL.md`, `docs/ui-modernization/AUDIT.md`, instruções atuais do usuário e AGENTS.md aplicáveis. `AUDIT_REPORT.md` é nome histórico citado na missão, ausente no checkout de planejamento; localizar fonte equivalente em vez de criar relatório fictício ou parar.
5. PDFs relevantes em `C:\Work\refs\hig`, partes gerais/tvOS. Leia antes da edição e registre página/regra/adaptação; rasterize tabelas/figuras ausentes na extração. Não copie PDFs para o repo.

## Autoridade e preservação

- O pedido novo autoriza planejar/implementar login QR, features e backend, ampliando o “fora de escopo” antigo. Media3 já foi autorizado e iniciado. Não repetir o legado como veto às novas tarefas.
- Preserve Kotlin/Views/XML, Hilt, Glide, Room, Drive, MAL, MPV, Media3/ExoPlayer, AniSkip, OpenSubtitles, estilos/offsets/ASS/SSA, fila, favoritos, perfis e atualizador.
- HIG v2 é a referência de design: não retornar a glow permanente. HIG não é certificação para Android nem especificação OAuth/backend.
- Atualize estado dos arquivos/checkout e aparelho antes de retomar; há mudanças locais do proprietário e Media3. Não resetar, sobrescrever ou incluir alterações alheias indiscriminadamente.
- Não criar branch se a instrução vigente manda main. Não duplicar/forçar tag pre-ui-v2. Não publicar/push/tag de release/alterar workflow sem instrução atual específica. Commit local apenas do escopo validado, sem assumir autoria das mudanças anteriores.
- Não limpar sessão, banco, app release ou conta do owner para testar. Use variante/pacote, dados e conta isolados; teste real com consentimento do proprietário no fluxo normal. Não desconecte sua conta Google global.
- Nunca ler/imprimir segredos em logs, chat, screenshots ou patches. Valores secretos por configuração segura; não embutir credenciais default no APK.

## Algoritmo de cada continuação

1. Registrar `git status --short`, HEAD e diff pertinente; conferir planos/evidências e últimos tickets. Não assumir que uma fase marcada antes está igual hoje.
2. Escolher primeiro ticket de maior prioridade com dependências satisfeitas. Começar em Q00; depois Q01/Q02/Q03/Q04/Q05/Q06. Uma dependência externa bloqueia aquele ticket, não todo trabalho independente.
3. Registrar no FRONTEND_LOG um plano de 5–10 linhas: bug/benefício, arquivos/qualifiers, regra HIG e cenário de aceite. Antes/depois com mesma configuração.
4. Reproduzir o problema ou provar a lacuna por código. Distinguir release instalado, debug atual, preview e mocks. Um fixture não prova Google ou Drive real.
5. Alterar um problema principal por subticket, idealmente até 8 arquivos funcionais. Se precisa migrar banco, adaptar engines e construir servidor ao mesmo tempo, dividir antes. Reutilizar features/classes existentes, não criar cópias paralelas.
6. Executar build/testes adequados; falha é diagnóstico, não licença para remover teste/feature. Escolher teste de comportamento, não assert que repete a implementação.
7. Instalar apenas APK identificado/pacote isolado e navegar com D-pad/touch. Usar ADB; verificar resposta de saída/comando. Capturar screenshots e hierarquia após ações, não apenas tela de entrada.
8. Comparar visual, foco, dados e recuperação com o baseline e critérios do ticket. Se falhar, corrigir e retestar aquele cenário; não repetir toda a matriz sem mudança/risco que justifique.
9. Atualizar evidências, HIG_CONFORMANCE, FRONTEND_LOG e tracker. Commit local de arquivos explícitos quando checks e autoria estão claros; nada de `git add .` num checkout com mudanças anteriores.
10. Continuar o próximo ticket independente. Ao fim do horizonte comercial, rodar Q26 e gerar backlog novo com defeitos/benefícios observados. Não inventar problemas só para trabalhar eternamente.

## Tracker obrigatório

Criar `docs/ui-modernization/EXECUTION_TRACKER.md` sem marcar tickets prévios como já aceitos. Para cada Qxx/subticket:

- Estado: NOT_STARTED → IN_PROGRESS → IMPLEMENTED → BUILT → RUNTIME_VALIDATED → ACCEPTED. BLOCKED_EXTERNAL tem dependência concreta; não equivale a concluído.
- HEAD/diff base, objetivo, dependências, arquivos, regras HIG/desvios.
- Comandos e resultados; artefato/hash, engine, pacote, Android, densidade/font/theme/rede, dataset.
- Evidência real/fixture/mocked, esperado/obtido, pendências e próximos passos.
- Critério de aceite específico e quem/quais evidências permitem aceitá-lo.

`IMPLEMENTED` não significa `ACCEPTED`; unit tests verdes não provam UX; screenshot não prova persistência; vinheta de 3 s não prova streaming; Media3 import não prova codecs.

## Ambiente e comandos iniciais

MuMu normalmente usa `127.0.0.1:16384`; conferir `adb devices -l` antes. ADB conhecido: `C:\Users\CAUV~1\AppData\Local\Android\Sdk\platform-tools\adb.exe`. SDK real pode mudar, resolver pelo ambiente sem imprimir credenciais de local.properties.

Build básico Windows: `./gradlew.bat assembleDebug testDebugUnitTest --no-daemon`. Confirmar tasks/flavors/APK produzido com Gradle atual. `-x test` não serve de prova de gate. Não assumir que `-PdistributionBuild` instala pacote de review: conferir applicationId e paths antes.

Há workaround anterior de caminhos ASCII `X:\` (checkout) e `G:\` (cache) e TEMP/TMP `C:\temp` para classpath/worker Windows com acento; confirmar mapeamentos e usar somente se problema se reproduzir, sem mover/deletar projeto nem repurposar HOME/CODEX_HOME. Não executar WSL esperando binários SDK Windows funcionarem.

ADB D-pad: 19/20/21/22/23; BACK 4; Play/Pause 85. Comandos sempre com serial explícito quando houver mais de um aparelho. Font scale 1/1.3/2: guardar valor original e restaurar em finally. Não mudar escala/dados do release sem saber qual teste será afetado.

Matriz completa e budgets estão na seção 9–10 do plano. Respeitar todas as variantes pertinentes em `layout*`, `values*` e mobile/TV.

## QR/OAuth: decisões que você não pode simplificar incorretamente

- Google Device Grant não aceita drive.readonly; `drive.file` não equivale automaticamente à biblioteca recursiva atual.
- QR localiza desafio próprio; Google Authorization Code/callback HTTPS no backend; confirmação mobile; TV recebe sessão própria revogável.
- Padrão proposto BFF não entrega tokens Google à TV; proxy streaming exige Range/cancelamento/vazão/custos comprovados. Alternativa broker direto muda a exposição e depende de decisão registrada.
- TV guarda sessão Makimono protegida; segredo do servidor/grant Google ficam no servidor. Não usar localhost do celular como callback do servidor TV.
- Logout não é limpeza de cache; perfil não é conta OAuth; bool Admin local não autoriza servidor.
- OAuth Testing com Drive pode expirar em 7 dias. “Saved login” não é garantia de autorização eterna. Não forçar consentimento toda vez para mascarar refresh quebrado.
- Pergunte por projeto OAuth/domínio/hosting/orçamento somente quando a execução dependente precisar. Continue contratos/UX/tickets independentes enquanto aguarda. Não inventar conta, token, serviço hospedado ou consentimento aprovado.

## Como reportar progresso

Seja conciso e concreto: resultado, evidência, próximo risco. Durante execução longa, atualize o usuário regularmente. Antes de finalizar, informe tickets aceitos, pendentes e o motivo exato de qualquer gate externo.

Só declarar núcleo premium concluído quando zero P0/P1 conhecido e gates do escopo aprovado estiverem fechados com dados reais. Não atribuir “1000/10”, “100% Apple” ou “production ready” por estética ou contagem de testes. Se não houve build/deploy nesta rodada, diga isso expressamente.

Comece por Q00 e prossiga para Q01; não comece criando um backend público ou redesenhando a Home antes de corrigir os defeitos de integridade conhecidos.

---

Este prompt não agenda automação nem cria tarefa em outro modelo. A escolha do modelo/esforço é feita pelo proprietário no chat onde executar este roteiro.
