package zechs.drive.stream.utils.util

class Constants {
    companion object {
        const val GITHUB_API = "https://api.github.com/"
        const val GOOGLE_API = "https://www.googleapis.com"
        const val DRIVE_API = "${GOOGLE_API}/drive/v3"
        const val GOOGLE_ACCOUNTS_URL = "https://accounts.google.com"
        const val GUIDE_TO_MAKE_DRIVE_CLIENT = "https://rclone.org/drive/#making-your-own-client-id"

        // SECURITY: previously this file shipped a real, personal Google OAuth
        // client secret AND a live refresh token with full Drive scope, hardcoded
        // in source. That means anyone with the APK or this repo could pull the
        // owner's Drive account credentials out of it. Never hardcode real secrets
        // here - if you are the person who had a real value in these fields
        // before, revoke it immediately: regenerate the OAuth client secret in
        // Google Cloud Console AND revoke the app's access at
        // https://myaccount.google.com/permissions before doing anything else.
        //
        // These are now empty placeholders. The app must not silently fall back
        // to a bundled identity - the user configures their own client on the
        // "Configure your drive client" screen (see SignInFragment), which is
        // exactly what the original DriveStream project intends.
        val DEFAULT_CLIENT_ID = zechs.drive.stream.BuildConfig.DRIVE_CLIENT_ID
        val DEFAULT_CLIENT_SECRET = zechs.drive.stream.BuildConfig.DRIVE_CLIENT_SECRET
        const val DEFAULT_REDIRECT_URI = "http://127.0.0.1:53682/"
        const val DEFAULT_DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        val DEFAULT_REFRESH_TOKEN = zechs.drive.stream.BuildConfig.DRIVE_REFRESH_TOKEN

        val DEFAULT_CLIENT = zechs.drive.stream.data.model.DriveClient(
            clientId = DEFAULT_CLIENT_ID,
            clientSecret = DEFAULT_CLIENT_SECRET,
            redirectUri = DEFAULT_REDIRECT_URI,
            scopes = listOf(DEFAULT_DRIVE_SCOPE)
        )

        // MyAnimeList (MAL) API v2 endpoints. Credentials are supplied by the
        // user and stored in the encrypted session store; never ship them here.
        val MAL_CLIENT_ID = zechs.drive.stream.BuildConfig.MAL_CLIENT_ID
        val MAL_CLIENT_SECRET = zechs.drive.stream.BuildConfig.MAL_CLIENT_SECRET
        const val MAL_API_BASE_URL = "https://api.myanimelist.net/"
        const val MAL_OAUTH_BASE_URL = "https://myanimelist.net/"
        const val MAL_REDIRECT_URI = "http://127.0.0.1:1420/auth/callback"
        const val MAL_REDIRECT_PORT = 1420

        // AniSkip API
        const val ANISKIP_API_BASE_URL = "https://api.aniskip.com/"
    }
}
