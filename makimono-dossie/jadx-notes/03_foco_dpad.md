# Subsistema 03 — Foco e Navegação por D-Pad

## 1. Identificadores Observados e Origem

- **Classes Principais:**
  - `com.crunchyroll.crunchyroid.tv.focus.TvFocusRestorationHomeConfig` (Módulo TV Focus)
  - `com.crunchyroll.crunchyroid.ui.modifiers.FocusHandlerModifierKt` (Extensões de Modifiers Compose)
  - `androidx.compose.ui.focus.FocusRequester` (Framework Compose)
  - `com.crunchyroll.crunchyroid.debug.DebugKeyHandler` (Captura de Eventos de Teclado/Controle)

---

## 2. Enquadramento e Análise Estrutural

### 2.1 Restauração e Memorização de Posição de Foco
- **Tag:** `[IDEIA]`
- **Observação Factual:** Em interfaces de TV estruturadas em carrosséis bidirecionais (linhas horizontais aninhadas em coluna vertical), a perda de posição de foco ao navegar entre linhas ou retornar de telas de detalhes é uma das falhas mais graves de UX. O aplicativo implementa `TvFocusRestorationHomeConfig`, que retém o índice de item previamente focado em cada linha horizontal. Ao retornar para uma linha anterior, o foco é restaurado diretamente no item correspondente em vez de reiniciar no índice 0.
- **Grau de Confiança:** Forte evidência estática (estruturas dedicadas de restauração de foco em Compose TV).
- **Lacunas Conhecidas:** Comportamento de reciclagem de memória quando o catálogo ultrapassa centenas de itens na mesma grade.

### 2.2 Efeito Visual de Foco e Elevação Dinâmica
- **Tag:** `[IDEIA]`
- **Observação Factual:** Elementos focáveis (cards de séries, botões de ação) recebem modificadores de estado de foco (`FocusHandlerModifierKt`) que aplicam simultaneamente:
  1. Escala proporcional suave (aproximadamente `1.05x` a `1.08x`).
  2. Borda de realce iluminada com alto contraste sobre o fundo escuro.
  3. Deslocamento do card para o topo do eixo Z (elevação visual sobre os cards adjacentes).
- **Grau de Confiança:** Forte evidência estática.
- **Lacunas Conhecidas:** Curvas de interpolação exatas (Spring vs FastOutSlowInEasing) exigem inspeção de frames dinâmicos.

### 2.3 Implementação Concreta de Modificadores de Foco
- **Tag:** `[EXPRESSÃO]`
- **Observação Factual:** A sintaxe de código exata dos métodos de extensão internos que encapsulam os modifiers do Compose.
- **Grau de Confiança:** Forte evidência estática.
- **Diretriz Clean-Room:** O Makimono deve implementar seus próprios modificadores de foco no ecossistema Android TV (seja Leanback `Presenter` ou Compose for TV `CardDefaults.colors`/`Border`), garantindo comportamento equivalente de forma nativa e limpa.
