package zechs.drive.stream.ui.player

import android.view.View
import zechs.drive.stream.R
import zechs.drive.stream.utils.DeviceUi

/** Keeps the engine-specific handlers intact while removing secondary OSD stops. */
object PlayerQuickOptions {
    fun bind(root: View, extraActions: List<Pair<String, () -> Unit>> = emptyList()) {
        zechs.drive.stream.utils.TvFocusRing.install(root)
        val more = root.findViewById<View>(R.id.btnMoreOptions)
        val ctx = root.context
        val actions = buildList {
            add(R.id.btnSpeed to ctx.getString(R.string.player_speed))
            add(R.id.btnResize to ctx.getString(R.string.resize))
            add(R.id.btnChapter to ctx.getString(R.string.chapters))
            add(R.id.btnInfo to ctx.getString(R.string.player_technical_info))
            if (!DeviceUi.isTenFootExperience(ctx)) add(R.id.btnRotate to ctx.getString(R.string.player_rotate_label))
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
            menu = PlayerGlassMenuDialog(ctx, ctx.getString(R.string.player_more_options), menuItems) { selected ->
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
