# Subsistema 02 — Navegação e Retorno (Backstack)

## 1. Identificadores Observados e Origem

- **Classes Principais:**
  - `androidx.navigation.NavHostController` (Biblioteca de Suporte)
  - `com.crunchyroll.crunchyroid.navigation.NavigationSuiteScaffoldConfig` (Módulo de Navegação)
  - `com.crunchyroll.crunchyroid.presentation.navigation.BrowseNavItem` (Enumeração de Rotas)
  - `com.crunchyroll.crunchyroid.presentation.navigation.TvDestination` (Contrato de Rotas)

---

## 2. Enquadramento e Análise Estrutural

### 2.1 Topologia de Navegação Hierárquica em TV
- **Tag:** `[IDEIA]`
- **Observação Factual:** A estrutura de navegação adota o `NavigationSuiteScaffold` com um menu lateral colapsável (*Navigation Rail*) à esquerda e uma área de conteúdo dinâmica à direita. As rotas fundamentais observadas estruturalmente compreendem: Home, Explorar/Buscar, Minha Lista (Watchlist) e Configurações/Perfil.
- **Grau de Confiança:** Forte evidência estática (estruturas de rotas Compose Navigation encontradas nas classes compiladas).
- **Lacunas Conhecidas:** Tempo de animação da expansão/retração da barra lateral requer validação visual sob taxa de quadros (60Hz vs 120Hz).

### 2.2 Política de Retorno (Back Press) e Proteção contra Saída Involuntária
- **Tag:** `[IDEIA]`
- **Observação Factual:** A navegação por D-pad em telas de TV sofre comumente de saídas acidentais do app quando o usuário pressiona "Voltar" (Back) na raiz de uma lista. A arquitetura intercepta o retorno com uma regra de ancoragem:
  1. Se o foco está no conteúdo interno, "Voltar" move o foco prioritariamente para a barra de navegação lateral esquerda ou para o item inicial da linha.
  2. Apenas quando o foco já se encontra na raiz da barra lateral principal um segundo evento "Voltar" aciona o diálogo de confirmação de saída ou finalização da atividade.
- **Grau de Confiança:** Inferência estrutural baseada em manipuladores de retorno (`BackHandler` associados a `NavHostController`).
- **Lacunas Conhecidas:** Comportamento exato quando atalhos de voz do controle remoto são acionados.

### 2.3 Rotas e Esquemas Literais
- **Tag:** `[EXPRESSÃO]`
- **Observação Factual:** Padrões de rotas literais (`crunchyroll://`, `/series/{seriesId}`, `/episode/{episodeId}`).
- **Grau de Confiança:** Forte evidência estática em filtros de Intent e tabelas de deep linking.
- **Diretriz Clean-Room:** O Makimono deve utilizar seu próprio esquema unificado baseado em IDs do Google Drive / Kitsu / AniList, sem herdar a sintaxe de rota da Crunchyroll.
