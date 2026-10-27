package zechs.drive.stream.data.model

object CatalogFolderFilters {
    fun isExcludedCatalogFolderName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val normalized = name.trim().lowercase()
        return normalized == "downloads diretos" ||
            normalized == "downloads_diretos" ||
            normalized == "direct downloads" ||
            normalized == "torrents" ||
            normalized == "temp" ||
            normalized == "incomplete" ||
            normalized == "_makimono_ambient" ||
            normalized == "makimono_ambient" ||
            normalized == "makimono ambient" ||
            normalized == "ambient" ||
            normalized == "ambient_music" ||
            normalized == "ambient music"
    }
}
