package zechs.drive.stream.domain.library

/**
 * Representação pura de um episódio na camada de domínio.
 */
data class DomainEpisode(
    val id: String,
    val title: String,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1
)

/**
 * Caso de uso para selecionar o próximo episódio em uma sequência de reprodução.
 * Lógica pura sem dependências do Android.
 */
class GetNextEpisodeUseCase {

    operator fun invoke(
        currentEpisodeId: String,
        episodes: List<DomainEpisode>
    ): DomainEpisode? {
        if (episodes.isEmpty()) return null

        val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex == -1) return null

        val nextIndex = currentIndex + 1
        return if (nextIndex < episodes.size) {
            episodes[nextIndex]
        } else {
            null
        }
    }
}
