package zechs.drive.stream.ui.home

import android.content.Context
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.utils.ext.resolveThemeColor

/** Shared visual binding for the production Home hero and its local QA fixture. */
internal class FeaturedHeroBinder(
    private val binding: FragmentHomeBinding,
    private val context: Context
) {
    private var displayedFeaturedId: String? = null
    private var displayedBackdropUrl: String? = null
    private var activeBackdropIsBase = true

    fun bind(featured: HomeViewModel.FeaturedAnime, reduceMotionEnabled: Boolean) {
        val isDifferentAnime = displayedFeaturedId != null && displayedFeaturedId != featured.folderId
        displayedFeaturedId = featured.folderId

        val textContent = binding.layoutHeroTextContent
        if (isDifferentAnime && !reduceMotionEnabled && textContent != null) {
            textContent.animate()?.cancel()
            textContent.animate()
                .alpha(0f)
                .translationY(-6f)
                .setDuration(context.resources.getInteger(R.integer.motion_osd_hide).toLong())
                .withEndAction {
                    if (displayedFeaturedId != featured.folderId) return@withEndAction
                    bindTextContent(featured)
                    textContent.translationY = 6f
                    textContent.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(context.resources.getInteger(R.integer.motion_card).toLong())
                        .start()
                }
                .start()
        } else {
            binding.layoutHeroTextContent?.animate()?.cancel()
            binding.layoutHeroTextContent?.alpha = 1f
            binding.layoutHeroTextContent?.translationY = 0f
            bindTextContent(featured)
        }

        val imageUrl = featured.backdropUrl ?: featured.posterUrl
        val current = if (activeBackdropIsBase) binding.ivFeaturedBackdrop else binding.ivFeaturedBackdropNext
        val incoming = if (activeBackdropIsBase) binding.ivFeaturedBackdropNext else binding.ivFeaturedBackdrop
        if (current != null && displayedBackdropUrl != imageUrl) {
            displayedBackdropUrl = imageUrl
            incoming?.animate()?.cancel()
            incoming?.alpha = 0f
            if (imageUrl.isNullOrBlank()) {
                Glide.with(current).clear(current)
                incoming?.let { Glide.with(it).clear(it) }
                current.setImageResource(R.drawable.kodi_primary_bg)
                current.alpha = 1f
            } else {
                val animate = incoming != null && !reduceMotionEnabled && current.drawable != null
                val imageTarget = if (animate) incoming!! else current
                Glide.with(imageTarget).load(imageUrl).override(1440, 810).centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?, model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean): Boolean = true
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean): Boolean {
                            if (displayedBackdropUrl != imageUrl) return true
                            imageTarget.setImageDrawable(resource)
                            if (animate) {
                                imageTarget.alpha = 0f
                                imageTarget.animate().alpha(1f)
                                    .setDuration(context.resources.getInteger(R.integer.motion_hero_crossfade).toLong())
                                    .withEndAction {
                                        if (displayedBackdropUrl == imageUrl) {
                                            current.alpha = 0f
                                            activeBackdropIsBase = !activeBackdropIsBase
                                        }
                                    }.start()
                            } else imageTarget.alpha = 1f
                            return true
                        }
                    }).into(imageTarget)
            }
        }
    }

    fun updatePaginationPills(
        count: Int,
        activeIndex: Int,
        onPillSelected: (Int) -> Unit
    ) {
        val container = binding.layoutHeroPaginationPills ?: return
        if (count <= 1) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val density = context.resources.displayMetrics.density
        val accentColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
        val inactiveColor = androidx.core.content.ContextCompat.getColor(context, R.color.obsidian_border_glass)

        for (i in 0 until count) {
            val isActive = i == activeIndex
            val pill = View(context).apply {
                val pillWidth = if (isActive) (28 * density).toInt() else (10 * density).toInt()
                val pillHeight = (5 * density).toInt()
                val lp = android.widget.LinearLayout.LayoutParams(pillWidth, pillHeight).apply {
                    marginEnd = (6 * density).toInt()
                }
                layoutParams = lp

                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 3 * density
                    setColor(if (isActive) accentColor else inactiveColor)
                }
                background = drawable
                isFocusable = true
                isFocusableInTouchMode = true
                contentDescription = "Destaque ${i + 1} de $count"

                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        drawable.setColor(accentColor)
                        animate().scaleY(1.4f).setDuration(context.resources.getInteger(R.integer.motion_focus).toLong()).start()
                        onPillSelected(i)
                    } else {
                        animate().scaleY(1.0f).setDuration(context.resources.getInteger(R.integer.motion_focus).toLong()).start()
                        drawable.setColor(if (i == activeIndex) accentColor else inactiveColor)
                    }
                }
                setOnClickListener {
                    onPillSelected(i)
                }
            }
            container.addView(pill)
        }
    }

    private fun bindTextContent(featured: HomeViewModel.FeaturedAnime) {
        val clearLogo = featured.clearLogoUrl
        binding.tvFeaturedTitle?.visibility = View.VISIBLE
        binding.ivFeaturedClearLogo?.let { logo ->
            logo.visibility = View.GONE
            if (clearLogo.isNullOrBlank()) Glide.with(logo).clear(logo)
            else Glide.with(logo).load(clearLogo).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?, model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean): Boolean = false
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean): Boolean {
                        if (displayedFeaturedId != featured.folderId) return true
                        logo.visibility = View.VISIBLE
                        binding.tvFeaturedTitle?.visibility = View.GONE
                        return false
                    }
                }).into(logo)
        }
        binding.tvFeaturedTitle?.text = featured.title
        val isTenFoot = zechs.drive.stream.utils.DeviceUi.isTenFootExperience(context)
        binding.tvFeaturedOverline?.let { overline ->
            if (isTenFoot) {
                overline.visibility = View.GONE
            } else {
                overline.visibility = View.VISIBLE
                overline.setText(R.string.home_featured_overline)
                overline.setTextColor(context.resolveThemeColor(R.attr.colorAccentPrimary))
            }
        }
        val genreContainer = binding.layoutFeaturedGenres
        val localizedGenres = if (androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language == "pt")
            featured.genres.map(AnimeGenrePolicy::normalize) else featured.genres
        val topGenres = localizedGenres.take(4)
        if (genreContainer != null && topGenres.isNotEmpty() && !isTenFoot) {
            genreContainer.removeAllViews()
            genreContainer.visibility = View.VISIBLE
            val density = context.resources.displayMetrics.density
            for (genreName in topGenres) {
                val tv = android.widget.TextView(context).apply {
                    text = genreName
                    androidx.core.widget.TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Makimono_TV_Badge)
                    setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.obsidian_text_secondary))
                    setBackgroundResource(R.drawable.tag_genre_pill_bg)
                    setPadding(
                        (8 * density).toInt(),
                        (3 * density).toInt(),
                        (8 * density).toInt(),
                        (3 * density).toInt()
                    )
                    val lp = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = (6 * density).toInt()
                    }
                    layoutParams = lp
                }
                genreContainer.addView(tv)
            }
        } else {
            genreContainer?.visibility = View.GONE
        }

        val separator = context.getString(R.string.home_featured_separator)
        val badgeLabel = featured.ageRating?.takeIf { it.isNotBlank() }
        val metadataItems = buildList {
            featured.year?.let { add(it.toString()) }
            featured.episodeCount?.let {
                add(context.resources.getQuantityString(R.plurals.home_featured_episode_count, it, it))
            }
            featured.score?.let {
                add(context.getString(R.string.home_featured_score, it))
            }
            if ((isTenFoot || topGenres.isEmpty()) && featured.genres.isNotEmpty()) {
                add(localizedGenres.take(3).joinToString(", "))
            }
        }.joinToString(separator)
        binding.tvFeaturedMetadata?.apply {
            if (badgeLabel == null && metadataItems.isBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = buildMetadataSpannable(context, badgeLabel, separator, metadataItems)
            }
        }

        val cleanSynopsis = featured.synopsis
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
        binding.tvFeaturedSynopsis?.apply {
            if (!cleanSynopsis.isNullOrBlank()) {
                text = cleanSynopsis
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        binding.tvFeaturedPlayLabel?.setText(
            if (featured.isFocusedPreview) R.string.home_featured_open
            else R.string.home_featured_play
        )
    }

    private fun buildMetadataSpannable(
        context: Context,
        badgeLabel: String?,
        separator: String,
        metadataItems: String
    ): CharSequence {
        if (badgeLabel == null) return metadataItems
        val fullText = if (metadataItems.isNotBlank()) {
            "$badgeLabel$separator$metadataItems"
        } else {
            badgeLabel
        }
        val spannable = android.text.SpannableString(fullText)
        val density = context.resources.displayMetrics.density
        val bgColor = context.resolveThemeColor(R.attr.colorAccentPrimary)
        val fgColor = context.resolveThemeColor(R.attr.colorOnPrimary)
        spannable.setSpan(
            AgeBadgeSpan(bgColor = bgColor, fgColor = fgColor, density = density),
            0,
            badgeLabel.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private class AgeBadgeSpan(
        private val bgColor: Int,
        private val fgColor: Int,
        private val density: Float
    ) : android.text.style.ReplacementSpan() {
        private val padH = 5f * density
        private val cornerRadius = 2f * density
        private val rect = android.graphics.RectF()

        override fun getSize(
            paint: android.graphics.Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: android.graphics.Paint.FontMetricsInt?
        ): Int {
            return (paint.measureText(text, start, end) + padH * 2).toInt()
        }

        override fun draw(
            canvas: android.graphics.Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: android.graphics.Paint
        ) {
            val width = paint.measureText(text, start, end) + padH * 2
            val fm = paint.fontMetrics
            val boxTop = y + fm.ascent - 1f * density
            val boxBottom = y + fm.descent + 1f * density
            rect.set(x, boxTop, x + width, boxBottom)
            val prevColor = paint.color
            val prevFakeBold = paint.isFakeBoldText
            paint.color = bgColor
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            paint.color = fgColor
            paint.isFakeBoldText = true
            canvas.drawText(text, start, end, x + padH, y.toFloat(), paint)
            paint.color = prevColor
            paint.isFakeBoldText = prevFakeBold
        }
    }
}
