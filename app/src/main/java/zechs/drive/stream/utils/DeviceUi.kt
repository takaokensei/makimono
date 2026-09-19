package zechs.drive.stream.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager

object DeviceUi {

    /** True for Android TV, leanback devices, or large tablet landscape (10-foot UI). */
    fun isTenFootExperience(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) return true
        
        // Check if it's actually a TV device by checking for lack of touch
        val hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        if (!hasTouch) return true
        
        // For tablets with touch, use screen size to determine TV-like experience
        val config = context.resources.configuration
        if (config.smallestScreenWidthDp >= 720 &&
            config.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            // Additional check to distinguish tablets from phones
            val displayMetrics = getDisplayMetrics(context)
            val minDimension = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val density = displayMetrics.density
            val minDp = minDimension / density
            
            // Only treat as TV experience if it's actually large (tablet sized)
            // This prevents phones in landscape from being treated as TV
            return minDp >= 600
        }
        return false
    }
    
    /** Check if device is a tablet (sw600dp+) */
    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.smallestScreenWidthDp >= 600
    }
    
    /** Check if device is a phone (smaller than tablet) */
    fun isPhone(context: Context): Boolean {
        return !isTablet(context)
    }
    
    /** Get display metrics safely */
    private fun getDisplayMetrics(context: Context): DisplayMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics
    }
}
