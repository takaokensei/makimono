package zechs.drive.stream.ui.files

/**
 * Pure policy generating 2-letter folder artwork placeholder initials (T3.5)
 * when folder poster artwork is unavailable, eliminating blank gray tiles.
 */
object FolderPlaceholderPolicy {

    fun initials(name: String): String {
        var title = name.trim()
        var removedLeadingGroup = false
        while (title.firstOrNull()?.let(OPENING_DELIMITERS::contains) == true) {
            val closing = when (title.first()) {
                '[' -> ']'
                '(' -> ')'
                '{' -> '}'
                '<' -> '>'
                else -> break
            }
            val closingIndex = title.indexOf(closing, startIndex = 1)
            if (closingIndex < 0) break
            removedLeadingGroup = true
            title = title.substring(closingIndex + 1).trimStart()
        }

        if (removedLeadingGroup) {
            val wordInitials = title.split(WHITESPACE)
                .map { word -> word.firstOrNull(Char::isLetterOrDigit) }
                .filterNotNull()
                .take(2)
                .joinToString("") { it.uppercaseChar().toString() }
            if (wordInitials.isNotBlank()) return wordInitials
        }

        val clean = title.filter(Char::isLetterOrDigit)

        if (clean.isBlank()) return "MK"
        return clean.take(2).uppercase(java.util.Locale.ROOT)
    }

    private val OPENING_DELIMITERS = setOf('[', '(', '{', '<')
    private val WHITESPACE = Regex("\\s+")
}
