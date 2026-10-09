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
- [ ] Foco inicial no primeiro perfil.
- [ ] Navegação horizontal fluida entre perfis com anel de foco visível.
- [ ] Acesso ao botão "Adicionar Perfil" / "Gerenciar" via direcional para baixo.
- [ ] Seleção de perfil carrega a Home e foca na ação primária do Hero.

### 2.2 Home Screen
- [ ] Foco inicial no botão "Assistir" / "Continuar" do Hero banner.
- [ ] D-pad Down navega para a primeira estante ("Continuar Assistindo" ou "Em Alta").
- [ ] D-pad Left no primeiro item da estante navega para o Rail de navegação lateral.
- [ ] D-pad Right a partir do Rail retorna para o item com foco memorizado na estante.
- [ ] Rolagem horizontal mantém o item focado dentro da área visível com margem confortável.

### 2.3 Detalhes (Filme / Série)
- [ ] Para Filme: ação primária "Assistir" destacada com indicação "Filme" (sem menção a "Episódio 01").
- [ ] Para Série: seletor de temporadas e lista de episódios acessíveis via D-pad Down.
- [ ] Botão "Mais" na sinopse abre diálogo com foco inicial no botão de fechar.
- [ ] Tecla Voltar restaura o foco no card de origem na Home.

### 2.4 Player (Media3 / MPV)
- [ ] Ao abrir o player ou pressionar direcional, o OSD aparece com foco no botão Play/Pause.
- [ ] D-pad Left/Right navega para Retroceder 10s e Avançar 10s.
- [ ] Botões de trilha de áudio, legendas e configurações acessíveis sem perder o foco.
- [ ] OSD desaparece automaticamente após inatividade.
- [ ] Tecla Voltar com OSD aberto oculta o painel.
- [ ] Tecla Voltar subsequente salva progresso no Room e retorna para a tela de Detalhes com foco no episódio assistido.

### 2.5 Configurações e Pastas
- [ ] Navegação entre categorias sem salto abrupto.
- [ ] Seletor de pastas de biblioteca do Drive permite seleção e confirmação via botão de ação destacado.
