package zechs.drive.stream.ui.files.adapter

/** Merges paged Drive results without mutating lists already submitted to ListAdapter. */
internal object FilesPageMerger {
    fun merge(
        previous: List<FilesDataModel>?,
        incoming: List<FilesDataModel.File>
    ): MutableList<FilesDataModel> {
        val merged = ArrayList<FilesDataModel>((previous?.size ?: 0) + incoming.size)
        val indexById = HashMap<String, Int>((previous?.size ?: 0) + incoming.size)

        previous.orEmpty().forEach { item ->
            if (item is FilesDataModel.File) {
                val id = item.driveFile.id
                if (id !in indexById) {
                    indexById[id] = merged.size
                    merged += item
                }
            }
        }

        incoming.forEach { item ->
            val id = item.driveFile.id
            val existingIndex = indexById[id]
            if (existingIndex == null) {
                indexById[id] = merged.size
                merged += item
            } else {
                // Keep its established visual position while accepting refreshed metadata/state.
                merged[existingIndex] = item
            }
        }

        return merged
    }
}
