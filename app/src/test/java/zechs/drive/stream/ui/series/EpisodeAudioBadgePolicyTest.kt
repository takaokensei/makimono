package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeAudioBadgePolicyTest {

    @Test
    fun resolvesDualAudioBadge() {
        val filenames = listOf(
            "[Erai-raws] Bleach - 01 [Dual Audio] [1080p].mkv",
            "Chainsaw Man - S01E01 Dual Áudio 1080p.mp4",
            "Naruto Shippuden 001 Multi-Audio.mkv",
            "One Piece 1000 2-audio 720p.mkv"
        )
        for (name in filenames) {
            val badge = EpisodeAudioBadgePolicy.resolve(name, isPortuguese = true)
            assertNotNull("Should resolve badge for $name", badge)
            assertEquals("DUAL", badge!!.label)
            assertEquals(AudioBadgeKind.DUAL, badge.kind)
            assertTrue(badge.isDubbed)
        }
    }

    @Test
    fun resolvesDubbedBadge() {
        val filenames = listOf(
            "Dragon Ball Super - 131 Dublado 1080p.mkv",
            "One Piece S01E01 PT-BR 720p.mp4",
            "Demon Slayer S03E01 (Dubbed) 1080p.mkv",
            "My Hero Academia 01 Brazilian Portuguese.mkv"
        )
        for (name in filenames) {
            val badge = EpisodeAudioBadgePolicy.resolve(name, isPortuguese = true)
            assertNotNull("Should resolve badge for $name", badge)
            assertEquals("DUB", badge!!.label)
            assertEquals(AudioBadgeKind.DUB, badge.kind)
            assertTrue(badge.isDubbed)
        }
    }

    @Test
    fun resolvesExplicitSubBadge() {
        val filenames = listOf(
            "Jujutsu Kaisen 01 Legendado.mkv",
            "Attack on Titan S04E01 Multi-Subs.mp4",
            "Vinland Saga S02E01 SoftSub.mkv",
            "Frieren 01 Hardsub.ts"
        )
        for (name in filenames) {
            val badge = EpisodeAudioBadgePolicy.resolve(name, isPortuguese = true)
            assertNotNull("Should resolve badge for $name", badge)
            assertEquals("LEG", badge!!.label)
            assertEquals(AudioBadgeKind.SUB, badge.kind)
            assertFalse(badge.isDubbed)
        }
    }

    @Test
    fun resolvesImplicitSubForStandardVideoFiles() {
        val filenames = listOf(
            "[SubsPlease] Sousou no Frieren - 01 (1080p) [ABCD1234].mkv",
            "Oshi no Ko - 01 (1080p).mp4",
            "Bocchi the Rock - 01.webm",
            "Cowboy Bebop - 01.avi",
            "Evangelion - 01.ts"
        )
        for (name in filenames) {
            val badge = EpisodeAudioBadgePolicy.resolve(name, isPortuguese = true)
            assertNotNull("Should resolve badge for $name", badge)
            assertEquals("LEG", badge!!.label)
            assertEquals(AudioBadgeKind.SUB, badge.kind)
            assertFalse(badge.isDubbed)
        }
    }

    @Test
    fun handlesLanguageLocalizationForSubBadge() {
        val file = "[SubsPlease] Frieren - 01 (1080p).mkv"
        val ptBadge = EpisodeAudioBadgePolicy.resolve(file, isPortuguese = true)
        val enBadge = EpisodeAudioBadgePolicy.resolve(file, isPortuguese = false)

        assertEquals("LEG", ptBadge?.label)
        assertEquals("SUB", enBadge?.label)
    }

    @Test
    fun ignoresWordsContainingSubstringsLikeDubiousOrSubroutine() {
        val file1 = "The Dubious Adventure - 01.mkv"
        val badge1 = EpisodeAudioBadgePolicy.resolve(file1, isPortuguese = true)
        assertEquals("LEG", badge1?.label)
        assertFalse(badge1!!.isDubbed)

        val file2 = "Subroutine Protocol - 01.mp4"
        val badge2 = EpisodeAudioBadgePolicy.resolve(file2, isPortuguese = true)
        assertEquals("LEG", badge2?.label)
        assertFalse(badge2!!.isDubbed)
    }

    @Test
    fun returnsNullForNonVideoFiles() {
        assertNull(EpisodeAudioBadgePolicy.resolve(null))
        assertNull(EpisodeAudioBadgePolicy.resolve(""))
        assertNull(EpisodeAudioBadgePolicy.resolve("   "))
        assertNull(EpisodeAudioBadgePolicy.resolve("subtitle_file.srt"))
        assertNull(EpisodeAudioBadgePolicy.resolve("description.nfo"))
        assertNull(EpisodeAudioBadgePolicy.resolve("folder_without_extension"))
    }

    @Test
    fun isDubbedHelperCorrectlyIdentifiesDubbedLabels() {
        assertTrue(EpisodeAudioBadgePolicy.isDubbed("DUB"))
        assertTrue(EpisodeAudioBadgePolicy.isDubbed("DUAL"))
        assertTrue(EpisodeAudioBadgePolicy.isDubbed("DUBLADO"))
        assertTrue(EpisodeAudioBadgePolicy.isDubbed("Dual Áudio"))
        assertTrue(EpisodeAudioBadgePolicy.isDubbed("PT-BR"))
        assertFalse(EpisodeAudioBadgePolicy.isDubbed("LEG"))
        assertFalse(EpisodeAudioBadgePolicy.isDubbed("SUB"))
        assertFalse(EpisodeAudioBadgePolicy.isDubbed(null))
        assertFalse(EpisodeAudioBadgePolicy.isDubbed(""))
    }
}
