package zechs.drive.stream.ui.player

import android.view.View
import zechs.drive.stream.R
import zechs.drive.stream.utils.DeviceUi

/** Keeps the engine-specific handlers intact while removing secondary OSD stops. */
object PlayerQuickOptions {
    fun bind(root: View) {
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
        more.setOnClickListener {
            lateinit var menu: PlayerGlassMenuDialog
            menu = PlayerGlassMenuDialog(root.context, "Mais opções", actions.map { (id, title) ->
                GlassMenuItem(id.toString(), title, tag = id)
            }) { selected ->
                menu.dismiss()
                root.findViewById<View>(selected.tag as Int).performClick()
            }.setOnDismiss { more.requestFocus() }
            menu.show()
        }
    }
}
