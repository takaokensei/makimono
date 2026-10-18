# Design System — Makimono (Obsidian)

Documento canônico do sistema visual, temas e de interação do Makimono para Android e Android TV (10-foot experience).  
Data de referência: Outubro de 2026.

---

## 1. Princípios do Design System Obsidian

1. **Local-First & Imersivo:** Fundo preto profundo (`#070B11`) e superfícies cinza-escuro com translucidez elegante para destacar pôsteres e mídia.
2. **Acento Visual Distinto:** Torii / Ciano `#22D3EE` como cor de acento primária para foco, seleções ativas e indicadores.
3. **Piso Tipográfico Estrito:** Nenhum elemento de texto na interface pode ter tamanho inferior a **12sp** (`values/dimens.xml`), garantindo legibilidade mínima a qualquer distância.
4. **Foco Visível e Previsível:** Todo elemento interativo na TV possui anel de foco bem delineado (espessura padrão de 3dp, cantos arredondados contínuos) e transições suaves de escala/elevação.

---

## 2. Decisão Arquitetural de Temas (Seção 4.3 do Plano)

Para eliminar a superfície de bugs de contraste em telas grandes e simplificar a manutenção dos 16 temas declarados anteriormente, o **Obsidian** é estabelecido como o **Design System Oficial e Canônico** do Makimono.

- **Tema Padrão:** `Theme.Makimono.Obsidian` (estendendo `Theme.Material3.Dark.NoActionBar`).
- **Eliminação de Variações Fragmentadas:** Todos os estilos de componentes, cartões, botões e controles convergem para os tokens semânticos do Obsidian.

---

## 3. Paleta Canônica (Tokens Semânticos)

| Token | Valor Hex | Função / Aplicação |
|---|---|---|
| `obsidian_bg_base` | `#070B11` | Fundo principal da aplicação (OLED black) |
| `obsidian_bg_surface` | `#111925` | Cards, modais e superfícies elevadas |
| `obsidian_bg_glass` | `#0F111925` | Superfícies com translucidez e menus OSD |
| `obsidian_border_glass` | `#1AFFFFFF` | Bordas sutis e divisores em cards |
| `obsidian_accent_primary` | `#22D3EE` | Acento ciano primário, anel de foco, indicador ativo |
| `obsidian_accent_secondary` | `#4F8CFF` | Acento secundário para destaques e gradientes |
| `obsidian_on_primary` | `#0A0E14` | Cor de texto sobre fundos com `accent_primary` |
| `obsidian_text_primary` | `#F5F7FA` | Títulos, rótulos principais (alto contraste > 12:1) |
| `obsidian_text_secondary` | `#B0B8C4` | Subtítulos, metadados (contraste WCAG AA > 4.5:1) |
| `obsidian_text_tertiary` | `#7C8696` | Textos terciários e badges sutis |
| `obsidian_focus_fill` | `#2622D3EE` | Preenchimento sutil translúcido para foco ativo |
| `obsidian_hairline` | `#1AFFFFFF` | Linhas delimitadoras finas |
| `obsidian_success` | `#54D18A` | Indicadores de sucesso / concluído |
| `obsidian_warning` | `#F6C76A` | Alertas e avisos |
| `obsidian_danger` | `#FF6B78` | Erros, falhas e botões destrutivos |
| `obsidian_scrim` | `#99000000` | Escurecimento de fundo em modais e gavetas |
| `obsidian_media_scrim` | `#CC000000` | Gradiente sobre backdrops para contraste de texto |

---

## 4. Tabela de Mapeamento dos Tokens Legados (`layout_literal_*`)

Todos os 69 identificadores literais de layout são mapeados diretamente para tokens semânticos:

