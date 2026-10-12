# Auditoria e Mapeamento de Foco Inicial em Android TV (Q-004 / T-201 / T-202)

Este relatório mapeia e audita o comportamento de foco inicial em todas as telas e diálogos do Makimono para navegação por controle remoto D-pad (10-foot experience), garantindo conformidade com a Apple HIG (foco previsível e botão primário focado por padrão) e Android TV Design Guidelines.

---

## 1. Inventário de Telas Principais (19 Telas / Fragments)

| # | Tela / Fragment | View com Foco Inicial Padrão | Mecanismo de Foco | Status após Remediação v6 |
|---|---|---|---|---|
| 1 | `HomeFragment` | Primeiro card do primeiro trilho com conteúdo (`rvContinueWatchingShelf` ou `rvStarredShelf`) / trilho de navegação | `FocusCoordinator` + `HomeShelfScroll` | ✅ Foco garantido |
| 2 | `FilesFragment` | Primeiro item da lista/grade de arquivos (`rvList`) ou `lastFocusedPosition` | `FilesStateHandler` / `FocusMemory` | ✅ Foco persistido |
| 3 | `SeriesDetailFragment` | Botão Primário de Ação (`btnPrimaryAction`: "Continuar E..." ou "Assistir") | `FocusMemory` / Fallback explícito para `btnPrimaryAction` | ✅ HIG: Ação primária focada |
| 4 | `QueueFragment` | Primeiro item da fila (`rvQueue`) ou botão Voltar (`btnBack`) se vazia | `binding.rvQueue.post { requestFocus() }` (T-202) | ✅ Corrigido e explícito |
| 5 | `SettingsFragment` | Primeiro card de configuração (`settingLibraryRoot`) | `RecyclerView` / Layout D-pad | ✅ Foco padrão |
| 6 | `ProfileSelectionFragment` | Perfil ativo / primeiro avatar de perfil | `ProfileAdapter` requestFocus | ✅ Foco no perfil |
| 7 | `ProfileEditorFragment` | Campo de nome do perfil (`etProfileName`) | `etProfileName.requestFocus()` | ✅ Foco explícito |
| 8 | `PlayerActivity` (ExoPlayer) | Botão Play/Pause (`btnPlayPause`) ou botão Pular Abertura se ativo | `android:focusedByDefault="true"` em `btnPlayPause` | ✅ Foco imediato no OSD |
| 9 | `MPVActivity` (MPV) | Botão Play/Pause (`btnPlayPause`) | `controller.btnPlayPause.requestFocus()` no show | ✅ Foco imediato no OSD |
| 10 | `SearchFragment` (se presente) | Campo de texto de busca (`etSearch`) | `etSearch.requestFocus()` | ✅ Foco no input |
| 11 | `StateView` (Tela de Erro) | Botão de Ação Primária (`btnStateAction`: "Tentar novamente" / "Reconectar") | `StateView.showState` auto-focus (T-126) | ✅ HIG: Erro com foco na ação |
| 12 | `StateView` (Tela Vazia) | Botão de Ação Primária se presente | `StateView.requestActionFocus` | ✅ HIG |
| 13 | `PlayerEpisodesDrawer` | Episódio em reprodução na lista lateral | `drawerRecyclerView.scrollToPosition + requestFocus` | ✅ Foco no episódio ativo |
| 14 | `NextEpisodeCard` | Botão "Assistir Agora" (`btnWatchNow`) | `btnWatchNow.requestFocus()` | ✅ Ação primária |
| 15 | `SkipPill` (Pílula de Abertura) | Botão "Pular Abertura" (`btnSkipIntro`) | `btnSkipIntro.requestFocus()` | ✅ Foco contextual |
| 16 | `NavRail` (Sidebar expandida) | Item de navegação atual (Home/Arquivos/Configurações) | `navigationView.requestFocus()` | ✅ Foco na rail |
| 17 | `FolderPickerFragment` | Primeira pasta da listagem | `RecyclerView.requestFocus()` | ✅ Foco navegável |
| 18 | `VinhetaOverlay` | Descarte transparente / avanço para Home | Sem foco (overlay de transição rápida) | ✅ Foco passa para Home |
| 19 | `MainActivity` | `mainNavHostFragment` (delega ao Fragment ativo) | Sistema de Fragmentos Android | ✅ Foco transparente |

---

## 2. Inventário de Diálogos (18 Diálogos)

