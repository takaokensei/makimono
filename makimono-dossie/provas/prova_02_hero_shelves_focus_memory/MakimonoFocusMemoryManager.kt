package makimono.dossie.provas.focus

/**
 * Gerenciador de Retenção de Foco Bidirecional para Carrosséis de Android TV.
 * Garante que ao navegar entre linhas horizontais aninhadas ou sair para o Hero banner,
 * o retorno à prateleira reponha o foco no item correto por identidade estável (ID) ou índice,
 * com clamping defensivo caso o tamanho da lista tenha sido alterado dinamicamente.
 */
class MakimonoFocusMemoryManager {

    private val shelfFocusIndexMap = mutableMapOf<String, Int>()
    private val shelfFocusIdMap = mutableMapOf<String, String>()

    fun remember(shelfKey: String, itemIndex: Int, itemId: String? = null) {
        shelfFocusIndexMap[shelfKey] = itemIndex.coerceAtLeast(0)
        if (itemId != null) {
            shelfFocusIdMap[shelfKey] = itemId
        } else {
            shelfFocusIdMap.remove(shelfKey)
        }
    }

    /**
     * Resolve o índice alvo procurando primeiro a identidade estável (ID) na lista fornecida.
     * Se o ID não for encontrado ou não existir, faz fallback para o índice numérico memorizado com clamping.
     */
    fun resolveTargetIndex(shelfKey: String, currentItems: List<String>): Int {
        if (currentItems.isEmpty()) return 0
        val rememberedId = shelfFocusIdMap[shelfKey]
        if (rememberedId != null) {
            val foundIndex = currentItems.indexOf(rememberedId)
            if (foundIndex != -1) {
                shelfFocusIndexMap[shelfKey] = foundIndex
                return foundIndex
            }
        }
        val rememberedIndex = shelfFocusIndexMap[shelfKey] ?: 0
        return rememberedIndex.coerceIn(0, currentItems.size - 1)
    }

    /**
     * Recupera o índice a ser focado com clamping defensivo baseado no último índice registrado.
     * Para resolução por identidade após reordenação, use resolveTargetIndex(shelfKey, currentItems: List<String>).
     */
    fun resolveTargetIndex(shelfKey: String, totalItemsCount: Int): Int {
        if (totalItemsCount <= 0) return 0
        val remembered = shelfFocusIndexMap[shelfKey] ?: 0
        return remembered.coerceIn(0, totalItemsCount - 1)
    }

    fun getRememberedId(shelfKey: String): String? = shelfFocusIdMap[shelfKey]

    fun clear(shelfKey: String) {
        shelfFocusIndexMap.remove(shelfKey)
        shelfFocusIdMap.remove(shelfKey)
    }

    fun clearAll() {
        shelfFocusIndexMap.clear()
        shelfFocusIdMap.clear()
    }
}
