package zechs.drive.stream.ui.common

import androidx.annotation.StringRes
import zechs.drive.stream.R
import zechs.drive.stream.domain.common.AppError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Ações recomendadas que a interface pode oferecer ao usuário
 * diante de um erro estruturado.
 */
enum class UiErrorAction(
    @StringRes val labelRes: Int
) {
    RETRY(R.string.state_error_network_action),
    REAUTHENTICATE(R.string.state_error_auth_action),
    GO_BACK(R.string.state_error_go_back_action),
    SWITCH_ENGINE(R.string.state_error_playback_action),
    DISMISS(R.string.close_button)
}

/**
 * Modelo selado de erro para a camada de apresentação (UI).
 * Encapsula a mensagem amigável ao usuário (via recurso de string),
 * a ação sugerida e a causa técnica opcional (para telemetria/logs).
 */
sealed class UiError(
    @StringRes val messageRes: Int,
    val suggestedAction: UiErrorAction,
    val technicalCause: Throwable? = null
) {
    data class Network(
        @StringRes val customMessageRes: Int = R.string.state_error_network_message,
        val cause: Throwable? = null
    ) : UiError(customMessageRes, UiErrorAction.RETRY, cause)

    data class Auth(
        @StringRes val customMessageRes: Int = R.string.state_error_auth_message,
        val cause: Throwable? = null
    ) : UiError(customMessageRes, UiErrorAction.REAUTHENTICATE, cause)

    data class NotFound(
        @StringRes val customMessageRes: Int = R.string.state_error_not_found_message,
        val cause: Throwable? = null
    ) : UiError(customMessageRes, UiErrorAction.GO_BACK, cause)

    data class Playback(
        @StringRes val customMessageRes: Int = R.string.state_error_playback_message,
        val cause: Throwable? = null
    ) : UiError(customMessageRes, UiErrorAction.SWITCH_ENGINE, cause)

    data class Unknown(
        @StringRes val customMessageRes: Int = R.string.state_error_generic_message,
        val cause: Throwable? = null
    ) : UiError(customMessageRes, UiErrorAction.RETRY, cause)

    companion object {
        fun fromThrowable(throwable: Throwable?): UiError {
            if (throwable == null) {
                return Unknown()
            }
            val msg = throwable.message?.lowercase().orEmpty()
            return when {
                throwable is UnknownHostException ||
                throwable is SocketTimeoutException ||
                throwable is ConnectException ||
                (throwable is IOException && msg.contains("network")) -> {
                    Network(cause = throwable)
                }
                msg.contains("auth") || msg.contains("401") || msg.contains("expired") || msg.contains("token") -> {
                    Auth(cause = throwable)
                }
                msg.contains("not found") || msg.contains("404") || msg.contains("não encontrado") -> {
                    NotFound(cause = throwable)
                }
                msg.contains("playback") || msg.contains("codec") || msg.contains("unsupported") ||
                msg.contains("decoder") || msg.contains("mpv") || msg.contains("player") -> {
                    Playback(cause = throwable)
                }
                else -> Unknown(cause = throwable)
            }
        }

        fun fromAppError(appError: AppError): UiError {
            return when (appError) {
                is AppError.Network -> Network(cause = appError.technicalCause)
                is AppError.Authentication -> Auth(cause = appError.technicalCause)
                is AppError.NotFound -> NotFound(cause = appError.technicalCause)
                is AppError.UnsupportedCodec -> Playback(cause = appError.technicalCause)
                is AppError.AccessDenied -> NotFound(
                    customMessageRes = R.string.state_error_access_denied_message,
                    cause = appError.technicalCause
                )
                is AppError.RateLimited -> Network(
                    customMessageRes = R.string.state_error_rate_limited_message,
                    cause = appError.technicalCause
                )
                is AppError.Server -> Unknown(
                    customMessageRes = R.string.state_error_server_message,
                    cause = appError.technicalCause
                )
                is AppError.Empty -> NotFound(cause = appError.technicalCause)
                is AppError.Unknown -> Unknown(cause = appError.technicalCause)
            }
        }
    }
}
