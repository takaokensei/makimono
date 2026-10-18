package zechs.drive.stream.data.repository

import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.TenraiAnimeService
import zechs.drive.stream.utils.AppLog
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FranchiseGroupingManager @Inject constructor(
    private val tenraiAnimeService: TenraiAnimeService,
    private val folderMetadataRepository: FolderMetadataRepository
) {

    companion object {
        private const val TAG = "FranchiseGrouping"
    }

    data class FranchiseHub(
        val franchiseKey: String,
        val canonicalTitle: String,
        val primaryFolderId: String,
        val posterUrl: String?,
        val bannerUrl: String?,
        val synopsis: String? = null,
        val members: List<FranchiseMember>
    )

    data class FranchiseMember(
        val folderId: String,
        val folderName: String,
        val arcTitle: String,
        val releaseYear: Int?,
        val episodeCount: Int?,
        val isMovie: Boolean,
        val posterUrl: String? = null,
        val bannerUrl: String? = null,
        val score: Float? = null,
        val scoreSource: String = "MAL"
    )

    private val franchiseHubsCache = ConcurrentHashMap<String, FranchiseHub>()
    private val folderToFranchiseMap = ConcurrentHashMap<String, String>()

    /**
     * Identifies canonical franchise key for a given folder name.
     * Groups known interrelated anime/movies into a unified franchise umbrella.
     */
    fun resolveFranchiseKey(folderName: String): String? {
        val norm = AnimePosterResolver.cleanAnimeTitle(folderName).lowercase(Locale.ROOT).trim()

        return when {
            norm.contains("monogatari") || norm.contains("kizumonogatari") || norm.contains("bakemonogatari") ||
                norm.contains("nisemonogatari") || norm.contains("nekomonogatari") ||
                norm.contains("koyomimonogatari") || norm.contains("owarimonogatari") ||
                norm.contains("tsukimonogatari") || norm.contains("zoku") -> {
                "monogatari_series"
            }
            norm.contains("owari no seraph") || norm.contains("seraph of the end") -> {
                "owari_no_seraph"
            }
            norm.contains("tensei shitara") || norm.contains("slime datta ken") -> {
                "tensei_shitara_slime"
            }
            norm.contains("bleach") -> {
                "bleach"
            }
            norm.contains("mushoku") || norm.contains("jobless reincarnation") -> {
                "mushoku_tensei"
            }
            norm.contains("jigokuraku") || norm.contains("hell's paradise") -> {
                "jigokuraku"
            }
            norm.contains("spider-man") || norm.contains("homem-aranha") || norm.contains("homem aranha") -> {
                "spider_man"
            }
            norm.contains("mentalist") || norm.contains("mentalista") -> {
                "the_mentalist"
            }
            norm.contains("grisaia") -> {
                "grisaia"
            }
            norm.contains("durarara") -> {
                "durarara"
            }
            norm.contains("shangri-la frontier") || norm.contains("shangri la frontier") -> {
                "shangri_la_frontier"
            }
            else -> null
        }
    }

    fun getCanonicalTitle(franchiseKey: String): String {
        return when (franchiseKey) {
            "monogatari_series" -> "Monogatari Series"
            "owari_no_seraph" -> "Owari no Seraph"
            "tensei_shitara_slime" -> "Tensei Shitara Slime Datta Ken"
            "bleach" -> "BLEACH"
            "mushoku_tensei" -> "Mushoku Tensei"
            "jigokuraku" -> "Jigokuraku"
            "spider_man" -> "Spider-Man"
            "the_mentalist" -> "The Mentalist"
            "grisaia" -> "Grisaia Series"
            "durarara" -> "Durarara!!"
            "shangri_la_frontier" -> "Shangri-La Frontier"
            else -> franchiseKey.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }


    /**
     * Groups library files into unified franchise hubs according to Option A.
     * Pastas that belong to the same franchise (e.g. Bakemonogatari + Kizumonogatari)
     * are consolidated into a single entry for the Home grid.
     */
    suspend fun groupLibrary(driveFiles: List<DriveFile>): List<DriveFile> {
        if (driveFiles.isEmpty()) return emptyList()

        val allMeta = try {
            folderMetadataRepository.getAllMetadata().associateBy { it.folderId }
        } catch (_: Exception) {
            emptyMap()
        }

        val franchiseBuckets = mutableMapOf<String, MutableList<DriveFile>>()
        val unclustered = mutableListOf<DriveFile>()

        for (file in driveFiles) {
            val key = resolveFranchiseKey(file.name)
            if (key != null) {
                franchiseBuckets.getOrPut(key) { mutableListOf() }.add(file)
            } else {
                unclustered.add(file)
            }
        }

        val result = mutableListOf<DriveFile>()

        // 1. Process franchise buckets
        for ((franchiseKey, members) in franchiseBuckets) {
            if (members.size > 1) {
                val canonicalTitle = getCanonicalTitle(franchiseKey)

                // Sort members chronologically by release year or name
                val sortedMembers = members.map { file ->
                    val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                    val meta = allMeta[targetId] ?: allMeta[file.id]
                    val arcTitle = meta?.titleRomaji ?: meta?.titleEnglish
                        ?: AnimePosterResolver.cleanAnimeTitle(file.name)
                    val isMovie = meta?.isMovie == true ||
                        arcTitle.contains("kizumonogatari", ignoreCase = true) ||
                        file.name.contains("kizumonogatari", ignoreCase = true) ||
                        arcTitle.contains("movie", ignoreCase = true) ||
                        file.name.contains("movie", ignoreCase = true) ||
                        arcTitle.contains("filme", ignoreCase = true) ||
                        file.name.contains("filme", ignoreCase = true)
                    FranchiseMember(
                        folderId = file.id,
                        folderName = file.name,
                        arcTitle = arcTitle,
                        releaseYear = meta?.year,
                        episodeCount = meta?.episodeCount,
                        isMovie = isMovie,
                        posterUrl = meta?.posterUrl ?: file.posterUrl,
                        bannerUrl = meta?.bannerUrl ?: file.thumbnailLink,
                        score = meta?.score,
                        scoreSource = if (isMovie ||
                            meta?.mediaType == "movie" || meta?.mediaType == "tv") "TMDB" else "MAL"
                    )
                }.sortedWith(
                    compareBy<FranchiseMember> { it.releaseYear ?: 9999 }
                        .thenBy { it.arcTitle }
                )

                val primaryMember = sortedMembers.first()
                val primaryFile = members.firstOrNull { it.id == primaryMember.folderId } ?: members.first()

                val bestPoster = sortedMembers.mapNotNull { it.posterUrl }.firstOrNull() ?: primaryFile.posterUrl
                val bestBanner = sortedMembers.mapNotNull { allMeta[it.folderId]?.bannerUrl }.firstOrNull()
                    ?: primaryFile.thumbnailLink

                val bestSynopsis = sortedMembers.mapNotNull { allMeta[it.folderId]?.synopsis }.firstOrNull { it.isNotBlank() }

                val hub = FranchiseHub(
                    franchiseKey = franchiseKey,
                    canonicalTitle = canonicalTitle,
                    primaryFolderId = primaryFile.id,
                    posterUrl = bestPoster,
                    bannerUrl = bestBanner,
                    synopsis = bestSynopsis,
                    members = sortedMembers
                )

                franchiseHubsCache[franchiseKey] = hub
                sortedMembers.forEach { folderToFranchiseMap[it.folderId] = franchiseKey }

                // Create a consolidated DriveFile card representing the whole franchise
                val consolidatedCard = primaryFile.copy(
                    name = "$canonicalTitle (${sortedMembers.size} Obras)",
                    posterUrl = bestPoster,
                    thumbnailLink = bestBanner
                )

                result.add(consolidatedCard)
                AppLog.d(TAG, "Grouped ${sortedMembers.size} folders into franchise '$canonicalTitle'")
            } else {
                // Only 1 folder in library for this franchise: present normally
                val singleFile = members.first()
                result.add(singleFile)
            }
        }

        // 2. Add remaining non-clustered files
        result.addAll(unclustered)

        // Sort overall by name
        return result.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun getFranchiseHubForFolder(folderId: String): FranchiseHub? {
        val key = folderToFranchiseMap[folderId] ?: return null
        return franchiseHubsCache[key]
    }

    fun getFranchiseHub(franchiseKey: String): FranchiseHub? {
        return franchiseHubsCache[franchiseKey]
    }

    suspend fun getOrCreateFranchiseHub(folderId: String, seriesTitle: String): FranchiseHub? {
        val existing = getFranchiseHubForFolder(folderId)
        if (existing != null) return existing

        val key = resolveFranchiseKey(seriesTitle) ?: return null
        val cached = getFranchiseHub(key)
        if (cached != null) return cached

        val allMeta = try {
            folderMetadataRepository.getAllMetadata()
        } catch (_: Exception) {
            emptyList()
        }

        val matchingFolders = allMeta.filter { meta ->
            resolveFranchiseKey(meta.folderName) == key
        }

        if (matchingFolders.size > 1) {
            val canonicalTitle = getCanonicalTitle(key)
            val members = matchingFolders.map { meta ->
                val arc = meta.titleRomaji?.takeIf { it.isNotBlank() }
                    ?: meta.titleEnglish?.takeIf { it.isNotBlank() }
                    ?: AnimePosterResolver.cleanAnimeTitle(meta.folderName)
                val isMovie = meta.isMovie ||
                    arc.contains("kizumonogatari", ignoreCase = true) ||
                    meta.folderName.contains("kizumonogatari", ignoreCase = true) ||
                    arc.contains("movie", ignoreCase = true) ||
                    meta.folderName.contains("movie", ignoreCase = true) ||
                    arc.contains("filme", ignoreCase = true) ||
                    meta.folderName.contains("filme", ignoreCase = true)
                FranchiseMember(
                    folderId = meta.folderId,
                    folderName = meta.folderName,
                    arcTitle = arc,
                    releaseYear = meta.year,
                    episodeCount = meta.episodeCount,
                    isMovie = isMovie,
                    posterUrl = meta.posterUrl,
                    bannerUrl = meta.bannerUrl,
                    score = meta.score,
                    scoreSource = if (isMovie ||
                        meta.mediaType == "movie" || meta.mediaType == "tv") "TMDB" else "MAL"
                )
            }
            val hub = FranchiseHub(
                franchiseKey = key,
                canonicalTitle = canonicalTitle,
                primaryFolderId = folderId,
                posterUrl = matchingFolders.firstOrNull { it.posterUrl?.isNotBlank() == true }?.posterUrl,
                bannerUrl = matchingFolders.firstOrNull { it.bannerUrl?.isNotBlank() == true }?.bannerUrl,
                synopsis = matchingFolders.firstOrNull { it.synopsis?.isNotBlank() == true }?.synopsis,
                members = members
            )
            franchiseHubsCache[key] = hub
            matchingFolders.forEach { folderToFranchiseMap[it.folderId] = key }
            return hub
        }
        return null
    }
}
