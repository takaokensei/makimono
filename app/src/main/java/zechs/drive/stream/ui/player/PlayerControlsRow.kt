package zechs.drive.stream.ui.player

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import zechs.drive.stream.R

/** Selects the constraints before measurement, including the first frame and rotation. */
class PlayerControlsRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {
    private var arrangement: Int? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthDp = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) / resources.displayMetrics.density
        val next = when { widthDp < 360 -> 3; widthDp < 700 -> 2; else -> 1 }
        if (arrangement != next) {
            arrangement = next
            ConstraintSet().apply {
                clone(this@PlayerControlsRow)
                clear(R.id.leftActions, ConstraintSet.END)
                clear(R.id.rightActions, ConstraintSet.START)
                connect(R.id.leftActions, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                connect(R.id.rightActions, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                if (next == 3) {
                    clear(R.id.mainControls, ConstraintSet.BOTTOM)
                    connect(R.id.mainControls, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    clear(R.id.leftActions, ConstraintSet.BOTTOM)
                    connect(R.id.leftActions, ConstraintSet.TOP, R.id.mainControls, ConstraintSet.BOTTOM, (8 * resources.displayMetrics.density).toInt())
                    connect(R.id.leftActions, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.rightActions, ConstraintSet.TOP, R.id.leftActions, ConstraintSet.BOTTOM, (8 * resources.displayMetrics.density).toInt())
                    connect(R.id.rightActions, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(R.id.rightActions, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                } else if (next == 2) {
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
