package zechs.drive.stream.ui.home

import java.util.Locale

/**
 * Pure policy for anime genre classification, normalization, and filtering
 * aligned with Crunchyroll category paradigms and Obsidian design.
 */
object AnimeGenrePolicy {

    const val GENRE_ALL = "Todos"

    private val STANDARD_GENRES = listOf(
        GENRE_ALL,
        "Ação",
        "Aventura",
        "Comédia",
        "Drama",
        "Fantasia",
        "Isekai",
        "Romance",
        "Ficção Científica",
        "Sobrenatural",
        "Suspense",
        "Cotidiano"
    )

    fun getStandardGenres(): List<String> = STANDARD_GENRES

    /**
     * Normalizes a raw genre string (from TMDB, AniList, or local database)
     * to a standard localized Portuguese title.
     */
    fun normalize(rawGenre: String): String {
        val clean = rawGenre.trim().lowercase(Locale.ROOT)
        return when (clean) {
            "all", "todos" -> GENRE_ALL
            "action", "ação", "acao" -> "Ação"
            "adventure", "aventura" -> "Aventura"
            "comedy", "comédia", "comedia" -> "Comédia"
            "drama" -> "Drama"
            "fantasy", "fantasia" -> "Fantasia"
            "isekai" -> "Isekai"
            "romance" -> "Romance"
            "sci-fi", "science fiction", "ficção científica", "ficcao cientifica" -> "Ficção Científica"
            "supernatural", "sobrenatural" -> "Sobrenatural"
            "thriller", "suspense", "mystery", "mistério", "misterio", "psychological", "psicológico", "psicologico" -> "Suspense"
            "slice of life", "cotidiano", "escolar", "school" -> "Cotidiano"
            "horror", "terror" -> "Terror"
            "sports", "esporte", "esportes" -> "Esportes"
            "mecha" -> "Mecha"
            "music", "música", "musica" -> "Música"
            "magic", "magia" -> "Fantasia"
            else -> rawGenre.trim().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
    }

    /**
     * Determines whether an anime with the given genres and/or title matches
     * the currently selected filter genre.
     */
    fun matches(
        selectedGenre: String,
        genres: List<String>,
        title: String? = null
    ): Boolean {
        val target = normalize(selectedGenre)
        if (target == GENRE_ALL || selectedGenre.isBlank()) {
            return true
        }

        // Match against normalized genres
        val normalizedGenres = genres.map { normalize(it) }
        if (normalizedGenres.any { it.equals(target, ignoreCase = true) }) {
            return true
        }

        // Fallback title-based heuristics when metadata genres are absent
        if (genres.isEmpty() && !title.isNullOrBlank()) {
            val lowerTitle = title.lowercase(Locale.ROOT)
            return when (target) {
                "Isekai" -> lowerTitle.contains("isekai") || lowerTitle.contains("re:zero") || lowerTitle.contains("slime")
                "Ação" -> lowerTitle.contains("shingeki") || lowerTitle.contains("jujutsu") || lowerTitle.contains("hero") || lowerTitle.contains("blade")
                "Romance" -> lowerTitle.contains("kaguya") || lowerTitle.contains("horimiya") || lowerTitle.contains("toradora")
                "Comédia" -> lowerTitle.contains("konosuba") || lowerTitle.contains("gintama") || lowerTitle.contains("spy x family")
                else -> false
            }
        }

        return false
    }
}
