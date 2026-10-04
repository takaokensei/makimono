package zechs.drive.stream.utils

import android.content.Context
import android.view.View
import kotlin.math.roundToInt
import zechs.drive.stream.R

/** Keeps the Home hero actions inside the visible hero when system text is enlarged. */
object TvAccessibilityLayout {
    fun adaptHomeHero(
        context: Context,
        hero: View?,
        primaryAction: View?,
        secondaryAction: View?
    ) {
        val scale = context.resources.configuration.fontScale.coerceIn(1f, 2f)
        if (scale <= 1f) return

        hero?.let {
            it.layoutParams = it.layoutParams.apply {
                height = (context.resources.getDimensionPixelSize(R.dimen.home_hero_height) * scale)
                    .roundToInt()
            }
        }

        val actionHeight = (context.resources.getDimensionPixelSize(R.dimen.home_hero_action_height) * scale)
            .roundToInt()
        listOfNotNull(primaryAction, secondaryAction).forEach { action ->
            action.layoutParams = action.layoutParams.apply { height = actionHeight }
        }
    }
}
