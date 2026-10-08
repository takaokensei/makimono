# Makimono — plano de produto premium, engenharia e aceite

Data: 04/10/2026. Destinatário: **GPT‑6 Luna, esforço alto**. Estado: **em execução; este documento define o backlog e os critérios, enquanto `EXECUTION_TRACKER.md` é a fonte de verdade para implementação e validação**. Tickets parcialmente implementados ou validados não são considerados aceitos até satisfazerem seus gates.

## 1. Resultado esperado

Entregar um streaming pessoal para Android TV e mobile no qual conectar a conta, encontrar conteúdo, retomar um episódio e sair do player sejam tarefas simples e previsíveis. A linguagem visual segue os PDFs HIG fornecidos, especialmente as partes gerais e tvOS. O acabamento inclui dados verdadeiros, persistência, acessibilidade, performance e recuperação de erros — não apenas aparência.

“Nível Apple” é uma meta editorial, não certificação da Apple para Android. O aceite exige os gates da seção 9; uma nota subjetiva ou screenshot favorável não os substitui.

O proprietário pediu expressamente **login por QR/celular, funcionalidades, customizações e backend** nesta rodada. Esse pedido amplia o escopo anterior da missão HIG v2, que excluía novo login e mudanças de lógica. A migração Media3 também foi autorizada posteriormente e já existe no checkout. Permanecem os invariantes de dados, compatibilidade e funcionalidades. A direção HIG v2 continua prevalecendo sobre os primeiros mockups: conteúdo em primeiro plano, foco inequívoco, materiais seletivos e nenhum glow permanente em todos os elementos.

Este documento organiza a implementação futura. Não autoriza contratar serviços pagos, mudar visibilidade do repositório, publicar credenciais, push, release ou migração destrutiva de contas. Uma publicação precisa de instrução atual específica do proprietário; planejamento e desenvolvimento local podem avançar.

## 2. Ponto de partida, sem recomeçar o projeto

Ler `REAUDIT_2026-10-04.md`, `CONTINUOUS_IMPROVEMENT_PLAN.md`, `HIG_NOTES.md`, `HIG_CONFORMANCE.md`, `DESIGN_SYSTEM.md`, `docs/ui-modernization/AUDIT.md`, `FRONTEND_LOG.md`, `README.md` e `GOAL.md`. A missão antiga menciona `AUDIT_REPORT.md`, ausente neste checkout; usar a auditoria existente e não interromper a execução por esse nome histórico.

- A reauditoria avaliou a maturidade em 6,5/10 e deixou R01–R08 abertos. Não usar esse valor como medição quantitativa de disponibilidade.
- O checkout estava em `853d3f7`, com mudanças locais anteriores preservadas. Ao executar, atualizar HEAD/status/diff; não presumir que continuam iguais.
- Kotlin/Views/XML, Hilt, Room, Retrofit/Coroutines/Flow e Glide permanecem. Não converter o app para Compose ou SwiftUI.
- Media3 contém o motor **ExoPlayer**; MPV continua alternativa. Não criar um terceiro player chamado “Media3” nem declarar migração concluída apenas por renomear o seletor.
- Home, arquivos, detalhes, perfis Room, galeria remota, fila, favoritos, seguir pastas, MAL, AniSkip, OpenSubtitles, estilos de legenda, backup e atualizador já têm implementação. Primeiro mapear e aproveitar, depois completar lacunas.
- `TvLoginServer.kt` existe, mas a busca de referências encontrou uso em testes, não no fluxo de produção. Sua página usa servidor HTTP local e callback loopback; isso não é um login cloud por QR pronto. `QRCodeGenerator.kt` é reutilizável como gerador. O QR do MAL não prova login Drive.
- Relatórios anteriores registram 171 testes JVM e 6 instrumentados; as contagens são históricas. Executar os testes pertinentes ao novo checkout. Preview não equivale a OAuth, streaming Drive ou uso de TV física.
- Min SDK 21 e quatro ABIs são restrições atuais; confirmar em Gradle. Não atualizar SDKs/dependências por copiar snippets recentes sem avaliar compatibilidade.

Os ciclos antigos C0–C10 não são descartados: C1 → Q03/Q17–Q19; C2 → Q01/Q02/Q08/Q09; C3/C4 → Q04/Q05; C5 → Q06; C6 → Q13/Q14; C7 → Q15/Q16; C8 → Q17/Q18; C9 → Q24; C10 → Q25/Q26. Q07–Q12 acrescentam o pareamento/backend solicitado agora.

## 3. Jornada de produto e escolhas de escopo

### Primeiro uso e sessão

1. Abrir uma apresentação curta, sem vinheta obrigatória. Mostrar **Conectar Google Drive**, descrição do acesso e opção de explorar a apresentação sem dados pessoais.
2. TV exibe QR, endereço HTTPS curto e código legível. O QR deve ser escaneável em uma TV real, sem filtros, blur ou animação sobre ele. Código manual permite usar outro navegador quando a câmera falha.
3. No celular: conferir código e nome da TV; abrir a autorização oficial Google; escolher conta e conceder acesso; confirmar a conexão dessa TV. Não digitar senha em formulário Makimono.
4. TV recebe confirmação autenticada, mostra “Conectado” e oferece selecionar a pasta da biblioteca. Sem copiar URLs OAuth/códigos longos entre dispositivos.
5. Criar/selecionar perfil. Com apenas um perfil, retomar o último automaticamente; permitir configurar a pergunta ao abrir quando houver vários.
6. Nas próximas aberturas: restaurar sessão do aparelho e último contexto. Expiração recuperável não vira logout. Revogação real apresenta reconexão por QR, preservando os dados do perfil.

Conta Google/identidade proprietária, sessão de aparelho e perfil de espectador são entidades distintas. Um perfil Kids não cria outra conta Google; um booleano Admin no banco local não autoriza endpoints administrativos.

### Recursos essenciais antes de ampliar descoberta

- Retomar do ponto correto; opção contextual de recomeçar; remover de Continuar Assistindo com desfazer.
- Próximo episódio regular correto; OP/ED, trailers e extras fora da sequência automática por padrão.
- Lista pessoal/fila/favoritos com nomes e significados distintos, reutilizando o que já existe.
- Pastas profundas com breadcrumbs, busca de escopo explícito, ordenação natural e listagem sem cortes silenciosos.
- Idioma preferido de áudio e legendas por perfil, estilos acessíveis, velocidade/proporção salvas com escopo claro.
- Configurações agrupadas; diagnóstico de sessão e rede compreensível, detalhes técnicos sob divulgação progressiva.
- Troca de conta/aparelho, desconexão e exclusão de dados com efeitos explícitos; não apagar arquivos do Drive.

