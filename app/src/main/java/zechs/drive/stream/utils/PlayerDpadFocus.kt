package zechs.drive.stream.utils

enum class PlayerFocusDirection { UP, DOWN }
enum class PlayerFocusItem { EXIT, TIMELINE, PLAY_PAUSE, OTHER }

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
            direction == PlayerFocusDirection.UP && (focusedItem == PlayerFocusItem.PLAY_PAUSE || focusedItem == PlayerFocusItem.OTHER) ->
                PlayerFocusItem.TIMELINE
            direction == PlayerFocusDirection.UP && focusedItem == PlayerFocusItem.TIMELINE ->
                PlayerFocusItem.EXIT
            direction == PlayerFocusDirection.DOWN && focusedItem == PlayerFocusItem.EXIT ->
                PlayerFocusItem.TIMELINE
            direction == PlayerFocusDirection.DOWN && focusedItem == PlayerFocusItem.TIMELINE ->
                PlayerFocusItem.PLAY_PAUSE
            else -> null
        }
    }
}
