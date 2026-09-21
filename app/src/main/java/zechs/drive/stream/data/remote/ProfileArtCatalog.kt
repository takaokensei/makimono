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

    /** Curated catalog of popular high quality anime avatar presets (served via CDN) */
    fun getPresetAvatars(): List<ProfileArt> = listOf(
        ProfileArt("Luffy (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b40-Xhk11t45S5xO.png", false),
        ProfileArt("Zoro (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b62-b91cO7sM84e8.png", false),
        ProfileArt("Gojo Satoru (Jujutsu Kaisen)", "https://s4.anilist.co/file/anilistcdn/character/large/b127969-eJcfK51W6v9w.png", false),
        ProfileArt("Sukuna (Jujutsu Kaisen)", "https://s4.anilist.co/file/anilistcdn/character/large/b135088-75pI4K9T1Q3j.png", false),
        ProfileArt("Frieren (Sousou no Frieren)", "https://s4.anilist.co/file/anilistcdn/character/large/b176274-pUe798eN2sPn.png", false),
        ProfileArt("Fern (Sousou no Frieren)", "https://s4.anilist.co/file/anilistcdn/character/large/b187588-e9XbO1Msqg1q.png", false),
        ProfileArt("Tanjiro (Demon Slayer)", "https://s4.anilist.co/file/anilistcdn/character/large/b126071-R5bVf0vYFj7j.png", false),
        ProfileArt("Nezuko (Demon Slayer)", "https://s4.anilist.co/file/anilistcdn/character/large/b126072-aUeS861cT4mQ.png", false),
        ProfileArt("Naruto (Shippuden)", "https://s4.anilist.co/file/anilistcdn/character/large/b17-7TmSNz5RIkW2.png", false),
        ProfileArt("Sasuke (Shippuden)", "https://s4.anilist.co/file/anilistcdn/character/large/b13-4qgT0vYv1w4P.png", false),
        ProfileArt("Itachi (Naruto)", "https://s4.anilist.co/file/anilistcdn/character/large/b14-g0t6P0uI08V9.png", false),
        ProfileArt("Eren Yeager (Attack on Titan)", "https://s4.anilist.co/file/anilistcdn/character/large/b40882-sQvQcsqG4vWd.png", false),
        ProfileArt("Levi Ackerman (Attack on Titan)", "https://s4.anilist.co/file/anilistcdn/character/large/b45627-pI9KSmL2h65d.png", false),
        ProfileArt("Anya Forger (Spy x Family)", "https://s4.anilist.co/file/anilistcdn/character/large/b149495-2w6zV4GZ818Q.png", false),
        ProfileArt("Yor Forger (Spy x Family)", "https://s4.anilist.co/file/anilistcdn/character/large/b149496-c6rE8u9h1bXq.png", false),
        ProfileArt("Megumin (KonoSuba)", "https://s4.anilist.co/file/anilistcdn/character/large/b89361-B9i1k8y8v8bK.png", false),
        ProfileArt("Saitama (One Punch Man)", "https://s4.anilist.co/file/anilistcdn/character/large/b73935-7g25Xp6g2P2j.png", false),
        ProfileArt("Killua Zoldyck (Hunter x Hunter)", "https://s4.anilist.co/file/anilistcdn/character/large/b27-0NvdT4pG8hWq.png", false),
        ProfileArt("Son Goku (Dragon Ball)", "https://s4.anilist.co/file/anilistcdn/character/large/b246-v7u0y7r6a6bE.png", false),
        ProfileArt("Vegeta (Dragon Ball)", "https://s4.anilist.co/file/anilistcdn/character/large/b813-f6E0T0mE5zU2.png", false)
    )

    /** Curated catalog of high quality anime wallpapers/banners (served via CDN) */
    fun getPresetWallpapers(): List<ProfileArt> = listOf(
        ProfileArt("Wallpaper · Frieren Beyond Journey's End", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/154587-n1HJZokEbgv0.jpg", true),
        ProfileArt("Wallpaper · Jujutsu Kaisen Shibuya", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/145064-07d08XbvG1o0.jpg", true),
        ProfileArt("Wallpaper · Demon Slayer Entertainment District", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/142329-aYv6wYfA4r1Z.jpg", true),
        ProfileArt("Wallpaper · One Piece Wano", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/21-4k2Hqg8vL1kF.jpg", true),
        ProfileArt("Wallpaper · Attack on Titan The Final Season", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/110277-2Z4oW6oOqgYj.jpg", true),
        ProfileArt("Wallpaper · Cyberpunk Edgerunners", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/120377-gVfN8g4Y8a2E.jpg", true),
        ProfileArt("Wallpaper · Bleach Thousand-Year Blood War", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/114446-c2Y0P8i6Wq5Z.jpg", true),
        ProfileArt("Wallpaper · Solo Leveling", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/151807-g4wGfG3X5a4d.jpg", true),
        ProfileArt("Wallpaper · Spy x Family", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/140960-w5eM3B2R6X7A.jpg", true),
        ProfileArt("Wallpaper · KonoSuba God's Blessing", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/21202-k1K4qJ6p2W1A.jpg", true)
    )

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
