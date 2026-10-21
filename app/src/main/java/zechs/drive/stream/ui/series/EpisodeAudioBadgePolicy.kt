package zechs.drive.stream.ui.series

enum class AudioBadgeKind {
    DUAL,
    DUB,
    SUB;

    fun defaultLabel(isPortuguese: Boolean = true): String = when (this) {
        DUAL -> "DUAL"
        DUB -> "DUB"
        SUB -> if (isPortuguese) "LEG" else "SUB"
    }
}

data class EpisodeAudioBadge(
    val kind: AudioBadgeKind,
    val label: String,
    val isDubbed: Boolean
)

/**
 * Pure policy class that derives Crunchyroll-style audio/dubbing badges
 * ("DUB", "DUAL", "LEG" / "SUB") from video filenames.
 */
object EpisodeAudioBadgePolicy {

    private val nonVideoExtensionRegex = Regex(
        "(?i)\\.(?:srt|ass|ssa|vtt|sub|idx|nfo|txt|xml|json|jpg|jpeg|png|gif|webp|zip|rar|7z|tar|gz|pdf)$"
    )

    private val videoExtensionRegex = Regex(
        "(?i)\\.(?:mkv|mp4|webm|avi|ts|m4v|mov|flv|wmv|3gp|ogv)$"
    )

    private val dualAudioRegex = Regex(
        "(?iu)(?<![a-z0-9])(?:dual[ ._-]?(?:audio|áudio)?|2[ ._-]?audio|multi[ ._-]?audio)(?![a-z0-9])"
    )

    private val dubbedRegex = Regex(
        "(?iu)(?<![a-z0-9])(?:dublado|dub(?:bed)?|pt[ ._-]?br|brazilian[ ._-]?portuguese|audio[ ._-]?pt|portugu[eê]s)(?![a-z0-9])"
    )

    private val explicitSubRegex = Regex(
        "(?iu)(?<![a-z0-9])(?:legendado|legendas?|multi[ ._-]?subs?|sub(?:bed|titles?)?|softsubs?|hardsubs?)(?![a-z0-9])"
    )

    fun resolve(fileName: String?, isPortuguese: Boolean = true): EpisodeAudioBadge? {
        val name = fileName?.trim() ?: return null
        if (name.isEmpty()) return null
        if (nonVideoExtensionRegex.containsMatchIn(name)) return null

        val kind = when {
            dualAudioRegex.containsMatchIn(name) -> AudioBadgeKind.DUAL
            dubbedRegex.containsMatchIn(name) -> AudioBadgeKind.DUB
            explicitSubRegex.containsMatchIn(name) -> AudioBadgeKind.SUB
            videoExtensionRegex.containsMatchIn(name) -> AudioBadgeKind.SUB
            else -> null
        } ?: return null

        val label = kind.defaultLabel(isPortuguese)
        val isDubbed = kind == AudioBadgeKind.DUAL || kind == AudioBadgeKind.DUB

        return EpisodeAudioBadge(
            kind = kind,
            label = label,
            isDubbed = isDubbed
        )
    }

    fun isDubbed(label: String?): Boolean {
        if (label.isNullOrBlank()) return false
        val clean = label.trim().uppercase()
        return clean == "DUB" || clean == "DUAL" || clean == "DUAL ÁUDIO" || clean == "DUBLADO" || clean == "PT-BR"
    }
}
