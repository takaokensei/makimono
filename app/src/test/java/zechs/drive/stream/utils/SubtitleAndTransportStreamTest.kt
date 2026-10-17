package zechs.drive.stream.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.ui.player.MediaInfoDisplay
import java.nio.charset.Charset

class SubtitleAndTransportStreamTest {

    private fun makeDriveFile(
        name: String,
        mimeType: String = "application/octet-stream",
        targetMimeType: String? = null
    ) = DriveFile(
        id = "id_1",
        name = name,
        size = 500_000_000L,
        mimeType = mimeType,
        iconLink = null,
        shortcutDetails = ShortcutDetails(targetId = null, targetMimeType = targetMimeType),
        starred = Starred.UNSTARRED
    )

    @Test
    fun `DriveFile recognizes ts m2ts mts and video mp2t even when Drive misclassifies as linguist`() {
        val tsLinguist = makeDriveFile(
            name = "[SubsPlease] Sousou no Frieren - 01 (1080p).ts",
            mimeType = "text/vnd.trolltech.linguist"
        )
        assertTrue(tsLinguist.isTransportStream)
        assertTrue(tsLinguist.isVideoFile)
        assertTrue(tsLinguist.isShortcutVideo)

        val m2tsOctet = makeDriveFile(
            name = "00001.m2ts",
            mimeType = "application/octet-stream"
        )
        assertTrue(m2tsOctet.isTransportStream)
        assertTrue(m2tsOctet.isVideoFile)

        val mtsFile = makeDriveFile(
            name = "Episode_02.mts",
            mimeType = "application/octet-stream"
        )
        assertTrue(mtsFile.isTransportStream)
        assertTrue(mtsFile.isVideoFile)

        val mp2tMime = makeDriveFile(
            name = "stream_without_extension",
            mimeType = "video/mp2t"
        )
        assertTrue(mp2tMime.isTransportStream)
        assertTrue(mp2tMime.isVideoFile)
    }

    @Test
    fun `EpisodeParser cleanly extracts show title and episode from ts and m2ts filenames`() {
        val parsedTs = EpisodeParser.parse("[SubsPlease] Solo Leveling - 07 (1080p) [ABCD1234].ts")
        assertEquals("Solo Leveling", parsedTs.showTitle)
        assertEquals(7.0, parsedTs.episode ?: -1.0, 0.001)

        val parsedM2ts = EpisodeParser.parse("Frieren S01E12 1080p BluRay.m2ts")
        assertEquals("Frieren", parsedM2ts.showTitle)
        assertEquals(1, parsedM2ts.season)
        assertEquals(12.0, parsedM2ts.episode ?: -1.0, 0.001)
    }

    @Test
    fun `MediaInfoDisplay formats MPEG-2 TS and audio codecs accurately`() {
        assertEquals("MPEG-2 TS", MediaInfoDisplay.videoCodec("video/mp2t"))
        assertEquals("MPEG-2", MediaInfoDisplay.videoCodec("video/mpeg2"))
        assertEquals("MP2 • Estéreo (2.0) • 48.0 kHz", MediaInfoDisplay.audioSummary("audio/mpeg-l2", 2, 48000))
        assertEquals("DTS • Surround (5.1) • 48.0 kHz", MediaInfoDisplay.audioSummary("audio/vnd.dts", 6, 48000))
    }

    @Test
    fun `SubtitleLanguageVerifier detects genuine PT-BR with high certainty and extracts dialogue preview`() {
        val srtBuilder = StringBuilder()
        for (i in 1..80) {
            srtBuilder.append(i).append("\n")
            srtBuilder.append("00:01:10,000 --> 00:01:13,500\n")
            srtBuilder.append("Você não está entendendo o que está acontecendo aqui!\n")
            srtBuilder.append("Precisamos ir pra lá agora mesmo, antes que seja tarde demais.\n\n")
        }

        val result = SubtitleLanguageVerifier.verifySubtitleText(
            rawText = srtBuilder.toString(),
            targetLanguage = SubtitleLanguage.PT_BR,
            declaredLangCode = "pob"
        )

        assertEquals(SubtitleLanguage.PT_BR, result.detectedLanguage)
        assertTrue("Expected high certainty match", result.isHighCertaintyMatch)
        assertTrue("Expected official grade for >=60 cues", result.isOfficialGrade)
        assertTrue("Expected score >= 95, got ${result.certaintyScore}", result.certaintyScore >= 95)
        assertEquals(80, result.cueCount)
        assertNotNull(result.previewSample)
        assertTrue(result.previewSample!!.contains("Você não está entendendo"))
    }

    @Test
    fun `SubtitleLanguageVerifier rejects Spanish subtitle mislabeled as Portuguese`() {
        val spanishSrt = """
            1
            00:00:10,000 --> 00:00:13,000
            ¿Pero qué estás haciendo aquí? ¡Usted no puede hacer eso!

            2
            00:00:14,000 --> 00:00:17,000
            Nosotros estamos muy lejos hacia el norte, todavía no sabemos dónde está él.

            3
            00:00:18,000 --> 00:00:21,000
            Muchas gracias por todo, ahora tenemos que irnos después de hablar con ustedes.
        """.trimIndent()

        val resultForPtBr = SubtitleLanguageVerifier.verifySubtitleText(
            rawText = spanishSrt,
            targetLanguage = SubtitleLanguage.PT_BR,
            declaredLangCode = "pob"
        )

        assertEquals(SubtitleLanguage.ES, resultForPtBr.detectedLanguage)
        assertFalse("Spanish text must NOT be accepted as high-certainty PT-BR", resultForPtBr.isHighCertaintyMatch)
        assertTrue("Expected low certainty score for mismatched language", resultForPtBr.certaintyScore < 50)
    }

    @Test
    fun `SubtitleLanguageVerifier automatically normalizes Windows-1252 Latin1 accents to clean UTF-8`() {
        val originalPt = """
            1
            00:00:05,000 --> 00:00:08,000
            Não acredito que você fez essa canção com tanto coração!
        """.trimIndent()

        val win1252Bytes = originalPt.toByteArray(Charset.forName("windows-1252"))
        val (normalizedText, encodingLabel) = SubtitleLanguageVerifier.decodeAndNormalizeToUtf8(win1252Bytes)

        assertEquals("Win-1252 → UTF-8", encodingLabel)
        assertTrue(normalizedText.contains("Não acredito que você fez essa canção com tanto coração!"))
        assertFalse(normalizedText.contains('\uFFFD'))
    }
}
