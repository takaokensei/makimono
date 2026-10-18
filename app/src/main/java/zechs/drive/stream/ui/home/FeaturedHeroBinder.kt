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

    fun bind(featured: HomeViewModel.FeaturedAnime, reduceMotionEnabled: Boolean) {
        val isDifferentAnime = displayedFeaturedId != null && displayedFeaturedId != featured.folderId
        displayedFeaturedId = featured.folderId

        if (isDifferentAnime && !reduceMotionEnabled && binding.layoutHeroTextContent != null) {
            binding.layoutHeroTextContent?.animate()?.cancel()
            binding.layoutHeroTextContent?.animate()
                ?.alpha(0f)
                ?.translationY(-6f)
                ?.setDuration(140L)
                ?.withEndAction {
                    bindTextContent(featured)
                    binding.layoutHeroTextContent?.translationY = 6f
                    binding.layoutHeroTextContent?.animate()
                        ?.alpha(1f)
                        ?.translationY(0f)
                        ?.setDuration(240L)
                        ?.start()
                }
                ?.start()
        } else {
            binding.layoutHeroTextContent?.animate()?.cancel()
            binding.layoutHeroTextContent?.alpha = 1f
            binding.layoutHeroTextContent?.translationY = 0f
            bindTextContent(featured)
        }

        val imageUrl = featured.backdropUrl ?: featured.posterUrl
        val baseImageView = binding.ivFeaturedBackdrop
        val nextImageView = binding.ivFeaturedBackdropNext
        if (baseImageView != null) {
            if (imageUrl.isNullOrBlank()) {
                displayedBackdropUrl = null
                Glide.with(baseImageView).clear(baseImageView)
                baseImageView.setImageResource(R.drawable.kodi_primary_bg)
                nextImageView?.alpha = 0f
            } else if (displayedBackdropUrl != imageUrl) {
                displayedBackdropUrl = imageUrl
                if (nextImageView != null && !reduceMotionEnabled && baseImageView.drawable != null) {
                    Glide.with(nextImageView)
                        .load(imageUrl)
                        .override(1440, 810)
                        .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: com.bumptech.glide.load.engine.GlideException?,
                                model: Any?,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                                isFirstResource: Boolean
                            ): Boolean = false

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable,
                                model: Any,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                dataSource: com.bumptech.glide.load.DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                nextImageView.animate().cancel()
                                nextImageView.alpha = 0f
                                nextImageView.setImageDrawable(resource)
                                nextImageView.animate()
                                    .alpha(1f)
                                    .setDuration(400L)
                                    .withEndAction {
                                        baseImageView.setImageDrawable(resource)
                                        nextImageView.alpha = 0f
                                    }
                                    .start()
                                return true
                            }
                        })
                        .into(nextImageView)
                } else {
                    Glide.with(baseImageView)
                        .load(imageUrl)
                        .override(1440, 810)
                        .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .placeholder(R.drawable.kodi_primary_bg)
                        .error(R.drawable.kodi_primary_bg)
                        .into(baseImageView)
                    nextImageView?.alpha = 0f
                }
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
                        animate().scaleY(1.4f).setDuration(120L).start()
                        onPillSelected(i)
                    } else {
                        animate().scaleY(1.0f).setDuration(120L).start()
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
        if (!clearLogo.isNullOrBlank()) {
            binding.ivFeaturedClearLogo?.let { logoView ->
                logoView.visibility = View.VISIBLE
                binding.tvFeaturedTitle?.visibility = View.GONE
                Glide.with(logoView)
                    .load(clearLogo)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(logoView)
            }
        } else {
            binding.ivFeaturedClearLogo?.visibility = View.GONE
            binding.tvFeaturedTitle?.visibility = View.VISIBLE
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
        binding.layoutFeaturedGenres?.visibility = View.GONE

        val separator = context.getString(R.string.home_featured_separator)
        val badgeLabel = featured.ageRating?.takeIf { it.isNotBlank() } ?: if (isTenFoot) "A14" else null
        val metadataItems = buildList {
            if (isTenFoot) add("Legendado")
            featured.year?.let { add(it.toString()) }
            featured.episodeCount?.let {
                add(context.resources.getQuantityString(R.plurals.home_featured_episode_count, it, it))
            }
            featured.score?.let {
                add(context.getString(R.string.home_featured_score, it))
            }
            if (featured.genres.isNotEmpty()) {
                add(featured.genres.take(3).joinToString(", "))
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
