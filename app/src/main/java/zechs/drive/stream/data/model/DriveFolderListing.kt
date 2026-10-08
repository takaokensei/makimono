package zechs.drive.stream.data.model

/** Best-effort contents for a player folder, with an explicit completeness signal. */
data class DriveFolderListing<T>(
    val items: List<T>,
    val isComplete: Boolean,
    val message: String? = null,
    val nextPageToken: String? = null,
    val sourceFolderId: String? = null
)
