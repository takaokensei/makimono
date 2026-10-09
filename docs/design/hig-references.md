# Referências de Design e Graus de Confiança

Documento que registra as referências técnicas de UX/UI utilizadas no desenvolvimento do Makimono, com o respectivo grau de confiança e aplicabilidade.

---

## 1. Tabela de Referências

| Referência | Grau de Confiança | Aplicação no Makimono |
|---|---|---|
| **Diretrizes Oficiais de TV do Android** | Oficial [R] | Margens seguras de overscan (48dp lateral, 27dp vertical), canvas base 960×540dp, manipulação de D-pad, acessibilidade Leanback. |
| **Playback em TV (Android Media / Google Cast)** | Oficial [R] | Integração com `MediaSession`, Now Playing, Watch Next no launcher, tratamento de ciclo de vida e liberação de memória em TV. |
| **Baseline Profiles & Macrobenchmark** | Oficial [R] | Métricas de startup time (TTID, TTFD), estabilidade de taxa de quadros e jank em listas e reprodução. |
| **Compose for TV (`tv-material` 1.1.0 estável)** | Oficial [R] | Avaliação de migração progressiva de telas (detalhes, configurações e home). |
| **Modelo de Foco do tvOS (Apple HIG)** | Oficial Apple / Secundária para TV [R] | Princípios fundamentais de foco: sempre visível, previsível, inicial definido, memória de foco preservada, sem armadilha. |
| **Design Visual Netflix TV** | Referência Visual [R] | Rail lateral de 80dp com ícones sempre acessíveis, hero banner sangrando por trás da navegação, estantes horizontais fluidas. |
| **Kodi (Skin Estuary)** | Referência Funcional [R] | Práticas de OSD avançado para reprodutores de mídia, buffers de streaming e apresentação de metadados. |

---

## 2. Princípios de Adoção

- Priorizar sempre as orientações oficiais do ecossistema Android quando houver divergência entre plataformas.
- As diretrizes de plataformas secundárias (como Apple tvOS) servem como modelo mental de ergonomia e previsibilidade de foco, não para especificação direta de dimensões ou fontes.
