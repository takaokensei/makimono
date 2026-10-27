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
            normalized == "incomplete"
    }
}
