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

    fun defaultAvatarForProfile(profileId: String): ProfileArt? =
        defaultAvatarForProfile(profileId, getPresetAvatars())

    companion object {
        internal fun defaultAvatarForProfile(profileId: String, avatars: List<ProfileArt>): ProfileArt? {
            if (avatars.isEmpty()) return null
            val index = Math.floorMod(profileId.hashCode(), avatars.size)
            return avatars[index]
        }
    }

    /** Curated catalog of popular high quality anime avatar presets (served via CDN) */
    fun getPresetAvatars(): List<ProfileArt> = listOf(
        ProfileArt("Luffy (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b40-MNypXsxSRb1R.png", false),
        ProfileArt("Zoro (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b62-S7oAeA9WInjV.png", false),
        ProfileArt("Gojo Satoru (Jujutsu Kaisen)", "https://s4.anilist.co/file/anilistcdn/character/large/b127691-9zqh1xpIubn7.png", false),
        ProfileArt("Yuji Itadori (Jujutsu Kaisen)", "https://s4.anilist.co/file/anilistcdn/character/large/b127212-FVm2tD0erQ5B.png", false),
        ProfileArt("Frieren (Sousou no Frieren)", "https://s4.anilist.co/file/anilistcdn/character/large/b176754-PCnpqIOkjhFk.png", false),
        ProfileArt("Fern (Sousou no Frieren)", "https://s4.anilist.co/file/anilistcdn/character/large/b183965-uGFohBjlFoTp.png", false),
        ProfileArt("Tanjiro (Demon Slayer)", "https://s4.anilist.co/file/anilistcdn/character/large/b126071-BTNEc1nRIv68.png", false),
        ProfileArt("Nezuko (Demon Slayer)", "https://s4.anilist.co/file/anilistcdn/character/large/b127518-NRlq1CQ1v1ro.png", false),
        ProfileArt("Naruto (Shippuden)", "https://s4.anilist.co/file/anilistcdn/character/large/b17-phjcWCkRuIhu.png", false),
        ProfileArt("Sasuke (Shippuden)", "https://s4.anilist.co/file/anilistcdn/character/large/b13-SISLEw1oAD7a.png", false),
        ProfileArt("Itachi (Naruto)", "https://s4.anilist.co/file/anilistcdn/character/large/b14-9Kb1E5oel1ke.png", false),
        ProfileArt("Eren Yeager (Attack on Titan)", "https://s4.anilist.co/file/anilistcdn/character/large/b40882-dsj7IP943WFF.jpg", false),
        ProfileArt("Mikasa Ackerman (Attack on Titan)", "https://s4.anilist.co/file/anilistcdn/character/large/b40881-F3gr1PkreDvj.png", false),
        ProfileArt("Anya Forger (Spy x Family)", "https://s4.anilist.co/file/anilistcdn/character/large/b138100-4Li0tWRCa5bQ.png", false),
        ProfileArt("Yor Forger (Spy x Family)", "https://s4.anilist.co/file/anilistcdn/character/large/b138102-ZOAu9jI2d5ke.png", false),
        ProfileArt("Megumin (KonoSuba)", "https://s4.anilist.co/file/anilistcdn/character/large/b89361-tq8PQQ4MmF0M.png", false),
        ProfileArt("Saitama (One Punch Man)", "https://s4.anilist.co/file/anilistcdn/character/large/b73935-ON5d0mAcrItd.jpg", false),
        ProfileArt("Killua Zoldyck (Hunter x Hunter)", "https://s4.anilist.co/file/anilistcdn/character/large/b27-Z5O02kQUydpT.jpg", false),
        ProfileArt("Son Goku (Dragon Ball)", "https://s4.anilist.co/file/anilistcdn/character/large/246-wsRRr6z1kii8.png", false),
        ProfileArt("Vegeta (Dragon Ball)", "https://s4.anilist.co/file/anilistcdn/character/large/b913-NIFkKazWM8VO.png", false)
    )

    /** Curated catalog of high quality anime wallpapers/banners (served via CDN) */
    fun getPresetWallpapers(): List<ProfileArt> = listOf(
        ProfileArt("Wallpaper · Frieren Beyond Journey's End", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/154587-ivXNJ23SM1xB.jpg", true),
        ProfileArt("Wallpaper · Jujutsu Kaisen", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/113415-jQBSkxWAAk83.jpg", true),
        ProfileArt("Wallpaper · Demon Slayer", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/101922-33MtJGsUSxga.jpg", true),
        ProfileArt("Wallpaper · One Piece", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/21-wf37VakJmZqs.jpg", true),
        ProfileArt("Wallpaper · Attack on Titan", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/16498-8jpFCOcDmneX.jpg", true),
        ProfileArt("Wallpaper · Cyberpunk Edgerunners", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/120377-c15oLS8CA31s.jpg", true),
        ProfileArt("Wallpaper · Bleach", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/269-08ar2HJOUAuL.jpg", true),
        ProfileArt("Wallpaper · Solo Leveling", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/151807-37yfQA3ym8PA.jpg", true),
        ProfileArt("Wallpaper · Spy x Family", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/140960-Z7xSvkRxHKfj.jpg", true),
        ProfileArt("Wallpaper · KonoSuba God's Blessing", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/21202-UWijdV7RMnXo.jpg", true)
    )

    /** Public AniList character portraits and anime banners. Cached results remain available offline. */
    suspend fun search(anime: String): List<ProfileArt> = withContext(Dispatchers.IO) {
        val key = anime.trim().lowercase(java.util.Locale.ROOT)
        val cached = cache.getString(key, null)
        if (cached != null && System.currentTimeMillis() - cache.getLong("${key}:time", 0L) < 86_400_000L) {
            return@withContext parse(cached)
        }
        val query = """query (${'$'}search: String) {
            Page(page: 1, perPage: 8) {
                media(search: ${'$'}search, type: ANIME, isAdult: false) {
                    id
                    title { romaji english }
                    bannerImage
                    characters(perPage: 6) {
                        nodes {
                            name { full }
                            image { large }
                        }
                    }
                }
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
        val data = JSONObject(raw).optJSONObject("data") ?: return emptyList()
        val mediaList = mutableListOf<JSONObject>()
        val pageMedia = data.optJSONObject("Page")?.optJSONArray("media")
        if (pageMedia != null) {
            for (i in 0 until pageMedia.length()) {
                pageMedia.optJSONObject(i)?.let { mediaList.add(it) }
            }
        } else {
            data.optJSONObject("Media")?.let { mediaList.add(it) }
        }

        if (mediaList.isEmpty()) return emptyList()

        val avatars = mutableListOf<ProfileArt>()
        val wallpapers = mutableListOf<ProfileArt>()
        val seenUrls = mutableSetOf<String>()

        for (media in mediaList) {
            val titleObj = media.optJSONObject("title")
            val animeTitle = titleObj?.optString("romaji")
                ?.takeIf { it.isNotBlank() }
                ?: titleObj?.optString("english")
                ?: "Anime"

            // 1. Wallpaper banner
            val banner = media.optString("bannerImage")
            if (banner.startsWith("https://") && seenUrls.add(banner)) {
                wallpapers.add(ProfileArt("Wallpaper · $animeTitle", banner, true))
            }

            // 2. Character portraits
            val characters = media.optJSONObject("characters")?.optJSONArray("nodes")
            if (characters != null) {
                for (j in 0 until characters.length()) {
                    val character = characters.optJSONObject(j) ?: continue
                    val url = character.optJSONObject("image")?.optString("large").orEmpty()
                    val charName = character.optJSONObject("name")?.optString("full") ?: "Personagem"
                    if (url.startsWith("https://") && seenUrls.add(url)) {
                        avatars.add(ProfileArt("$charName ($animeTitle)", url, false))
                    }
                }
            }
        }

        return avatars + wallpapers
    }
}
