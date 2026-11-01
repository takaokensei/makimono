package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.domain.library.EpisodeDeduplicationPolicy

class EpisodeDeduplicationPolicyTest {
    @Test
    fun identicalDriveChecksumsCollapseEvenWhenNamesDiffer() {
        val entries = listOf(
            candidate("a", "Episode 01 1080p.mkv", checksum = "abc"),
            candidate("b", "renamed copy.mp4", checksum = "ABC")
        )

        assertEquals(listOf("a"), EpisodeDeduplicationPolicy.removeDuplicates(entries).map { it.id })
    }

    @Test
    fun matchingTitleSizeAndDurationCollapseAsFallback() {
        val entries = listOf(
            candidate("a", "Episode 01 [1080p].mkv"),
            candidate("b", "Episode 01 [WEB-DL].mp4")
        )

        assertEquals(listOf("a"), EpisodeDeduplicationPolicy.removeDuplicates(entries).map { it.id })
    }

    @Test
    fun differentEncodesAndUnknownMetadataRemainAvailable() {
        val entries = listOf(
            candidate("a", "Episode 01 [1080p].mkv"),
            candidate("b", "Episode 01 [720p].mkv", size = 90_000_000L),
            candidate("c", "Episode 01 copy.mkv", size = null, duration = null)
        )

        assertEquals(listOf("a", "b", "c"), EpisodeDeduplicationPolicy.removeDuplicates(entries).map { it.id })
    }

    private fun candidate(
        id: String,
        title: String,
        size: Long? = 100_000_000L,
        duration: Long? = 1_440_000L,
        checksum: String? = null
    ) = EpisodeDeduplicationPolicy.Candidate(id, title, size, duration, checksum)
    @Test fun genericEpisodeNamesInDifferentSeasonFoldersRemainDistinct() {
        val first = zechs.drive.stream.domain.library.EpisodeDeduplicationPolicy.Candidate(
            "a", "01.mkv", 1000L, 120000L, null, "season-1")
        val second = first.copy(id = "b", groupKey = "season-2")
        org.junit.Assert.assertEquals(2,
            zechs.drive.stream.domain.library.EpisodeDeduplicationPolicy.removeDuplicates(listOf(first, second)).size)
    }
    @Test fun equalChecksumsStillCollapseCopiesAcrossFolders() {
        val first = zechs.drive.stream.domain.library.EpisodeDeduplicationPolicy.Candidate(
            "a", "01.mkv", 1000L, 120000L, "same-checksum", "season-1")
        val second = first.copy(id = "b", groupKey = "season-2")
        org.junit.Assert.assertEquals(1,
            zechs.drive.stream.domain.library.EpisodeDeduplicationPolicy.removeDuplicates(listOf(first, second)).size)
    }

    @Test fun theSameDriveIdCannotCreateTwoRowsEvenIfItsChecksumChanges() {
        val first = EpisodeDeduplicationPolicy.Candidate("same-file", "01.mkv", 1000L, 120000L, "before")
        val second = first.copy(checksum = "after")
        org.junit.Assert.assertEquals(1, EpisodeDeduplicationPolicy.removeDuplicates(listOf(first, second)).size)
    }

}
