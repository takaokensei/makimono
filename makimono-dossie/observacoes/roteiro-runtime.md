# Roteiro de Observação em Runtime no Android TV Emulator (Etapa V2)

**Data:** 14 de Outubro de 2026  
**Dispositivo:** `emulator-5554` (`sdk_google_atv64_x86_64`, API 36)  
**Viewport:** 1920 x 1080 px | Densidade: 320 dpi (Fator 2.0x: 1dp = 2px; dimensões em dp = 960 x 540 dp)  
**App:** `com.crunchyroll.crunchyroid` v3.75.0 (instalado legitimamente via Google Play).

---

## 1. Roteiro Passo a Passo de Interação D-Pad e Medições

Todas as transições foram executadas e verificadas via extração da árvore de acessibilidade (`uiautomator dump`):

```text
                                  [ESTADO INICIAL]
                                  Hero: startWatching
                                      (focused)
                                         │
                         ┌───────────────┴───────────────┐
                    DPAD_RIGHT                       DPAD_DOWN
                         ▼                               ▼
                 Hero: MoreInfo                  Shelf: panelCard_1_1
                                                         │
                                                     DPAD_RIGHT (x2)
                                                         ▼
                                                 Shelf: panelCard_1_3
                                                         │
                                                     DPAD_UP
                                                         ▼
                                                 Hero: startWatching
                                                         │
                                                     DPAD_DOWN
                                                         ▼
                                                 Shelf: panelCard_1_3
                                               (Foco Restaurado: Item 3!)
                                                         │
                                                     DPAD_LEFT (x3)
                                                         ▼
                                                Rail Expandido (264dp)
                                                         │
                                                       BACK
                                                         ▼
                                                 Diálogo de Saída
                                                         │
                                                       BACK
                                                         ▼
                                                Rail Expandido (Home)
                                                         │
                                                     DPAD_RIGHT
                                                         ▼
                                                Rail Colapsa (80dp)
                                                Shelf: panelCard_1_1
                                                         │
                                                     DPAD_CENTER
                                                         ▼
                                                ShowDetailsActivity
                                                         │
                                                       BACK
                                                         ▼
                                                Home: panelCard_1_1
                                               (Foco Restaurado com Sucesso!)
```

---

## 2. Tabela de Estados, Nós Capturados e Geometria Medida

| Passo | Ação do D-Pad | Nó com Foco (`focused="true"`) | Bounds (Pixels) | Bounds Convertidos (dp) | Efeito Observado na Interface |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **0** | Estado Inicial | `startWatching` | `[178,662][590,758]` | `[89,331][295,379]` | Hero banner visível; Rail colapsado em `[0,0][160,1080]` (80dp de largura). |
| **1** | `DPAD_RIGHT` | `heroComponentMoreInfoButton` | `[630,662][918,758]` | `[315,331][459,379]` | Foco desloca horizontalmente entre botões do Hero sem rolar tela. |
| **2** | `DPAD_DOWN` | `panelCard_1_1` | `[170,544][460,1080]` | `[85,272][230,540]` | Tela rola suavemente para cima trazendo a prateleira "Trending Anime" para o centro. |
| **3** | `DPAD_RIGHT` (x2) | `panelCard_1_3` | `[750,272][1040,808]` | `[375,136][520,404]` | Foco percorre os cards horizontais da prateleira (largura card = 290px = 145dp). |
| **4** | `DPAD_UP` | `startWatching` | `[178,662][590,758]` | `[89,331][295,379]` | Foco sobe de volta para a ação primária do Hero; tela rola para baixo. |
| **5** | `DPAD_DOWN` | `panelCard_1_3` | `[750,544][1040,1080]` | `[375,272][520,540]` | **Retenção de Foco Confirmada:** O foco retorna exatamente ao item 3 previamente selecionado! |
| **6** | `DPAD_LEFT` (x3) | Item `Home` no Rail | `[0,386][528,490]` | `[0,193][264,245]` | Rail expande de 160px para 528px (264dp) e empurra o conteúdo (`homeFeedComponent` vai para `528px`). |
| **7** | `BACK` (no Rail) | `exitDialogConfirm` | `[620,572][940,668]` | `[310,286][470,334]` | **Proteção contra Saída Involuntária:** Abre diálogo "Exit Crunchyroll" em vez de fechar o app. |
| **8** | `BACK` (no Diálogo)| Item `Home` no Rail | `[0,386][528,490]` | `[0,193][264,245]` | Diálogo cancelado com segurança; foco restaurado no item do menu lateral. |
| **9** | `DPAD_RIGHT` | `panelCard_1_1` | `[170,272][460,808]` | `[85,136][230,404]` | Rail colapsa instantaneamente para 160px (80dp); foco pousa no primeiro card visível. |
| **10**| `DPAD_CENTER` | `showDetailsHeroWatchlistButton` | `[88,652][752,748]` | `[44,326][376,374]` | Abre `ShowDetailsActivity`; foco inicial posiciona-se no botão "Add to Watchlist". |
| **11**| `BACK` (em Detalhes)| `panelCard_1_1` | `[170,272][460,808]` | `[85,136][230,404]` | **Restauração de Contexto de Origem:** Retorna para a Home com foco perfeitamente ancorado no card que disparou o clique. |

---

## 3. Padrões Canônicos de TV Extraídos para o Makimono

1. **Largura da Barra Lateral (Rail Navigation):**
   - Estado Colapsado: `80dp` (ícones de 48dp centralizados com 16dp de margem).
   - Estado Expandido: `264dp` (revela labels textuais sem sobrepor o conteúdo como scrim, mas redimensionando a viewport do feed).
2. **Dimensão dos Cards em Prateleiras Horizontais:**
   - Proporção: Largura de `145dp`, espaçamento inter-card de `8dp` (4dp de margem interna em cada card).
   - Altura do Card: Aproximadamente `215dp` a `268dp` (proporção 2:3 padrão de pôster de anime).
3. **Comportamento do Botão "Voltar" (Back Handling):**
   - Do conteúdo interno → Retorna o foco para o menu lateral esquerdo (ou item anterior).
   - Do menu lateral esquerdo → Dispara diálogo de confirmação de saída ("Deseja sair do Makimono?"), nunca fechando abruptamente o aplicativo.
