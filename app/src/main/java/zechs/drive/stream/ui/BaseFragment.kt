package zechs.drive.stream.ui

import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialSharedAxis
import zechs.drive.stream.R

abstract class BaseFragment : Fragment() {

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        zechs.drive.stream.utils.TvFocusRing.install(view)
    }

    /**
     * Base fragment class currently only for
     * transition animation
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterTransition = MaterialSharedAxis(
            /* axis */ MaterialSharedAxis.X,
            /* forward */ true
        ).apply {
            interpolator = LinearInterpolator()
            duration = resources.getInteger(R.integer.motion_spec_gentle).toLong()
        }

        returnTransition = MaterialSharedAxis(
            /* axis */ MaterialSharedAxis.X,
            /* forward */ false
        ).apply {
            interpolator = LinearInterpolator()
            duration = resources.getInteger(R.integer.motion_spec_gentle).toLong()
        }

        exitTransition = MaterialSharedAxis(
            /* axis */ MaterialSharedAxis.X,
            /* forward */ true
        ).apply {
            interpolator = LinearInterpolator()
            duration = resources.getInteger(R.integer.motion_spec_normal).toLong()
        }

    }

}
