package zechs.drive.stream.utils.util

/** Public API endpoints and MyAnimeList OAuth configuration. Drive OAuth is handled by the pairing service. */
class Constants {
    companion object {
        const val GITHUB_API = "https://api.github.com/"
        const val GOOGLE_API = "https://www.googleapis.com"
        const val DRIVE_API = "${GOOGLE_API}/drive/v3"

        val MAL_CLIENT_ID = zechs.drive.stream.BuildConfig.MAL_CLIENT_ID
        val MAL_CLIENT_SECRET = zechs.drive.stream.BuildConfig.MAL_CLIENT_SECRET
        const val MAL_API_BASE_URL = "https://api.myanimelist.net/"
        const val MAL_OAUTH_BASE_URL = "https://myanimelist.net/"
        const val MAL_REDIRECT_URI = "http://127.0.0.1:1420/auth/callback"
        const val MAL_REDIRECT_PORT = 1420

        const val ANISKIP_API_BASE_URL = "https://api.aniskip.com/"
    }
}
