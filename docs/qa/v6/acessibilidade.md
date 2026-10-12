# Auditoria e Execução de Texto e Acessibilidade (Fase 7 — T-701 a T-724)

## 1. Zero Literais de UI em Kotlin (`T-720`)

Todos os literais de interface em Kotlin (`Toast.makeText(..., "...")`, `.setTitle("...")`, `.setMessage("...")`, `.setText("...")`) foram extraídos para `app/src/main/res/values/strings.xml` e `app/src/main/res/values-pt/strings.xml` (24 novas chaves localizadas) e protegidos continuamente por `tools/checks/check_kotlin_literals.py` em `scripts/verify-checks.ps1`.

| Arquivo | Chaves extraídas |
|---|---|
| `ui/files/FilesFragment.kt` | `R.string.drive_session_unavailable` |
| `ui/home/controller/CatalogPresentationController.kt` | `R.string.home_catalog_sort_title`, `R.string.home_catalog_genre_filter_title`, `R.string.home_catalog_genre_all` |
| `ui/home/controller/HomeActionHandler.kt` | `R.string.drive_session_unavailable`, `R.string.home_marked_watched`, `R.string.home_progress_reset`, `R.string.home_added_to_favorites`, `R.string.home_removed_from_favorites`, `R.string.home_added_to_queue`, `R.string.home_already_in_queue` |
| `ui/main/MainActivity.kt` | `R.string.update_available_title` |
| `ui/player/PlayerActivity.kt` | `R.string.drive_session_unavailable` |
| `ui/player/SubtitleStyleDialog.kt` | `R.string.subtitle_style_saved` |
| `ui/player2/MPVActivity.kt` | `R.string.drive_session_unavailable`, `R.string.mpv_no_chapters_found`, `R.string.mpv_switching_to_exoplayer` |
| `ui/queue/QueueFragment.kt` | `R.string.drive_session_unavailable`, `R.string.queue_clear_dialog_title`, `R.string.queue_clear_dialog_message`, `R.string.queue_clear_confirm` |
| `ui/settings/DataSessionSettingsDelegate.kt` | `R.string.settings_backup_created`, `R.string.settings_backup_error`, `R.string.settings_backup_restored`, `R.string.settings_backup_invalid`, `R.string.settings_cache_cleared` |
| `ui/settings/IntegrationsSettingsDelegate.kt` | `R.string.settings_mal_disconnected`, `R.string.settings_mal_tv_not_found`, `R.string.settings_trakt_disconnected` |

## 2. Plurais e Formatação (`T-721`)

- Adicionada a quantidade `many` exigida pelo locale `pt` em `values-pt/strings.xml` para `home_continue_remaining_minutes` e `notif_new_episodes_body` (zerando `MissingQuantity`).
- Convertido `home_featured_episode_count` de `<string>` para `<plurals>` (`one`, `many`, `other`) em `values/strings.xml`, `values-pt/strings.xml` e `FeaturedHeroBinder.kt` (zerando `PluralsCandidate`).

## 3. Descrições de Conteúdo (`T-722`)

Os 12 itens `ContentDescription` da baseline foram resolvidos marcando ícones puramente decorativos (acompanhados de `TextView` adjacente no mesmo contêiner focável) com `android:importantForAccessibility="no"`:
- `dialog_mal_auth.xml` (`ivDialogIcon`, `ivMalQrCode`)
- `fragment_profile_selection.xml` e `layout-land/fragment_profile_selection.xml` (`ivFantasyBg`)
- `fragment_settings.xml` (`ivSettingProfileAvatar`, `ivMalChevron`, chevron de transferência)
- `item_profile_card.xml` (`ivAddIcon`, `ivEditBadge`)
- `view_kodi_info_hud.xml`, `layout-land/view_kodi_info_hud.xml`, `layout-h480dp-land/view_kodi_info_hud.xml` (ícone de cabeçalho)

## 4. Rótulos Falados (TalkBack) na Home e no Detalhe (`T-723`)

- `EpisodeCardAccessibilityLabel.build` constrói o rótulo falado dos episódios na tela de Detalhe (`"Reproduzir Episódio 02 - Uma nova jornada, 24 min, Áudio duplo, Em andamento, 42% assistido"`).
- `EpisodeCardAccessibilityLabel.buildContinueWatchingLabel` constrói o rótulo falado dos cards da prateleira "Continuar assistindo" na Home (`"Frieren, T1: Ep. 05, 12 min restantes"`), aplicado em `ContinueWatchingAdapter.kt` e validado em `EpisodeCardAccessibilityLabelTest.kt`.

## 5. Contraste sobre Imagem e Gradientes (`T-724`)

`tools/checks/check_contrast.py` foi estendido para compor o alfa dos 5 gradientes de proteção (`bg_scrim_bottom.xml`, `bg_scrim_left.xml`, `shelf_item_scrim.xml`, `series_detail_hero_scrim.xml`, `player_bottom_scrim.xml`) sobre arte branca pura (`#FFFFFF`) e verificar contraste $\ge 4,5:1$ para `text_primary` e `text_secondary` nas 6 paletas:

| Drawable | Contraste `text_primary` (sobre `#FFFFFF`) | Contraste `text_secondary` (sobre `#FFFFFF`) |
|---|---|---|
| `bg_scrim_bottom.xml` | 18,28:1 | 9,81:1 |
| `bg_scrim_left.xml` | 17,66:1 | 9,47:1 |
| `shelf_item_scrim.xml` | 17,86:1 | 9,58:1 |
| `series_detail_hero_scrim.xml` | 18,46:1 | 9,91:1 |
| `player_bottom_scrim.xml` | 16,24:1 | 8,71:1 |
