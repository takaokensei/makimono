package zechs.drive.stream.ui

import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialSharedAxis
import zechs.drive.stream.R
import zechs.drive.stream.utils.DeviceUi
import zechs.drive.stream.utils.FocusEngine
import zechs.drive.stream.utils.MotionPolicy

abstract class BaseFragment : Fragment() {

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        zechs.drive.stream.utils.TvFocusRing.install(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx = context
        val isTenFoot = ctx != null && DeviceUi.isTenFootExperience(ctx)
        val isLowRamOrReduced = ctx != null && (MotionPolicy.isReduced(ctx) || FocusEngine.isLowRam(ctx))

        if (isTenFoot || isLowRamOrReduced) {
            // Android TV / Low-RAM: Disable heavy MaterialSharedAxis surface transitions.
            // On TV, capturing and animating two full 1080p hierarchies causes
            // a 4-5s freeze on back navigation (Netflix / Crunchyroll TV standard).
            enterTransition = null
            returnTransition = null
            exitTransition = null
        } else {
            enterTransition = MaterialSharedAxis(
                MaterialSharedAxis.X,
                true
            ).apply {
                interpolator = LinearInterpolator()
                duration = resources.getInteger(R.integer.motion_spec_gentle).toLong()
            }

            returnTransition = MaterialSharedAxis(
                MaterialSharedAxis.X,
                false
            ).apply {
                interpolator = LinearInterpolator()
                duration = resources.getInteger(R.integer.motion_spec_gentle).toLong()
            }

            exitTransition = MaterialSharedAxis(
                MaterialSharedAxis.X,
                true
            ).apply {
                interpolator = LinearInterpolator()
                duration = resources.getInteger(R.integer.motion_spec_normal).toLong()
            }
        }
    }
}
