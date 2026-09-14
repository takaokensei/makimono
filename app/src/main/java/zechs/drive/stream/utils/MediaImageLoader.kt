package zechs.drive.stream.utils

import android.widget.ImageView
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
        load(view, url, 1440, 810, R.drawable.kodi_primary_bg)
    }

    private fun load(
        view: ImageView,
        url: String?,
        width: Int,
        height: Int,
        placeholder: Int
    ) {
        if (url.isNullOrBlank()) {
            GlideApp.with(view).clear(view)
            view.setImageResource(placeholder)
            return
        }

        GlideApp.with(view)
            .load(url)
            .override(width, height)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .placeholder(placeholder)
            .error(placeholder)
            .thumbnail(0.18f)
            .transition(DrawableTransitionOptions.withCrossFade(160))
            .into(view)
    }
}
