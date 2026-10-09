package zechs.drive.stream.domain.library

/**
 * Modelo de domínio puro para um item da biblioteca.
 */
data class LibraryMediaItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val mediaType: MediaType = MediaType.UNKNOWN,
    val lastAccessedTimestamp: Long = 0L,
    val progressPercentage: Float = 0f
)

/**
 * Estante de mídia organizada para a tela inicial.
 */
data class LibraryShelf(
    val id: String,
    val title: String,
    val items: List<LibraryMediaItem>
)

/**
 * Caso de uso para construir estantes organizadas da tela inicial a partir dos itens da biblioteca.
 * Lógica pura sem dependências do Android.
 */
class BuildLibraryShelvesUseCase {

    operator fun invoke(
        allItems: List<LibraryMediaItem>,
        continueWatchingItems: List<LibraryMediaItem> = emptyList()
    ): List<LibraryShelf> {
        val shelves = mutableListOf<LibraryShelf>()

        // 1. Estante "Continuar Assistindo" (se houver itens com progresso)
        val inProgress = continueWatchingItems
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

        // 2. Estante "Adicionados Recentemente"
        val recent = allItems
            .sortedByDescending { it.lastAccessedTimestamp }
            .take(20)

        if (recent.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_recently_added",
                    title = "Adicionados Recentemente",
                    items = recent
                )
            )
        }

        // 3. Estante "Séries"
        val series = allItems.filter { it.mediaType == MediaType.SERIES }
        if (series.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_series",
                    title = "Séries",
                    items = series
                )
            )
        }

        // 4. Estante "Filmes"
        val movies = allItems.filter { it.mediaType == MediaType.MOVIE }
        if (movies.isNotEmpty()) {
            shelves.add(
                LibraryShelf(
                    id = "shelf_movies",
                    title = "Filmes",
                    items = movies
                )
            )
        }

        return shelves
    }
}