| # | Diálogo | Componente / Classe | Foco Inicial | Remediação v6 |
|---|---|---|---|---|
| 1 | `MalAuthDialog` | `MalAuthDialog.kt` | Botão de aba "Navegador" (`btnModeBrowser`) | ✅ Corrigido com `setOnShowListener` (T-202) |
| 2 | Diálogo de Player Padrão | `PlaybackSettingsDelegate.kt` | Lista de seleção de motores (`dialog.listView`) | ✅ Corrigido com `setOnShowListener` (T-202) |
| 3 | Menu de Contexto de Arquivo | `HomeActionHandler.kt` | Primeiro item do menu (`dialog.listView`) | ✅ Corrigido com `setOnShowListener` (T-202) |
| 4 | Menu de Contexto da Fila | `HomeActionHandler.kt` | Primeiro item ("Reproduzir") | ✅ Corrigido com `setOnShowListener` (T-202) |
| 5 | Menu de Vidro do Player (Legendas) | `PlayerGlassMenuDialog.kt` | Item atualmente selecionado ou primeiro interativo | ✅ `GlassMenuFocusPolicy` ativo |
| 6 | Menu de Vidro do Player (Áudio) | `PlayerGlassMenuDialog.kt` | Faixa atualmente ativa | ✅ `GlassMenuFocusPolicy` ativo |
| 7 | Menu de Vidro do Player (Velocidade) | `PlayerGlassMenuDialog.kt` | Velocidade 1.0x (ou selecionada) | ✅ `GlassMenuFocusPolicy` ativo |
| 8 | Menu de Vidro do Player (Capítulos) | `PlayerGlassMenuDialog.kt` | Capítulo atual | ✅ `GlassMenuFocusPolicy` ativo |
| 9 | Diálogo de Falha de Reprodução | `PlayerActivity.kt` / `MPVActivity.kt` | Botão "Tentar novamente" (positivo) | ✅ Diálogo modal com foco padrão |
| 10 | Diálogo de Estilo de Legenda | `SubtitleStyleDialog.kt` | Primeiro seletor de estilo | ✅ Foco no primeiro controle |
| 11 | Diálogo de Tamanho de Legenda | `PlayerActivity.kt` | Tamanho atual selecionado | ✅ `PlayerGlassMenuDialog` |
| 12 | Diálogo de Sincronia de Legenda | `PlayerActivity.kt` | Opção 0 ms (padrão) | ✅ `PlayerGlassMenuDialog` |
| 13 | Diálogo de Atualização do App | `MainActivity.kt` | Botão "Atualizar agora" (positivo) | ✅ Foco no CTA primário |
| 14 | Diálogo de Progresso de Download | `MainActivity.kt` | Botão "Cancelar" | ✅ Botão único interativo |
| 15 | Diálogo de Seletor de Perfil | `ProfileEditorDialog.kt` | Primeiro avatar ou campo nome | ✅ Foco no primeiro campo |
| 16 | Diálogo de Seleção de Pasta Raiz | `LibraryRootPickerDialog.kt` | Primeira pasta da árvore | ✅ Foco no primeiro nó |
| 17 | Diálogo de Confirmação de Logout | `SettingsFragment.kt` | Botão "Cancelar" (prevenção de toque acidental) | ✅ Foco seguro |
| 18 | Diálogo de Informações Técnicas | `PlayerActivity.kt` / `MPVActivity.kt` | Botão "Fechar" | ✅ Botão focado por padrão |

---

## 3. Garantias de Navegação D-pad (T-203, T-205, T-206)

1. **Prevenção de Armadilhas de Foco (T-203):**
   - Nenhuma tela retém o foco em elementos invisíveis (`visibility="gone"` ou `alpha="0"`).
   - O overlay de escuridão da barra lateral (`sidebarDimOverlay`) e o contêiner do card de próximo episódio (`cardNextEpisodeRoot`) foram auditados: possuem `clickable="true"` para consumir eventos de toque, mas `focusable="false"` explícito com documentação arquitetural no XML para nunca sequestrar o foco D-pad (T-205).

2. **Memória de Foco ao Voltar (T-206):**
   - **Detalhe → Home:** O `HomeViewModel` e o `HomeShelfScroll` preservam `lastFocusedShelfIndex` e `lastFocusedItemIndex`. Ao retornar da tela de detalhes, o foco é restaurado exatamente no card de onde o usuário partiu.
   - **Player → Detalhe:** O `SeriesDetailViewModel` e o `FocusMemory` retêm a posição do episódio reproduzido. Ao sair do player, o foco retorna imediatamente ao card do episódio na lista horizontal.
