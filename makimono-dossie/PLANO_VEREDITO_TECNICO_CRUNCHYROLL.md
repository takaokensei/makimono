# Plano para Veredito Técnico de Aproveitamento: Crunchyroll Android TV → Makimono

**Documento Norteador de Execução (Etapas V0 a V5)**  
**Projeto:** Makimono (`intelligent-mendel`)  
**Data:** 14 de Outubro de 2026  
**Enquadramento:** Premissa documental fornecida pelo usuário (`Act No. 19 of 2026`). Protocolo estrito Clean-Room.

---

## 1. Missão Central e Pergunta Orientadora

> **Quais elementos da experiência Crunchyroll TV podem virar componentes funcionais no Makimono, por qual caminho, com quais dependências e com qual esforço?**

O objetivo não é replicar código proprietário nem propor um clone visual com dados de terceiros. A meta é determinar com rigor de engenharia o que foi:
1. **Recuperado:** metadados, estruturas, classes, métodos ou nada.
2. **Compreendido:** regras de negócio, contratos de estado, árvores de UI e transições.
3. **Reproduzido:** prova executável independente com dados próprios.
4. **Integrado:** viabilidade na arquitetura real do Makimono (Views/XML, RecyclerView, Media3/MPV, Room/StateFlow).

---

## 2. Critérios de Classificação por Componente

Cada componente auditado receberá uma das 5 classificações estritas:

- **Classe A — Candidato Tecnicamente Isolável:** Informação ou implementação recuperada suficientemente compreensível, dependências delimitadas e prova de adaptação compilável.
- **Classe B — Reproduzível por Especificação:** Comportamento ou apresentação compreendidos e demonstrados em implementação Makimono, embora o código original não seja recuperável ou aproveitável.
- **Classe C — Parcialmente Reproduzível:** Comportamento local demonstrado, com dependências externas (serviços de backend, entitlements) explicitamente delimitadas.
- **Classe D — Sem Utilidade Prática no Makimono:** Elemento identificado, mas fortemente acoplado a serviços proprietários ou com custo de adaptação superior ao desenvolvimento nativo.
- **Classe E — Inconclusivo:** Evidência insuficiente, bloqueio intransponível de ferramenta, ou tela inacessível.

---

## 3. Matriz de Priorização (Escopo de Auditoria)

### Prioridade 1 (P1) — Casos Críticos (Provas Obrigatórias)
1. **Shell, Sidebar (Rail Navigation), Back e Foco:** Expansão lateral, política de ancoragem do botão Back e prevenção de saída involuntária.
2. **Home, Hero Banner e Prateleiras (Shelves):** Hierarquia visual de TV, proporções, scroll sincronizado e memorização de foco bidirecional (`FocusMemory`).
3. **Player OSD e Transporte:** Máquina de estados de controle, HUD temporizado, overlay de *Skip Intro/Recap* e contagem regressiva de *Up Next*.

### Prioridade 2 (P2) — Telas Secundárias e Acessibilidade
4. **Detalhes, Temporadas e Episódios:** Abas horizontais, seletor de temporadas e lista de episódios com indicador de progresso.
5. **Perfis, Avatares e Edição:** Seleção de perfil, isolamento de estado e fluxo de troca rápida.
6. **Busca, Menus, Loading e Estados Vazios:** Teclado na TV, transições e estados de erro/retry.

### Prioridade 3 (P3) — Mídia Avançada e Serviços Remotos
7. **Faixas de Áudio, Legendas e Autoplay:** Seleção dinâmica de trilhas no player.
8. **Conta, Dados e Serviços:** Limites entre front-end e backend de pareamento (Device Code / Pairing API).

---

## 4. Gates de Qualidade e Critérios de Parada

1. **Gate V0/V1:** Identidade do artefato e assinaturas validadas; correção explícita de qualquer afirmação sem sustentação estática.
2. **Gate V2:** Roteiro de interação no AVD Android TV executado com logs e dumps de nós de acessibilidade capturados.
3. **Gate V3/V4:** Três provas prioritárias implementadas e validadas em código limpo no Makimono (`makimono-dossie/provas/`).
4. **Gate V5:** Veredito técnico final consolidado em `VEREDITO_TECNICO.md`.
