package zechs.drive.stream.domain.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogPolicyTest {

    @Test
    fun `classifyEpisodeState distinguishes Unwatched, InProgress, and Watched`() {
        assertEquals(
            EpisodeWatchState.Unwatched,
            LibraryCatalogPolicy.classifyEpisodeState(0L, 1_440_000L)
        )
        assertEquals(
            EpisodeWatchState.InProgress(50, 720_000L, 1_440_000L),
            LibraryCatalogPolicy.classifyEpisodeState(720_000L, 1_440_000L)
        )
        assertEquals(
            EpisodeWatchState.Watched,
            LibraryCatalogPolicy.classifyEpisodeState(1_380_000L, 1_440_000L)
        )
        // Near end on >= 5 min video (<= 15s remaining)
        assertEquals(
            EpisodeWatchState.Watched,
            LibraryCatalogPolicy.classifyEpisodeState(590_000L, 600_000L)
        )
    }

    @Test
    fun `buildMarkSeasonWatchedDurations uses known duration or fallback duration`() {
        val result = LibraryCatalogPolicy.buildMarkSeasonWatchedDurations(
            episodeIds = listOf("ep1", "ep2"),
            knownTotalDurationsMs = mapOf("ep1" to 1_500_000L, "ep2" to 0L),
            fallbackDurationMs = 1_440_000L
        )
        assertEquals(1_500_000L, result["ep1"])
        assertEquals(1_440_000L, result["ep2"])
    }

    @Test
    fun `detectNewEpisodes emits badge only when new file appears after initial listing`() {
        val initial = LibraryCatalogPolicy.detectNewEpisodes(
            previousFileIds = emptySet(),
            currentFileIds = listOf("ep1", "ep2")
        )
        assertFalse(initial.hasNewEpisodes)
        assertNull(initial.badgeLabel)

        val unchanged = LibraryCatalogPolicy.detectNewEpisodes(
            previousFileIds = setOf("ep1", "ep2"),
            currentFileIds = listOf("ep1", "ep2")
        )
        assertFalse(unchanged.hasNewEpisodes)
        assertNull(unchanged.badgeLabel)

        val updated = LibraryCatalogPolicy.detectNewEpisodes(
            previousFileIds = setOf("ep1", "ep2"),
            currentFileIds = listOf("ep1", "ep2", "ep3")
        )
        assertTrue(updated.hasNewEpisodes)
        assertEquals(listOf("ep3"), updated.newFileIds)
        assertEquals(LibraryCatalogPolicy.NEW_EPISODE_BADGE, updated.badgeLabel)
    }

    @Test
    fun `mergeDriveAndLocal deduplicates by normalized title and preserves origin badge`() {
        val driveItems = listOf(
            LibraryMediaItem(id = "drive-1", title = "Sousou_no_Frieren_01.mkv"),
            LibraryMediaItem(id = "drive-2", title = "Dungeon Meshi 01.mkv")
        )
        val localItems = listOf(
            LibraryMediaItem(id = "local-1", title = "💾 Sousou no Frieren 01.mp4"),
            LibraryMediaItem(id = "local-3", title = "💾 Bleach 01.mkv")
        )

        val merged = LibraryCatalogPolicy.mergeDriveAndLocal(
            driveItems = driveItems,
            localItems = localItems,
            deduplicateByName = true,
            preferLocalOnDuplicate = true
        )

        assertEquals(3, merged.size)
        val byId = merged.associate { it.first.id to it.second }
        assertEquals(LibraryOrigin.LOCAL, byId["local-1"])
        assertEquals(LibraryOrigin.LOCAL, byId["local-3"])
        assertEquals(LibraryOrigin.DRIVE, byId["drive-2"])
        assertFalse(byId.containsKey("drive-1"))
    }
}
