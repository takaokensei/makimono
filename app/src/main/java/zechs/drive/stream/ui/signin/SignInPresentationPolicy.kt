package zechs.drive.stream.ui.signin

import zechs.drive.stream.R

data class SignInPresentationState(
    val isQrCodeVisible: Boolean,
    val isDirectBrowserActionVisible: Boolean,
    val isCopyActionsVisible: Boolean,
    val isToggleQrActionVisible: Boolean,
    val toggleQrButtonTextRes: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val instructionsRes: Int,
    val formattedConfirmCode: String,
    val directAuthUrl: String
)

object SignInPresentationPolicy {

    fun resolve(
        isTenFoot: Boolean,
        qrUrl: String,
        confirmCode: String,
        isQrExpandedOnMobile: Boolean = false
    ): SignInPresentationState {
        val cleanCode = confirmCode.filter { it.isDigit() }
        val formattedCode = cleanCode.chunked(3).joinToString(" ")
        val directUrl = if (cleanCode.isNotBlank() && qrUrl.isNotBlank()) {
            if (qrUrl.contains("?")) "$qrUrl&code=$cleanCode" else "$qrUrl?code=$cleanCode"
        } else {
            qrUrl
        }

        return if (isTenFoot) {
            SignInPresentationState(
                isQrCodeVisible = true,
                isDirectBrowserActionVisible = false,
                isCopyActionsVisible = false,
                isToggleQrActionVisible = false,
                toggleQrButtonTextRes = R.string.pairing_mobile_show_qr,
                titleRes = R.string.pairing_title,
                descriptionRes = R.string.pairing_description,
                instructionsRes = R.string.pairing_instructions,
                formattedConfirmCode = formattedCode,
                directAuthUrl = directUrl
            )
        } else {
            SignInPresentationState(
                isQrCodeVisible = isQrExpandedOnMobile,
                isDirectBrowserActionVisible = true,
                isCopyActionsVisible = true,
                isToggleQrActionVisible = true,
                toggleQrButtonTextRes = if (isQrExpandedOnMobile) {
                    R.string.pairing_mobile_hide_qr
                } else {
                    R.string.pairing_mobile_show_qr
                },
                titleRes = R.string.pairing_mobile_title,
                descriptionRes = R.string.pairing_mobile_description,
                instructionsRes = R.string.pairing_mobile_instructions,
                formattedConfirmCode = formattedCode,
                directAuthUrl = directUrl
            )
        }
    }

    fun deviceName(isTenFoot: Boolean): String {
        return if (isTenFoot) "Makimono TV" else "Makimono Celular"
    }
}
