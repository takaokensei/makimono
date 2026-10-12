package zechs.drive.stream.ui.home.controller

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.navigation.NavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import zechs.drive.stream.R
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.ui.home.ContinueWatchingContextMenu
import zechs.drive.stream.ui.home.HomeFragmentDirections
import zechs.drive.stream.ui.home.HomeViewModel
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.ui.player.GlassMenuItem
import zechs.drive.stream.ui.player.PlayerGlassMenuDialog
import zechs.drive.stream.ui.player.PlayerLauncher
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.ext.navigateSafe

/**
 * Manipulador de ações de reprodução, diálogos de histórico/fila e menus de contexto da Home.
 * Desacopla navegação e ações de reprodução do HomeFragment per Seção 4.10.
 */
class HomeActionHandler(
    private val context: Context,
    private val rootView: View,
    private val viewModel: HomeViewModel,
    private val mainViewModel: MainViewModel,
    private val navController: NavController,
    private val onFindLibraryFolder: (String) -> DriveFile?,
    private val onMpvStartPending: (Long) -> Unit,
    private val getCatalogVideoFiles: () -> List<DriveFile>
) {

    fun handleQuickPlay(file: DriveFile) {
        if (file.isVideoFile || file.isShortcutVideo) {
            val target = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                file.copy(id = file.shortcutDetails.targetId)
            } else file
            launchVideoPlayer(target)
        } else if (file.isFolder || file.isShortcutFolder) {
            Toast.makeText(context, "Iniciando ${file.name}...", Toast.LENGTH_SHORT).show()
            viewModel.getResumeOrFirstEpisode(file) { video, startPos ->
                if (video != null) {
                    launchVideoPlayer(video, startPos)
                } else {
                    handleOpenFolder(file)
                }
            }
        } else {
            handleOpenFolder(file)
        }
    }

    fun handleOpenFolder(file: DriveFile) {
        val isFolder = file.isFolder || file.isShortcutFolder
        if (isFolder) {
            val folderId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
            viewModel.recordFolderOpened(folderId, file.name)
            val action = HomeFragmentDirections.actionHomeFragmentToSeriesDetailFragment(
                name = file.name,
                folderId = folderId,
                posterUrl = file.posterUrl ?: file.thumbnailLarge ?: file.thumbnailLink
            )
            navController.navigateSafe(action)
        } else if (file.isVideoFile || file.isShortcutVideo) {
            launchVideoPlayer(file)
        } else {
            Toast.makeText(context, file.name, Toast.LENGTH_SHORT).show()
        }
    }

    fun showAnimeContextMenu(file: DriveFile) {
        val isStarred = file.starred == Starred.STARRED
        val options = mutableListOf("Abrir detalhes")
        val actions = mutableListOf<() -> Unit>({ handleOpenFolder(file) })

        options += if (isStarred) "Remover dos favoritos" else "Adicionar aos favoritos"
        actions += { viewModel.starFile(file, !isStarred) }

        if (file.isVideoFile || file.isShortcutVideo) {
            options += "Adicionar à fila"
            actions += { viewModel.addToQueue(file) }
        }

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(file.name)
            .setItems(options.toTypedArray()) { d, which ->
                d.dismiss()
                actions.getOrNull(which)?.invoke()
            }
            .create()
        dialog.setOnShowListener {
            dialog.listView?.post { dialog.listView?.requestFocus() }
        }
        dialog.show()
    }

    fun showWatchHistoryDialog() {
        val items = viewModel.watchHistory.value
        if (items.isEmpty()) {
            Toast.makeText(context, "O histórico está vazio", Toast.LENGTH_SHORT).show()
            return
        }
        val menuItems = items.mapIndexed { index, watch ->
            GlassMenuItem(
                id = "history_$index",
                title = watch.name,
                subtitle = watch.watchProgress().let { if (it > 0) "$it% assistido" else null },
                tag = watch
            )
        }
        PlayerGlassMenuDialog(
            context = context,
            title = "Histórico recente",
            items = menuItems
        ) { selected ->
            (selected.tag as? WatchList)?.let { playWatchItem(it) }
        }.show()
    }

    fun showWatchQueueDialog() {
        val action = HomeFragmentDirections.actionHomeFragmentToQueueFragment()
        navController.navigateSafe(action)
    }

    fun playQueueItem(item: WatchQueueItem) {
        playWatchItem(
            WatchList(
                name = item.name,
                videoId = item.fileId,
                watchedDuration = 0L,
                totalDuration = 0L,
                thumbnailLink = item.posterUrl
            )
        )
    }

    fun showQueueItemContextMenu(item: WatchQueueItem) {
        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle(item.name)
            .setItems(arrayOf("Reproduzir", "Remover da fila")) { d, which ->
                d.dismiss()
                if (which == 0) {
                    playQueueItem(item)
                } else {
                    viewModel.removeFromQueue(item.fileId)
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.listView?.post { dialog.listView?.requestFocus() }
        }
        dialog.show()
    }

    fun launchVideoPlayer(file: DriveFile, startPosition: Long = -1L) {
        val fileId = file.id
        val thumb = file.thumbnailLarge ?: file.posterUrl ?: file.thumbnailLink
        val catalogPlaylist: ArrayList<PlaylistItem>? = run {
            val videoFiles = getCatalogVideoFiles()
            if (videoFiles.size > 1 && videoFiles.any { it.id == fileId }) {
                ArrayList(videoFiles.map { PlaylistItem(it.id, it.name, it.thumbnailLarge ?: it.thumbnailLink) })
            } else null
        }
        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> {
                PlayerLauncher.launch(
                    context = context,
                    playerType = VideoPlayer.EXO_PLAYER,
                    fileId = fileId,
                    title = file.name,
                    thumbnailLink = thumb,
                    themeIndex = mainViewModel.currentThemeIndex,
                    playlist = catalogPlaylist,
                    startPosition = startPosition
                )
            }
            VideoPlayer.MPV -> {
                Toast.makeText(context, context.getString(R.string.starting_mpv), Toast.LENGTH_SHORT).show()
                viewModel.fetchToken(fileId, file.name, thumb)
            }
        }
    }

    fun showContinueWatchingContextMenu(watchItem: WatchList, focusAnchor: View) {
        ContinueWatchingContextMenu.show(
            context = context,
            watchItem = watchItem,
            focusAnchor = focusAnchor
        ) { action ->
            when (action) {
                ContinueWatchingContextMenu.Action.RESUME ->
                    playWatchItem(watchItem, startFromBeginning = false)
                ContinueWatchingContextMenu.Action.RESTART ->
                    playWatchItem(watchItem, startFromBeginning = true)
                ContinueWatchingContextMenu.Action.OPEN_SERIES -> {
                    val series = onFindLibraryFolder(watchItem.name)
                    if (series != null) handleOpenFolder(series)
                    else Toast.makeText(context, R.string.home_series_not_found, Toast.LENGTH_SHORT).show()
                }
                ContinueWatchingContextMenu.Action.MARK_FINISHED -> {
                    viewModel.markWatchItemFinished(watchItem)
                    Snackbar.make(rootView, R.string.home_marked_finished, 1500).show()
                }
                ContinueWatchingContextMenu.Action.REMOVE -> {
                    viewModel.removeWatchItem(watchItem)
                    Snackbar.make(rootView, R.string.home_continue_removed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) { viewModel.restoreWatchItem(watchItem) }
                        .show()
                }
            }
        }
    }

    fun playWatchItem(watchItem: WatchList, startFromBeginning: Boolean = false) {
        val startPos = if (startFromBeginning || watchItem.hasFinished()) {
            0L
        } else if (watchItem.watchedDuration > 0L) {
            watchItem.watchedDuration
        } else {
            -1L
        }
        when (mainViewModel.currentPlayerIndex) {
            VideoPlayer.EXO_PLAYER -> {
                PlayerLauncher.launch(
                    context = context,
                    playerType = VideoPlayer.EXO_PLAYER,
                    fileId = watchItem.videoId,
                    title = watchItem.name,
                    thumbnailLink = watchItem.thumbnailLink,
                    themeIndex = mainViewModel.currentThemeIndex,
                    startPosition = startPos
                )
            }
            VideoPlayer.MPV -> {
                Toast.makeText(context, context.getString(R.string.starting_mpv), Toast.LENGTH_SHORT).show()
                onMpvStartPending(startPos)
                viewModel.fetchToken(watchItem.videoId, watchItem.name, watchItem.thumbnailLink)
            }
        }
    }
}
