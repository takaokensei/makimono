package zechs.drive.stream.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ProfileArt(val label: String, val url: String, val isWallpaper: Boolean)

@Singleton
class ProfileArtCatalog @Inject constructor(@ApplicationContext context: Context) {
    private val cache = context.getSharedPreferences("profile_art_catalog", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()

    /** Public AniList character portraits and anime banners. Cached results remain available offline. */
    suspend fun search(anime: String): List<ProfileArt> = withContext(Dispatchers.IO) {
        val key = anime.trim().lowercase(java.util.Locale.ROOT)
        val cached = cache.getString(key, null)
        if (cached != null && System.currentTimeMillis() - cache.getLong("${key}:time", 0L) < 86_400_000L) {
            return@withContext parse(cached)
        }
        val query = """query (${'$'}search: String) {
            Media(search: ${'$'}search, type: ANIME, isAdult: false) {
                title { romaji } bannerImage
                characters(perPage: 12) { nodes { name { full } image { large } } }
            }
        }"""
        try {
            val body = JSONObject().put("query", query).put("variables", JSONObject().put("search", anime))
            client.newCall(Request.Builder().url("https://graphql.anilist.co")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { response ->
                check(response.isSuccessful)
                val raw = response.body?.string() ?: error("Empty catalog")
                val items = parse(raw)
                if (items.isNotEmpty()) cache.edit().putString(key, raw)
                    .putLong("${key}:time", System.currentTimeMillis()).apply()
                items
            }
        } catch (e: Exception) {
            if (cached != null) parse(cached) else throw e
        }
    }

    internal fun parse(raw: String): List<ProfileArt> {
        val media = JSONObject(raw).optJSONObject("data")?.optJSONObject("Media") ?: return emptyList()
        return buildList {
            val characters = media.optJSONObject("characters")?.optJSONArray("nodes")
            for (i in 0 until (characters?.length() ?: 0)) {
                val character = characters?.optJSONObject(i) ?: continue
                val url = character.optJSONObject("image")?.optString("large").orEmpty()
                if (url.startsWith("https://")) add(ProfileArt(character.optJSONObject("name")?.optString("full") ?: "Personagem", url, false))
            }
            val banner = media.optString("bannerImage")
            if (banner.startsWith("https://")) add(ProfileArt("Wallpaper · ${media.optJSONObject("title")?.optString("romaji") ?: "Anime"}", banner, true))
        }
    }
}
