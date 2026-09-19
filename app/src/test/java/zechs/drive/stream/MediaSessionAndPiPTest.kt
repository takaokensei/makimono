package zechs.drive.stream

import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.utils.MakimonoPiPHelper

/**
 * FEAT-08: Unit tests for Picture-in-Picture helper and aspect ratio calculation.
 */
class MediaSessionAndPiPTest {

    @Test
    fun `aspect ratio bounds match Android specifications`() {
        assertEquals(0.42f, MakimonoPiPHelper.MIN_ASPECT_RATIO, 0.001f)
        assertEquals(2.38f, MakimonoPiPHelper.MAX_ASPECT_RATIO, 0.001f)
    }

    @Test
    fun `aspect ratio validation accepts standard 16 by 9 video`() {
        assertTrue(MakimonoPiPHelper.isValidAspectRatio(1920, 1080))
        assertTrue(MakimonoPiPHelper.isValidAspectRatio(1280, 720))
    }

    @Test
    fun `aspect ratio validation accepts 4 by 3 video`() {
        assertTrue(MakimonoPiPHelper.isValidAspectRatio(1440, 1080))
        assertTrue(MakimonoPiPHelper.isValidAspectRatio(640, 480))
    }

    @Test
    fun `aspect ratio validation accepts 21 by 9 cinematic video`() {
        assertTrue(MakimonoPiPHelper.isValidAspectRatio(2560, 1080))
    }

    @Test
    fun `extreme aspect ratios are rejected outside Android PiP bounds`() {
        // Ultra-wide (e.g. 32:9 or multi-monitor) > 2.38
        assertFalse(MakimonoPiPHelper.isValidAspectRatio(3840, 1080))

        // Ultra-tall (e.g. 1:3 vertical banner) < 0.42
        assertFalse(MakimonoPiPHelper.isValidAspectRatio(500, 1500))

        // Invalid zero/negative dimensions
        assertFalse(MakimonoPiPHelper.isValidAspectRatio(0, 1080))
        assertFalse(MakimonoPiPHelper.isValidAspectRatio(1920, 0))
        assertFalse(MakimonoPiPHelper.isValidAspectRatio(-100, -100))
    }
}
