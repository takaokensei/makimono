package zechs.drive.stream.domain.library

/**
 * Caso de uso para determinar se um título, arquivo ou pasta representa um Filme ou uma Série.
 * Lógica pura sem dependências de framework Android.
 */
class DetermineMediaTypeUseCase {

    private val episodicPatterns = listOf(
        Regex("""(?i)\b(s\d{1,2}[e\s._-]*\d{1,4}|s\d{1,2}|season\s*\d*|temporada\s*\d*)\b"""),
        Regex("""(?i)\b(e\d{1,4}|ep\d{1,4}|episode\s*\d+|epis[oó]dio\s*\d+)\b"""),
        Regex("""(?i)\b(\d{1,2}x\d{1,4})\b"""),
        Regex("""(?i)\b(batch|ova|oad|especiais|specials)\b""")
    )

    private val moviePatterns = listOf(
        Regex("""(?i)\b(filme|movie|gekijouban|the\s+movie)\b"""),
        Regex("""(?i)\b(\d{4})\b.*(bluray|bdrip|remux|web-dl)""")
    )

    operator fun invoke(title: String, itemCount: Int = 1): MediaType {
        if (title.isBlank()) return MediaType.UNKNOWN

        // Se há múltiplos itens e não é expressamente um filme com extras, tende a ser série
        for (pattern in episodicPatterns) {
            if (pattern.containsMatchIn(title)) {
                return MediaType.SERIES
            }
        }

        for (pattern in moviePatterns) {
            if (pattern.containsMatchIn(title)) {
                return MediaType.MOVIE
            }
        }

        return if (itemCount > 1) {
            MediaType.SERIES
        } else {
            MediaType.MOVIE
        }
    }
}