| Grupo Hex / Categoria | Tokens Legados | Token Semântico Alvo |
|---|---|---|
| **Fundo & Superfície Profunda** | `layout_literal_000000`, `layout_literal_070a10`, `layout_literal_0a0a0a`, `layout_literal_0b0e14`, `layout_literal_0d1117`, `layout_literal_0e0d14`, `layout_literal_030c16`, `layout_literal_03121c`, `layout_literal_040c16` | `@color/obsidian_bg_base` |
| **Superfície Elevada & Cards** | `layout_literal_0f172a`, `layout_literal_151722`, `layout_literal_1e0f172a`, `layout_literal_1e293b`, `layout_literal_1f2335`, `layout_literal_e60a0f1d`, `layout_literal_f20b0e14`, `layout_literal_f20e131e` | `@color/obsidian_bg_surface` |
| **Acentos Primários (Ciano)** | `layout_literal_22d3ee`, `layout_literal_00c0ff`, `layout_literal_12a0c7`, `layout_literal_38bdf8`, `layout_literal_7dd3fc` | `@color/obsidian_accent_primary` |
| **Acentos Secundários (Azul)** | `layout_literal_2563eb`, `layout_literal_7fa3d6` | `@color/obsidian_accent_secondary` |
| **Preenchimentos e Foco Ciano** | `layout_literal_2638bdf8`, `layout_literal_3338bdf8`, `layout_literal_164451` | `@color/obsidian_focus_fill` |
| **Texto Primário (Claro)** | `layout_literal_ffffff`, `layout_literal_ffffffff`, `layout_literal_f8fafc`, `layout_literal_f1f5f9`, `layout_literal_f1f7ff`, `layout_literal_f2f6fc`, `layout_literal_f0f0f0`, `layout_literal_e2e8f0`, `layout_literal_e3edff` | `@color/obsidian_text_primary` |
| **Texto Secundário** | `layout_literal_cbd5e1`, `layout_literal_94a3b8`, `layout_literal_b4c0cf`, `layout_literal_b9cadf`, `layout_literal_b9d1d7`, `layout_literal_c0c0c0`, `layout_literal_c1cfde`, `layout_literal_d4d4d4`, `layout_literal_d5dee9`, `layout_literal_8ea6b4`, `layout_literal_8fa3ba`, `layout_literal_a3b0c2` | `@color/obsidian_text_secondary` |
| **Texto Terciário / Muted** | `layout_literal_64748b`, `layout_literal_475569`, `layout_literal_334155`, `layout_literal_71909a`, `layout_literal_7b82a3`, `layout_literal_7ba7b5` | `@color/obsidian_text_tertiary` |
| **Translucidez Branca / Bordas Glass** | `layout_literal_1affffff`, `layout_literal_20ffffff`, `layout_literal_24ffffff`, `layout_literal_26ffffff`, `layout_literal_2fffffff`, `layout_literal_33ffffff`, `layout_literal_40ffffff` | `@color/obsidian_border_glass` ou `@color/surface_hairline` |
| **Translucidez Alta Branca** | `layout_literal_99ffffff`, `layout_literal_ccffffff` | `@color/surface_specular` |
| **Translucidez Escura / Scrims** | `layout_literal_33000000`, `layout_literal_59080a10`, `layout_literal_66020617`, `layout_literal_80000000`, `layout_literal_99000000`, `layout_literal_cc000000`, `layout_literal_b30e0d14`, `layout_literal_e00b1928` | `@color/obsidian_scrim` ou `@color/obsidian_media_scrim` |
| **Cores Semânticas de Estado** | `layout_literal_e11d48`, `layout_literal_f7768e` | `@color/obsidian_danger` |
| **Status / Aviso / Dourado** | `layout_literal_f39c12`, `layout_literal_fbbf24`, `layout_literal_ffd700` | `@color/obsidian_warning` |

---

## 5. Diretrizes de Android TV (10-Foot Experience)

### 5.1 Overscan e Áreas Seguras
- **Canvas Base de TV:** 960 × 540dp (escalado pelo sistema para 1080p e 4K).
- **Margem Segura Lateral:** Mínimo de **48dp** em cada borda lateral.
- **Margem Segura Vertical:** Mínimo de **27dp** nas bordas superior e inferior.
- **Backdrops e Hero:** Sangram até a borda física da tela por baixo dos trilhos de navegação.

### 5.2 Rail de Navegação Lateral
- **Largura:** 80dp fixos (`values-television/dimens.xml:tv_rail_width`).
- **Ícones:** 24dp centralizados vertical e horizontalmente.
- **Indicador Ativo:** Barra discreta de 16 × 2dp posicionada abaixo do ícone com a cor de acento `#22D3EE`.
- **Fundo:** Gradiente sutil de `obsidian_bg_base` para transparente, sem bloquear o sangramento do hero backdrop.

### 5.3 Anéis de Foco e Transições
- Largura do anel de foco: **3dp**.
- Cantos: contínuos acompanhando o raio do componente (cards: 8dp a 12dp; botões: 8dp ou pílula).
- Elevação e Escala: 1.05x a 1.08x em cards focados, respeitando a redução de movimento quando o sistema solicitar (`ANIMATOR_DURATION_SCALE == 0`).

---

## 6. Verificadores Automatizados no CI

- `tools/checks/check_contrast.py`: Verifica contraste WCAG AA (> 4.5:1).
- `tools/checks/check_layout_colors.py`: Bloqueia o uso de hexadecimais brutos nos layouts.
- `tools/checks/check_typography.py`: Garante o piso tipográfico mínimo de 12sp.
- `tools/checks/check_strings.py`: Valida a paridade estrita entre `values/strings.xml` e `values-pt/strings.xml`.


---

## 7. Tela de Detalhes da Série (Alvo v4)

Especificação de referência para a tela canônica de detalhes de série/franquia no Android TV (mockup em [`docs/design/mockups/series-detail-v4.png`](mockups/series-detail-v4.png)).

