# Especificação — Navegação Global, Sidebar (56dp / 240dp) e Memória de Foco

**Data:** 15/10/2026  
**Superfície:** `RailController.kt`, `FocusCoordinator.kt`, `FocusMemory.kt`, `fragment_home.xml` (`television`, `land`, `sw720dp-land`)

---

## 1. Geometria e Estados da Navigation Rail

| Parâmetro | Estado Retraído (Foco no Conteúdo) | Estado Expandido (Foco na Rail) |
| :--- | :--- | :--- |
| Largura (`layout_width`) | `56dp` (`@dimen/tv_nav_rail_width`) | `240dp` (`@dimen/tv_nav_rail_expanded_width`) |
| Ícones | `24dp × 24dp`, `paddingStart="16dp"`, `paddingEnd="16dp"` | `24dp × 24dp`, `paddingStart="16dp"`, `paddingEnd="16dp"` |
| Rótulos (`TextView`) | `View.GONE` | `View.VISIBLE` (`marginStart="14dp"`) |
| Plano de Fundo | `@drawable/bg_sidebar_panel` (gradiente translúcido) | `@color/obsidian_glass_heavy` (`#F4070A10`) |
| Elevação (`elevation`) | `0dp` | `24dp` |
| Duração da Transição | `180ms` (`DecelerateInterpolator`) | `180ms` (`DecelerateInterpolator`) |

---

## 2. Ordem Vertical de Topo a Base (Padrão Crunchyroll TV)

1. `btnBrandLogo` — Logo Torii (`ic_makimono_torii`) + texto `"MAKIMONO"` (visível apenas expandido).
2. `userProfilePill` — Avatar do perfil ativo (`ivUserAvatar`) + nome do perfil (`tvUserName`, visível apenas expandido). Abre `ProfileSelectionFragment`.
3. `btnNavInicio` — Seção `"Início"` (Hero + Prateleiras + Catálogo).
4. `btnNavFavoritos` — Seção `"Minha Lista"` (títulos favoritados com estrela).
5. `btnNavAnimes` — Seção `"Catálogo"` (grade completa da pasta raiz `oneblacki`).
6. `btnNavFilmes` — Seção `"Filmes"` (longas-metragens).
7. `btnNavPastas` — Seção `"Pastas"` (`FilesFragment` para navegação direta no Google Drive).
8. `View` espaçadora (`layout_weight="1"`).
9. `btnNavConfig` — Ancorado no rodapé (`SettingsFragment`).

---

## 3. Máquina de Estados de Foco e Botão `Back`

- **Conteúdo → Rail (`KEYCODE_DPAD_LEFT` na borda esquerda ou `Back`):**
  - Expande a Rail para `240dp` e foca o item correspondente à aba ativa (`focusActiveRailItem()`).
- **Rail → Conteúdo (`KEYCODE_DPAD_RIGHT`):**
  - Retrai a Rail para `56dp` e move o foco para o primeiro alvo visível da área de conteúdo (`focusFirstContentTarget()`).
- **`Back` com Foco na Rail:**
  - Se `currentTab != "Início"`, retorna para a aba `"Início"` sem fechar o aplicativo.
  - Se `currentTab == "Início"`, exibe o diálogo de confirmação de saída (`showExitConfirmationDialog()`).

---

## 4. Contrato de Memória de Foco (`FocusMemory`)

- **Gravação:** `FocusMemory.remember(shelfKey: String, itemIndex: Int, itemId: String?)` armazena tanto o índice focado quanto o identificador estável (`DriveFile.id`, `videoId` ou `fileId`).
- **Resolução após Reordenação:** `resolveTargetIndex(shelfKey, currentItems)` busca primeiro `currentItems.indexOf(rememberedId)`. Se encontrado, atualiza `indexMemory[shelfKey]` e retorna a nova posição.
- **Fallback após Remoção:** Se `rememberedId` não existir mais na lista (ex.: item removido da fila ou concluído), aplica `rememberedIndex.coerceIn(0, currentItems.size - 1)`.
