package zechs.drive.stream.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import zechs.drive.stream.R
import zechs.drive.stream.databinding.ComponentStateViewBinding

/**
 * Reusable empty, error, and partial state component (T3.6 / Gate G7).
 * Standardizes illustrations, semantic typography, action buttons and
 * prevents technical error leakage.
 */
class StateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ComponentStateViewBinding.inflate(LayoutInflater.from(context), this, true)

    enum class StateType {
        EMPTY_HOME,
        EMPTY_FOLDER,
        ERROR_NETWORK,
        ERROR_AUTH,
        ERROR_ACCESS_DENIED,
        ERROR_RATE_LIMITED,
        ERROR_NOT_FOUND,
        ERROR_SERVER,
        ERROR_PLAYBACK,
        ERROR_GENERIC,
        CUSTOM
    }

    data class StateConfig(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val messageRes: Int,
        @StringRes val actionRes: Int? = null
    )

    companion object {
        fun configFor(type: StateType): StateConfig? {
            return when (type) {
                StateType.EMPTY_HOME -> StateConfig(
                    iconRes = R.drawable.ic_folder_24,
                    titleRes = R.string.state_empty_home_title,
                    messageRes = R.string.state_empty_home_message,
                    actionRes = R.string.state_empty_home_action
                )
                StateType.EMPTY_FOLDER -> StateConfig(
                    iconRes = R.drawable.ic_folder_24,
                    titleRes = R.string.state_empty_folder_title,
                    messageRes = R.string.state_empty_folder_message,
                    actionRes = null
                )
                StateType.ERROR_NETWORK -> StateConfig(
                    iconRes = R.drawable.ic_error_72,
                    titleRes = R.string.state_error_network_title,
                    messageRes = R.string.state_error_network_message,
                    actionRes = R.string.state_error_network_action
                )
                StateType.ERROR_AUTH -> StateConfig(
                    iconRes = R.drawable.ic_person_24,
                    titleRes = R.string.state_error_auth_title,
                    messageRes = R.string.state_error_auth_message,
                    actionRes = R.string.state_error_auth_action
                )
                StateType.ERROR_ACCESS_DENIED -> StateConfig(
                    iconRes = R.drawable.ic_error_72,
                    titleRes = R.string.state_error_access_denied_title,
                    messageRes = R.string.state_error_access_denied_message,
                    actionRes = R.string.state_error_go_back_action
                )
                StateType.ERROR_RATE_LIMITED -> StateConfig(
                    iconRes = R.drawable.ic_error_72,
                    titleRes = R.string.state_error_rate_limited_title,
                    messageRes = R.string.state_error_rate_limited_message,
                    actionRes = R.string.state_error_network_action
                )
                StateType.ERROR_NOT_FOUND -> StateConfig(
                    iconRes = R.drawable.ic_error_72,
                    titleRes = R.string.state_error_not_found_title,
                    messageRes = R.string.state_error_not_found_message,
                    actionRes = R.string.state_error_go_back_action
                )
                StateType.ERROR_SERVER -> StateConfig(
                    iconRes = R.drawable.ic_error_72,
                    titleRes = R.string.state_error_server_title,
                    messageRes = R.string.state_error_server_message,
                    actionRes = R.string.state_error_network_action
                )
                StateType.ERROR_PLAYBACK -> StateConfig(
                    iconRes = R.drawable.ic_error_72,
                    titleRes = R.string.state_error_playback_title,
                    messageRes = R.string.state_error_playback_message,
                    actionRes = R.string.state_error_playback_action
                )
                StateType.ERROR_GENERIC -> StateConfig(
                    iconRes = R.drawable.ic_error_72,
                    titleRes = R.string.state_error_generic_title,
                    messageRes = R.string.state_error_generic_message,
                    actionRes = R.string.state_error_network_action
                )
                StateType.CUSTOM -> null
            }
        }

        /**
         * Cleans technical jargon, exception names, HTTP codes, and tokens from error messages.
         */
        fun sanitizeErrorMessage(rawMessage: String?): String {
            if (rawMessage.isNullOrBlank()) {
                return "Não foi possível carregar os dados. Tente novamente."
            }
            val lower = rawMessage.lowercase()
            if (lower.contains("socket") || lower.contains("connect") || lower.contains("network") ||
                lower.contains("timeout") || lower.contains("unknownhost") || lower.contains("offline")
            ) {
                return "Verifique sua conexão com a internet e tente novamente."
            }
            if (lower.contains("auth") || lower.contains("401") || lower.contains("403") || lower.contains("expired")) {
                return "Sua conexão com o Google Drive expirou. Reconecte sua conta."
            }
            if (lower.contains("exception") || lower.contains("http") || lower.contains("500") || lower.contains("502") || lower.contains("503")) {
                return "Ocorreu uma instabilidade momentânea. Tente novamente."
            }
            // Error payloads can contain provider internals, file identifiers, paths, or
            // implementation details that are not safe or useful to echo into the UI.
            return "Não foi possível carregar os dados. Tente novamente."
        }
    }

    fun setState(type: StateType, onActionClick: (() -> Unit)? = null) {
        val config = configFor(type) ?: return
        showState(
            iconRes = config.iconRes,
            title = context.getString(config.titleRes),
            message = context.getString(config.messageRes),
            actionText = config.actionRes?.let { context.getString(it) },
            onActionClick = onActionClick
        )
    }

    fun showState(
        @DrawableRes iconRes: Int,
        title: String,
        message: String,
        actionText: String? = null,
        onActionClick: (() -> Unit)? = null
    ) {
        binding.ivStateIcon.setImageResource(iconRes)
        binding.tvStateTitle.text = title
        // Callers pass localized copy here. Sanitize provider/exception text at the
        // boundary where it enters the UI so normal empty-state copy stays intact.
        binding.tvStateMessage.text = message.trim().ifBlank {
            "Não foi possível carregar os dados. Tente novamente."
        }

        if (actionText != null && onActionClick != null) {
            binding.btnStateAction.isVisible = true
            binding.btnStateAction.text = actionText
            binding.btnStateAction.setOnClickListener { onActionClick() }
            binding.btnStateAction.post {
                if (binding.btnStateAction.isVisible && isAttachedToWindow) {
                    binding.btnStateAction.requestFocus()
                }
            }
        } else {
            binding.btnStateAction.isVisible = false
        }
    }

    /**
     * Requisita foco imediato no botão primário de ação (T-126, HIG tvOS).
     */
    fun requestActionFocus(): Boolean {
        return if (binding.btnStateAction.isVisible) {
            binding.btnStateAction.requestFocus()
        } else {
            false
        }
    }
}
