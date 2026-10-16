# Matriz de Aproveitamento Técnico: Crunchyroll TV → Makimono (Etapa V3)

**Data:** 14 de Outubro de 2026  
**Finalidade:** Quadro sintético de decisões de engenharia por componente para o roadmap do Makimono.

---

## 1. Quadro Comparativo de Viabilidade e Classificação

| Componente | Prioridade | 1. Recuperação | 2. Compreensão | 3. Reprodução | 4. Integração Makimono | Classe | Caminho Recomendado | Custo Estimado |
| :--- | :---: | :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **Shell & Rail Navigation** | **P1** | Assinaturas, nós e medidas | **Total** (80dp→264dp, empurra feed) | Demonstrada em prova limpa | Perfeita (Views/XML ou Compose) | **B** | Views nativas com animação de largura | 4–6 h |
| **Proteção de Back Press** | **P1** | Nós do diálogo de saída e retorno | **Total** (Conteúdo→Rail→ExitDialog) | Demonstrada em prova limpa | Perfeita (interceptação de Back) | **B** | `BackHandler` customizado no Makimono | 2–3 h |
| **Home Hero Banner** | **P1** | Modelo `TvPromotionalAsset` e bounds | **Total** (452dp, tags, 2 ações) | Demonstrada em prova limpa | Perfeita (integra com anime em destaque) | **B** | Layout XML com ViewBinding | 4–6 h |
| **Prateleiras com FocusMemory** | **P1** | Bounds de cards (145dp) e nós | **Total** (Restauração exata por índice) | Demonstrada em prova limpa | Perfeita (RecyclerView horizontal) | **B** | Custom `FocusMemoryManager` | 4–6 h |
| **Player OSD & Auto-Hide** | **P1** | Classes Compose, nós e timer | **Total** (3–5s auto-dismiss, D-pad wake) | Demonstrada em prova limpa | Perfeita (ExoPlayer e MPV) | **B** | Máquina de estados desacoplada | 4–6 h |
| **Skip Intro / Recap Overlay** | **P1** | Modelos `SkipEvent` e tipos | **Total** (Janela `[start, end]`, D-pad) | Demonstrada em prova limpa | Perfeita (via MKV chapters / AniSkip) | **B** | Componente visual sobreposto ao player | 3–4 h |
| **Autoplay / Up Next** | **P2** | Classes `UpNext` e `PlayerEventBus` | **Total** (Contagem 15s nos créditos) | Viável | Perfeita (integra com fila de episódios)| **B** | Overlay de contagem regressiva | 4–6 h |
| **Detalhes da Série e Abas** | **P2** | Estrutura de abas e metadados | **Total** (Abas: Episódios, Detalhes, etc.)| Viável | Perfeita (integra com pastas do Drive) | **B** | TabLayout / ViewPager para TV | 6–8 h |
| **Perfis e Troca Rápida** | **P2** | Estrutura `LupinProfileSync` | **Média** (Acoplado ao backend Lupin) | Parcial | Exige banco local (Room) no Makimono | **C** | Room SQLite + PreferenceManager local | 8–10 h |
| **Busca e Teclado de TV** | **P2** | Rota de busca e filtros | **Alta** (Grid dinâmico com debouncing) | Viável | Perfeita (busca local no catálogo) | **B** | SearchView com debounce de 300ms | 4–6 h |
| **Seleção de Trilhas de Áudio**| **P3** | Dialogs de faixas do player | **Total** (Seleção via TrackGroup) | Viável | Nativamente suportado pelo Media3/MPV | **B** | Dialog nativo com D-pad navigation | 3–4 h |
| **Backend / Serviços Remotos** | **P3** | Endpoints privados, OAuth2 | **N/A** (Serviços proprietários) | Não aplicável | Incompatível (Makimono usa Google Drive)| **D** | **Descarte técnico justificado** | — |

---

## 2. Legenda das Classificações

- **Classe A (Candidato Isolável):** Código ou módulo de terceiros reutilizável diretamente com dependências delimitadas. *(Nenhum código proprietário foi transplantado para manter conformidade Clean-Room).*
- **Classe B (Reproduzível por Especificação):** Comportamento, interação e proporções perfeitamente compreendidos e reimplementados de forma independente no Makimono com código limpo.
- **Classe C (Parcialmente Reproduzível):** UI reproduzível localmente, mas dependente da implementação de infraestrutura própria (ex.: banco Room para múltiplos perfis).
- **Classe D (Sem Utilidade Prática):** Elementos altamente acoplados a infraestruturas de nuvem proprietárias da Crunchyroll (descarte definitivo).
- **Classe E (Inconclusivo):** Estados não atingidos por limitações de ferramenta ou acesso.
