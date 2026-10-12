package zechs.drive.stream.ui.home.controller

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.ui.FocusedItemScroll
import zechs.drive.stream.utils.DeviceUi

/**
 * Coordenador de foco D-pad para a tela inicial em dispositivos de 10 pés (Android TV).
 * Garante foco previsível, sem armadilhas, transições suaves entre trilhos e memória de foco.
 * Conforme Seção 4.10, 7.2 e 9.5 do Plano v2.
 */
class FocusCoordinator(
    private val binding: FragmentHomeBinding
) {

    fun setupTenFootFocusChain(context: Context) {
        val isTabletOrTV = DeviceUi.isTenFootExperience(context) || DeviceUi.isTablet(context)
        if (!isTabletOrTV) return

        val search = binding.etSearchAnime
        val play = binding.btnFeaturedPlay
        val info = binding.btnFeaturedInfo
        val continueShelf = binding.rvContinueWatchingShelf
        val queueShelf = binding.rvWatchQueueShelf
        val starredShelf = binding.rvStarredShelf
        val followedShelf = binding.rvNewEpisodesShelf
        val recentlyAddedShelf = binding.rvRecentlyAddedShelf
        val catalog = binding.rvAnimeLibrary

        val railItems = listOfNotNull(
            binding.btnBrandLogo,
            binding.btnNavInicio,
            binding.btnNavAnimes,
            binding.btnNavFilmes,
            binding.btnNavPastas,
            binding.btnNavFavoritos,
            binding.userProfilePill,
            binding.btnNavConfig
        )
        val initialTarget = play?.takeIf { it.isShown } ?: search.takeIf { it.isShown } ?: catalog
        railItems.forEach { railItem ->
            railItem.nextFocusRightId = initialTarget.id
        }

        search.nextFocusLeftId = binding.btnNavInicio.id
        play?.nextFocusUpId = search.id
        info?.nextFocusUpId = search.id
        play?.nextFocusLeftId = binding.btnNavInicio.id
        play?.nextFocusRightId = info?.id ?: View.NO_ID
        info?.nextFocusLeftId = play?.id ?: View.NO_ID
        catalog.nextFocusLeftId = binding.btnNavInicio.id

        val root = binding.root
        val focusLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rows = listOfNotNull(continueShelf, queueShelf, followedShelf, starredShelf, recentlyAddedShelf, catalog).filter { it.isShown }
            val heroTarget = play?.takeIf { it.isShown } ?: search
            val primaryTarget = play?.takeIf { it.isShown } ?: search.takeIf { it.isShown } ?: rows.firstOrNull() ?: catalog
            railItems.forEach { railItem ->
                railItem.nextFocusRightId = primaryTarget.id
            }
            play?.nextFocusDownId = rows.firstOrNull()?.id ?: View.NO_ID
            info?.nextFocusDownId = rows.firstOrNull()?.id ?: View.NO_ID
            search.nextFocusDownId = play?.takeIf { it.isShown }?.id ?: rows.firstOrNull()?.id ?: View.NO_ID
            rows.forEachIndexed { index, row ->
                row.nextFocusUpId = rows.getOrNull(index - 1)?.id ?: heroTarget.id
                row.nextFocusDownId = rows.getOrNull(index + 1)?.id ?: View.NO_ID
            }
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(focusLayoutListener)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnGlobalLayoutListener(focusLayoutListener)
                v.removeOnAttachStateChangeListener(this)
            }
        })

        FocusedItemScroll.install(
            viewport = binding.contentScrollView,
            rows = listOfNotNull(continueShelf, queueShelf, followedShelf, starredShelf, recentlyAddedShelf, catalog)
        )
    }

    /** Mantém o movimento do D-pad dentro da coluna correspondente ao cruzar estantes. */
    fun focusAdjacentHomeShelf(
        source: RecyclerView,
        focusedChild: View,
        keyCode: Int
    ): Boolean {
        val rows = listOfNotNull(
            binding.rvContinueWatchingShelf,
            binding.rvWatchQueueShelf,
            binding.rvNewEpisodesShelf,
            binding.rvStarredShelf,
            binding.rvRecentlyAddedShelf,
            binding.rvAnimeLibrary
        ).filter { it.isShown && (it.adapter?.itemCount ?: 0) > 0 }
        val sourceIndex = rows.indexOf(source)
        if (sourceIndex < 0) return false

        val targetIndex = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> sourceIndex - 1
            KeyEvent.KEYCODE_DPAD_DOWN -> sourceIndex + 1
            else -> return false
        }
        if (targetIndex < 0) {
            (binding.btnFeaturedPlay?.takeIf { it.isShown } ?: binding.etSearchAnime).requestFocus()
            return true
        }
        val target = rows.getOrNull(targetIndex) ?: return false
        val sourcePosition = source.getChildAdapterPosition(focusedChild)
            .takeIf { it != RecyclerView.NO_POSITION } ?: 0
        val targetPosition = if (target.layoutManager is GridLayoutManager) {
            sourcePosition % (target.layoutManager as GridLayoutManager).spanCount
        } else {
            sourcePosition
        }.coerceIn(0, (target.adapter?.itemCount ?: 1) - 1)

        target.scrollToPosition(targetPosition)
        zechs.drive.stream.utils.LifecycleDelays.postDelayed(target, 64) {
            if (target.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus() != true) {
                zechs.drive.stream.utils.LifecycleDelays.postDelayed(target, 64) {
                    target.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
                }
            }
        }
        return true
    }

    fun focusContent(
        continueWatchingCount: Int,
        watchQueueCount: Int,
        followedCount: Int,
        starredCount: Int,
        recentCount: Int
    ) {
        val target = when {
            binding.featuredHeroContainer?.visibility == View.VISIBLE -> binding.btnFeaturedPlay
            binding.rvContinueWatchingShelf.visibility == View.VISIBLE && continueWatchingCount > 0 -> binding.rvContinueWatchingShelf
            binding.rvWatchQueueShelf?.visibility == View.VISIBLE && watchQueueCount > 0 -> binding.rvWatchQueueShelf
            binding.rvNewEpisodesShelf.visibility == View.VISIBLE && followedCount > 0 -> binding.rvNewEpisodesShelf
            binding.rvStarredShelf?.visibility == View.VISIBLE && starredCount > 0 -> binding.rvStarredShelf
            binding.rvRecentlyAddedShelf.visibility == View.VISIBLE && recentCount > 0 -> binding.rvRecentlyAddedShelf
            binding.rvAnimeLibrary.visibility == View.VISIBLE -> binding.rvAnimeLibrary
            else -> binding.contentScrollView
        }
        if (target === binding.rvAnimeLibrary) {
            binding.rvAnimeLibrary.post {
                binding.rvAnimeLibrary.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    ?: binding.rvAnimeLibrary.requestFocus()
            }
        } else {
            target?.requestFocus()
        }
    }

    fun restoreShelfFocus(shelfIndex: Int, itemIndex: Int) {
        val shelves = listOfNotNull(
            binding.rvContinueWatchingShelf,
            binding.rvWatchQueueShelf,
            binding.rvNewEpisodesShelf,
            binding.rvStarredShelf,
            binding.rvRecentlyAddedShelf,
            binding.rvAnimeLibrary
        )
        val shelf = shelves.getOrNull(shelfIndex) ?: return
        if (!shelf.isShown) return

        val itemCount = shelf.adapter?.itemCount ?: 0
        val resolved = RailNavigationPolicy.resolveShelfFocusRestoration(
            shelfIndex = shelfIndex,
            itemIndex = itemIndex,
            shelfItemCounts = mapOf(shelfIndex to itemCount)
        ) ?: return

        val targetPos = resolved.second
        shelf.scrollToPosition(targetPos)
        zechs.drive.stream.utils.LifecycleDelays.postDelayed(shelf, 100) {
            shelf.findViewHolderForAdapterPosition(targetPos)?.itemView?.requestFocus()
        }
    }
}
