package zechs.drive.stream.utils

import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.domain.library.EpisodeCategorizer
import zechs.drive.stream.domain.library.EpisodeParser
import zechs.drive.stream.domain.library.GroupableItem
import zechs.drive.stream.domain.library.MediaItemCategory
import zechs.drive.stream.domain.library.SeasonGrouper
import zechs.drive.stream.ui.files.adapter.FilesDataModel

data class SeasonGroup(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val folderId: String? = null,
    val fileItems: List<FilesDataModel> = emptyList(),
    val playlistItems: List<PlaylistItem> = emptyList()
) {
    val count: Int
        get() = if (fileItems.isNotEmpty()) fileItems.size else playlistItems.size
}

/**
 * Adaptador de apresentação para agrupamento de temporadas e episódios.
 * Delega o particionamento algorítmico para a camada de domínio puro ([SeasonGrouper]).
 */
object SeasonEpisodeGrouper {

    enum class ItemCategory(val priority: Int) {
        STORY(1),
        SPECIAL(2),
        PROMO(3),
        MUSIC(4);

        companion object {
            fun fromDomain(domainCat: MediaItemCategory): ItemCategory = when (domainCat) {
                MediaItemCategory.STORY -> STORY
                MediaItemCategory.SPECIAL -> SPECIAL
                MediaItemCategory.PROMO -> PROMO
                MediaItemCategory.MUSIC -> MUSIC
            }
        }
    }

    fun categorize(title: String, parsed: EpisodeParser.ParsedEpisode): ItemCategory {
        return ItemCategory.fromDomain(EpisodeCategorizer.categorize(title, parsed))
    }

    fun compareItems(
        titleA: String, parsedA: EpisodeParser.ParsedEpisode,
        titleB: String, parsedB: EpisodeParser.ParsedEpisode
    ): Int {
        return EpisodeCategorizer.compareItems(titleA, parsedA, titleB, parsedB)
    }

    fun groupFiles(showName: String, files: List<FilesDataModel>): List<SeasonGroup> {
        val fileItems = files.filterIsInstance<FilesDataModel.File>()
        if (fileItems.isEmpty()) return emptyList()

        val itemMap = fileItems.associateBy { file ->
            val df = file.driveFile
            if (df.isShortcut) df.shortcutDetails.targetId ?: df.id else df.id
        }

        val groupableItems = fileItems.map { file ->
            val df = file.driveFile
            val targetId = if (df.isShortcut) df.shortcutDetails.targetId ?: df.id else df.id
            GroupableItem(
                id = targetId,
                name = df.name,
                isFolder = df.isFolder || df.isShortcutFolder,
                isVideo = df.isVideoFile || (df.isShortcut && df.isShortcutVideo)
            )
        }

        val domainGroups = SeasonGrouper.groupItems(showName, groupableItems)

        return domainGroups.map { dg ->
            SeasonGroup(
                id = dg.id,
                name = dg.name,
                subtitle = dg.subtitle,
                folderId = dg.folderId,
                fileItems = dg.items.mapNotNull { itemMap[it.id] }
            )
        }
    }

    fun groupPlaylist(showName: String, playlist: List<PlaylistItem>): List<SeasonGroup> {
        if (playlist.isEmpty()) return emptyList()

        val itemMap = playlist.associateBy { it.fileId }
        val groupableItems = playlist.map { item ->
            GroupableItem(
                id = item.fileId,
                name = item.title,
                isFolder = false,
                isVideo = true
            )
        }

        val domainGroups = SeasonGrouper.groupItems(showName, groupableItems)

        return domainGroups.map { dg ->
            SeasonGroup(
                id = dg.id,
                name = dg.name,
                subtitle = dg.subtitle,
                folderId = dg.folderId,
                playlistItems = dg.items.mapNotNull { itemMap[it.id] }
            )
        }
    }
}
