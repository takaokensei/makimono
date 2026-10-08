package zechs.drive.stream.ui.settings

/** Builds a bounded Drive query for one level of the root-folder picker. */
object LibraryRootFolderQuery {
    private const val DRIVE_ROOT_ID = "root"
    private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

    fun forParent(parentId: String?): String {
        val parent = parentId?.takeIf(String::isNotBlank) ?: DRIVE_ROOT_ID
        val escapedParent = parent.replace("\\", "\\\\").replace("'", "\\'")
        return "'$escapedParent' in parents and trashed=false and mimeType='$FOLDER_MIME_TYPE'"
    }
}
