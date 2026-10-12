package zechs.drive.stream.playback.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.playback.engine.PlayerEngine
import zechs.drive.stream.playback.osd.OsdAction
import zechs.drive.stream.playback.osd.OsdState
import zechs.drive.stream.playback.osd.OsdStateMachine
import zechs.drive.stream.playback.session.TrackPreferencePolicy.TrackCandidate
import zechs.drive.stream.playback.session.TrackPreferencePolicy.TrackPreference
import zechs.drive.stream.utils.MatroskaChapterParser.ParsedChapter
import zechs.drive.stream.utils.PlaybackProgressPolicy

/**
 * Controlador central de reprodução sem dependência de View (Fase 3: T-302 a T-308).
 *
 * Orquestra:
 * - [PlayerEngine] (abstração para ExoPlayer / Media3 e MPV)
 * - [ProgressRecorder] (persistência de progresso T-303)
 * - [PlaybackCoordinator] & [NextEpisodePolicy] (próximo episódio e pós-play T-304, P-12, P-13)
 * - [SkipController] (pulo de abertura, recapitulação e créditos T-305, P-11)
 * - [TrackPreferencePolicy] (seleção de faixas e atrasos T-306, P-14, P-15)
 * - [OsdStateMachine] (máquina de estados de visibilidade do OSD T-307)
 * - Liberação única e idempotente de recursos (T-308)
 */
