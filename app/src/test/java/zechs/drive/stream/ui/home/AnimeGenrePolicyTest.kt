package zechs.drive.stream.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeGenrePolicyTest {

    @Test
    fun genreAllMatchesAnyInput() {
        assertTrue(AnimeGenrePolicy.matches(AnimeGenrePolicy.GENRE_ALL, listOf("Action", "Comedy")))
        assertTrue(AnimeGenrePolicy.matches("Todos", emptyList()))
        assertTrue(AnimeGenrePolicy.matches("all", emptyList(), "Any Title"))
        assertTrue(AnimeGenrePolicy.matches("", listOf("Drama")))
    }

    @Test
    fun normalizesEnglishGenresToPortuguese() {
        assertEquals("Ação", AnimeGenrePolicy.normalize("Action"))
        assertEquals("Comédia", AnimeGenrePolicy.normalize("Comedy"))
        assertEquals("Aventura", AnimeGenrePolicy.normalize("adventure"))
        assertEquals("Fantasia", AnimeGenrePolicy.normalize("Fantasy"))
        assertEquals("Ficção Científica", AnimeGenrePolicy.normalize("Sci-Fi"))
        assertEquals("Ficção Científica", AnimeGenrePolicy.normalize("science fiction"))
        assertEquals("Sobrenatural", AnimeGenrePolicy.normalize("Supernatural"))
        assertEquals("Suspense", AnimeGenrePolicy.normalize("Thriller"))
        assertEquals("Suspense", AnimeGenrePolicy.normalize("Mystery"))
        assertEquals("Cotidiano", AnimeGenrePolicy.normalize("Slice of Life"))
    }

    @Test
    fun matchesGenresBilingually() {
        // Query in Portuguese, metadata in English
        assertTrue(AnimeGenrePolicy.matches("Ação", listOf("Action", "Adventure")))
        assertTrue(AnimeGenrePolicy.matches("Comédia", listOf("Drama", "Comedy")))
        assertTrue(AnimeGenrePolicy.matches("Ficção Científica", listOf("Sci-Fi")))
        assertTrue(AnimeGenrePolicy.matches("Suspense", listOf("Mystery", "Horror")))

        // Query in English, metadata in Portuguese
        assertTrue(AnimeGenrePolicy.matches("Action", listOf("Ação", "Aventura")))
        assertTrue(AnimeGenrePolicy.matches("Fantasy", listOf("Fantasia")))
    }

    @Test
    fun nonMatchingGenresReturnFalse() {
        assertFalse(AnimeGenrePolicy.matches("Romance", listOf("Action", "Sci-Fi")))
        assertFalse(AnimeGenrePolicy.matches("Comédia", listOf("Drama", "Horror")))
        assertFalse(AnimeGenrePolicy.matches("Isekai", listOf("Sports", "School")))
    }

    @Test
    fun fallbackToTitleKeywordsWhenGenresEmpty() {
        assertTrue(AnimeGenrePolicy.matches("Isekai", emptyList(), "Tensei Shitara Slime Datta Ken"))
        assertTrue(AnimeGenrePolicy.matches("Isekai", emptyList(), "Re:Zero Starting Life in Another World"))
        assertTrue(AnimeGenrePolicy.matches("Ação", emptyList(), "Shingeki no Kyojin"))
        assertTrue(AnimeGenrePolicy.matches("Romance", emptyList(), "Kaguya-sama Love is War"))
        assertFalse(AnimeGenrePolicy.matches("Romance", emptyList(), "Random Unknown Show"))
    }

    @Test
    fun trimsWhitespaceAndCaseInsensitivity() {
        assertTrue(AnimeGenrePolicy.matches("  ação  ", listOf("  ACTION  ")))
        assertTrue(AnimeGenrePolicy.matches("ROMANCE", listOf("romance ")))
    }

    @Test
    fun standardGenresContainsCoreCategories() {
        val standard = AnimeGenrePolicy.getStandardGenres()
        assertTrue(standard.contains("Todos"))
        assertTrue(standard.contains("Ação"))
        assertTrue(standard.contains("Comédia"))
        assertTrue(standard.contains("Fantasia"))
        assertTrue(standard.contains("Isekai"))
        assertTrue(standard.contains("Romance"))
        assertTrue(standard.contains("Ficção Científica"))
        assertTrue(standard.contains("Sobrenatural"))
    }

    @Test
    fun unknownGenresNormalizedWithCapitalization() {
        assertEquals("Cyberpunk", AnimeGenrePolicy.normalize("cyberpunk"))
        assertEquals("Post-apocalyptic", AnimeGenrePolicy.normalize("post-apocalyptic"))
    }
}
