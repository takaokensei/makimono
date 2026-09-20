package zechs.drive.stream.utils

/**
 * P2-08: regra única de detecção/pontuação de faixas de áudio e legenda,
 * compartilhada pelas engines ExoPlayer e mpv. Antes, as constantes mágicas
 * (10/15/25) e a detecção de PT/JA estavam duplicadas nos dois players e
 * podiam divergir.
 */
object SubtitleSelectionPolicy {

    private val PORTUGUESE_LANGS = setOf("pt", "por", "pt-br", "pt_br", "pob", "portuguese")
    private val JAPANESE_LANGS = setOf("ja", "jpn", "jp", "japanese")

    fun isPortuguese(lang: String?, label: String?, name: String = ""): Boolean {
        if (lang?.lowercase() in PORTUGUESE_LANGS) return true
        val haystack = "${label ?: ""} $name".lowercase()
        return haystack.contains("portugu") || haystack.contains("pt-br") ||
                haystack.contains("pt_br") || haystack.contains("ptbr") ||
                haystack.contains("brazil") || haystack.contains("brasil")
    }

    fun isJapanese(lang: String?, label: String?, name: String = ""): Boolean {
        if (lang?.lowercase() in JAPANESE_LANGS) return true
        val haystack = "${label ?: ""} $name".lowercase()
        return haystack.contains("jap") || haystack.contains("jpn")
    }

    /**
     * Pontuação da melhor legenda em português:
     * 25 = vinda do Drive ([Drive]), 15 = PT-BR explícito, 5 = forced/signs/
     * músicas/songs, 10 = português genérico.
     */
    fun scorePortuguese(label: String): Int {
        val l = label.lowercase()
        return when {
            l.contains("[drive]") -> 25
            l.contains("brasil") || l.contains("brazil") || l.contains("pt-br") ||
                    l.contains("pt_br") || l.contains("ptbr") -> 15
            l.contains("forced") || l.contains("forçada") || l.contains("forçado") ||
                    l.contains("signs") || l.contains("músicas") || l.contains("songs") -> 5
            else -> 10
        }
    }
}
