package zechs.drive.stream.playback.session

import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.utils.SubtitleSelectionPolicy
import java.util.Locale

/**
 * Política unificada para seleção automática e preferências de faixas de áudio e legenda
 * por série/perfil, filtragem de legendas externas e sincronia de atrasos (T-306, P-14, P-15).
 */
object TrackPreferencePolicy {

    const val MIN_DELAY_MS = -60_000L
    const val MAX_DELAY_MS = 60_000L
    const val DELAY_STEP_MS = 100L

    data class TrackPreference(
        val preferredAudioLang: String? = null,
        val preferredSubtitleLang: String? = null,
        val subtitlesDisabled: Boolean = false
    )

    data class TrackCandidate(
        val id: String,
        val lang: String? = null,
        val title: String? = null,
        val label: String? = null
    )

    /**
     * Filtra as legendas da pasta que correspondem ao arquivo de vídeo em reprodução.
     */
    fun filterMatchingExternalSubtitles(
        videoTitle: String,
        folderSubtitles: List<SubtitleItem>
    ): List<SubtitleItem> {
        if (videoTitle.isBlank() || folderSubtitles.isEmpty()) return emptyList()
        return folderSubtitles.filter { it.matchesVideo(videoTitle) }
    }

    /**
     * Cria um SubtitleItem virtual para uma legenda salva localmente do repositório online.
     */
    fun createSavedSubtitleItem(
        fileNameWithoutExtension: String,
        langName: String,
        langCode: String
    ): SubtitleItem {
        val virtualId = "saved_$fileNameWithoutExtension"
        return SubtitleItem(
            id = virtualId,
            name = "💾 [Salva] $langName",
            languageLabel = langName,
            languageCode = langCode
        )
    }

    /**
     * Seleciona a faixa de áudio preferida.
     * Prioriza a preferência explícita da série/perfil (P-14); em caso de ausência, aplica o
     * fallback padrão para áudio original japonês.
     */
    fun selectPreferredAudioTrack(
        candidates: List<TrackCandidate>,
        preference: TrackPreference? = null
    ): TrackCandidate? {
        if (candidates.isEmpty()) return null

        val prefLang = preference?.preferredAudioLang?.trim()?.lowercase(Locale.ROOT)
        if (!prefLang.isNullOrEmpty()) {
            val matchedByPref = candidates.firstOrNull { candidate ->
                matchesLanguageToken(candidate, prefLang)
            }
            if (matchedByPref != null) return matchedByPref
        }

        return candidates.firstOrNull { candidate ->
            SubtitleSelectionPolicy.isJapanese(candidate.lang, candidate.title, candidate.label.orEmpty())
        }
    }

    /**
     * Seleciona a faixa de legenda preferida.
     * Respeita desativação explícita, preferência da série/perfil (P-14) e, por fim,
     * pontuação de português brasileiro (`SubtitleSelectionPolicy.scorePortuguese`).
     */
    fun selectPreferredSubtitleTrack(
        candidates: List<TrackCandidate>,
        preference: TrackPreference? = null
    ): TrackCandidate? {
        if (candidates.isEmpty()) return null
        if (preference?.subtitlesDisabled == true) return null

        val prefLang = preference?.preferredSubtitleLang?.trim()?.lowercase(Locale.ROOT)
        if (!prefLang.isNullOrEmpty()) {
            val matchedByPref = candidates.filter { candidate ->
                matchesLanguageToken(candidate, prefLang)
            }
            if (matchedByPref.isNotEmpty()) {
                return matchedByPref.maxByOrNull { candidate ->
                    SubtitleSelectionPolicy.scorePortuguese(
                        "${candidate.title.orEmpty()} ${candidate.label.orEmpty()}"
                    )
                }
            }
        }

        val ptCandidates = candidates.filter { candidate ->
            SubtitleSelectionPolicy.isPortuguese(candidate.lang, candidate.title, candidate.label.orEmpty())
        }
        return ptCandidates.maxByOrNull { candidate ->
            SubtitleSelectionPolicy.scorePortuguese(
                "${candidate.title.orEmpty()} ${candidate.label.orEmpty()}"
            )
        }
    }

    private fun matchesLanguageToken(candidate: TrackCandidate, token: String): Boolean {
        val combined = listOfNotNull(candidate.lang, candidate.title, candidate.label)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return when (token) {
            "pt", "por", "pt-br", "portuguese", "português" ->
                SubtitleSelectionPolicy.isPortuguese(candidate.lang, candidate.title, candidate.label.orEmpty())
            "ja", "jpn", "japanese", "japonês" ->
                SubtitleSelectionPolicy.isJapanese(candidate.lang, candidate.title, candidate.label.orEmpty())
            else -> combined.contains(token)
        }
    }

    /**
     * Limita o atraso de áudio ou legenda ao intervalo seguro [-60s, +60s].
     */
    fun clampDelayMs(
        delayMs: Long,
        minMs: Long = MIN_DELAY_MS,
        maxMs: Long = MAX_DELAY_MS
    ): Long {
        return delayMs.coerceIn(minMs, maxMs)
    }

    /**
     * Formata o atraso em milissegundos para exibição amigável no OSD (ex: "+200 ms", "-150 ms", "0 ms").
     */
    fun formatDelayLabel(delayMs: Long): String {
        val clamped = clampDelayMs(delayMs)
        return when {
            clamped > 0L -> "+$clamped ms"
            else -> "$clamped ms"
        }
    }
}
