package zechs.drive.stream.ui.player

/** Keeps a playback history owner stable when a session is handed between player Activities. */
object PlaybackProfileSessionPolicy {
    fun resolve(requestedProfileId: String?, activeProfileId: String): String =
        requestedProfileId?.takeIf(String::isNotBlank) ?: activeProfileId

    fun siblingFolderIdForHandoff(folderId: String?): String? = folderId?.takeIf(String::isNotBlank)

    fun explicitPositionForHandoff(positionMs: Long): Long = positionMs.coerceAtLeast(0L)
}
