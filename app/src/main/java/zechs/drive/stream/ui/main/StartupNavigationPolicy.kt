package zechs.drive.stream.ui.main

object StartupNavigationPolicy {
    enum class Destination { PAIRING, PREPARATION, PROFILES }

    fun destination(hasSession: Boolean, justPaired: Boolean = false): Destination = when {
        !hasSession -> Destination.PAIRING
        justPaired -> Destination.PREPARATION
        else -> Destination.PROFILES
    }
}
