package zechs.drive.stream.utils

import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry that remembers the last focused item id for each list context.
 * Enables TV-standard focus restoration on back navigation (Gate G1).
 */
object FocusMemory {

    private val indexMemory = ConcurrentHashMap<String, Int>()
    private val idMemory = ConcurrentHashMap<String, String>()

    fun remember(key: String, itemId: String) {
        idMemory[key] = itemId
        if (!indexMemory.containsKey(key)) {
            indexMemory[key] = 0
        }
    }

    fun remember(shelfKey: String, itemIndex: Int, itemId: String? = null) {
        indexMemory[shelfKey] = itemIndex.coerceAtLeast(0)
        if (itemId != null) {
            idMemory[shelfKey] = itemId
        } else {
            idMemory.remove(shelfKey)
        }
    }

    fun get(key: String): String? {
        return idMemory[key]
    }

    fun getRememberedId(shelfKey: String): String? = idMemory[shelfKey]

    fun getRememberedIndex(key: String): Int? {
        return indexMemory[key]
    }

    fun resolveTargetIndex(shelfKey: String, currentItems: List<String>): Int {
        if (currentItems.isEmpty()) return 0
        val rememberedId = idMemory[shelfKey]
        if (rememberedId != null) {
            val foundIndex = currentItems.indexOf(rememberedId)
            if (foundIndex != -1) {
                indexMemory[shelfKey] = foundIndex
                return foundIndex
            }
        }
        val rememberedIndex = indexMemory[shelfKey] ?: 0
        return rememberedIndex.coerceIn(0, currentItems.size - 1)
    }

    fun resolveTargetIndex(shelfKey: String, totalItemsCount: Int): Int {
        if (totalItemsCount <= 0) return 0
        val remembered = indexMemory[shelfKey] ?: 0
        return remembered.coerceIn(0, totalItemsCount - 1)
    }

    fun clear(key: String) {
        idMemory.remove(key)
        indexMemory.remove(key)
    }

    fun clearAll() {
        idMemory.clear()
        indexMemory.clear()
    }

    /**
     * Attempts to find the position matching the remembered id (or index fallback) and requests focus.
     */
    fun restore(
        recycler: RecyclerView,
        key: String,
        idOf: (position: Int) -> String?
    ): Boolean {
        val lastId = get(key)
        val adapter = recycler.adapter ?: return false
        val itemCount = adapter.itemCount
        if (itemCount <= 0) return false

        var targetPos = -1
        if (lastId != null) {
            for (i in 0 until itemCount) {
                if (idOf(i) == lastId) {
                    targetPos = i
                    break
                }
            }
        }
        if (targetPos == -1) {
            targetPos = resolveTargetIndex(key, itemCount)
        }

        if (targetPos in 0 until itemCount) {
            val finalPos = targetPos
            recycler.post {
                val holder = recycler.findViewHolderForAdapterPosition(finalPos)
                if (holder != null) {
                    holder.itemView.requestFocus()
                } else {
                    recycler.scrollToPosition(finalPos)
                    recycler.post {
                        recycler.findViewHolderForAdapterPosition(finalPos)?.itemView?.requestFocus()
                    }
                }
            }
            return true
        }
        return false
    }
}
