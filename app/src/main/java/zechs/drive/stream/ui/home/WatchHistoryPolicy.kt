package zechs.drive.stream.ui.home

import zechs.drive.stream.data.model.WatchList

internal fun sortWatchHistory(history: List<WatchList>): List<WatchList> =
    history.sortedWith(compareByDescending<WatchList> { it.lastPlayedAt }.thenByDescending { it.id ?: 0 })
