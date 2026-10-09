package zechs.drive.stream.domain.playback

/**
 * Modelo de domínio que representa o estado de progresso de uma reprodução.
 */
data class PlaybackProgress(
    val watchedDurationMs: Long,
    val totalDurationMs: Long,
    val progressPercentage: Float,
    val remainingMs: Long,
    val isCompleted: Boolean,
    val isUnwatched: Boolean
)

/**
 * Caso de uso para calcular o progresso de reprodução e determinar conclusão.
 * Lógica pura sem dependências do Android.
 */
class CalculatePlaybackProgressUseCase {

    operator fun invoke(
        watchedDurationMs: Long,
        totalDurationMs: Long,
        completionThreshold: Float = DEFAULT_COMPLETION_THRESHOLD,
        unwatchedThreshold: Float = DEFAULT_UNWATCHED_THRESHOLD
    ): PlaybackProgress {
        if (totalDurationMs <= 0L || watchedDurationMs <= 0L) {
            return PlaybackProgress(
                watchedDurationMs = 0L,
                totalDurationMs = maxOf(0L, totalDurationMs),
                progressPercentage = 0f,
                remainingMs = maxOf(0L, totalDurationMs),
                isCompleted = false,
                isUnwatched = true
            )
        }

        val clampedWatched = watchedDurationMs.coerceAtMost(totalDurationMs)
        val fraction = (clampedWatched.toDouble() / totalDurationMs.toDouble()).toFloat()
        val percentage = (fraction * 100f).coerceIn(0f, 100f)
        val remaining = (totalDurationMs - clampedWatched).coerceAtLeast(0L)

        val isCompleted = fraction >= completionThreshold
        val isUnwatched = fraction < unwatchedThreshold

        return PlaybackProgress(
            watchedDurationMs = clampedWatched,
            totalDurationMs = totalDurationMs,
            progressPercentage = percentage,
            remainingMs = remaining,
            isCompleted = isCompleted,
            isUnwatched = isUnwatched
        )
    }

    companion object {
        const val DEFAULT_COMPLETION_THRESHOLD = 0.90f // 90% assistido = concluído
        const val DEFAULT_UNWATCHED_THRESHOLD = 0.05f  // <5% = não iniciado
    }
}
