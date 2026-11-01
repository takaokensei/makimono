package zechs.drive.stream.ui.home

import zechs.drive.stream.data.model.WatchList

internal fun sortWatchHistory(history: List<WatchList>): List<WatchList> =
    history.sortedWith(compareByDescending<WatchList> { it.lastPlayedAt }.thenByDescending { it.id ?: 0 })

internal enum class WatchHistoryFilter { ALL, IN_PROGRESS, COMPLETED }

internal class WatchHistoryPresentation(var filter: WatchHistoryFilter = WatchHistoryFilter.ALL)

internal fun filterWatchHistory(history: List<WatchList>, filter: WatchHistoryFilter): List<WatchList> =
    sortWatchHistory(history).filter {
        when (filter) {
            WatchHistoryFilter.ALL -> true
            WatchHistoryFilter.IN_PROGRESS -> !it.hasFinished()
            WatchHistoryFilter.COMPLETED -> it.hasFinished()
        }
    }
