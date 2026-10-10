# Checklist de Navegação e Foco D-Pad (Android TV)

Percurso obrigatório de verificação de foco antes de cada release do Makimono.

---

## 1. Princípios de Aceite

1. **Foco Inicial Definido:** Nenhuma tela abre sem um elemento interativo claramente destacado.
2. **Previsibilidade Bidirecional:** Pressionar direcional reverso restaura o foco no elemento anterior.
3. **Memória de Foco:** Ao retornar de uma tela filha (ex.: Detalhes -> Home, Player -> Detalhes), o foco retorna exatamente ao card/item de origem.
4. **Sem Armadilha:** O usuário nunca fica preso em nenhum container, modal ou lista lateral.
5. **Comportamento da Tecla Voltar (Back):**
   - No conteúdo da Home: volta para o rail lateral.
   - No rail lateral: confirma saída ou sai da aplicação.
   - Em modais/diálogos: fecha o modal e retorna o foco ao gatilho que o abriu.
   - No Player com OSD visível: oculta o OSD sem parar a reprodução.
   - No Player com OSD oculto: encerra a reprodução e retorna à tela anterior.

---

## 2. Roteiro de Teste por Tela

### 2.1 Seleção de Perfis
- [x] Foco inicial no primeiro perfil. *(Validado via dump AVD: `focused="true"` em `Main profile`)*
- [x] Navegação horizontal fluida entre perfis com anel de foco visível. *(Validado via AVD)*
- [x] Acesso ao botão "Adicionar Perfil" / "Gerenciar" via direcional para baixo. *(Validado via dump AVD)*
- [x] Seleção de perfil carrega a Home e foca na ação primária do Hero. *(Validado via UiAutomator)*

### 2.2 Home Screen
- [x] Foco inicial no botão "Assistir" / "Continuar" do Hero banner. *(Validado: `homeScreenHasInitialFocusAndRespondsToDpad`)*
- [x] D-pad Down navega para a primeira estante ("Continuar Assistindo" ou "Em Alta"). *(Validado via UiAutomator)*
- [x] D-pad Left no primeiro item da estante navega para o Rail de navegação lateral. *(Validado via UiAutomator)*
- [x] D-pad Right a partir do Rail retorna para o item com foco memorizado na estante. *(Validado via UiAutomator)*
- [x] Rolagem horizontal mantém o item focado dentro da área visível com margem confortável. *(Validado via UiAutomator)*

### 2.3 Detalhes (Filme / Série)
- [x] Para Filme: ação primária "Assistir" destacada com indicação "Filme" (sem menção a "Episódio 01"). *(Validado: `seriesDetailScreenEstablishesInitialFocusOnPrimaryAction`)*
- [x] Para Série: seletor de temporadas e lista de episódios acessíveis via D-pad Down. *(Validado via UiAutomator)*
- [ ] Botão "Mais" na sinopse abre diálogo com foco inicial no botão de fechar. *(Não testado nesta rodada)*
- [x] Tecla Voltar restaura o foco no card de origem na Home. *(Validado via UiAutomator)*

### 2.4 Player (Media3 / MPV)
- [x] Ao abrir o player ou pressionar direcional, o OSD aparece com foco no botão Play/Pause. *(Validado: `playerScreenShowsOsdWithFocusAndHidesOnBackKey`)*
- [x] D-pad Left/Right navega para Retroceder 10s e Avançar 10s. *(Validado via UiAutomator)*
- [ ] Botões de trilha de áudio, legendas e configurações acessíveis sem perder o foco. *(Não testado nesta rodada)*
- [x] OSD desaparece automaticamente após inatividade. *(Validado via OsdStateMachine)*
- [x] Tecla Voltar com OSD aberto oculta o painel. *(Validado: `playerScreenShowsOsdWithFocusAndHidesOnBackKey`)*
- [ ] Tecla Voltar subsequente salva progresso no Room e retorna para a tela de Detalhes com foco no episódio assistido. *(Não testado nesta rodada)*

### 2.5 Configurações e Pastas
- [x] Navegação entre categorias sem salto abrupto. *(Validado: `settingsScreenAllowsVerticalCategoryNavigation`)*
- [ ] Seletor de pastas de biblioteca do Drive permite seleção e confirmação via botão de ação destacado. *(Não testado nesta rodada)*
