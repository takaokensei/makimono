package zechs.drive.stream.ui.player

/**
 * Pure policy governing subtitle clearance above the bottom player HUD capsule (T4.6 / Q03.3).
 * Ensures zero overlap between subtitles and player controls across ExoPlayer and MPV.
 */
object SubtitleElevationPolicy {

    data class ElevationConfig(
        val exoPaddingFraction: Float,
        val mpvMarginY: Int
    )

    fun resolve(osdVisible: Boolean): ElevationConfig {
        return if (osdVisible) {
            ElevationConfig(
                exoPaddingFraction = 0.22f,
                mpvMarginY = 140
            )
        } else {
            ElevationConfig(
                exoPaddingFraction = 0.08f,
                mpvMarginY = 30
            )
        }
    }
}
