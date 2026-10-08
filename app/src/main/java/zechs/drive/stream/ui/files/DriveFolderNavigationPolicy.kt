package zechs.drive.stream.ui.files

/** Guards the Files back stack against Drive shortcuts that point to an ancestor folder. */
object DriveFolderNavigationPolicy {
    sealed interface Decision {
        data class Navigate(val folderPath: List<String>) : Decision
        data object Cycle : Decision
    }

    fun decide(
        folderPath: List<String>?,
        currentQuery: String?,
        targetFolderId: String
    ): Decision {
        val ancestors = folderPath.orEmpty().filter(String::isNotBlank).ifEmpty {
            listOfNotNull(parentFolderId(currentQuery))
        }
        return if (targetFolderId in ancestors) {
            Decision.Cycle
        } else {
            Decision.Navigate(ancestors + targetFolderId)
        }
    }

    /** Extracts the parent ID from the Drive query shape used by the Files routes. */
    fun parentFolderId(query: String?): String? {
        val match = PARENT_QUERY.find(query.orEmpty()) ?: return null
        return match.groupValues[1]
            .replace("\\'", "'")
            .replace("\\\\", "\\")
            .takeIf(String::isNotBlank)
    }

    private val PARENT_QUERY = Regex("'((?:\\\\.|[^'])+)'\\s+in\\s+parents", RegexOption.IGNORE_CASE)
}
