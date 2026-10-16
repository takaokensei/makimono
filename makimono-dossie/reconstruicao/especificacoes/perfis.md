# Especificação — Perfis e Sessão (`ProfileSelectionFragment` / `ProfileManager`)

**Data:** 15/10/2026

- **Acesso Rápido na Rail:** `userProfilePill` posicionado no topo da Navigation Rail (abaixo do logo Torii), alinhado ao padrão da Crunchyroll Android TV.
- **Isolamento de Dados:** `ProfileManager.activeProfileFlow` propaga o `profileId` ativo para `WatchHistoryRepository`, `WatchQueueRepository` e `FollowedFolderRepository`.
