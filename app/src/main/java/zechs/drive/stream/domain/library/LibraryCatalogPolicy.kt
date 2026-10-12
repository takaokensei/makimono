package zechs.drive.stream.domain.library

import java.util.Locale

/**
 * Estado explícito de reprodução por episódio (Fase 5: T-501).
 */
sealed interface EpisodeWatchState {
    data object Unwatched : EpisodeWatchState
    data class InProgress(
        val progressPercent: Int,
        val watchedDurationMs: Long,
        val totalDurationMs: Long
    ) : EpisodeWatchState
    data object Watched : EpisodeWatchState
}

/**
 * Origem explícita de um item de mídia na biblioteca unificada (Fase 5: T-506).
 */
enum class LibraryOrigin(val badgeLabel: String) {
    DRIVE("DRIVE"),
    LOCAL("LOCAL")
}

/**
 * Resultado da comparação entre duas listagens de uma série seguida (Fase 5: T-502).
 */
data class NewEpisodeDetection(
    val hasNewEpisodes: Boolean,
    val newFileIds: List<String>,
    val badgeLabel: String?
)

/**
 * Políticas puras de domínio para a Biblioteca (Fase 5: T-501, T-502, T-506).
 * Livre de dependências de `android.*` ou `androidx.*`.
 */
object LibraryCatalogPolicy {

    const val WATCHED_THRESHOLD_PERCENT = 95
    const val MIN_IN_PROGRESS_PERCENT = 1
    const val DEFAULT_EPISODE_DURATION_MS = 24 * 60_000L
    const val NEW_EPISODE_BADGE = "NOVO EPISÓDIO"

    /**
     * Classifica o estado explícito de um episódio a partir dos tempos assistido e total (T-501).
     */
    fun classifyEpisodeState(
        watchedDurationMs: Long,
        totalDurationMs: Long
    ): EpisodeWatchState {
        if (watchedDurationMs <= 0L || totalDurationMs <= 0L) {
            return EpisodeWatchState.Unwatched
        }
        val rawPercent = ((watchedDurationMs.toDouble() / totalDurationMs.toDouble()) * 100.0).toInt()
            .coerceIn(0, 100)
        val remainingMs = totalDurationMs - watchedDurationMs
        val isNearEndOnLongVideo = totalDurationMs >= 300_000L && remainingMs in 0..15_000L

        return when {
            rawPercent >= WATCHED_THRESHOLD_PERCENT || isNearEndOnLongVideo -> EpisodeWatchState.Watched
            rawPercent >= MIN_IN_PROGRESS_PERCENT -> EpisodeWatchState.InProgress(
                progressPercent = rawPercent,
                watchedDurationMs = watchedDurationMs,
                totalDurationMs = totalDurationMs
            )
            else -> EpisodeWatchState.Unwatched
        }
    }

    /**
     * Gera os registros de duração resolvida para marcar todos os episódios de uma temporada
     * como assistidos (T-501).
     */
    fun buildMarkSeasonWatchedDurations(
        episodeIds: List<String>,
        knownTotalDurationsMs: Map<String, Long>,
        fallbackDurationMs: Long = DEFAULT_EPISODE_DURATION_MS
    ): Map<String, Long> {
        return episodeIds.associateWith { id ->
            val known = knownTotalDurationsMs[id]
            if (known != null && known > 0L) known else fallbackDurationMs
        }
    }

    /**
     * Detecta se surgiram novos arquivos de episódio entre duas listagens de uma série seguida (T-502).
     * Só emite o selo [NEW_EPISODE_BADGE] quando já havia uma listagem anterior conhecida
     * (`previousFileIds.isNotEmpty()`) e surgiram IDs inéditos.
     */
    fun detectNewEpisodes(
        previousFileIds: Set<String>,
        currentFileIds: List<String>
    ): NewEpisodeDetection {
        if (previousFileIds.isEmpty() || currentFileIds.isEmpty()) {
            return NewEpisodeDetection(
                hasNewEpisodes = false,
                newFileIds = emptyList(),
                badgeLabel = null
            )
        }
        val added = currentFileIds.filter { it !in previousFileIds }.distinct()
        return NewEpisodeDetection(
            hasNewEpisodes = added.isNotEmpty(),
            newFileIds = added,
            badgeLabel = if (added.isNotEmpty()) NEW_EPISODE_BADGE else null
        )
    }

    /**
     * Mescla itens do Google Drive e de armazenamento Local na mesma biblioteca,
     * preservando a origem visível e deduplicando por título normalizado (T-506).
     */
    fun mergeDriveAndLocal(
        driveItems: List<LibraryMediaItem>,
        localItems: List<LibraryMediaItem>,
        deduplicateByName: Boolean = true,
        preferLocalOnDuplicate: Boolean = true
    ): List<Pair<LibraryMediaItem, LibraryOrigin>> {
        val taggedDrive = driveItems.map { it to LibraryOrigin.DRIVE }
        val taggedLocal = localItems.map { it to LibraryOrigin.LOCAL }

        if (!deduplicateByName) {
            return taggedDrive + taggedLocal
        }

        val ordered = if (preferLocalOnDuplicate) {
            taggedLocal + taggedDrive
        } else {
            taggedDrive + taggedLocal
        }

        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<Pair<LibraryMediaItem, LibraryOrigin>>()
        for (entry in ordered) {
            val key = normalizeTitleForDedup(entry.first.title)
            if (seenKeys.add(key)) {
                result.add(entry)
            }
        }
        return result
    }

    internal fun normalizeTitleForDedup(rawTitle: String): String {
        return rawTitle
            .removePrefix("💾")
            .trim()
            .substringBeforeLast('.')
            .lowercase(Locale.ROOT)
            .replace(Regex("[_\\-.]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
