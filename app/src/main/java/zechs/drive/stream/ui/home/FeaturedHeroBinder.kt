package zechs.drive.stream.ui.home

import android.content.Context
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentHomeBinding

/** Shared visual binding for the production Home hero and its local QA fixture. */
internal class FeaturedHeroBinder(
    private val binding: FragmentHomeBinding,
    private val context: Context
) {
    private var displayedBackdropUrl: String? = null

    fun bind(featured: HomeViewModel.FeaturedAnime, reduceMotionEnabled: Boolean) {
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
        binding.tvFeaturedOverline?.setText(R.string.home_featured_overline)
        if (featured.genres.isNotEmpty()) {
            binding.layoutFeaturedGenres?.let { container ->
                container.removeAllViews()
                container.visibility = View.VISIBLE
                val density = context.resources.displayMetrics.density
                featured.genres.take(3).forEach { genreName ->
                    val pill = android.widget.TextView(context).apply {
                        text = genreName
                        setTextAppearance(R.style.TextAppearance_Makimono_TV_Caption)
                        setBackgroundResource(R.drawable.glass_meta_pill_bg)
                        setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.layout_literal_cbd5e1))
                        val padH = (8 * density).toInt()
                        val padV = (2 * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        val lp = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = (6 * density).toInt()
                        }
                        layoutParams = lp
                    }
                    container.addView(pill)
                }
            }
        } else {
            binding.layoutFeaturedGenres?.visibility = View.GONE
        }

        val separator = context.getString(R.string.home_featured_separator)
        val metadata = buildList {
            featured.year?.let { add(it.toString()) }
            featured.episodeCount?.let {
                add(context.getString(R.string.home_featured_episode_count, it))
            }
            featured.score?.let {
                add(context.getString(R.string.home_featured_score, it))
            }
            featured.ageRating?.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString(separator)
        binding.tvFeaturedMetadata?.apply {
            text = metadata
            visibility = if (metadata.isBlank()) View.GONE else View.VISIBLE
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

        val imageUrl = featured.backdropUrl ?: featured.posterUrl
        binding.ivFeaturedBackdrop?.let { imageView ->
            if (imageUrl.isNullOrBlank()) {
                displayedBackdropUrl = null
                Glide.with(imageView).clear(imageView)
                imageView.setImageResource(R.drawable.kodi_primary_bg)
            } else if (displayedBackdropUrl != imageUrl) {
                displayedBackdropUrl = imageUrl
                val request = Glide.with(imageView)
                    .load(imageUrl)
                    .override(1440, 810)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.kodi_primary_bg)
                    .error(R.drawable.kodi_primary_bg)
                if (reduceMotionEnabled) {
                    request.dontAnimate()
                } else {
                    request.transition(DrawableTransitionOptions.withCrossFade(400))
                }
                request.into(imageView)
            }
        }

        binding.tvFeaturedPlayLabel?.setText(
            if (featured.isFocusedPreview) R.string.home_featured_open
            else R.string.home_featured_play
        )
    }
}
