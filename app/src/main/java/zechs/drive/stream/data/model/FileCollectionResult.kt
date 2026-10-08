package zechs.drive.stream.data.model

/** Result of a bounded multi-page Drive query. Callers must check [isComplete]. */
data class FileCollectionResult(
    val files: List<File>,
    val isComplete: Boolean,
    val nextPageToken: String?,
    val pagesFetched: Int,
    val queryContext: DriveQueryContext,
    val incompleteReason: IncompleteReason? = null
) {
    enum class IncompleteReason {
        PAGE_LIMIT,
        FILE_LIMIT,
        REQUEST_FAILED
    }
}

/** Parameters that must stay paired with [FileCollectionResult.nextPageToken]. */
data class DriveQueryContext(
    val query: String,
    val pageSize: Int,
    val orderBy: String
)
