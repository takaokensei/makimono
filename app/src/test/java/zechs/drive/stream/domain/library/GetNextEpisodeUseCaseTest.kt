package zechs.drive.stream.domain.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GetNextEpisodeUseCaseTest {

    private lateinit var useCase: GetNextEpisodeUseCase

    @Before
    fun setUp() {
        useCase = GetNextEpisodeUseCase()
    }

    @Test
    fun returnsNextEpisodeWhenAvailable() {
        val episodes = listOf(
            DomainEpisode("ep_1", "Episódio 1", 1, 1),
            DomainEpisode("ep_2", "Episódio 2", 1, 2),
            DomainEpisode("ep_3", "Episódio 3", 1, 3)
        )

        val next = useCase("ep_1", episodes)
        assertEquals("ep_2", next?.id)
        assertEquals(2, next?.episodeNumber)
    }

    @Test
    fun returnsNullWhenCurrentIsLastEpisode() {
        val episodes = listOf(
            DomainEpisode("ep_1", "Episódio 1", 1, 1),
            DomainEpisode("ep_2", "Episódio 2", 1, 2)
        )

        val next = useCase("ep_2", episodes)
        assertNull(next)
    }

    @Test
    fun returnsNullWhenCurrentEpisodeNotFound() {
        val episodes = listOf(
            DomainEpisode("ep_1", "Episódio 1", 1, 1),
            DomainEpisode("ep_2", "Episódio 2", 1, 2)
        )

        val next = useCase("non_existing_ep", episodes)
        assertNull(next)
    }

    @Test
    fun returnsNullForEmptyEpisodeList() {
        val next = useCase("ep_1", emptyList())
        assertNull(next)
    }
}
