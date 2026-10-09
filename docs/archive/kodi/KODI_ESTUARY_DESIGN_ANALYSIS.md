# Análise de Design System: Kodi Estuary para Android TV (10-Foot UI)

Este documento sintetiza os princípios de design visual e de usabilidade extraídos diretamente do código-fonte do tema oficial **Estuary** do Kodi (`addons/skin.estuary/`), com foco em sua aplicação em um **player de vídeo leve e nativo em Android (Kotlin)**.

---

## 1. Visão Geral & Filosofia

O Kodi Estuary é referência mundial em interfaces "10-foot" (experiência de sala de estar a 3 metros da tela de TV), projetado para navegação fluida exclusivamente via **D-Pad do controle remoto** (Cima, Baixo, Esquerda, Direita, OK/Enter, Voltar).

Ao contrário do Kodi completo — que é um media center pesado com dezenas de dependências em C++, Python e banco de dados SQLite complexo —, o nosso projeto busca **apenas a elegância estética, a ergonomia de navegação e a responsividade do OSD do Kodi**, implementada com a máxima leveza através de **componentes nativos do Android** (XML, `ConstraintLayout`, `RecyclerView`, `MaterialCardView` e seletores de estado).

---

## 2. Paleta de Cores e Tokens Visuais (Extraídos de `defaults.xml`)

| Token Kodi | Valor Hex / Alpha | Papel na UI |
| :--- | :--- | :--- |
| `primary_background` | `#0E597E` / `#0A1C2A` | Gradiente radial de fundo cinematográfico (Petroleum / Deep Cyan) |
| `background` | `#000000` | Preto absoluto (bordas e overscan) |
| `dialog_tint` | `#1A2123` | Fundo de superfícies, cartões e painéis laterais (dark slate) |
| `dialog_header_tint` | `#147995` | Cabeçalhos e barras de título |
| **`button_focus`** | **`#12A0C7`** | **O ciano/teal elétrico clássico do Kodi**, usado para destacar o item focado |
| `button_alt_focus` | `#8012A0C7` (50%) | Foco secundário / estados hover / seleções parciais |
| `selected` | `#FFB70F` | Destaque dourado/âmbar para itens marcados (ex.: favoritos) |
| `white` / `text_primary` | `#F0F0F0` | Texto principal de alto contraste |
| `grey` / `text_secondary` | `#A0A0A0` | Metadados, tempos, tamanhos de arquivo (WCAG AA) |
| `border_alpha` | `#60FFFFFF` (37%) | Bordas sutis de delimitação |

---

## 3. Arquitetura de Layout: As Duas Telas Principais

### A. Home Dashboard (Navegação em Trilho Lateral + Prateleiras de Conteúdo)
*Conforme visível nas capturas de tela anexas:*

```
+-----------------------------------------------------------------------------------+
| [KODI / LOGO]                                        20:29 Quarta-feira, 2 Março  |
|                                                                                   |
| [Pesquisar...]                                                                    |
|                                                                                   |
| > Meu Drive          [ Continuar Assistindo ]                                     |
|   Drives Compart.    +------------------+  +------------------+                   |
|   Compartilhados     | [Miniatura]      |  | [Miniatura]      |                   |
|   Com Estrela        | Ep 04 - Batalha  |  | Filme X          |                   |
|   Lixeira            | 14:20 / 24:00    |  | 1:02:10 / 2:15:00|                   |
|                      | [======    ] 60% |  | [===       ] 28% |                   |
|                      +------------------+  +------------------+                   |
|                                                                                   |
|                      [ Arquivos Recentes / Pastas ]                               |
|                      +-------+  +-------+  +-------+  +-------+                   |
| [Config] [Sair]      | Pasta1|  | Pasta2|  | Ep 01 |  | Ep 02 |                   |
|                      +-------+  +-------+  +-------+  +-------+                   |
+-----------------------------------------------------------------------------------+
```

1. **Trilho Lateral Esquerdo (Left Navigation Rail)**:
   - Largura fixa (~220dp–260dp).
   - Logotipo e relógio/status integrados.
   - Itens de navegação empilhados verticalmente com ícone + rótulo.
   - **Foco do D-Pad**: O item sob foco adquire fundo em formato de pílula retangular na cor ciano `#12A0C7` com ícone e texto brancos nítidos.
   - Pressionar **Direita** no controle remoto transfere o foco imediatamente para a prateleira de conteúdo ativa.

2. **Prateleiras Horizontais (Content Shelves)**:
   - `RecyclerView` horizontal com espaçamento de 16dp.
   - **Prateleira 1: "Continuar assistindo"**: Cartões em proporção 16:9 com título, tempo decorrido/total e barra de progresso em `#12A0C7`.
   - **Prateleira 2: "Pastas / Acesso Rápido"**: Atalhos para as pastas mais acessadas.
   - Pressionar **Esquerda** a partir do primeiro item de qualquer prateleira devolve o foco para o menu lateral.

3. **Status Bar Minimalista Superior**:
   - Relógio digital elegante no canto superior direito (`20:29`).

---

### B. Player OSD (Extraído de `VideoOSD.xml` & `DialogSeekBar.xml`)

1. **Barra de Controle Inferior (Docked OSD)**:
   - Fundo translúcido com gradiente escuro na base (`#CC1A2123`).
   - Barra de progresso ultrafina com preenchimento em `#12A0C7`.
   - **Capítulos**: Marcadores verticais discretos ao longo da linha do tempo indicando aberturas, encerramentos e partes.
   - **Tempo**: Decorrido à esquerda, restante/total à direita.
   - **Ações OSD**:
     - Botão Play/Pause central com halo de foco.
     - Retroceder 10s e Avançar 10s.
     - Botão "Pular Abertura (+90s)" em destaque.
     - Seletores rápidos de Faixa de Áudio, Legendas e Aspecto de Vídeo.
   - **Auto-hide**: Desaparece suavemente após 3 segundos de inatividade sem interromper o vídeo.

---

## 4. Diretrizes de Implementação Leve para Android Nativo

* **Performance 60 FPS na TV**: Uso estrito de `RecyclerView` com `DiffUtil` e layouts simples sem aninhamentos profundos.
* **Hardware Canvas**: Não utilizar bibliotecas pesadas de animação em tempo de execução; preferir animações simples de `fade` e `scale` (ex.: `scaleX: 1.05` no foco).
* **Consistência de D-Pad**: Garantir `android:focusable="true"`, `android:clickable="true"` e `android:nextFocusRight`, `android:nextFocusLeft` para garantir que a navegação do controle remoto nunca "se perca".
