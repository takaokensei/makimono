package zechs.drive.stream.utils

enum class PlayerFocusDirection { UP, DOWN }
enum class PlayerFocusItem { EXIT, PLAY_PAUSE, OTHER }

/** Keeps the player's primary remote path predictable across Media3 and MPV surfaces. */
object PlayerDpadFocus {
    fun next(
        direction: PlayerFocusDirection,
        controlsVisible: Boolean,
        controlsTransitioning: Boolean,
        blockingOverlayVisible: Boolean,
        focusedItem: PlayerFocusItem
    ): PlayerFocusItem? {
        if (!controlsVisible || controlsTransitioning || blockingOverlayVisible) return null

        return when {
            direction == PlayerFocusDirection.UP && focusedItem == PlayerFocusItem.PLAY_PAUSE ->
                PlayerFocusItem.EXIT
            direction == PlayerFocusDirection.DOWN && focusedItem == PlayerFocusItem.EXIT ->
                PlayerFocusItem.PLAY_PAUSE
            else -> null
        }
    }
}