### Evoluções após estabilidade

- Sincronizar perfis, progresso, fila e preferências entre aparelhos do proprietário, com resolução de conflitos.
- Selecionar/reordenar prateleiras; novos episódios de pastas seguidas; histórico e buscas recentes por perfil.
- Corrigir associação a anime/temporada quando o metadado estiver errado; apresentar proveniência e incerteza.
- Integração Android TV Watch Next/deep links, somente onde suportada. Top Shelf da Apple não é API Android.
- Controle remoto simples pelo celular já pareado, se houver utilidade comprovada e autorização por aparelho.

Não incluir no primeiro horizonte: rede social, watch parties, catálogo comercial público, pagamentos, anúncios, recomendações por IA, transcodificação de todo o Drive ou download offline indiscriminado. Esses recursos ampliam operação sem resolver os problemas atuais. Casting/PiP só entram após prova de compatibilidade dos URLs autenticados, engines e ciclo de vida.

## 4. Login por QR: decisão técnica pesquisada

O fluxo Google para dispositivos de entrada limitada aceita `drive.file` e `drive.appdata`, mas não o `drive.readonly` atual. `drive.file` limita o conjunto de arquivos acessíveis; não assumir que selecionar uma pasta concede acesso recursivo a toda uma biblioteca preexistente. Esta diferença exige prova de escopo antes de trocar o fluxo. Fontes: [Google TV OAuth](https://developers.google.com/identity/protocols/oauth2/limited-input-device) e [escopos Drive](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).

**Direção recomendada:** pareamento próprio TV↔backend, com autorização Google Authorization Code no navegador do celular e callback HTTPS do backend. O QR localiza uma solicitação temporária; não contém senha, refresh token Google ou segredo permanente. O contrato detalhado está em `docs/architecture/ADR-001-TV-PAIRING.md`.

Persistência: manter o grant Google cifrado no backend; na TV, armazenar somente a sessão Makimono revogável no armazenamento protegido, excluída de backups. Não embutir refresh token, client secret de servidor ou PAT GitHub no APK. A renovação usa acesso offline e exige recuperação quando o grant é invalidado. Em consentimento externo no estado Google “Testing”, o refresh token de escopos Drive expira em sete dias; documentar a configuração antes de prometer permanência. Fontes: [Web OAuth](https://developers.google.com/identity/protocols/oauth2/web-server), [lifecycle OAuth](https://developers.google.com/identity/protocols/oauth2) e [boas práticas Google](https://developers.google.com/identity/protocols/oauth2/resources/best-practices).

**Privacidade padrão proposta:** BFF para metadados e streaming autenticado, de modo que tokens Google não sejam entregues à TV. Isso acrescenta custo de banda e dependência do servidor. Antes de hospedar, medir vazão/latência e apresentar custo do provedor escolhido. Alternativa mais simples: broker mantém refresh token no servidor, mas entrega access token Google curto à memória da TV para streaming direto. Essa alternativa tem exposição diferente e só substitui o padrão mediante decisão registrada do proprietário; não descrevê-la como “nenhum token Drive no aparelho”.

Credential Manager/passkeys é uma evolução opcional, não solução mínima deste projeto: a documentação atual de TV exige Android 14/GMS recentes. Autenticar identidade por passkey também não concede automaticamente permissões Drive. Não elevar minSdk 21 para resolver o onboarding. Fonte: [autenticação por formato Android](https://developer.android.com/identity/form-factors).

O app existente pode continuar com sessão legada durante a migração opt-in. Não invalidar a TV já conectada, não importar silenciosamente um segredo embutido e não presumir que tokens do cliente OAuth antigo pertencem ao novo cliente web.

## 5. Sistema de design, regras operacionais

Antes de alterar componente, registrar regra HIG, página, adaptação Android, arquivos/variantes e teste. Usar IDs existentes; criar novos `HG-Pxx` apenas para regras efetivamente lidas. Desvios `D-xx` exigem motivo e evidência, conforme missão v2. Não copiar PDFs para o repositório.

- Um sistema de superfícies, tipografia, espaçamentos, ícones e estados. Acento identifica ação/foco; favorito/selecionado não imitam foco.
- Um único responsável pelo anel de foco por controle. Superfície de vídeo e containers decorativos nunca recebem anel. Foco deve funcionar com efeitos reduzidos e sem cor como única pista.
- Proporções coerentes entre hero, botões e fileiras; título fora do pôster 2:3; sinopse longa expande/reflui. Não buscar densidade de desktop em 10-foot UI.
- Backdrop pertence ao hero; o fim da imagem é composto com scrim, sem faixa crua atrás dos episódios. Legibilidade deve resistir a artes claras, escuras e ausência de imagem.
- Materiais translúcidos em OSD, drawers e modais; fallback opaco legível em hardware limitado. Não aplicar blur ao vídeo por padrão nem implementar captura da SurfaceView para simular glass.
- Textos em `sp`, medidas semânticas em `dp`. A conversão pt→dp registrada vale para a referência/densidade utilizada, não é equivalência universal para todos os Androids.
- Touch respeita alvo Android adequado (referência 48dp), ainda que o desenho visível seja menor. TV mantém medidas/adaptações da missão e testa foco/distância. Não criar dois handlers que exijam dois toques para ativar. Fonte: [Android accessibility para Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).
- Os seis temas preservam escolhas existentes. Defaults bons; opções avançadas sob contexto, não dezenas de toggles na Home.
- Linguagem pt-BR consistente: ação, estado e recuperação; nada de exception, HTTP bruto, token ou caminho interno na mensagem de erro normal.
- Movimento curto e funcional; respeitar redução de animação do sistema e oferecer redução de efeitos específica do app sem contradizer o sistema.

### PDFs por conjunto de tarefas

Base em todas as tarefas visuais: **Accessibility, Focus and selection, Layout, Lockups, Materials, Motion, Color, Dark Mode, Typography, Remotes**.

Ler também antes de editar:

- Q04–Q05: Buttons, Labels, Toggles, Segmented controls.
- Q06: Settings, Split views, Pickers, Lists and tables, Search fields, Searching.
- Q07–Q12/Q20: Managing accounts, Loading, Progress indicators, Alerts, Modality, Sheets.
- Q13: Top Shelf, Sidebars, Tab bars, Collections, Loading.
- Q14–Q16: Lists and tables, Search fields, Searching, Progress indicators, Collections, Segmented controls.
- Q17–Q19: Playing video, Going full screen, Sliders, Menus, Sheets, Alerts, Action sheets, Modality, Rating indicators, Ratings and reviews.
- Q20–Q23: Managing accounts, Pickers, Collections, Settings, Toggles, Menus.
- Q24–Q26: releitura dos documentos relativos aos cenários alterados; não aceitar N/A genérico em toda a área.

Pasta: `C:\Work\refs\hig`; nomes terminam em ` _ Apple Developer Documentation.pdf`. Os 37 arquivos foram localizados. Na elaboração deste plano foram inspecionados trechos de Managing accounts (incluindo tvOS p.3), Settings (geral pp.1–2), Split views (geral/tvOS), Collections, Top Shelf, Loading, Playing video, Searching, Menus, Toggles, Pickers e Progress indicators. Managing accounts p.3 e Split views p.3 também foram rasterizados e inspecionados. O registro anterior cobre a base F0. **Não se afirma releitura integral dos 37 PDFs nem validação de todas as figuras/tabelas novas.** O executor deve completar a leitura pertinente e rasterizar quando o texto extraído não representar a figura.

## 6. Backlog executável

Legenda de caminhos: `J = app/src/main/java/zechs/drive/stream`, `R = app/src/main/res`. Caminhos novos são propostas, não arquivos existentes. Confirmar com `rg --files` antes de editar. Um ticket pode ter subetapas; nunca fazer todos em um patch. Os códigos Qxx são estáveis.

### P0/P1 — integridade e base de experiência

#### Q00 — registrar baseline e preservar estado

- Dependência: nenhuma. Prioridade P0 de processo. Escopo: documentação/evidência, sem limpeza de conta.
- Atualizar git status/diff, versões, variants, pacote instalado, SDK/ABIs, testes e fluxo de autenticação. Mapear os recursos já existentes e qual banco/configuração pertence a cada perfil. Reproduzir R01–R08 no checkout atual.
- Arquivos: reauditoria, `FRONTEND_LOG.md`, `HIG_CONFORMANCE.md`; ler `app/build.gradle`, nav graph, SessionManager, TvFocusRing e MediaImageLoader. Não resetar alterações do proprietário.
- Aceite: baseline com HEAD, diff, comandos, aparelho, pacote, telas reais versus fixture e lista de limitações. Cada bug possui passos, esperado/obtido e fonte. Identificar tag `pre-ui-v2` existente; não recriar/forçar tag.

#### Q01 — informação verdadeira de mídia (R01 / C2)

- Depende Q00. Prioridade P1. Arquivos: `J/ui/series/SeriesDetailViewModel.kt`, `J/ui/player/PlayerActivity.kt`, MPV equivalente e modelos de metadados.
- Modelar `Confirmed`, `Inferred`, `Unknown`, com fonte. Resolução/codec/decoder vêm do motor ativo; nomes de arquivo são inferência identificada. Remover defaults falsos de 1080p, japonês, multi-subs, estéreo/48kHz e HW decoder. Não atribuir característica de um episódio a toda a série.
- Aceite: mídia sem metadados não faz promessa técnica; tracks efetivas atualizam HUD; série heterogênea não exibe uniformidade falsa. Testar vídeo SD, áudio pt-BR, sem legenda e decoder alternativo em ambos motores. Não ocultar erro por substituir todo unknown por “premium”.

#### Q02 — paginação e recuperação Drive (R02 / C2)

- Depende Q00. Prioridade P1. Arquivos: `J/data/repository/DriveRepository.kt`, `J/ui/files/FilesViewModel.kt`, modelos/DAO de catálogo.
- Retornar resultado paginado com `nextPageToken`, `isComplete`, contexto da consulta e falha parcial. Remover sucesso completo ao atingir o limite de 2.500. Carregamento incremental/cancelável; deduplicação por ID; ordem determinística. Índice de busca informa seu alcance.
- Aceite: fixture com 2.501+ itens não perde último item; falha na terceira página preserva primeiras com retry correto; trocar pasta cancela consulta antiga; lista vazia, acesso negado e offline são estados distintos. Shared drives/shortcuts não geram ciclos. Nenhuma recursão ilimitada na UI thread.

#### Q03 — fechar migração Media3 e paridade de codecs (R03 / C1)

- Depende Q00; usa Q01. Prioridade P1, sobe P0 se mídia previamente suportada deixar de tocar.
- Arquivos: `app/build.gradle`, `J/ui/player/**`, `J/utils/Subtitle*.kt`, `J/utils/MakimonoMediaSessionHelper.kt`, `J/ui/player2/MPVActivity.kt`.
- Registrar dependências atuais, imports legacy, extensões FFmpeg e diferenças ASS/SSA. Selecionar versões compatíveis, sem importar snippet de versão nova indiscriminadamente. Recuperar extensão compatível ou estratégia MPV explícita e verificável; não excluir o AAR antigo e chamar isso de paridade.
- Aceite: matriz AAC, AC3/EAC3, DTS, FLAC, H.264/HEVC e SRT/ASS/SSA com assets legais/locais e URLs Drive; capabilities reais por aparelho. Falha oferece trocar motor preservando mídia, posição e perfil, sem loop. Build das quatro ABIs e evidência de decodificação; nenhum legado no caminho Media3 sem justificativa.

#### Q04 — componentes e navegação unificados (R05/R06/R08 / C3)

- Depende Q00. Prioridade P1. Arquivos: `J/utils/TvFocusRing.kt`, `J/ui/FocusedItemScroll.kt`, `J/ui/main/MainActivity.kt`, `J/ui/files/adapter/**`, `R/values*/styles.xml`, `R/drawable/*focus*` e componentes reutilizados.
- Eliminar dupla borda/escala, foco sobre vídeo, alvo cortado e primeiro toque consumido. Estados normal/focused/pressed/selected/disabled consistentes. Restaurar foco por identidade estável depois de diff, diálogo, mudança de modo e retorno de navegação.
- Aceite: um toque em card/CTA ativa uma vez; CENTER DOWN/UP ativa uma vez; 100% das ações alcançáveis por D-pad, inclusive seta de saída dos dois players. Teclas repetidas não geram múltiplas rotas. Lista curta/longa/sem dados tem saída; nenhum foco invisível após drawer.

#### Q05 — acessibilidade e efeitos reduzidos (R04 / C4)

- Depende Q04. Prioridade P1. Arquivos: política nova de acessibilidade/efeitos; `TvFocusRing`, motion spec, settings, estilos e binders de imagens.
- Respeitar sistema; preferência por perfil Auto/Reduzidos para efeitos do app. Reduzir escala/parallax/blur e animações, manter indicação de foco. Descrições e estados TalkBack; anunciar carregamento/conclusão sem flood. Textos adaptáveis e contraste em seis temas sobre imagens reais.
- Aceite: fonte 1×/1,3×/2×, reduced motion e sem blur preservam ações. Texto ampliado pode exigir rolagem, nunca ocultar função. TalkBack executa conectar, abrir, reproduzir, sair, mudar perfil e salvar preferência. Medir contraste com scrim composto; passar apenas as cores de base não basta.

#### Q06 — configurações por intenção (R07 / C5)

- Depende Q04/Q05. Prioridade P1/P2. Arquivos: `J/ui/settings/SettingsFragment.kt`, `R/layout*/fragment_settings.xml`, navegação e strings.
- TV: categorias à esquerda/conteúdo à direita, sem duplicar rail em cada pane. Mobile: navegação sequencial. Grupos Conta/aparelhos; Perfis; Reprodução; Áudio/legendas; Aparência/acessibilidade; Biblioteca/sincronização; Armazenamento; Sobre/diagnóstico.
- Expor escolhas frequentes no contexto (idioma no OSD, ordenação em Files), mantendo defaults/persistência. Migração de preferences preserva engine, temas e estilos. Limpar cache não limpa login/histórico; listar efeito antes de confirmar ação destrutiva.
- Aceite: trocar engine, tema, auto-skip e legenda, reiniciar e confirmar persistência; LEFT retorna à categoria e BACK ao contexto. Usar 1,3×/2× e touch. Não colocar credenciais de cliente ou segredo em formulário normal.

### P1 — conexão pelo celular e backend

#### Q07 — prova de arquitetura e escopos, antes de servidor público

- Depende Q00/Q02. Ler ADR. Prioridade P1 habilitadora; nenhum deploy pago automático.
- Novos arquivos propostos: `backend/README.md`, `backend/openapi.yaml`, `backend/` modular monolith Kotlin/Ktor; `web/pairing/` página pequena TypeScript estrito. Só introduzir toolchain após conferir versões oficiais/ambiente.
- Construir spike local de Authorization Code, callback, armazenamento cifrado e list/download autenticados. Comparar BFF versus broker em medição controlada. Preferência BFF para manter tokens Google no servidor; registrar custos/latência antes de hospedar.
- Aceite: decisão de escopo com biblioteca preexistente aninhada, refresh, download Range e dois motores; inventário dos recursos de hosting, domínio, TLS, banco, gestão de segredos e orçamento. Se faltar Cloud/OAuth real, marcar essa prova bloqueada e executar tickets independentes. Nunca concluir usando apenas mock.

#### Q08 — desafio de pareamento e página mobile

- Depende Q07 contrato local; pode usar fake provider explícito até credenciais reais. Arquivos novos: backend pairing service/repository/routes, `web/pairing/**`.
- Implementar desafio expirável, código manual, poll secret independente e confirmação do aparelho. OAuth `state` vinculado à sessão do navegador; grant e confirmação separados. Cancelamento/expiração não podem ser reativados por callback tardio. Rate limit/bounds, respostas sem cache e logs redigidos.
- Aceite: dois aparelhos simultâneos; captura do QR não concede sessão; browser/código trocado, callback com state errado, replay, dupla confirmação, TTL, cancelamento, relógio e polling agressivo rejeitados. Celular funciona fora do Wi-Fi da TV. Nenhum segredo em URL, HTML, storage web ou analytics.

#### Q09 — grant Google e sessão revogável de aparelho

- Depende Q08. Arquivos novos backend account/grant/device-session; Android `J/data/repository/DeviceSessionRepository.kt` proposto; integrar `EncryptedSessionStore`, `SessionManager`, `TokenProvider` via interface.
- Identidade proprietária por Google `sub` verificado; validação issuer/audience/expiração/nonce onde aplicável; allowlist inicial do proprietário. Refresh Google cifrado, chave fora do banco; evitar sobrescrever grant válido quando callback não retorna refresh token. Sessão própria rotativa/atômica, mutex single-flight, expiração/revogação e disconnect-device distintos de revoke-account.
- Aceite: reinício/process death restaura sessão; expiração recuperável renova uma vez para dez chamadas concorrentes; invalid_grant exige reconectar sem apagar Room; aparelho revogado não recebe nova sessão. Testar race/rotação/retry após perda de resposta, backup não inclui credenciais. Repetir com grant real.

#### Q10 — streaming BFF e adapters Drive

- Depende Q02/Q09. Novos endpoints/backend stream proxy; Android `AuthenticatingDataSource.kt`, repositórios Drive, integração HTTP MPV.
- Preservar APIs de domínio no Android com adapters legacy/BFF. No padrão BFF, metadados e bytes passam por sessão Makimono; servidor valida owner/root/file autorizado, não aceita proxy de URL arbitrária. Streaming incremental com cancelamento e backpressure. Não armazenar vídeo inteiro para responder seek. Range válido, 206, Content-Range/Length, 416, content type; refresh upstream no máximo uma vez em 401. Vídeo não entra em analytics/logs.
- Aceite: Media3 e MPV reproduzem >30 min, procuram início/meio/fim e sobrevivem a refresh; conexão encerrada cancela upstream; arquivo sem permissão/restrito é distinguível; pasta e shortcut fora do root não vazam dados. Medir duas streams simultâneas antes de escolher hosting. Fonte da capacidade Range: [Drive downloads](https://developers.google.com/workspace/drive/api/guides/manage-downloads).

#### Q11 — onboarding TV/mobile e estados de reconexão

- Depende Q04/Q05/Q08/Q09; conexão completa depende Q10. Arquivos novos propostos onboarding/pairing Fragment/ViewModel/layout/nav; integrar Home, Files e Settings.
- Estados tipados: desconectado, criando QR, aguardando, Google autorizado/aguardando confirmação, conectado, expirado, recusado, offline, indisponível, reconexão necessária. Código/QR mantêm prazo visível; renovar gera nova solicitação e invalida anterior. Foco não salta ao atualizar status. Não transformar timeout em credencial inválida.
- Aceite: scan real TV→celular→Google→pasta→episódio; nova abertura sem QR; fallback código; cancel/BACK; perder Wi-Fi; app em background/process death; QR expirado; negar consentimento. Nenhum e-mail completo/segredo aparece na TV sem necessidade. Perfil/conta existentes não são resetados.

#### Q12 — migração e operação do backend

- Depende Q09–Q11. Arquivos: ADR/runbooks/backend config; app startup/session adapter.
- Opt-in por aparelho mantendo login legado até nova sessão comprovada. Deploy incremental com health/readiness, migração reversível, TLS, secret manager, banco privado, backup/restauração e kill switch da rota nova. Telemetria de falhas sem file names, tokens, conteúdo ou identificadores pessoais brutos. Fechar Google consentimento/escopos para o uso pretendido; repo privado não altera OAuth “Testing”.
- Aceite: rollback preserva biblioteca/perfis; serviço indisponível não derruba navegação em cache; restauração de backup não reativa aparelho revogado; local/dev/prod separados. Owner realiza somente consentimento e fornece configurações via canal seguro quando necessário. Sem gravar valores secretos no repo ou chat.

### P2 — biblioteca, descoberta e experiência de assistir

#### Q13 — Home como destino editorial

- Depende Q01/Q04/Q05. Arquivos: `J/ui/home/{HomeFragment,HomeViewModel}.kt`, adapters, `R/layout*/fragment_home.xml`, MainActivity/rail.
- Hero estático com CTA principal e detalhes, backdrop contido e sinopse curta expansível. Fileiras úteis com dados reais: continuar, próxima sequência/fila, novos episódios de seguidas, adicionados e favoritos. Não mostrar prateleira vazia como banner de falha geral. Favorito vira badge discreto/ação contextual, não estrela gigante sobre rosto.
- Aceite: em 960×540dp/1×, hero com texto extremo não corta CTAs e deixa início de conteúdo seguinte; em fontes maiores reflow/scroll previsível. Card focado sem clipping, título abaixo do pôster, avatar circular real/fallback neutro. Retornar do episódio restaura item/posição. Refresh não reorganiza fileira enquanto o usuário a navega.

#### Q14 — detalhes limpos e sequência correta

- Depende Q01/Q04/Q16. Arquivos: `J/ui/series/**`, `R/layout*/fragment_series_detail.xml`, season/episode cards.
- Poster e hero juntos, scrim termina antes da área de episódios. Uma ação “Reproduzir/Continuar”, secundárias enxutas e Mais ações para raras; preservar trailer, favorito, seguir e marcar visto. Temporadas/extras em navegação clara. Nomes humanos preferidos, filename acessível sob info. Sinopse/elenco/estúdio/ano/rating com fontes, sem placeholder técnico falso.
- Aceite: long title/synopsis/missing art/metadados tardios/font2×; nenhum vazamento entre hero e episódios. Primeiro viewport normal contém seleção de episódio; alvo primário é próximo regular, não primeiro OP do nome alfabético. Estado assistido/progresso verdadeiro e desfazer funcionam.

#### Q15 — Files e busca com orientação espacial

- Depende Q02/Q04. Arquivos: `J/ui/files/**`, layouts Files/item, repositório catálogo/search.
- Breadcrumbs completos com acesso aos ancestrais, root definido, ordenação natural/persistida por contexto, grid/list equivalentes, indicação de itens carregados/total quando conhecido. Busca local vs biblioteca explícita; debounce/cancelamento; filtro tipo/temporada/assistidos. Índice parcial informa status.
- Aceite: cinco níveis, nomes >150 caracteres, 0/1/2.501+ itens, paginação com erro, voltar e restaurar scroll/foco; trocar grid/list preserva seleção lógica. Termo com acento/japonês; busca sem resultados não sugere sessão expirada. Um toque abre pasta, sem necessidade de toque duplo. Busca por voz opcional com permissão contextual e fallback teclado.

#### Q16 — catálogo, agrupamento e correção de metadados

- Depende Q01/Q02. Arquivos: `J/utils/{EpisodeParser,SeasonEpisodeGrouper}.kt`, `CatalogRepository`, `FolderMetadataRepository`, DAO/modelos.
- Extrair temporada/episódio/tipo (regular/OP/ED/extra/filme/desconhecido), ordem estável e mapeamento MAL/AniSkip independente do número apresentado. Match manual reversível e proveniência com confidence. Não fazer heurística de filename virar classificação etária confirmada.
- Aceite: S01E02, episódios absolutos, EP 2/10, nomes sem número, OVA/NCOP/NCED, volumes, filmes e múltiplas temporadas. Autoplay/continue nunca escolhem extra por engano. Erro de metadata não bloqueia playback de arquivo conhecido; correção persiste por perfil/conta conforme contrato.

#### Q17 — política única de input/OSD para ambos motores

- Depende Q03/Q04. Arquivos: `PlayerActivity`, `MPVActivity`, `player_control_view.xml`, drawers/dialogs; novo reducer/policy puro compartilhado proposto.
- Definir eventos e estado comuns sem reescrever engines em um patch. Seta visual sempre sai do episódio. BACK fecha camada superior, depois OSD, depois episódio. CENTER com OSD oculto revela transporte sem dupla ação. Esquerda/direita com OSD oculto fazem seek acumulado com HUD discreto; OSD visível usa navegação/scrubber. Play/Pause físico funciona. Timers suspendem durante foco, interação e acessibilidade relevante.
- Aceite: mesma tabela de eventos por engine com mídia longa; ACTION_DOWN/UP, long press/repeat, touch, teclado e BACK. Nenhuma saída só esconde OSD; foco retorna ao invocador, vídeo não fica selecionado. Encerrar libera decoder/rede/audio focus e salva progresso uma vez.

#### Q18 — faixas, legendas e informação contextual

- Depende Q03/Q05/Q17. Arquivos: `SubtitleStyleDialog`, `SubtitleSelectionPolicy`, `SubtitleAppearance`, menus OSD, renderers/adapters de engines.
- Primária: transporte, áudio, legendas, episódios e skip. Secundária: velocidade, proporção, capítulos, info técnica e offsets. Idioma por profile com fallback de tracks efetivas; preferência não desaparece quando uma mídia não possui a faixa. Preservar OpenSubtitles, externo/Drive, ASS/SSA, fontes/bordas/tamanhos/offset.
- Aceite: mudar áudio/legenda durante playback, forced/default, nenhuma legenda, SRT/ASS multilinha/estilos, idiomas inexistentes, offset positivo/negativo e capítulos. Preview de estilo é fiel ao engine ou diferencia limitação. Retornar ao vídeo exige no máximo fechar drawer; sem cadeia interminável de modais. Info relata decoder/buffer reais, desconhecido é desconhecido.

#### Q19 — continuar, skip e próximo episódio

- Depende Q16/Q17/Q18. Arquivos: `PlaybackProgressPolicy`, `WatchListRepository`, fila, AniSkip e ambos players.
- Persistir posição/duração/estado por perfil com identidade file+account, autosave limitado e flush em pause/exit. Completed não significa 100% arbitrário para todo vídeo. Retomar direto; recomeçar contextual. AniSkip só com mapeamento confiável; OP/ED auto independentes. Próximo card cancelável com título/preview/tempo; foco/interação interrompem timer. Desligar autoplay é persistente.
- Aceite: kill/restart, 3s vs30min vs2h, seek final, cancelar countdown, última mídia, arquivo removido e erro no próximo. Não pular OP errado por MAL mismatch. Repetir 20 transições em cada engine, sem dupla navegação/progress commit; desfazer marcar assistido/removecontinue funciona.

### P2/P3 — personalização e continuidade

#### Q20 — perfis completos, privacidade e Kids

- Depende Q05/Q06/Q09. Arquivos: `J/ui/profile/**`, `ProfileEntity/Dao/DataCleaner`, `ProfileManager`, catálogo de arte.
- Reutilizar Room; editor remoto D-pad com nome, avatar/wallpaper catálogo web cacheado e URL HTTPS customizada. Validar conteúdo/tamanho/rede sem buscar localhost/redes internas no backend. Histórico/fila/favoritos/preferências isolados. Kids restringe descoberta e deep links; conteúdo sem classificação confirmado não é automaticamente seguro. PIN administrativo opcional, bloqueio de tentativas e ação explícita para sair do Kids.
- Aceite: CRUD, último Admin/último perfil, nomes iguais/longos, URL quebrada/cacheoffline, exclusão de perfil com dados, restart e migração banco. Login Google compartilhado não mistura históricos. Admin local não dá poder no backend; PIN não é descrito como proteção de credenciais nem autenticação Google.

#### Q21 — preferências úteis com defaults

- Depende Q06/Q18/Q20. Arquivos: preference repository/model e Settings/ProfileEditor; policies engines.
- Por perfil: áudio/legenda, autoplay, OP/ED, estilo de legenda, tema e ordem de prateleiras. Por aparelho: engine/capabilities/cache; guardar escopo em modelo. Efeitos do sistema prevalecem. Gestos mobile desligados na TV. Velocidade/proporção têm contexto explícito, com voltar ao original disponível.
- Aceite: trocar perfil e aparelho lógico não herda escolhas incorretas; reset de uma categoria não apaga sessão. Migração preserva configurações antigas. Não criar três toggles para a mesma opção nem exigir configuração antes de assistir.

#### Q22 — sincronização multiaparelho e continuidade

- Depende Q09/Q19/Q20. Backend sync/event store/outbox; Room migration/repository.
- IDs estáveis por owner/profile/file; outbox transacional, operações idempotentes, revision/cursor e tombstones. Conflito de progresso não usa max(position), porque recomeçar deve funcionar. Session/run ID, eventos ordenados, clock servidor; conflito entre dois playbacks exige política explícita, não last-write por relógio da TV. Exclusão de perfil não pode ressuscitar via offline sync.
- Aceite: A offline pausa/recomeça, B avança, A reconecta; duplicate/delayed/out-of-order writes; duração desconhecida; exclusão; conta trocada. Estado exibido converge sem perder ação de recomeçar. Cache local permite navegar sem rede; streaming remoto requer rede e informa isso.

#### Q23 — descoberta, sistema TV e controle mobile opcional

- Depende Q13/Q19/Q22. Prioridade P3; não bloqueia núcleo premium quando plataforma não suporta.
- Completar fila/followedfolders/new episodes e busca recente antes de recomendação nova. Deep links validam owner/profile/root e arquivo atual. Watch Next atualiza progresso/terminados e remove itens revogados; launcher/API/certificação elegível precisam de teste. Controle mobile só com sessão própria pareada/revogável e ações autorizadas, sem endpoint LAN aberto.
- Aceite: link para episódio removido ou outro profile falha de modo recuperável; sistema não expõe Kids/adultos cruzados. Integração ausente não quebra Home. Controle mobile não toma prioridade do controle físico nem toca conteúdo sem escolha explícita. Fonte: [Android Watch Next](https://developer.android.com/training/tv/discovery/watch-next-add-programs).

### P1/P3 — desempenho, distribuição e gate final

#### Q24 — desempenho medido e acessibilidade em hardware

- Depende fluxo alvo implementado. Arquivos: RecyclerView/adapters, MediaImageLoader, cache, benchmarks/instrumentação e evidence manifest.
- Medir cold/warm start, navegação, focus latency, seek/firstframe, memória e buffering; baseline+depois com mesmo APK/aparelho/conteúdo/rede. Glide com tamanhos reais, prefetch limitado, cache cancelável; sem decode/parse/rede na main thread. Não pré-carregar biblioteca inteira para suavizar uma fileira.
- Aceite: budgets da seção 9 e duas sessões de 60 min por engine em TV física escolhida; listas largas/baixoRAM/rede ruim. Android21/28/34+ por capabilities quando disponíveis. MuMu cobre regressão, não substitui dispositivo físico nem mede termal/decoder TV real. Se não houver aparelho, gate permanece pendente.

#### Q25 — atualização/assinatura e recuperação

- Depende Q12, capacidades existentes, autorização atual antes de publicar.
- Arquivos: `GithubRepository`, `GithubAuth`, `AppUpdateManager`, build/release docs; workflow só quando autorizado. Remover dependência de PAT estático no APK novo: manifesto/artefatos públicos de distribuição ou relay backend autenticado conforme escolha de repo. Assinatura APK e cadeia de update devem permanecer compatíveis; hash detecta corrupção, não substitui autenticidade de origem.
- Aceite: versão anterior instalada atualiza sem perder Room/sessão; certificado, versionCode e ABI corretos; falta de espaço/rede/instalador retornam estado recuperável. Manifesto autenticado ou assinatura verificável, anti-downgrade e arquivos verificados antes da instalação. Não trocar keystore unilateralmente; testar linhagem/estratégia se rotação for necessária. Nunca exibir valores secretos em logs.

#### Q26 — aceite independente e nova rodada baseada em evidência

- Depende todos os P0/P1 e núcleo P2 aceitos; opções Q22/Q23 podem ter horizonte próprio documentado.
- Executar a matriz inteira, não apenas telas tocadas. Rubrica com legibilidade/hierarquia, input/saída, continuidade, integridade, recuperação, acessibilidade e performance. Review de capturas reais com datasets extremos; validar ausência de regressão em assets/themes/variants.
- Aceite: gates fechados ou pendências explícitas impedindo declaração de conclusão. Atualizar backlog com próximos defeitos reproduzidos e recursos justificados; não inventar polimento para preencher um loop. Fazer relatório “o que funciona / o que falta / qual evidência”, sem prometer perfeição absoluta.

## 7. Ordem de execução e tamanho das entregas

Primeiro: **Q00 → Q01 → Q02 → Q03 → Q04 → Q05 → Q06**. A prova Q07 pode começar depois de Q02; não é motivo para adiar correções independentes enquanto faltam credenciais.

Segundo: **Q07 → Q08 → Q09 → Q10 → Q11 → Q12**. Q11 layout/estados fake podem ser trabalhados antes de streaming, mas ficam IMPLEMENTED, sem aceite de conexão real.

Terceiro: **Q16 → Q13 → Q14 → Q15 → Q17 → Q18 → Q19 → Q20 → Q21**. Respeitar dependências adicionais listadas em cada ticket.

Quarto: Q22/Q23 evoluções; Q24 acompanha cada percurso e fecha depois; Q25 distribuição quando autorizada; Q26 revisão transversal.

Cada subentrega tem uma mudança principal, idealmente até 8 arquivos funcionais. Se exceder ou misturar Room, player e backend, dividir em subtickets antes de editar. Nunca declarar “P1 pronto” por ter criado uma classe ou migrado imports. Não executar automaticamente fase de provisionamento que dependa de domínio/credenciais/orçamento ainda não fornecidos.

## 8. Contratos de dados e recuperação

### Dados que não podem se misturar

- `OwnerAccount`: identidade Google verificada, grants e roots permitidos.
- `DeviceSession`: aparelho, owner, expiração/revogação, versão de protocolo; não é profile.
- `Profile`: nome/arte/papéis/preferências; papel administrativo validado no servidor quando afeta servidor.
- `MediaIdentity`: accountId+fileId+revisão quando relevante; título não é chave.
- `PlaybackRun`: profile, media, session/runId, posição/duração, sequência e último evento aceito.
- `CatalogEntry`: fonte/revisão/confidence, tipo regular/extra, season/order, classificação confirmada/desconhecida.

Room local continua fonte imediata da UI. Backend é autoridade para grants/sessões e, quando Q22 for implementado, sincronização. Não elevar uma preferência visual a configuração global nem usar SharedPreferences soltas para autorização server-side.

### Política comum de falhas

- Offline/timeouts/5xx: preservar cache e contexto; retry cancelável/backoff; nunca apagar grant automaticamente.
- 401 de sessão Makimono: renovar uma vez; segunda falha → reconectar, sem loops.
- 401 upstream Google: refresh single-flight no servidor; invalid_grant → consentimento requerido.
- 403: distinguir permissão, política/download restriction e quota conforme payload; não chamar tudo de “login expirado”.
- 429: respeitar Retry-After e reduzir concorrência; usuário pode cancelar.
- Dados parciais: sinalizar incompleto e permitir continuar; não registrar catálogo completo.
- Arquivo removido: alternativa próxima/contexto; tombstone de biblioteca sem apagar histórico inteiro.
- Cancelamento Coroutine: propagar CancellationException, não convertê-la em toast de falha.
- Progresso desconhecido: indicador indeterminado; sem porcentagem fictícia.

UI recebe domínio tipado e erro de recuperação, não exceptions brutas. Logs têm correlation ID e categoria, jamais authorization/cookies/grants/código OAuth/URL com segredo. Telemetria detalhada opcional e desligável.

## 9. Gates objetivos de qualidade

Os números abaixo são **budgets propostos do Makimono**, não requisitos numéricos universais da Apple. Medir antes/depois; ajustar somente com evidência de hardware e impacto, registrando motivo.

### G1 — navegação e compreensão

- Zero P0/P1 aberto no percurso crítico. 100% das ações visíveis alcançáveis e executáveis por D-pad e touch aplicável.
- Mesma política BACK/arrow/CENTER nos dois engines; cinquenta sequências de entrar/sair sem ficar preso, incluindo processo recriado.
- Foco visível, um único contorno e restauração por ID; metadados/paginação não roubam foco.
- Tarefa “continuar episódio” da Home em até 3 ativações, sem contar deslocamentos direcionais; áudio/legenda em até 2 aberturas de camadas; nenhum wizard repetido para sessão válida.
- Ensaiar tarefas sem instrução prévia com proprietário e, se disponível, outros 2 espectadores. Registrar sucesso, erros e tempo; não inventar resultados ausentes.

### G2 — visual e acessibilidade

- Matriz: Home/Details/Files/Perfis/Settings/OSD/drawers/onboarding, estados loading/vazio/error/partial e 6 temas.
- Fonte 1×/1,3×/2×; 720p/1080p/4K em densidades registradas; mobile portrait/landscape/tablet/TV física. Capturas equivalentes, não misturar release antigo e debug novo.
- Zero CTA essencial cortado, faixa de backdrop vazando, overlay em vídeo ou favorito gigantesco. Longos nomes/sem artes não desmancham layout.
- Contraste conforme missão e scripts; medir texto/ícone sobre imagem/scrim. Controles por forma/estado, não só cor.
- TalkBack e efeitos reduzidos completam fluxo crítico; timers não punem interação. QR legível de celular real a distância, sem depender apenas de teste de decode de bitmap.

### G3 — autenticação, privacidade e dados

- OAuth real no celular, TV fora da mesma LAN, restart/process death, consentimento negado/revogado, polling expirado e concorrência aprovados.
- Nenhuma credencial Google permanente/PAT/client secret de servidor no APK, backup, evidência ou URL de QR. APK release inspecionado por nomes/padrões sem imprimir segredos.
- Refresh e retries sem logout falso; sessão revogada realmente perde acesso; histórico/fila preservados na reconexão da mesma conta.
- Perfis/contas isolados; exclusão idempotente; paginação >2.500 itens; Room migração antiga→nova com conteúdo realista, sem destructive fallback.
- BFF/proxy ou alternativa aprovada e sua exposição/custos documentados; nenhuma dependência cloud tratada como gratuita/ilimitada.

### G4 — playback e continuidade

- Media3/MPV: 30+ min Drive no gate streaming, 60 min soak por engine no gate final; áudio/legenda/seek/chapters/skip/autoplay/pausar/sair/retomar testados.
- Posição restaurada com erro ≤2 s em mídia seekable normal; exceções por keyframe/engine documentadas e verificadas, não ocultadas.
- 20 transições de próximos episódios por engine; nenhum OP/ED escolhido como próximo regular; parar no último e cancelar funciona.
- Codec que funcionava permanece suportado ou tem alternativa explícita aprovada. Decoder info real; ASS/SSA complexa exercitada, não substituída por SRT e declarada igual.
- Comandos de sessão Android corretos. Para MPV, adapter compatível ou ponte documentada, sem duas sessões disputando comandos. Fonte: [Media3 migration](https://developer.android.com/media/media3/exoplayer/migration-guide) e [MediaSession](https://developer.android.com/media/media3/session/control-playback).

### G5 — fluidez e operação

- Em TV de referência: resposta visual de foco p95 ≤100 ms; jank em percurso de scroll padronizado <1%; nenhum frame congelado >700 ms atribuível ao app. Medir instrumento/hardware, não percepção.
- Conteúdo navegável em cache: cold start p95 ≤2,5 s e warm ≤1 s em 10 execuções controladas; informar aparelho/rede/estado de cache. Sem splash artificial.
- First frame online de referência p95 ≤4 s, seek p95 ≤2 s, sob vazão sustentada ≥2× bitrate e latência documentada; não extrapolar budget para rede insuficiente.
- API própria excluindo Google/streaming: p95 ≤300 ms sob carga pessoal definida; proxy sustenta pelo menos 2 streams do bitrate testado sem buffer integral. Reportar tempo upstream separado.
- Sem crash/ANR no soak; após 20 entra/sai, recursos de decoder/socket retornam ao baseline e heap estabiliza, sem crescimento linear.
- Orçamento mensal calculado, alertas de falha/custo configuráveis, backup restaurado em staging. Disponibilidade ainda não medida não ganha porcentagem inventada.

### G6 — entrega e evidência

- Build/testes atuais passam; screenshots de todos os layouts/qualifiers relevantes; tests não usam assert apenas do package como prova de UX.
- Artefato testado identificado por SHA-256/versionCode/engine/device/network/font/theme, com manifest de cenários. Fixture rotulada e separada de prova real.
- Atualização assinada compatível validada quando autorizada; instalação manual debug não prova upgrade da TV release.
- `HIG_CONFORMANCE.md` atualizado por ID/regra, inclusive desvios e N/A específico. Nenhum “100% HIG” com gate pendente.

## 10. Matriz mínima de cenários de QA

Executar por subticket os cenários relevantes e por Q26 o conjunto:

1. Instalação nova isolada → QR válido → Google → root → perfil → Home → episódio → sair → restart.
2. QR expirado/recusado/duplicado, state/código trocado, duas TVs e rede móvel no celular.
3. Sessão válida offline, token expirado renovável, grant revogado, troca de conta, aparelho desconectado remotamente.
4. Home sem histórico/sem art/longos textos; mudanças de dados com foco ativo; favoritos e continue com desfazer.
5. Files cinco níveis/grid/list/search/root/shortcuts/2.501+ itens/erro parcial; cache e refresh distinguidos.
6. Details metadados ausentes/tardios, extras antes de regulares, temporadas inconsistentes, nome japonês/longo, manual match.
7. Os dois engines com mídia longa; arrow touch e D-pad, BACK três camadas, CENTER UP/DOWN, playpause, seek acumulado, memória/process death.
8. Tracks e ASS/SSA, offsets, forced/default, OpenSubtitles sem rede, capítulo inexistente, aspect ratio, codecs/decoder.
9. AniSkip errado/ausente; OP e ED manuais/auto; próximo card foco/cancel/autoplay off/fim/arquivo deletado.
10. Perfis CRUD/PIN/Kids/isolamento/exclusão; preferences e reset; sync conflitante quando implementado.
11. Settings cache/banco/login distintos; backup/restauração sem segredos; launcher/deep links e update elegível.
12. TalkBack/font/theme/redução de efeitos/720p/tablet/portrait/TV física/baixo RAM; performance e 60-min soak.

Instrumentos: JVM para policies/reducers/ordenação/Room; MockWebServer para falhas HTTP; integração backend para race/TTL/ACL; instrumentados/UIAutomator/Espresso para input e foco; ADB/capturas para evidência; teste humano para QR e 10-foot. Fake provider e assets locais validam mecanismos, **não** aprovam integração Google/Drive real.

## 11. Dependências externas e perguntas no momento certo

Q00–Q06 e protótipos locais não precisam de segredo. Antes de OAuth real/deploy, obter do proprietário por mecanismo seguro: projeto Google Cloud controlado, cliente web e redirect URI, domínio/HTTPS, conta autorizada, local seguro dos secrets, provedor e teto de orçamento. Não pedir token no chat nem repetir segredo já fornecido anteriormente.

Verificar escopos restritos/verificação aplicável ao modo de uso antes de ampliar para público; uso pessoal e configuração do consentimento não são sinônimos de repositório privado. O plano não presume aprovação de produção, custos ou conclusão de verificação.

Quando algo externo bloquear: registrar ticket e condição concreta; continuar o próximo ticket independente. Não fabricar captura/execução nem declarar todo o app pronto. Dar ao dono instruções específicas apenas sobre o recurso que realmente falta.

## 12. Melhoria contínua com critério de conclusão

O horizonte comercial termina ao fechar os gates do escopo aprovado. Depois, rodada por área: observar uso real → priorizar defeito comprovado → implementar → medir → registrar. Avaliar novos recursos por benefício, esforço, suporte de plataforma, custo operacional e redução de passos.

Reabrir ticket por regressão ou nova evidência. Não alterar tela aprovada sem problema/benefício definido. “Ad infinitum” significa evolução sustentável, não trabalho sem critério de saída ou scheduler criado sem pedido.

O prompt de execução completo está em `LUNA_HIGH_EXECUTION.md`. Usar ambos os arquivos e o ADR como entrada; não depender do histórico inteiro deste chat.
