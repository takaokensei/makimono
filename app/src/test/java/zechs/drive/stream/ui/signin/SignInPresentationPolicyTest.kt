package zechs.drive.stream.ui.signin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.R

class SignInPresentationPolicyTest {

    @Test
    fun `tv experience returns 10-foot layout with visible QR code and hidden mobile actions`() {
        val state = SignInPresentationPolicy.resolve(
            isTenFoot = true,
            qrUrl = "https://pairing.test/p/abc",
            confirmCode = "284763",
            isQrExpandedOnMobile = false
        )

        assertTrue(state.isQrCodeVisible)
        assertFalse(state.isDirectBrowserActionVisible)
        assertFalse(state.isCopyActionsVisible)
        assertFalse(state.isToggleQrActionVisible)
        assertEquals(R.string.pairing_title, state.titleRes)
        assertEquals(R.string.pairing_description, state.descriptionRes)
        assertEquals(R.string.pairing_instructions, state.instructionsRes)
        assertEquals("284 763", state.formattedConfirmCode)
        assertEquals("https://pairing.test/p/abc?code=284763", state.directAuthUrl)
        assertEquals("Makimono TV", SignInPresentationPolicy.deviceName(isTenFoot = true))
    }

    @Test
    fun `mobile experience collapses QR code by default and surfaces direct browser CTA`() {
        val state = SignInPresentationPolicy.resolve(
            isTenFoot = false,
            qrUrl = "https://pairing.test/p/abc",
            confirmCode = "592184",
            isQrExpandedOnMobile = false
        )

        assertFalse(state.isQrCodeVisible)
        assertTrue(state.isDirectBrowserActionVisible)
        assertTrue(state.isCopyActionsVisible)
        assertTrue(state.isToggleQrActionVisible)
        assertEquals(R.string.pairing_mobile_show_qr, state.toggleQrButtonTextRes)
        assertEquals(R.string.pairing_mobile_title, state.titleRes)
        assertEquals(R.string.pairing_mobile_description, state.descriptionRes)
        assertEquals(R.string.pairing_mobile_instructions, state.instructionsRes)
        assertEquals("592 184", state.formattedConfirmCode)
        assertEquals("https://pairing.test/p/abc?code=592184", state.directAuthUrl)
        assertEquals("Makimono Celular", SignInPresentationPolicy.deviceName(isTenFoot = false))
    }

    @Test
    fun `mobile experience with QR expanded shows QR and changes toggle label to hide`() {
        val state = SignInPresentationPolicy.resolve(
            isTenFoot = false,
            qrUrl = "https://pairing.test/p/abc",
            confirmCode = "592184",
            isQrExpandedOnMobile = true
        )

        assertTrue(state.isQrCodeVisible)
        assertTrue(state.isDirectBrowserActionVisible)
        assertTrue(state.isToggleQrActionVisible)
        assertEquals(R.string.pairing_mobile_hide_qr, state.toggleQrButtonTextRes)
    }

    @Test
    fun `direct auth url handles existing query parameters correctly`() {
        val state = SignInPresentationPolicy.resolve(
            isTenFoot = false,
            qrUrl = "https://pairing.test/p/abc?ref=qr",
            confirmCode = "123456"
        )

        assertEquals("https://pairing.test/p/abc?ref=qr&code=123456", state.directAuthUrl)
    }

    @Test
    fun `formatting strips non digits and chunks code into pairs of three`() {
        val state = SignInPresentationPolicy.resolve(
            isTenFoot = true,
            qrUrl = "https://pairing.test/p/abc",
            confirmCode = "284-763"
        )

        assertEquals("284 763", state.formattedConfirmCode)
        assertEquals("https://pairing.test/p/abc?code=284763", state.directAuthUrl)
    }

    @Test
    fun `empty code does not append empty query parameter`() {
        val state = SignInPresentationPolicy.resolve(
            isTenFoot = false,
            qrUrl = "https://pairing.test/p/abc",
            confirmCode = ""
        )

        assertEquals("", state.formattedConfirmCode)
        assertEquals("https://pairing.test/p/abc", state.directAuthUrl)
    }
}
