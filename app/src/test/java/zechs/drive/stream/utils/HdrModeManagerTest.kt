package zechs.drive.stream.utils

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrModeManagerTest {

    @Test
    fun `detectExoPlayerHdr returns SDR when format or colorInfo is null`() {
        assertEquals(HdrModeManager.DynamicRange.SDR, HdrModeManager.detectExoPlayerHdr(null))

        val sdrFormat = Format.Builder().build()
        assertEquals(HdrModeManager.DynamicRange.SDR, HdrModeManager.detectExoPlayerHdr(sdrFormat))
    }

    @Test
    fun `detectExoPlayerHdr detects Dolby Vision via codec`() {
        val dvFormat = Format.Builder()
            .setCodecs("dvhe.05.06")
            .build()
        assertEquals(HdrModeManager.DynamicRange.DOLBY_VISION, HdrModeManager.detectExoPlayerHdr(dvFormat))

        val dvh1Format = Format.Builder()
            .setCodecs("dvh1.08.01")
            .build()
        assertEquals(HdrModeManager.DynamicRange.DOLBY_VISION, HdrModeManager.detectExoPlayerHdr(dvh1Format))
    }

    @Test
    fun `detectExoPlayerHdr detects HDR10 via ST2084 PQ color transfer`() {
        val colorInfo = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build()
        val format = Format.Builder()
            .setColorInfo(colorInfo)
            .build()
        assertEquals(HdrModeManager.DynamicRange.HDR10, HdrModeManager.detectExoPlayerHdr(format))
    }

    @Test
    fun `detectExoPlayerHdr detects HLG transfer`() {
        val colorInfo = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .build()
        val format = Format.Builder()
            .setColorInfo(colorInfo)
            .build()
        assertEquals(HdrModeManager.DynamicRange.HLG, HdrModeManager.detectExoPlayerHdr(format))
    }

    @Test
    fun `detectMpvHdr detects Dolby Vision, HDR10 PQ and HLG`() {
        // Dolby Vision
        assertEquals(
            HdrModeManager.DynamicRange.DOLBY_VISION,
            HdrModeManager.detectMpvHdr(gamma = "pq", colormatrix = "bt.2020-ncl", codec = "dovi")
        )

        // HDR10 PQ
        assertEquals(
            HdrModeManager.DynamicRange.HDR10,
            HdrModeManager.detectMpvHdr(gamma = "pq", colormatrix = "bt.2020-ncl", codec = "hevc")
        )

        // HLG
        assertEquals(
            HdrModeManager.DynamicRange.HLG,
            HdrModeManager.detectMpvHdr(gamma = "hlg", colormatrix = "bt.2020-ncl", codec = "hevc")
        )

        // SDR
        assertEquals(
            HdrModeManager.DynamicRange.SDR,
            HdrModeManager.detectMpvHdr(gamma = "bt.1886", colormatrix = "bt.709", codec = "h264")
        )
    }

    @Test
    fun `dynamicRange badgeText and isHdr properties are correct`() {
        assertTrue(HdrModeManager.DynamicRange.HDR10.isHdr)
        assertEquals("HDR10", HdrModeManager.DynamicRange.HDR10.badgeText)

        assertTrue(HdrModeManager.DynamicRange.DOLBY_VISION.isHdr)
        assertEquals("VISION", HdrModeManager.DynamicRange.DOLBY_VISION.badgeText)

        assertTrue(HdrModeManager.DynamicRange.HLG.isHdr)
        assertEquals("HLG", HdrModeManager.DynamicRange.HLG.badgeText)

        assertFalse(HdrModeManager.DynamicRange.SDR.isHdr)
        assertEquals("", HdrModeManager.DynamicRange.SDR.badgeText)
    }
}
