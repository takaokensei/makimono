package zechs.drive.stream.utils.ext

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import zechs.drive.stream.R

fun NavController.navigateSafe(directions: NavDirections) {
    val resId = directions.actionId
    val args = directions.arguments
    navigateSafe(resId, args)
}

fun NavController.navigateSafe(
    @IdRes resId: Int,
    args: Bundle? = null,
    navOptions: NavOptions? = null,
    navExtras: Navigator.Extras? = null
) {
    val action = currentDestination?.getAction(resId) ?: graph.getAction(resId)
    val existing = navOptions ?: action?.navOptions
    val resolvedOptions = NavOptions.Builder()
        .apply {
            if (existing != null) {
                setPopUpTo(existing.popUpToId, existing.isPopUpToInclusive())
                setLaunchSingleTop(existing.shouldLaunchSingleTop())
            }
            val enter = existing?.enterAnim?.takeIf { it != -1 } ?: R.anim.nav_fade_in
            val exit = existing?.exitAnim?.takeIf { it != -1 } ?: R.anim.nav_fade_out
            val popEnter = existing?.popEnterAnim?.takeIf { it != -1 } ?: R.anim.nav_pop_enter
            val popExit = existing?.popExitAnim?.takeIf { it != -1 } ?: R.anim.nav_pop_exit
            setEnterAnim(enter)
            setExitAnim(exit)
            setPopEnterAnim(popEnter)
            setPopExitAnim(popExit)
        }
        .build()

    if (action != null && currentDestination?.id != action.destinationId) {
        navigate(resId, args, resolvedOptions, navExtras)
    } else if (currentDestination?.id != resId) {
        val dest = graph.findNode(resId)
        if (dest != null) {
            navigate(resId, args, resolvedOptions, navExtras)
        }
    }
}

