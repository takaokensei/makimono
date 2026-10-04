package zechs.drive.stream.ui.player

/** Player activities use this callback to keep the OSD out from under modal content. */
interface PlayerModalHost {
    fun onPlayerModalVisibilityChanged(isVisible: Boolean)
}

enum class PlayerModalTransition {
    NONE,
    HIDE_OSD,
    RESTORE_OSD
}

/** Tracks nested player dialogs so a child picker doesn't reveal the OSD behind its parent. */
class PlayerModalVisibilityState {
    private var modalDepth = 0
    private var restoreOsdWhenClosed = false

    fun onModalOpened(osdWasVisible: Boolean): PlayerModalTransition {
        if (modalDepth > 0) {
            modalDepth++
            return PlayerModalTransition.NONE
        }

        modalDepth = 1
        restoreOsdWhenClosed = osdWasVisible
        return if (osdWasVisible) PlayerModalTransition.HIDE_OSD else PlayerModalTransition.NONE
    }

    fun onModalClosed(): PlayerModalTransition {
        if (modalDepth == 0) return PlayerModalTransition.NONE
        modalDepth--
        if (modalDepth > 0) return PlayerModalTransition.NONE

        val shouldRestore = restoreOsdWhenClosed
        restoreOsdWhenClosed = false
        return if (shouldRestore) PlayerModalTransition.RESTORE_OSD else PlayerModalTransition.NONE
    }
}
