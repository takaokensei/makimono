package zechs.drive.stream.utils

/**
 * ExoPlayer keeps custom controller child views attached (and often VISIBLE) after
 * PlayerView hides its controller. Use the container state as the source of truth.
 */
object PlayerControllerVisibility {
    fun isVisible(controllerVisible: Boolean, isHiding: Boolean): Boolean =
        controllerVisible && !isHiding
}
