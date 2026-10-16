package makimono.dossie.provas.player

/**
 * Máquina de Estados e Coordenador de HUD / Skip Intro para Player de TV.
 * Modela a exibição temporária de controles (auto-dismiss de 3000ms) e o aparecimento
 * contextual do botão "Pular Abertura" quando a reprodução atinge intervalos marcados,
 * prevenindo o reaparecimento indevido do botão enquanto o seek assíncrono é concluído.
 */
enum class SkipType {
    INTRO,
    RECAP,
    OUTRO
}

data class SkipInterval(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,
    val label: String = "Pular Abertura"
)

sealed interface SkipButtonState {
    object Hidden : SkipButtonState
    data class Visible(val interval: SkipInterval, val targetSeekPositionMs: Long) : SkipButtonState
}

class MakimonoPlayerOsdSkipCoordinator(
    private val autoHideTimeoutMs: Long = 3000L,
    private val skipBufferOffsetMs: Long = 100L // Pequeno offset para pousar após o frame final da intro
) {
    var isControlsVisible: Boolean = false
        private set

    var skipButtonState: SkipButtonState = SkipButtonState.Hidden
        private set

    private var lastUserInteractionTimeMs: Long = 0L
    private var acknowledgedInterval: SkipInterval? = null

    fun onUserActivity(currentTimeMs: Long) {
        lastUserInteractionTimeMs = currentTimeMs
        isControlsVisible = true
    }

    fun onPlaybackTimeTick(currentPlaybackPositionMs: Long, intervals: List<SkipInterval>, clockTimeMs: Long) {
        // 1. Gerenciamento do Auto-Hide do HUD principal
        if (isControlsVisible && (clockTimeMs - lastUserInteractionTimeMs >= autoHideTimeoutMs)) {
            isControlsVisible = false
        }

        // 2. Liberação de intervalo já pulado quando o cursor sai da janela
        val ack = acknowledgedInterval
        if (ack != null && currentPlaybackPositionMs !in ack.startMs until ack.endMs) {
            acknowledgedInterval = null
        }

        // 3. Detecção de Skip Interval ativo que ainda não foi pulado pelo usuário
        val activeInterval = intervals.firstOrNull { interval ->
            currentPlaybackPositionMs in interval.startMs until interval.endMs && interval != acknowledgedInterval
        }

        skipButtonState = if (activeInterval != null) {
            SkipButtonState.Visible(
                interval = activeInterval,
                targetSeekPositionMs = activeInterval.endMs + skipBufferOffsetMs
            )
        } else {
            SkipButtonState.Hidden
        }
    }

    /**
     * Executa a ação de pular. Retorna a posição em ms para onde o player deve fazer seek,
     * registra o intervalo como reconhecido para evitar reaparecimento por ticks obsoletos
     * e oculta imediatamente o botão.
     */
    fun performSkip(): Long? {
        val current = skipButtonState
        return if (current is SkipButtonState.Visible) {
            acknowledgedInterval = current.interval
            skipButtonState = SkipButtonState.Hidden
            current.targetSeekPositionMs
        } else {
            null
        }
    }

    fun hideControlsExplicitly() {
        isControlsVisible = false
    }
}
