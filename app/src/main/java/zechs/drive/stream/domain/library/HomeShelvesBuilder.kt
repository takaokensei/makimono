package zechs.drive.stream.domain.library

/**
 * Monta as estantes organizadas da tela inicial a partir dos itens da biblioteca.
 * Regra de domínio pura (ADR-001 / Seção 4.10).
 */
class HomeShelvesBuilder {

    fun build(
        continueWatching: List<LibraryMediaItem> = emptyList(),
        starred: List<LibraryMediaItem> = emptyList(),
        followed: List<LibraryMediaItem> = emptyList(),
        recentlyAdded: List<LibraryMediaItem> = emptyList(),
        catalog: List<LibraryMediaItem> = emptyList()
    ): List<LibraryShelf> {
        val shelves = mutableListOf<LibraryShelf>()

        // 1. Continuar assistindo (primeira estante, ordenada por último acesso)
        val inProgress = continueWatching
            .filter { it.progressPercentage in 1f..95f }
            .sortedByDescending { it.lastAccessedTimestamp }

        if (inProgress.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_continue_watching",
                    title = "Continuar Assistindo",
                    items = inProgress
                )
            )
        }

        // 2. Favoritos
        if (starred.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_starred",
                    title = "Favoritos",
                    items = starred
                )
            )
        }

        // 3. Novos episódios / Seguidos
        if (followed.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_followed",
                    title = "Novos Episódios",
                    items = followed
                )
            )
        }

        // 4. Adicionados recentemente
        if (recentlyAdded.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_recently_added",
                    title = "Adicionados Recentemente",
                    items = recentlyAdded
                )
            )
        }

        // 5. Catálogo geral
        if (catalog.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_catalog",
                    title = "Explorar Catálogo",
                    items = catalog
                )
            )
        }

        return shelves
    }
}