class PlaybackController(
    val engine: PlayerEngine,
    private val progressRecorder: ProgressRecorder,
    val coordinator: PlaybackCoordinator = PlaybackCoordinator(),
    val skipController: SkipController = SkipController(),
    val osdStateMachine: OsdStateMachine = OsdStateMachine()
) {

    sealed interface AutoplayTransitionResult {
        object NoNextEpisode : AutoplayTransitionResult
        object PromptStillWatching : AutoplayTransitionResult
        data class Advanced(val nextItem: PlaylistItem) : AutoplayTransitionResult
    }

    data class ControllerTickOutcome(
        val autoplayDecision: PlaybackProgressPolicy.AutoplayDecision,
        val skipState: SkipController.SkipState,
        val skipAction: SkipController.SkipAction,
        val osdState: OsdState
    )

    private var isReleased: Boolean = false
    val released: Boolean
        get() = isReleased

    var consecutiveAutoplayedCount: Int = 0
        private set

    private val _stillWatchingPromptVisible = MutableStateFlow(false)
    val stillWatchingPromptVisible: StateFlow<Boolean> = _stillWatchingPromptVisible.asStateFlow()

    /**
     * Carrega a playlist e posiciona no item atual (T-304).
     */
    fun setPlaylist(items: List<PlaylistItem>, currentFileId: String, currentTitle: String? = null) {
        coordinator.setPlaylist(items, currentFileId, currentTitle)
    }

    /**
     * Inicia reprodução de um item em uma posição inicial (T-302).
     */
    fun loadAndPlay(item: PlaylistItem, startPositionMs: Long = 0L) {
        if (isReleased) return
        engine.load(item, startPositionMs)
        engine.play()
    }

    /**
     * Processa um pulso periódico de reprodução (T-304, T-305).
     * Se houver auto-skip configurado para o capítulo atual, aplica o seek automaticamente no motor.
     */
    fun onPlaybackTick(
        autoSkipEnabled: Boolean = false,
        controlsLocked: Boolean = false
    ): ControllerTickOutcome {
        val engineState = engine.playbackState.value
        val positionMs = engineState.currentPositionMs
        val durationMs = engineState.durationMs

        val autoplayDecision = coordinator.onPlaybackTick(
            positionMs = positionMs,
            durationMs = durationMs,
            controlsLocked = controlsLocked
        )

        val (skipState, skipAction) = skipController.onPlaybackTick(
            positionMs = positionMs,
            autoSkipEnabled = autoSkipEnabled,
            controllerVisible = osdStateMachine.isVisible,
            controlsLocked = controlsLocked
        )

        if (skipAction is SkipController.SkipAction.AutoSkip) {
            engine.seekTo(skipAction.targetPositionMs)
        }

        return ControllerTickOutcome(
            autoplayDecision = autoplayDecision,
            skipState = skipState,
            skipAction = skipAction,
            osdState = osdStateMachine.state
        )
    }

    /**
     * Executa o pulo manual de abertura/recapitulação/créditos (T-305) e aplica o seek no motor.
     */
    fun performSkipIntroOrCredits(): Long? {
        if (isReleased) return null
        val currentPositionMs = engine.playbackState.value.currentPositionMs
        if (skipController.getActiveChapter() == null) {
            skipController.onPlaybackTick(
                positionMs = currentPositionMs,
                autoSkipEnabled = false,
                controllerVisible = osdStateMachine.isVisible,
                controlsLocked = false
            )
        }
        val targetMs = skipController.performSkip(currentPositionMs) ?: return null
        engine.seekTo(targetMs)
        return targetMs
    }

    /**
     * Define capítulos nativos MKV (T-305).
     */
    fun setChapters(chapters: List<ParsedChapter>) {
        skipController.setChapters(chapters)
    }

    /**
     * Mescla capítulos do AniSkip sem sobrescrever capítulos nativos MKV (T-305).
     */
    fun mergeAniSkipChapters(chapters: List<ParsedChapter>) {
        skipController.mergeAniSkipChapters(chapters)
    }

    /**
     * Persiste o progresso atual do motor via [ProgressRecorder] (T-303).
     */
    suspend fun saveProgress(
        fileId: String? = null,
        title: String? = null,
        thumbnailLink: String? = null,
        audioDelayMs: Long? = null,
        subtitleDelayMs: Long? = null
    ): Boolean {
        val state = engine.playbackState.value
        val resolvedFileId = fileId?.takeIf { it.isNotBlank() } ?: state.currentItem?.fileId ?: return false
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: state.currentItem?.title ?: ""
        val resolvedThumb = thumbnailLink ?: state.currentItem?.thumbnailLink

        return progressRecorder.recordProgress(
            fileId = resolvedFileId,
            title = resolvedTitle,
            watchedDuration = state.currentPositionMs,
            totalDuration = state.durationMs,
            thumbnailLink = resolvedThumb,
            audioDelayMs = audioDelayMs,
            subtitleDelayMs = subtitleDelayMs
        )
    }

    /**
     * Avança automaticamente para o próximo episódio ao fim da contagem pós-play (T-304, P-12, P-13).
     * Verifica a política "Ainda está assistindo?" após [NextEpisodePolicy.MAX_AUTOPLAY_WITHOUT_INTERACTION] episódios.
     */
    fun advanceByAutoplay(
        threshold: Int = NextEpisodePolicy.MAX_AUTOPLAY_WITHOUT_INTERACTION
    ): AutoplayTransitionResult {
        if (isReleased) return AutoplayTransitionResult.NoNextEpisode
        val next = coordinator.getNextEpisode() ?: return AutoplayTransitionResult.NoNextEpisode

        val nextCount = consecutiveAutoplayedCount + 1
        if (NextEpisodePolicy.shouldPromptStillWatching(nextCount, threshold)) {
            consecutiveAutoplayedCount = nextCount
            engine.pause()
            _stillWatchingPromptVisible.value = true
            return AutoplayTransitionResult.PromptStillWatching
        }

        consecutiveAutoplayedCount = nextCount
        _stillWatchingPromptVisible.value = false
        val advanced = coordinator.advanceToNext() ?: return AutoplayTransitionResult.NoNextEpisode
        engine.load(advanced, 0L)
        engine.play()
        return AutoplayTransitionResult.Advanced(advanced)
    }

    /**
     * Confirma que o usuário ainda está assistindo ("Continuar assistindo" no diálogo P-13),
     * zera o contador de inatividade e avança para o próximo episódio.
     */
    fun confirmStillWatching(): AutoplayTransitionResult {
        consecutiveAutoplayedCount = 0
        _stillWatchingPromptVisible.value = false
        val advanced = coordinator.advanceToNext() ?: return AutoplayTransitionResult.NoNextEpisode
        engine.load(advanced, 0L)
        engine.play()
        return AutoplayTransitionResult.Advanced(advanced)
    }

    /**
     * Avança manualmente para o próximo episódio (ação direta do usuário zera o contador de autoplay).
     */
    fun playNextEpisodeDirectly(): PlaylistItem? {
        if (isReleased) return null
        consecutiveAutoplayedCount = 0
        _stillWatchingPromptVisible.value = false
        val next = coordinator.advanceToNext() ?: return null
        engine.load(next, 0L)
        engine.play()
        return next
    }

    /**
     * Volta manualmente para o episódio anterior.
     */
    fun playPreviousEpisodeDirectly(): PlaylistItem? {
        if (isReleased) return null
        consecutiveAutoplayedCount = 0
        _stillWatchingPromptVisible.value = false
        val prev = coordinator.advanceToPrevious() ?: return null
        engine.load(prev, 0L)
        engine.play()
        return prev
    }

    /**
     * Seleciona faixas preferidas de áudio e legenda segundo a política unificada (T-306, P-14).
     */
    fun selectPreferredTracks(
        audioCandidates: List<TrackCandidate>,
        subtitleCandidates: List<TrackCandidate>,
        preference: TrackPreference? = null
    ): Pair<TrackCandidate?, TrackCandidate?> {
        val audio = TrackPreferencePolicy.selectPreferredAudioTrack(audioCandidates, preference)
        val subtitle = TrackPreferencePolicy.selectPreferredSubtitleTrack(subtitleCandidates, preference)
        return Pair(audio, subtitle)
    }

    /**
     * Filtra legendas externas da pasta que correspondem ao título do vídeo atual (T-306).
     */
    fun filterMatchingExternalSubtitles(
        videoTitle: String,
        folderSubtitles: List<SubtitleItem>
    ): List<SubtitleItem> {
        return TrackPreferencePolicy.filterMatchingExternalSubtitles(videoTitle, folderSubtitles)
    }

    /**
     * Registra interação do usuário: zera o contador de episódios sem interação (P-13)
     * e reinicia o timer do OSD se estiver visível (T-307).
     */
    fun onUserInteraction(): OsdAction {
        consecutiveAutoplayedCount = 0
        return if (osdStateMachine.isVisible) {
            OsdAction.ResetInactivityTimer
        } else {
            OsdAction.None
        }
    }

    /**
     * Solicita exibição do OSD (T-307).
     */
    fun showOsd(): OsdAction = osdStateMachine.onShowRequested()

    /**
     * Solicita ocultação do OSD (T-307).
     */
    fun hideOsd(): OsdAction = osdStateMachine.onHideRequested()

    /**
     * Alterna visibilidade do OSD (T-307).
     */
    fun toggleOsd(): OsdAction {
        return if (osdStateMachine.isVisible) {
            osdStateMachine.onHideRequested()
        } else {
            osdStateMachine.onShowRequested()
        }
    }

    /**
     * Oculta o OSD imediatamente ao entrar em Picture-in-Picture sem liberar o motor (T-307, T-308).
     */
    fun onEnterPictureInPicture() {
        osdStateMachine.forceHidden()
    }

    /**
     * Libera os recursos do motor de forma estritamente única e idempotente (T-308).
     * Chamadas subsequentes (ex.: rotação, finish + onDestroy, ou saída de PiP) são ignoradas com segurança.
     */
    fun release() {
        if (isReleased) return
        isReleased = true
        osdStateMachine.forceHidden()
        engine.release()
    }
}
