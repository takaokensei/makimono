package zechs.drive.stream.utils.ext

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.resolveThemeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    if (theme.resolveAttribute(attrRes, typedValue, true)) {
        return typedValue.data
    }
    return 0
}
