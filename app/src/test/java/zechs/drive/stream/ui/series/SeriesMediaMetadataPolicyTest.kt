package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesMediaMetadataPolicyTest {
    @Test
    fun completeUniformFileNameHintsAreIdentifiedAsInferred() {
        val files = listOf("Show - 01 1080p Dual Audio Multi Subs.mkv", "Show - 02 1080p Dual Audio Multi Subs.mkv")

        val qualityFact = SeriesMediaMetadataPolicy.quality(files, listingComplete = true)
        val audioFact = SeriesMediaMetadataPolicy.audio(files, listingComplete = true)
        val subtitleFact = SeriesMediaMetadataPolicy.subtitles(files, listingComplete = true)

        assertEquals(MediaFact("1080p", Coverage.COMPLETE), qualityFact)
        assertEquals("1080p", qualityFact?.formatQuality())

        assertEquals(MediaFact("Dual Áudio", Coverage.COMPLETE), audioFact)
        assertEquals("Dual Áudio", audioFact?.formatAudio())

        assertEquals(MediaFact("Legendas", Coverage.COMPLETE), subtitleFact)
        assertEquals("Legendas", subtitleFact?.formatSubtitles())
    }

    @Test
    fun incompleteListingNeverClaimsObservedUniformityForTheWholeSeries() {
        val files = listOf("Show - 01 1080p Dual Audio Multi Subs.mkv", "Show - 02 1080p Dual Audio Multi Subs.mkv")

        val qualityFact = SeriesMediaMetadataPolicy.quality(files, listingComplete = false)
        val audioFact = SeriesMediaMetadataPolicy.audio(files, listingComplete = false)
        val subtitleFact = SeriesMediaMetadataPolicy.subtitles(files, listingComplete = false)

        assertEquals(MediaFact("1080p", Coverage.PARTIAL), qualityFact)
        assertEquals("1080p (parcial)", qualityFact?.formatQuality())

        assertEquals(MediaFact("Dual Áudio", Coverage.PARTIAL), audioFact)
        assertEquals("Dual Áudio (parcial)", audioFact?.formatAudio())

        assertEquals(MediaFact("Legendas", Coverage.PARTIAL), subtitleFact)
        assertEquals("Legendas (parcial)", subtitleFact?.formatSubtitles())
    }

    @Test
    fun observedDifferencesRemainVisibleEvenIfTheListingIsIncomplete() {
        val qualityFact = SeriesMediaMetadataPolicy.quality(listOf("01 1080p.mkv", "02 720p.mkv"), listingComplete = false)
        val audioFact = SeriesMediaMetadataPolicy.audio(listOf("01 Dual Audio.mkv", "02 PT-BR.mkv"), listingComplete = false)

        assertEquals(MediaFact(null, Coverage.MIXED), qualityFact)
        assertEquals("Variada", qualityFact?.formatQuality())

        assertEquals(MediaFact(null, Coverage.MIXED), audioFact)
        assertEquals("Variado", audioFact?.formatAudio())
    }

    @Test
    fun missingHintsStayUnknownWhilePartiallyTaggedFilesStayPartial() {
        val noHints = listOf("01.mkv", "02.mkv")
        assertNull(SeriesMediaMetadataPolicy.quality(noHints, listingComplete = true))
        assertNull(SeriesMediaMetadataPolicy.audio(noHints, listingComplete = true))
        assertNull(SeriesMediaMetadataPolicy.subtitles(noHints, listingComplete = true))

        val qualityPartial = SeriesMediaMetadataPolicy.quality(listOf("01 1080p.mkv", "02.mkv"), listingComplete = true)
        val audioPartial = SeriesMediaMetadataPolicy.audio(listOf("01 Dual Audio.mkv", "02.mkv"), listingComplete = true)
        val subPartial = SeriesMediaMetadataPolicy.subtitles(listOf("01 Multi Subs.mkv", "02.mkv"), listingComplete = true)

        assertEquals(MediaFact("1080p", Coverage.PARTIAL), qualityPartial)
        assertEquals("1080p (parcial)", qualityPartial?.formatQuality())

        assertEquals(MediaFact("Dual Áudio", Coverage.PARTIAL), audioPartial)
        assertEquals("Dual Áudio (parcial)", audioPartial?.formatAudio())

        assertEquals(MediaFact("Legendas", Coverage.PARTIAL), subPartial)
        assertEquals("Legendas (parcial)", subPartial?.formatSubtitles())
    }

    @Test
    fun dualAudioRequiresAnExplicitAudioMarkerAndAcceptsPortugueseSpelling() {
        assertNull(
            SeriesMediaMetadataPolicy.audioLabelForFile(
                "Dual! Parallel Trouble Adventure - 01.mkv"
            )
        )
        assertEquals("Dual Áudio", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 01 Dual Audio.mkv"))
        assertEquals("Dual Áudio", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 02 Dual-Audio.mkv"))
        assertEquals("Dual Áudio", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 03 Dual Áudio.mkv"))
        assertEquals("PT-BR", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 04 PT-BR.mkv"))
        assertEquals("Dublado", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 05 Dublado.mkv"))
        assertEquals("Dublado", SeriesMediaMetadataPolicy.audioLabelForFile("Show - 06 [Dub].mkv"))
    }

    @Test
    fun `formatHeroSynopsis extracts complete sentences without mid-sentence truncation`() {
        assertNull(SeriesMediaMetadataPolicy.formatHeroSynopsis(null))
        assertNull(SeriesMediaMetadataPolicy.formatHeroSynopsis(""))
        assertNull(SeriesMediaMetadataPolicy.formatHeroSynopsis("   "))

        val fullSynopsis = "Destruction looms at the end of the Thousand-Year Blood War between Soul Reapers and Quincies. " +
            "As the death of the Soul King mirrors the collapse of the Three Worlds, Ichigo fights to save everything. " +
            "A third sentence that should not be included in the hero overview because it exceeds max sentences."

        val formatted = SeriesMediaMetadataPolicy.formatHeroSynopsis(fullSynopsis, maxSentences = 2, maxChars = 220)
        assertEquals(
            "Destruction looms at the end of the Thousand-Year Blood War between Soul Reapers and Quincies. " +
                "As the death of the Soul King mirrors the collapse of the Three Worlds, Ichigo fights to save everything.",
            formatted
        )
    }

    @Test
    fun `formatHeroSynopsis strips HTML tags and MAL source citations`() {
        val raw = "<p>Ichigo is back in action.</p><br>He faces new enemies! [Written by MAL Rewrite] (Source: IMDB)"
        val formatted = SeriesMediaMetadataPolicy.formatHeroSynopsis(raw, maxSentences = 2)
        assertEquals("Ichigo is back in action. He faces new enemies!", formatted)
    }
}

