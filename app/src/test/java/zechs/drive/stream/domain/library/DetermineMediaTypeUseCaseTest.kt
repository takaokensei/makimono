package zechs.drive.stream.domain.library

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DetermineMediaTypeUseCaseTest {

    private lateinit var useCase: DetermineMediaTypeUseCase

    @Before
    fun setUp() {
        useCase = DetermineMediaTypeUseCase()
    }

    @Test
    fun blankTitleReturnsUnknown() {
        assertEquals(MediaType.UNKNOWN, useCase(""))
        assertEquals(MediaType.UNKNOWN, useCase("   "))
    }

    @Test
    fun detectsEpisodicFromPatterns() {
        assertEquals(MediaType.SERIES, useCase("Attack on Titan S04E12"))
        assertEquals(MediaType.SERIES, useCase("One Piece 1080p - Episode 01"))
        assertEquals(MediaType.SERIES, useCase("Jujutsu Kaisen Season 2"))
        assertEquals(MediaType.SERIES, useCase("Bleach 01x15"))
        assertEquals(MediaType.SERIES, useCase("Frieren [Batch]"))
        assertEquals(MediaType.SERIES, useCase("Violet Evergarden OVA"))
    }

    @Test
    fun detectsMovieFromPatterns() {
        assertEquals(MediaType.MOVIE, useCase("Spirited Away Movie Bluray"))
        assertEquals(MediaType.MOVIE, useCase("Kimi no Na wa (The Movie)"))
        assertEquals(MediaType.MOVIE, useCase("Akira 1988 Bluray Remux"))
    }

    @Test
    fun usesItemCountFallbackWhenTitleAmbiguous() {
        // Multi-item folder defaults to series
        assertEquals(MediaType.SERIES, useCase("Cyberpunk Edgerunners", itemCount = 10))

        // Single-item folder defaults to movie
        assertEquals(MediaType.MOVIE, useCase("Your Name", itemCount = 1))
    }
}
