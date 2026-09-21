package zechs.drive.stream.ui.player

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import zechs.drive.stream.R

/** Selects the constraints before measurement, including the first frame and rotation. */
class PlayerControlsRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {
    private var compact: Boolean? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val next = MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density < 700
        if (compact != next) {
            compact = next
            ConstraintSet().apply {
                clone(this@PlayerControlsRow)
                if (next) {
                    clear(R.id.mainControls, ConstraintSet.BOTTOM)
                    connect(R.id.mainControls, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    for (id in listOf(R.id.leftActions, R.id.rightActions)) {
                        connect(id, ConstraintSet.TOP, R.id.mainControls, ConstraintSet.BOTTOM,
                            (8 * resources.displayMetrics.density).toInt())
                        connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    }
                } else {
                    for (id in listOf(R.id.mainControls, R.id.leftActions, R.id.rightActions)) {
                        connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    }
                }
                applyTo(this@PlayerControlsRow)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