### 7.1 Backdrop e Scrim Direcional
- **Composição em Camadas:** Imagem do backdrop em tela cheia (1920×1080), sem overlay escuro uniforme e sem blur dinâmico (render-cost proibitivo em chipsets de TV).
- **Scrim Direcional por Vetores XML:**
  - **Gradiente Horizontal:** Inicia com ~95% de opacidade na borda esquerda (`rgba(7, 11, 17, 0.95)` / `@color/obsidian_bg_base`), suaviza até transparente em ~45% da largura da tela, deixando a arte do canto superior direito e centro completamente nítida e brilhante.
  - **Gradiente Vertical:** Transparente no terço superior (~50% da altura) até sólido (`@color/obsidian_bg_base`) no rodapé, fornecendo contraste absoluto sob a área de episódios e listas.

### 7.2 Painel Hero (Cabeçalho de Metadados)
- **Título Canônico:** Sempre o nome raiz da Franquia (ex: "Monogatari Series"), nunca o nome isolado da subpasta selecionada.
- **Pill de Franquia:** Pílula informativa translúcida indicando "Franquia Canônica • N Obras Disponíveis" visível apenas quando houver 2 ou mais obras agrupadas (`PillVisibilityPolicy`).
- **Linha de Metadados e Badges Condicionais:**
  - Exibição de Ano de Lançamento e Nota (formato "★ X.XX MAL" ou "★ X.X TMDB").
  - Badges técnicos ("16+", "HD", "1080p") renderizados apenas sob metadados reais confirmados, omitindo badges inexistentes.
- **Sinopse com Expansão:** Sinopse limitada a no máximo 2 linhas com link de expansão "Ler sinopse completa" em cinza-azulado (`obsidian_text_secondary`).
- **Ações Primárias:** Fileira horizontal de botões: botão primário "Assistir" / "Continuar" com preenchimento de acento e ícone de play, seguido de botões de favoritos e ações secundárias em estilo glass.

### 7.3 Abas e Divisão em Dois Painéis
- **Abas de Navegação:** Tipografia limpa sem bloco retangular de fundo; aba ativa marcada com sublinhado fino ciano (`obsidian_accent_primary`).
- **Painel Esquerdo (~32% da largura):**
  - Card vertical do arco/obra atualmente selecionado com thumbnail, título e metadado ("N Episódios • ano").
  - Fileira de chips secundários logo abaixo do card selecionado para alternar categorias de conteúdo: "Episódios", "Especiais (SP)", "Aberturas (OP/ED)", "Prévias (PV)".
- **Painel Direito (~68% da largura):**
  - Lista vertical contendo cards horizontais de episódios da categoria ativa, sobre o fundo sólido inferior.
  - Cada card exibe número do episódio, miniatura 16:9, título, duração e marcador de progresso.

### 7.4 Estados de Episódio e Indicadores Visuais
- **Não Assistido:** Borda sutil de superfície glass, sem preenchimento colorido de progresso.
- **Em Andamento:** Barra de progresso horizontal fina posicionada na base da miniatura com a cor de acento ciano (`obsidian_accent_primary`), refletindo a porcentagem exata salva no Room.
- **Concluído:** Indicador de status com checkmark em verde esmeralda (`obsidian_success` `#54D18A`), comunicando conclusão sem poluição visual.

### 7.5 Navegação por Foco no D-Pad (10-Foot Experience)
- **Foco Inicial:** Ao abrir a tela, o foco repousa automaticamente na ação primária ("Assistir" / "Continuar").
- **Ordem de Travessia:**
  - D-pad Down a partir do Hero navega para a barra de abas ou diretamente para a coluna de arcos.
  - No painel esquerdo: D-pad Down percorre os arcos; D-pad Right navega diretamente para o primeiro card de episódio visível no painel direito.
  - No painel direito: D-pad Up/Down rola a lista de episódios; D-pad Left retorna para o arco ou chip ativo no painel esquerdo.
- **Memória de Foco:** Ao retornar do player ou de um diálogo, a seleção e o anel de foco são restaurados com precisão no episódio correspondente.
- **Unicidade de Foco:** Apenas um elemento em toda a tela exibe o anel de foco ciano de 3dp por vez; estados selecionados (como arco ativo) usam preenchimento sutil de superfície ou badge, nunca o anel de foco ativo.

### 7.6 Margens Seguras de TV (Overscan Protection)
- **Margens Laterais:** Mínimo de **48dp** em ambas as bordas (esquerda e direita).
- **Margens Verticais:** Mínimo de **27dp** no topo e na base da tela.
- **Proteção de Borda:** Nenhum texto legível, botão, chip ou elemento interativo pode penetrar a zona de overscan. Apenas a arte do backdrop pode sangrar até a borda física por baixo dos scrims de proteção.
