package zechs.drive.stream.ui.player

import android.content.res.Configuration
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.playback.osd.OsdController
import zechs.drive.stream.utils.EpisodeParser

/**
 * Classe base comum para as Activities de reprodução (PlayerActivity e MPVActivity).
 * Centraliza comportamentos de sistema, OSD, gestos e diálogos de episódios,
 * eliminando as 6 funções homônimas de corpo idêntico (T-301 / Q-001).
 */
abstract class BasePlayerActivity : AppCompatActivity(), PlayerModalHost {

    protected lateinit var osdController: OsdController

    protected val isOsdInitialized: Boolean
        get() = ::osdController.isInitialized

    protected abstract val isGesturesEnabled: Boolean
    protected abstract val isGestureHelperInitialized: Boolean
    protected abstract fun onGestureTouchEvent(ev: MotionEvent): Boolean
    protected abstract fun updateOrientation(newConfig: Configuration)

    /**
     * Encaminha eventos de toque para o helper de gestos quando habilitado,
     * ou para o dispatch padrão da Activity.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isGesturesEnabled && isGestureHelperInitialized && onGestureTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Oculta a barra de status e de navegação do sistema para imersão total.
     */
    protected fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Verifica se os controles de reprodução (OSD) estão visíveis no momento.
     */
    protected fun isControlsShowing(): Boolean {
        if (!::osdController.isInitialized) return false
        return osdController.isVisible
    }

    /**
     * Reinicia o temporizador de inatividade do OSD ao receber qualquer interação do usuário.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::osdController.isInitialized && osdController.isVisible) {
            osdController.resetInactivityTimer()
        }
    }

    /**
     * Atualiza orientação da tela na mudança de configuração do dispositivo.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateOrientation(newConfig)
    }

    /**
     * Exibe a gaveta/diálogo lateral de episódios para seleção rápida pelo usuário.
     */
    protected fun showEpisodesDrawer(
        currentTitle: String,
        currentFileId: String,
        playlist: List<PlaylistItem>,
        tenraiService: TenraiAnimeService,
        onEpisodeSelected: (PlaylistItem) -> Unit
    ) {
        val seriesFromIntent = intent.getStringExtra("seriesTitle")?.takeIf { it.isNotBlank() }
        val showName = seriesFromIntent ?: EpisodeParser.parse(currentTitle).showTitle.ifBlank { currentTitle }
        PlayerEpisodeDrawerDialog(
            activity = this,
            showTitle = showName,
            currentPlayingFileId = currentFileId,
            playlist = playlist,
            tenraiService = tenraiService
        ) { selectedEpisode ->
            onEpisodeSelected(selectedEpisode)
        }.show()
    }
}
