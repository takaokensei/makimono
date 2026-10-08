package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesMediaMetadataPolicyTest {
    @Test
    fun completeUniformFileNameHintsAreIdentifiedAsInferred() {
        val files = listOf("Show - 01 1080p Dual Audio Multi Subs.mkv", "Show - 02 1080p Dual Audio Multi Subs.mkv")

        assertEquals("1080p · nome", SeriesMediaMetadataPolicy.quality(files, listingComplete = true))
        assertEquals("Dual · nome", SeriesMediaMetadataPolicy.audio(files, listingComplete = true))
        assertEquals("Legendas · nome", SeriesMediaMetadataPolicy.subtitles(files, listingComplete = true))
    }

    @Test
    fun incompleteListingNeverClaimsObservedUniformityForTheWholeSeries() {
        val files = listOf("Show - 01 1080p Dual Audio Multi Subs.mkv", "Show - 02 1080p Dual Audio Multi Subs.mkv")

        assertEquals("Parcial · nome", SeriesMediaMetadataPolicy.quality(files, listingComplete = false))
        assertEquals("Parcial · nome", SeriesMediaMetadataPolicy.audio(files, listingComplete = false))
        assertEquals("Parcial · nome", SeriesMediaMetadataPolicy.subtitles(files, listingComplete = false))
    }

    @Test
    fun observedDifferencesRemainVisibleEvenIfTheListingIsIncomplete() {
        assertEquals(
            "Variada · nome",
            SeriesMediaMetadataPolicy.quality(listOf("01 1080p.mkv", "02 720p.mkv"), listingComplete = false)
        )
        assertEquals(
            "Variado · nome",
            SeriesMediaMetadataPolicy.audio(listOf("01 Dual Audio.mkv", "02 PT-BR.mkv"), listingComplete = false)
        )
    }

    @Test
    fun missingHintsStayUnknownWhilePartiallyTaggedFilesStayPartial() {
        val noHints = listOf("01.mkv", "02.mkv")
        assertNull(SeriesMediaMetadataPolicy.quality(noHints, listingComplete = true))
        assertNull(SeriesMediaMetadataPolicy.audio(noHints, listingComplete = true))
        assertNull(SeriesMediaMetadataPolicy.subtitles(noHints, listingComplete = true))

        assertEquals(
            "Parcial · nome",
            SeriesMediaMetadataPolicy.quality(listOf("01 1080p.mkv", "02.mkv"), listingComplete = true)
        )
        assertEquals(
            "Parcial · nome",
            SeriesMediaMetadataPolicy.audio(listOf("01 Dual Audio.mkv", "02.mkv"), listingComplete = true)
        )
        assertEquals(
            "Parcial · nome",
            SeriesMediaMetadataPolicy.subtitles(listOf("01 Multi Subs.mkv", "02.mkv"), listingComplete = true)
        )
    }

    @Test
    fun dualAudioRequiresAnExplicitAudioMarkerAndAcceptsPortugueseSpelling() {
        assertNull(
            SeriesMediaMetadataPolicy.audioLabelForFile(
                "Dual! Parallel Trouble Adventure - 01.mkv"
            )
        )
        assertEquals("Dual · nome", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 01 Dual Audio.mkv"))
        assertEquals("Dual · nome", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 02 Dual-Audio.mkv"))
        assertEquals("Dual · nome", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 03 Dual Áudio.mkv"))
    }
}
