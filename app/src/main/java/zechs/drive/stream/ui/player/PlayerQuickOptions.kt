package zechs.drive.stream.ui.player

import android.view.View
import zechs.drive.stream.R
import zechs.drive.stream.utils.DeviceUi

/** Keeps the engine-specific handlers intact while removing secondary OSD stops. */
object PlayerQuickOptions {
    fun bind(root: View, extraActions: List<Pair<String, () -> Unit>> = emptyList()) {
        zechs.drive.stream.utils.TvFocusRing.install(root)
        val more = root.findViewById<View>(R.id.btnMoreOptions)
        val actions = buildList {
            add(R.id.btnSpeed to "Velocidade")
            add(R.id.btnResize to "Proporção de tela")
            add(R.id.btnChapter to "Capítulos")
            add(R.id.btnInfo to "Informações técnicas")
            if (!DeviceUi.isTenFootExperience(root.context)) add(R.id.btnRotate to "Girar tela")
        }
        listOf(R.id.btnSpeed, R.id.btnResize, R.id.btnChapter, R.id.btnInfo, R.id.btnRotate)
            .forEach { root.findViewById<View>(it).visibility = View.GONE }
        root.findViewById<View>(R.id.btnAudio)?.nextFocusRightId = R.id.btnPrevEp
        root.findViewById<View>(R.id.btnPrevEp)?.nextFocusLeftId = R.id.btnAudio
        root.findViewById<View>(R.id.btnEpisodes)?.nextFocusRightId = R.id.btnEpisodes
        more.setOnClickListener {
            val menuItems = actions.map { (id, title) ->
                GlassMenuItem(id.toString(), title, tag = id)
            } + extraActions.mapIndexed { idx, (title, _) ->
                GlassMenuItem("extra_$idx", title, tag = title)
            }
            lateinit var menu: PlayerGlassMenuDialog
            menu = PlayerGlassMenuDialog(root.context, "Mais opções", menuItems) { selected ->
                menu.dismiss()
                val tag = selected.tag
                if (tag is Int) {
                    root.findViewById<View>(tag).performClick()
                } else if (tag is String) {
                    extraActions.firstOrNull { it.first == tag }?.second?.invoke()
                }
            }.setOnDismiss { more.requestFocus() }
            menu.show()
        }
    }
}
