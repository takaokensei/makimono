package zechs.drive.stream.playback.session

import zechs.drive.stream.data.model.PlaylistItem
import kotlin.math.ceil

/**
 * Política pura e testável para transição de próximo episódio, contagem regressiva pós-play (P-12)
 * e verificação de continuidade sem interação ("Ainda está assistindo?" - P-13, T-304).
 */
object NextEpisodePolicy {

    const val DEFAULT_COUNTDOWN_SECONDS = 10
    const val COUNTDOWN_WINDOW_MS = 10_000L
    const val ADVANCE_THRESHOLD_MS = 1_000L
    const val MAX_AUTOPLAY_WITHOUT_INTERACTION = 3

    /**
     * Calcula os segundos restantes arredondados para cima a partir do tempo restante em ms.
     */
    fun calculateRemainingSeconds(remainingMs: Long): Int {
        if (remainingMs <= 0L) return 0
        return ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)
    }

    /**
     * Calcula o percentual (0..100) da barra de progresso da contagem regressiva.
     */
    fun calculateCountdownProgress(
        remainingSeconds: Int,
        totalCountdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS
    ): Int {
        if (totalCountdownSeconds <= 0) return 0
        return ((remainingSeconds.toFloat() / totalCountdownSeconds.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 100)
    }

    /**
     * Determina se o cartão de próximo episódio deve avançar imediatamente para o próximo item.
     */
    fun shouldAdvanceImmediately(remainingMs: Long, remainingSeconds: Int): Boolean {
        return remainingSeconds <= 0 || remainingMs <= ADVANCE_THRESHOLD_MS
    }

    /**
     * Determina se a pergunta "Ainda está assistindo?" (P-13) deve pausar a reprodução automática
     * após [consecutiveAutoplayed] episódios seguidos sem interação do usuário.
     */
    fun shouldPromptStillWatching(
        consecutiveAutoplayed: Int,
        threshold: Int = MAX_AUTOPLAY_WITHOUT_INTERACTION
    ): Boolean {
        return threshold > 0 && consecutiveAutoplayed >= threshold
    }

    /**
     * Formata o rótulo de episódio exibido no cartão pós-play.
     */
    fun formatEpisodeBadge(episodeNumber: Int?): String {
        return if (episodeNumber != null && episodeNumber > 0) {
            "EPISÓDIO $episodeNumber"
        } else {
            "PRÓXIMO EPISÓDIO"
        }
    }

    /**
     * Formata o cabeçalho de contagem regressiva do cartão pós-play.
     */
    fun formatCountdownLabel(seconds: Int): String {
        return "A SEGUIR • ${seconds.coerceAtLeast(0)}s"
    }

    /**
     * Determina se os botões de navegação de episódio e gaveta devem estar visíveis/habilitados.
     */
    data class NavigationControlsState(
        val showEpisodesDrawerButton: Boolean,
        val canSkipPrevious: Boolean,
        val canSkipNext: Boolean,
        val prevButtonAlpha: Float,
        val nextButtonAlpha: Float
    )

    fun resolveNavigationControls(
        isMovie: Boolean,
        playlistSize: Int,
        prevEpisode: PlaylistItem?,
        nextEpisode: PlaylistItem?
    ): NavigationControlsState {
        val hasPrev = prevEpisode != null
        val hasNext = nextEpisode != null
        return NavigationControlsState(
            showEpisodesDrawerButton = !isMovie && playlistSize > 1,
            canSkipPrevious = hasPrev,
            canSkipNext = hasNext,
            prevButtonAlpha = if (hasPrev) 1.0f else 0.35f,
            nextButtonAlpha = if (hasNext) 1.0f else 0.35f
        )
    }
}
