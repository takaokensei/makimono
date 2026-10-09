# Design System — Makimono (Obsidian)

Documento canônico do sistema visual e de interação do Makimono para Android e Android TV (10-foot experience).

---

## 1. Princípios do Design System Obsidian

1. **Local-First & Imersivo:** Fundo preto profundo e superfícies cinza-escuro com translucidez elegante para destacar pôsteres e mídia.
2. **Acento Visual Distinto:** Torii / Ciano `#22D3EE` como cor de acento primária para foco, seleções ativas e indicadores.
3. **Piso Tipográfico Estrito:** Nenhum elemento de texto na interface pode ter tamanho inferior a **12sp** (`values/dimens.xml`), garantindo legibilidade mínima a qualquer distância.
4. **Foco Visível e Previsível:** Todo elemento interativo na TV possui anel de foco bem delineado (espessura padrão de 3dp, cantos arredondados contínuos) e transições suaves de escala/elevação.

---

## 2. Paleta Canônica (Tokens Semânticos)

| Token | Valor Hex | Função / Aplicação |
|---|---|---|
| `obsidian_base` | `#0A0D14` | Fundo principal da aplicação |
| `obsidian_surface` | `#111622` | Cards, modais e superfícies elevadas |
| `obsidian_surface_variant` | `#1A202E` | Headers, barras de ferramenta, superfícies secundárias |
| `obsidian_accent` | `#22D3EE` | Acento ciano primário, anel de foco, indicador ativo |
| `obsidian_accent_variant` | `#06B6D4` | Estado pressionado/hover de elementos de acento |
| `obsidian_text_primary` | `#F1F5F9` | Títulos, rótulos principais (alto contraste > 12:1) |
| `obsidian_text_secondary` | `#94A3B8` | Subtítulos, metadados (contraste WCAG AA > 4.5:1) |
| `obsidian_text_muted` | `#64748B` | Textos desativados ou secundários |
| `obsidian_focus_ring` | `#22D3EE` | Borda de foco em D-pad na TV |

---

## 3. Diretrizes de Android TV (10-Foot Experience)

### 3.1 Overscan e Áreas Seguras
- **Canvas Base de TV:** 960 × 540dp (escalado pelo sistema para 1080p e 4K).
- **Margem Segura Lateral:** Mínimo de **48dp** em cada borda lateral.
- **Margem Segura Vertical:** Mínimo de **27dp** nas bordas superior e inferior.
- **Backdrops e Hero:** Sangram até a borda física da tela por baixo dos trilhos de navegação.

### 3.2 Rail de Navegação Lateral
- **Largura:** 80dp fixos (`values-television/dimens.xml:tv_rail_width`).
- **Ícones:** 24dp centralizados vertical e horizontalmente.
- **Indicador Ativo:** Barra discreta de 16 × 2dp posicionada abaixo do ícone com a cor de acento `#22D3EE`.
- **Fundo:** Gradiente sutil de `obsidian_base` para transparente, sem bloquear o sangramento do hero backdrop.

### 3.3 Anéis de Foco e Transições
- Largura do anel de foco: **3dp**.
- Cantos: contínuos acompanhando o raio do componente (cards: 8dp a 12dp; botões: 8dp ou pílula).
- Elevação e Escala: 1.05x a 1.08x em cards focados, respeitando a redução de movimento quando o sistema solicitar (`ANIMATOR_DURATION_SCALE == 0`).

---

## 4. Verificação de Acessibilidade e Contraste
- Todos os pares de texto e fundo devem cumprir o padrão mínimo WCAG AA (4.5:1 para texto normal, 3.0:1 para texto grande).
- Scripts automatizados em `tools/checks/check_contrast.py` executam validação contínua.
