package zechs.drive.stream.utils

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import zechs.drive.stream.R

/** Shared image policy for predictable TV memory and fast perceived loading. */
object MediaImageLoader {
    const val CARD_WIDTH = 640
    const val CARD_HEIGHT = 400
    const val POSTER_WIDTH = 640
    const val POSTER_HEIGHT = 960
    const val BACKDROP_WIDTH = 1440
    const val BACKDROP_HEIGHT = 810

    fun card(view: ImageView, url: String?) {
        load(view, url, CARD_WIDTH, CARD_HEIGHT, R.drawable.glass_card_bg)
    }

    fun poster(view: ImageView, url: String?) {
        load(view, url, POSTER_WIDTH, POSTER_HEIGHT, R.drawable.glass_card_bg)
    }

    fun backdrop(view: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.setImageDrawable(null)
            return
        }
        Glide.with(view)
            .load(url)
            .override(BACKDROP_WIDTH, BACKDROP_HEIGHT)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .dontAnimate()
            .into(view)
    }

    /**
     * Preloads a horizontal shelf card image (`640x400`) into Glide's cache
     * ahead of D-pad scrolling (T-804).
     */
    fun preloadCard(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            Glide.with(context.applicationContext)
                .load(url)
                .override(CARD_WIDTH, CARD_HEIGHT)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .preload(CARD_WIDTH, CARD_HEIGHT)
        } catch (e: Exception) {
            AppLog.logSwallowed("MediaImageLoader", "preloading card", e)
        }
    }

    /**
     * Pure policy selecting non-blank neighbor URLs within `[focusedIndex - radius .. focusedIndex + radius]`
     * (excluding `focusedIndex` itself, which is already bound) for Glide prefetching (T-804).
     */
    fun selectNeighborPreloadUrls(
        urls: List<String?>,
        focusedIndex: Int,
        radius: Int = 2
    ): List<String> {
        if (urls.isEmpty() || focusedIndex !in urls.indices || radius <= 0) return emptyList()
        val start = (focusedIndex - radius).coerceAtLeast(0)
        val end = (focusedIndex + radius).coerceAtMost(urls.lastIndex)
        return (start..end)
            .filter { it != focusedIndex }
            .mapNotNull { index -> urls[index]?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
    }

    fun preloadPoster(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            Glide.with(context.applicationContext)
                .load(url)
                .override(POSTER_WIDTH, POSTER_HEIGHT)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .preload(POSTER_WIDTH, POSTER_HEIGHT)
        } catch (e: Exception) {
            AppLog.logSwallowed("MediaImageLoader", "preloading poster", e)
        }
    }

    fun preloadBackdrop(context: Context, url: String?) {
        if (url.isNullOrBlank()) return
        try {
            Glide.with(context.applicationContext)
                .load(url)
                .override(BACKDROP_WIDTH, BACKDROP_HEIGHT)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .preload(BACKDROP_WIDTH, BACKDROP_HEIGHT)
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
