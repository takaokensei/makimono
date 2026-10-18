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
class ProfileArtCatalog @Inject constructor(
    @ApplicationContext context: Context,
    private val folderMetadataRepository: dagger.Lazy<zechs.drive.stream.data.repository.FolderMetadataRepository>? = null
) {
    private val cache = context.getSharedPreferences("profile_art_catalog", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()

    @Volatile
    private var memoryCachedSections: Pair<List<Pair<String, List<ProfileArt>>>, List<Pair<String, List<ProfileArt>>>>? = null

    fun clearCache() {
        memoryCachedSections = null
    }

    suspend fun getLibraryArtSections(): Pair<List<Pair<String, List<ProfileArt>>>, List<Pair<String, List<ProfileArt>>>> = withContext(Dispatchers.IO) {
        memoryCachedSections?.let { return@withContext it }
        val repo = folderMetadataRepository?.get() ?: return@withContext Pair(emptyList(), emptyList())
        val allMeta = try {
            repo.getAllMetadata()
        } catch (_: Exception) {
            emptyList()
        }
        if (allMeta.isEmpty()) return@withContext Pair(emptyList(), emptyList())

        val presetAvatars = getPresetAvatars()
        val presetWallpapers = getPresetWallpapers()

        val presetAvatarGroups = presetAvatars.groupBy { art ->
            art.label.substringAfterLast("(", "").substringBefore(")", "").trim().lowercase(java.util.Locale.ROOT)
        }
        val presetWallpaperGroups = presetWallpapers.groupBy { art ->
            art.label.substringAfter("· ", "").trim().lowercase(java.util.Locale.ROOT)
        }

        val avatarSections = mutableListOf<Pair<String, List<ProfileArt>>>()
        val wallpaperSections = mutableListOf<Pair<String, List<ProfileArt>>>()
        val seenAnimeKeys = mutableSetOf<String>()

        for (meta in allMeta) {
            val title = meta.titleRomaji?.takeIf { it.isNotBlank() }
                ?: meta.titleEnglish?.takeIf { it.isNotBlank() }
                ?: continue

            val clean = cleanTitleForCatalog(meta.folderName)
            val confidence = AnimePosterResolver.calculateSimilarity(clean, title)
            // Filter out corrupted/hallucinated scraping matches (e.g. college homework or random non-media files)
            if (confidence < 0.50) continue
            // Skip folders with no valid poster or banner
            if (meta.posterUrl.isNullOrBlank() && meta.bannerUrl.isNullOrBlank()) continue

            val key = clean.lowercase(java.util.Locale.ROOT).trim()
            if (key.isBlank() || !seenAnimeKeys.add(key)) continue

            val displayTitle = meta.titleRomaji?.takeIf { it.isNotBlank() }
                ?: meta.titleEnglish?.takeIf { it.isNotBlank() }
                ?: clean

            // 1. Character avatars for this anime/work (strictly portraits from presets or AniList/TVmaze character search)
            val matchingPresetAvatars = findMatchingPresetAvatars(clean, presetAvatarGroups)
            val avatarsForAnime = if (matchingPresetAvatars.isNotEmpty()) {
                matchingPresetAvatars
            } else {
                val searched = search(displayTitle)
                searched.filter { !it.isWallpaper }
            }

            if (avatarsForAnime.isNotEmpty()) {
                avatarSections.add(Pair(displayTitle, avatarsForAnime))
            }

            // 2. Wallpapers for this anime/work (from presets, Room bannerUrl, or search)
            val wallpapersForAnime = mutableListOf<ProfileArt>()
            val matchingPresetWallpapers = findMatchingPresetWallpapers(clean, presetWallpaperGroups)
            wallpapersForAnime.addAll(matchingPresetWallpapers)

            val banner = meta.bannerUrl?.takeIf { it.isNotBlank() && it.startsWith("http") }
            if (banner != null && wallpapersForAnime.none { it.url == banner }) {
                wallpapersForAnime.add(ProfileArt("Wallpaper · $displayTitle", banner, true))
            }

            if (wallpapersForAnime.isEmpty()) {
                val searched = search(displayTitle)
                wallpapersForAnime.addAll(searched.filter { it.isWallpaper })
            }

            if (wallpapersForAnime.isNotEmpty()) {
                wallpaperSections.add(Pair(displayTitle, wallpapersForAnime))
            }
        }

        val result = Pair(avatarSections, wallpaperSections)
        memoryCachedSections = result
        result
    }

    suspend fun getLibraryArt(): Pair<List<ProfileArt>, List<ProfileArt>> = withContext(Dispatchers.IO) {
        val (avatarSecs, wpSecs) = getLibraryArtSections()
        Pair(avatarSecs.flatMap { it.second }, wpSecs.flatMap { it.second })
    }

    private fun findMatchingPresetAvatars(
        query: String,
        presetGroups: Map<String, List<ProfileArt>>
    ): List<ProfileArt> {
        val q = query.lowercase(java.util.Locale.ROOT).trim()
        presetGroups[q]?.let { return it }
        for ((franchise, arts) in presetGroups) {
            if (q.contains(franchise) || franchise.contains(q) || isAliasMatch(q, franchise)) {
                return arts
            }
        }
        return emptyList()
    }

    private fun findMatchingPresetWallpapers(
        query: String,
        presetGroups: Map<String, List<ProfileArt>>
    ): List<ProfileArt> {
        val q = query.lowercase(java.util.Locale.ROOT).trim()
        presetGroups[q]?.let { return it }
        for ((franchise, arts) in presetGroups) {
            if (q.contains(franchise) || franchise.contains(q) || isAliasMatch(q, franchise)) {
                return arts
            }
        }
        return emptyList()
    }

    private fun isAliasMatch(q: String, franchise: String): Boolean {
        if (q.contains("frieren") && franchise.contains("frieren")) return true
        if (q.contains("jujutsu") && franchise.contains("jujutsu")) return true
        if (q.contains("kimetsu") && franchise.contains("demon slayer")) return true
        if (q.contains("shingeki") && franchise.contains("attack on titan")) return true
        if (q.contains("boku no hero") && franchise.contains("my hero academia")) return true
        if (q.contains("kizumonogatari") && franchise.contains("kizumonogatari")) return true
        if (q.contains("bakemonogatari") && franchise.contains("bakemonogatari")) return true
        if (q.contains("monogatari") && (franchise.contains("bakemonogatari") || franchise.contains("kizumonogatari"))) return true
        if (q.contains("shippuden") && franchise.contains("naruto")) return true
        if (q.contains("konosuba") && franchise.contains("konosuba")) return true
        if (q.contains("mushoku") && franchise.contains("mushoku")) return true
        if (q.contains("one piece") && franchise.contains("one piece")) return true
        if (q.contains("chainsaw") && franchise.contains("chainsaw")) return true
        if (q.contains("bleach") && franchise.contains("bleach")) return true
        if (q.contains("solo leveling") && franchise.contains("solo leveling")) return true
        if ((q.contains("aranha") || q.contains("spider")) && franchise.contains("spider")) return true
        if (q.contains("mentalist") && franchise.contains("mentalist")) return true
        return false
    }

    private fun cleanTitleForCatalog(raw: String): String {
        return AnimePosterResolver.cleanAnimeTitle(raw)
    }

    fun defaultAvatarForProfile(profileId: String): ProfileArt? =
        defaultAvatarForProfile(profileId, getPresetAvatars())

    companion object {
        internal fun defaultAvatarForProfile(profileId: String, avatars: List<ProfileArt>): ProfileArt? {
            if (avatars.isEmpty()) return null
            val index = Math.floorMod(profileId.hashCode(), avatars.size)
            return avatars[index]
        }
    }

    /** Curated catalog of high quality character avatar presets (served via CDN) */
    fun getPresetAvatars(): List<ProfileArt> = listOf(
        // One Piece
        ProfileArt("Monkey D. Luffy (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b40-MNypXsxSRb1R.png", false),
        ProfileArt("Roronoa Zoro (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b62-S7oAeA9WInjV.png", false),
        ProfileArt("Nami (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b723-vp5hPptgnNEC.png", false),
        ProfileArt("Sanji (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b305-6lisPmHtCnLT.png", false),
        ProfileArt("Tony Tony Chopper (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b309-H64NhbJ2ywIQ.jpg", false),
        ProfileArt("Nico Robin (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b61-ywXUyyocEEqt.png", false),
        ProfileArt("Franky (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/n64-ChX6ZzHHjXqA.png", false),
        ProfileArt("Usopp (One Piece)", "https://s4.anilist.co/file/anilistcdn/character/large/b724-GFGgI9AJQkfy.jpg", false),

        // Bakemonogatari
        ProfileArt("Hitagi Senjougahara (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22037-sY7GWSKYr2Nl.jpg", false),
        ProfileArt("Koyomi Araragi (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22036-Ed3CjwPlDLp4.png", false),
        ProfileArt("Shinobu Oshino (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b23602-Z4MDNYcoAWZu.png", false),
        ProfileArt("Tsubasa Hanekawa (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22055-EaTsy30ihdgb.png", false),
        ProfileArt("Mayoi Hachikuji (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22052-beyfl4AyMGhn.png", false),
        ProfileArt("Suruga Kanbaru (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/22054-n6Jsvp80bUhD.jpg", false),
        ProfileArt("Nadeko Sengoku (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22050-RUw3vyGYdr9W.jpg", false),
        ProfileArt("Meme Oshino (Bakemonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22552-CZz1T3AjRyVd.png", false),

        // Kizumonogatari
        ProfileArt("Kiss-Shot (Kizumonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b23602-Z4MDNYcoAWZu.png", false),
        ProfileArt("Koyomi Araragi (Kizumonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22036-Ed3CjwPlDLp4.png", false),
        ProfileArt("Tsubasa Hanekawa (Kizumonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22055-EaTsy30ihdgb.png", false),
        ProfileArt("Meme Oshino (Kizumonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b22552-CZz1T3AjRyVd.png", false),
        ProfileArt("Dramaturgy (Kizumonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b42362-tf2uLWPrv2AO.png", false),
        ProfileArt("Episode (Kizumonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/b42363-SqGCv9gBmJbM.png", false),
        ProfileArt("Guillotine Cutter (Kizumonogatari)", "https://s4.anilist.co/file/anilistcdn/character/large/42364-DetrilF9Os35.jpg", false),

        // Mushoku Tensei
        ProfileArt("Rudeus Greyrat (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b88348-bIe5XnXdRpmX.png", false),
        ProfileArt("Roxy Migurdia (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b88350-QU1iwgZ887U8.png", false),
        ProfileArt("Sylphiette (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b88346-h8kCL93AXj4n.png", false),
        ProfileArt("Eris Boreas Greyrat (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b88349-5dsNUah3oBj8.png", false),
        ProfileArt("Ruijerd Superdia (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b88351-grqY44xz6bvP.png", false),
        ProfileArt("Paul Greyrat (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b88347-YtQTlChhN50m.png", false),
        ProfileArt("Shizuka Nanahoshi (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b137816-ub5QE86fHD3C.png", false),
        ProfileArt("Norn Greyrat (Mushoku Tensei)", "https://s4.anilist.co/file/anilistcdn/character/large/b145458-JG4FkAjUjw2l.png", false),

        // Spider-Man / Homem-Aranha
        ProfileArt("Spider-Man (Spider-Man)", "https://m.media-amazon.com/images/M/MV5BOWNjYWM3NWItOGE0ZS00MWRjLThiZWEtYjc4ZmNmMmU5ZTVmXkEyXkFqcGc@._V1_SX500.jpg", false),
        ProfileArt("Peter Parker (Spider-Man)", "https://images.metahub.space/background/medium/tt22084616/img", false),

        // The Mentalist / Mentalista
        ProfileArt("Patrick Jane (The Mentalist)", "https://static.tvmaze.com/uploads/images/medium_portrait/1/3990.jpg", false),
        ProfileArt("Teresa Lisbon (The Mentalist)", "https://static.tvmaze.com/uploads/images/medium_portrait/1/3991.jpg", false),
        ProfileArt("Kimball Cho (The Mentalist)", "https://static.tvmaze.com/uploads/images/medium_portrait/1/3992.jpg", false),
        ProfileArt("Wayne Rigsby (The Mentalist)", "https://static.tvmaze.com/uploads/images/medium_portrait/1/3993.jpg", false),
        ProfileArt("Grace Van Pelt (The Mentalist)", "https://static.tvmaze.com/uploads/images/medium_portrait/1/3994.jpg", false),

        // Sousou no Frieren
        ProfileArt("Frieren (Sousou no Frieren)", "https://s4.anilist.co/file/anilistcdn/character/large/b176754-PCnpqIOkjhFk.png", false),
        ProfileArt("Fern (Sousou no Frieren)", "https://s4.anilist.co/file/anilistcdn/character/large/b183965-uGFohBjlFoTp.png", false),

        // Jujutsu Kaisen
        ProfileArt("Gojo Satoru (Jujutsu Kaisen)", "https://s4.anilist.co/file/anilistcdn/character/large/b127691-9zqh1xpIubn7.png", false),
        ProfileArt("Yuji Itadori (Jujutsu Kaisen)", "https://s4.anilist.co/file/anilistcdn/character/large/b127212-FVm2tD0erQ5B.png", false),
        ProfileArt("Megumi Fushiguro (Jujutsu Kaisen)", "https://s4.anilist.co/file/anilistcdn/character/large/b127693-tM6x5E7h6Y7q.png", false),

        // Demon Slayer
        ProfileArt("Tanjiro Kamado (Demon Slayer)", "https://s4.anilist.co/file/anilistcdn/character/large/b126071-BTNEc1nRIv68.png", false),
        ProfileArt("Nezuko Kamado (Demon Slayer)", "https://s4.anilist.co/file/anilistcdn/character/large/b127518-NRlq1CQ1v1ro.png", false),

        // Naruto
        ProfileArt("Naruto Uzumaki (Shippuden)", "https://s4.anilist.co/file/anilistcdn/character/large/b17-phjcWCkRuIhu.png", false),
        ProfileArt("Sasuke Uchiha (Shippuden)", "https://s4.anilist.co/file/anilistcdn/character/large/b13-SISLEw1oAD7a.png", false),
        ProfileArt("Itachi Uchiha (Naruto)", "https://s4.anilist.co/file/anilistcdn/character/large/b14-9Kb1E5oel1ke.png", false),

        // Attack on Titan
        ProfileArt("Eren Yeager (Attack on Titan)", "https://s4.anilist.co/file/anilistcdn/character/large/b40882-dsj7IP943WFF.jpg", false),
        ProfileArt("Mikasa Ackerman (Attack on Titan)", "https://s4.anilist.co/file/anilistcdn/character/large/b40881-F3gr1PkreDvj.png", false),

        // Spy x Family
        ProfileArt("Anya Forger (Spy x Family)", "https://s4.anilist.co/file/anilistcdn/character/large/b138100-4Li0tWRCa5bQ.png", false),
        ProfileArt("Yor Forger (Spy x Family)", "https://s4.anilist.co/file/anilistcdn/character/large/b138102-ZOAu9jI2d5ke.png", false),

        // KonoSuba
        ProfileArt("Megumin (KonoSuba)", "https://s4.anilist.co/file/anilistcdn/character/large/b89361-tq8PQQ4MmF0M.png", false),

        // Dragon Ball & Hunter x Hunter
        ProfileArt("Son Goku (Dragon Ball)", "https://s4.anilist.co/file/anilistcdn/character/large/246-wsRRr6z1kii8.png", false),
        ProfileArt("Vegeta (Dragon Ball)", "https://s4.anilist.co/file/anilistcdn/character/large/b913-NIFkKazWM8VO.png", false),
        ProfileArt("Killua Zoldyck (Hunter x Hunter)", "https://s4.anilist.co/file/anilistcdn/character/large/b27-Z5O02kQUydpT.jpg", false),
        ProfileArt("Saitama (One Punch Man)", "https://s4.anilist.co/file/anilistcdn/character/large/b73935-ON5d0mAcrItd.jpg", false)
    )

    /** Curated catalog of high quality anime wallpapers/banners (served via CDN) */
    fun getPresetWallpapers(): List<ProfileArt> = listOf(
        ProfileArt("Wallpaper · One Piece", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/21-wf37VakJmZqs.jpg", true),
        ProfileArt("Wallpaper · Bakemonogatari", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/5081-3E5mYl1Ue22M.jpg", true),
        ProfileArt("Wallpaper · Kizumonogatari", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/9260-69LbBxcWN2tA.jpg", true),
        ProfileArt("Wallpaper · Mushoku Tensei", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/108465-buhbF91GqK1E.jpg", true),
        ProfileArt("Wallpaper · Spider-Man: Brand New Day", "https://images.metahub.space/background/medium/tt22084616/img", true),
        ProfileArt("Wallpaper · The Mentalist", "https://images.metahub.space/background/medium/tt1196946/img", true),
        ProfileArt("Wallpaper · Frieren Beyond Journey's End", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/154587-ivXNJ23SM1xB.jpg", true),
        ProfileArt("Wallpaper · Jujutsu Kaisen", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/113415-jQBSkxWAAk83.jpg", true),
        ProfileArt("Wallpaper · Demon Slayer", "https://s4.anilist.co/file/anilistcdn/media/anime/banner/101922-33MtJGsUSxga.jpg", true),
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
        if (cached != null && System.currentTimeMillis() - cache.getLong("${key}:time", 0L) < 86_400_000L * 30L) {
            val parsed = parse(cached)
            if (parsed.isNotEmpty()) return@withContext parsed
            val tvMazeParsed = parseTvMaze(cached, anime)
            if (tvMazeParsed.isNotEmpty()) return@withContext tvMazeParsed
        }
        val query = """query (${'$'}search: String) {
            Page(page: 1, perPage: 8) {
                media(search: ${'$'}search, type: ANIME, isAdult: false) {
                    id
                    title { romaji english }
                    bannerImage
                    characters(perPage: 8, sort: [ROLE, RELEVANCE]) {
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
                if (response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    val items = parse(raw)
                    if (items.isNotEmpty()) {
                        cache.edit().putString(key, raw)
                            .putLong("${key}:time", System.currentTimeMillis()).apply()
                        return@withContext items
                    }
                }
            }
        } catch (_: Exception) {
            // fall through to TVmaze or cache
        }

        // TV / Series fallback (e.g. for "The Mentalist" / "Mentalista")
        try {
            val tvQuery = if (key.contains("mentalista")) "mentalist" else anime
            val url = "https://api.tvmaze.com/singlesearch/shows?q=" + java.net.URLEncoder.encode(tvQuery, "UTF-8") + "&embed=cast"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    val items = parseTvMaze(raw, anime)
                    if (items.isNotEmpty()) {
                        cache.edit().putString(key, raw)
                            .putLong("${key}:time", System.currentTimeMillis()).apply()
                        return@withContext items
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        if (cached != null) {
            parse(cached).ifEmpty { parseTvMaze(cached, anime) }
        } else {
            emptyList()
        }
    }

    internal fun parseTvMaze(raw: String, requestedTitle: String): List<ProfileArt> {
        val root = try { JSONObject(raw) } catch (_: Exception) { return emptyList() }
        val showName = root.optString("name").ifBlank { requestedTitle }
        val items = mutableListOf<ProfileArt>()

        val imageObj = root.optJSONObject("image")
        val bgUrl = imageObj?.optString("original") ?: imageObj?.optString("medium")
        if (!bgUrl.isNullOrBlank() && bgUrl.startsWith("http")) {
            items.add(ProfileArt("Wallpaper · $showName", bgUrl, true))
        }

        val castArray = root.optJSONObject("_embedded")?.optJSONArray("cast")
        if (castArray != null) {
            for (i in 0 until castArray.length().coerceAtMost(8)) {
                val castMember = castArray.optJSONObject(i) ?: continue
                val person = castMember.optJSONObject("person")
                val character = castMember.optJSONObject("character")
                val charName = character?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: person?.optString("name")
                    ?: "Personagem"

                val charImg = character?.optJSONObject("image")?.optString("medium")
                    ?: person?.optJSONObject("image")?.optString("medium")
                    ?: person?.optJSONObject("image")?.optString("original")

                if (!charImg.isNullOrBlank() && charImg.startsWith("http")) {
                    items.add(ProfileArt("$charName ($showName)", charImg, false))
                }
            }
        }
        return items
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
