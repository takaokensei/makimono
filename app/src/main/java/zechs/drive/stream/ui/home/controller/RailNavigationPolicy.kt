package zechs.drive.stream.ui.home.controller

/**
 * Regras puras de navegação e foco da barra lateral (Rail) e estantes na Android TV.
 * Conforme Seções 4.10, 7.2 e 9.5 do Plano v2.
 */
object RailNavigationPolicy {

    enum class RailTab(val label: String) {
        INICIO("Início"),
        ANIMES("Animes"),
        FILMES("Filmes"),
        PASTAS("Pastas"),
        FAVORITOS("Favoritos"),
        CONFIGURACOES("Configurações")
    }

    enum class ContentTarget {
        HERO_PLAY,
        SEARCH_HEADER,
        FIRST_SHELF,
        CATALOG_GRID,
        CONTENT_VIEWPORT
    }

    /**
     * TV de 10 pés SEMPRE usa rail permanente fixo de 80dp (sem overlay recolhível).
     */
    fun isOverlaySidebarActive(
        isTenFootExperience: Boolean,
        hasDimOverlay: Boolean,
        isCollapsibleRailConfig: Boolean
    ): Boolean {
        if (isTenFootExperience) return false
        if (!hasDimOverlay) return false
        return isCollapsibleRailConfig
    }

    /**
     * Determina o alvo primário de foco ao navegar com D-pad Right a partir do rail.
     */
    fun resolveContentTargetOnRight(
        isHeroVisible: Boolean,
        isSearchVisible: Boolean,
        hasShelvesWithItems: Boolean,
        isCatalogVisible: Boolean
    ): ContentTarget {
        return when {
            isHeroVisible -> ContentTarget.HERO_PLAY
            isSearchVisible -> ContentTarget.SEARCH_HEADER
            hasShelvesWithItems -> ContentTarget.FIRST_SHELF
            isCatalogVisible -> ContentTarget.CATALOG_GRID
            else -> ContentTarget.CONTENT_VIEWPORT
        }
    }

    /**
     * Calcula o próximo índice de estante ao pressionar D-pad UP ou DOWN.
     */
    fun calculateAdjacentShelfIndex(
        currentIndex: Int,
        totalShelves: Int,
        isDpadDown: Boolean
    ): Int? {
        if (currentIndex < 0 || currentIndex >= totalShelves) return null
        val nextIndex = if (isDpadDown) currentIndex + 1 else currentIndex - 1
        return if (nextIndex in 0 until totalShelves) nextIndex else null
    }

    /**
     * Determina se o botão Back deve ser consumido pelo menu lateral (se expandido).
     */
    fun shouldConsumeBackPress(isSidebarExpanded: Boolean): Boolean {
        return isSidebarExpanded
    }

    /**
     * Determina a visibilidade dos blocos da tela inicial de acordo com a aba selecionada.
     */
    data class TabVisibility(
        val isHeroVisible: Boolean,
        val areShelvesVisible: Boolean,
        val isCatalogVisible: Boolean,
        val isFilterToggleVisible: Boolean
    )

    fun resolveTabVisibility(tab: RailTab, hasHeroData: Boolean, hasShelvesData: Boolean): TabVisibility {
        return when (tab) {
            RailTab.INICIO -> TabVisibility(
                isHeroVisible = hasHeroData,
                areShelvesVisible = hasShelvesData,
                isCatalogVisible = true,
                isFilterToggleVisible = false
            )
            RailTab.ANIMES, RailTab.FILMES, RailTab.FAVORITOS -> TabVisibility(
                isHeroVisible = false,
                areShelvesVisible = false,
                isCatalogVisible = true,
                isFilterToggleVisible = true
            )
            RailTab.PASTAS, RailTab.CONFIGURACOES -> TabVisibility(
                isHeroVisible = false,
                areShelvesVisible = false,
                isCatalogVisible = false,
                isFilterToggleVisible = false
            )
        }
    }

    /**
     * Valida e ajusta a restauração de foco em estantes horizontais (D-pad memory per Seção 7.3 e 9.5).
     */
    fun resolveShelfFocusRestoration(
        shelfIndex: Int?,
        itemIndex: Int?,
        shelfItemCounts: Map<Int, Int>
    ): Pair<Int, Int>? {
        if (shelfIndex == null || itemIndex == null) return null
        val count = shelfItemCounts[shelfIndex] ?: 0
        if (count <= 0) return null
        val safeItemIndex = itemIndex.coerceIn(0, count - 1)
        return shelfIndex to safeItemIndex
    }
}
