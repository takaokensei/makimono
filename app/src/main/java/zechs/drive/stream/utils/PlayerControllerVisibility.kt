package zechs.drive.stream.utils

/**
 * Controller children can remain VISIBLE during/after a fade. Treat controller state
 * and the transition flag as the source of truth so ExoPlayer and MPV route keys alike.
 */
object PlayerControllerVisibility {
    fun isVisible(controllerVisible: Boolean, isHiding: Boolean): Boolean =
        controllerVisible && !isHiding

    /** A Back press during the hide animation must not finish the video underneath it. */
    fun shouldConsumeBack(controllerVisible: Boolean, isHiding: Boolean): Boolean =
        controllerVisible || isHiding
}
