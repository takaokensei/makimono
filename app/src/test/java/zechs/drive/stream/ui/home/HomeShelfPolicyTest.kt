package zechs.drive.stream.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.local.FollowedFolder
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred

class HomeShelfPolicyTest {

    @Test
    fun myListContainsOnlyFavoriteFoldersPresentInConfiguredLibrary() {
        val starred = listOf(
            folder("favorite", "Outside library"),
            DriveFile(
                id = "favorite-shortcut",
                name = "Series shortcut",
                size = null,
                mimeType = "application/vnd.google-apps.shortcut",
                iconLink = null,
                shortcutDetails = ShortcutDetails(
                    targetId = "series-a",
                    targetMimeType = "application/vnd.google-apps.folder"
                ),
                starred = Starred.STARRED
            ),
            video("episode", "Series A - 01.mkv")
        )
        val library = listOf(
            folder("series-a", "Series A"),
            folder("series-b", "Series B"),
            folder("library-root", "oneblacki")
        )

        val result = filterStarredLibraryFolders(starred, library)

        assertEquals(listOf("series-a"), result.map { it.id })
    }

    @Test
    fun recentlyAddedShelfShowsTimestampedSeriesNewestFirstAndOmitsFilesWithoutTime() {
        val files = listOf(
            folder("older", "Older").copy(modifiedTime = "2026-09-20T10:00:00.000Z"),
            folder("newer", "Newer").copy(modifiedTime = "2026-10-01T10:00:00.000Z"),
            folder("unknown", "Unknown"),
            video("episode", "Episode.mkv").copy(modifiedTime = "2026-10-02T10:00:00.000Z")
        )

        val result = recentlyModifiedLibraryFolders(files)

        assertEquals(listOf("newer", "older"), result.map { it.id })
    }

    @Test
    fun recentlyAddedShelfHonorsNonNegativeLimit() {
        val files = listOf(
            folder("one", "One").copy(modifiedTime = "2026-10-02T10:00:00.000Z"),
            folder("two", "Two").copy(modifiedTime = "2026-10-01T10:00:00.000Z")
        )

        assertEquals(listOf("one"), recentlyModifiedLibraryFolders(files, limit = 1).map { it.id })
        assertEquals(emptyList<String>(), recentlyModifiedLibraryFolders(files, limit = -1).map { it.id })
    }

    @Test
    fun followedShelfContainsOnlyFoldersInTheConfiguredLibraryInFollowedOrder() {
        val library = listOf(
            folder("series-a", "Series A"),
            folder("series-b", "Series B"),
            video("episode", "Episode.mkv")
        )
        val follows = listOf(
            FollowedFolder("profile", "series-a", "Series A", followedAt = 10),
            FollowedFolder("profile", "outside", "Outside", followedAt = 30),
            FollowedFolder("profile", "series-b", "Series B", followedAt = 20),
            FollowedFolder("profile", "episode", "Episode", followedAt = 40)
        )

        val result = filterFollowedLibraryFolders(follows, library)

        assertEquals(listOf("series-b", "series-a"), result.map { it.id })
    }

    private fun folder(id: String, name: String) = DriveFile(
        id = id,
        name = name,
        size = null,
        mimeType = "application/vnd.google-apps.folder",
        iconLink = null,
        shortcutDetails = ShortcutDetails(),
        starred = Starred.STARRED
    )

    private fun video(id: String, name: String) = DriveFile(
        id = id,
        name = name,
        size = null,
        mimeType = "video/x-matroska",
        iconLink = null,
        shortcutDetails = ShortcutDetails(),
        starred = Starred.STARRED
    )
}
