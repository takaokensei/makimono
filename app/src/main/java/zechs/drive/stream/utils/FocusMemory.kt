package zechs.drive.stream.utils

import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry that remembers the last focused item id for each list context.
 * Enables TV-standard focus restoration on back navigation (Gate G1).
 */
object FocusMemory {

    private val memory = ConcurrentHashMap<String, String>()

    fun remember(key: String, itemId: String) {
        memory[key] = itemId
    }

    fun get(key: String): String? {
        return memory[key]
    }

    fun clear(key: String) {
        memory.remove(key)
    }

    fun clearAll() {
        memory.clear()
    }

    /**
     * Attempts to find the position matching the remembered id and requests focus.
     */
    fun restore(
        recycler: RecyclerView,
        key: String,
        idOf: (position: Int) -> String?
    ): Boolean {
        val lastId = get(key) ?: return false
        val adapter = recycler.adapter ?: return false
        val itemCount = adapter.itemCount

        for (i in 0 until itemCount) {
            if (idOf(i) == lastId) {
                recycler.post {
                    val holder = recycler.findViewHolderForAdapterPosition(i)
                    if (holder != null) {
                        holder.itemView.requestFocus()
                    } else {
                        recycler.scrollToPosition(i)
                        recycler.post {
                            recycler.findViewHolderForAdapterPosition(i)?.itemView?.requestFocus()
                        }
                    }
                }
                return true
            }
        }
        return false
    }
}
