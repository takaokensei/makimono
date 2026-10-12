package zechs.drive.stream.utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import zechs.drive.stream.R

/** Shared image policy for predictable TV memory and fast perceived loading. */
object MediaImageLoader {
    fun card(view: ImageView, url: String?) {
        load(view, url, 640, 400, R.drawable.glass_card_bg)
    }

    fun poster(view: ImageView, url: String?) {
        load(view, url, 640, 960, R.drawable.glass_card_bg)
    }

    fun backdrop(view: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.setImageDrawable(null)
            return
        }
        Glide.with(view)
            .load(url)
            .override(1440, 810)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .dontAnimate()
            .into(view)
    }

    fun preloadPoster(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            Glide.with(context.applicationContext)
                .load(url)
                .override(640, 960)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .preload(640, 960)
        } catch (e: Exception) {
            AppLog.logSwallowed("MediaImageLoader", "preloading poster", e)
        }
    }

    fun preloadBackdrop(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            Glide.with(context.applicationContext)
                .load(url)
                .override(1440, 810)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .preload(1440, 810)
        } catch (e: Exception) {
            AppLog.logSwallowed("MediaImageLoader", "preloading backdrop", e)
        }
    }

    private fun load(
        view: ImageView,
        url: String?,
        width: Int,
        height: Int,
        placeholder: Int
    ) {
        if (url.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.setImageResource(placeholder)
            return
        }

        val thumbnailRequest = Glide.with(view)
            .load(url)
            .sizeMultiplier(0.18f)

        Glide.with(view)
            .load(url)
            .override(width, height)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(placeholder)
            .error(placeholder)
            .thumbnail(thumbnailRequest)
            .transition(DrawableTransitionOptions.withCrossFade(160))
            .into(view)
    }
}
