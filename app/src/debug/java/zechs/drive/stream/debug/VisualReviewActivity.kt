package zechs.drive.stream.debug

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import zechs.drive.stream.R
import zechs.drive.stream.data.model.*
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.settings.DriveFolderPickerDialog
import zechs.drive.stream.ui.profile.ProfilesAdapter
import zechs.drive.stream.ui.profile.ProfileUiModel
import zechs.drive.stream.ui.player.PlayerQuickOptions
import zechs.drive.stream.ui.FocusedItemScroll
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.state.ResourceErrorKind
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.ui.profile.ProfileEditorDialog
import zechs.drive.stream.ui.series.SeriesMoreActionsDialog
import zechs.drive.stream.ui.series.SeriesEpisodeContextMenu
import zechs.drive.stream.ui.series.SeriesSynopsisDisclosure
import zechs.drive.stream.ui.series.presentSeriesEpisode
import zechs.drive.stream.ui.series.adapter.SeriesDetailEpisodeAdapter
import zechs.drive.stream.ui.series.adapter.SeriesEpisodeItem
import zechs.drive.stream.ui.home.adapter.ContinueWatchingAdapter
import zechs.drive.stream.ui.home.adapter.StarredShelfAdapter
import zechs.drive.stream.ui.home.adapter.WatchQueueShelfAdapter
import zechs.drive.stream.ui.home.FeaturedPreviewScheduler
import zechs.drive.stream.ui.home.FeaturedHeroBinder
import zechs.drive.stream.ui.home.HomeShelfScroll
import zechs.drive.stream.ui.home.preserveFocusedShelfOrder
import zechs.drive.stream.ui.home.HomeViewModel
import zechs.drive.stream.databinding.FragmentHomeBinding
import zechs.drive.stream.utils.ProfileManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

/** Renders production XML and adapters with explicit fixtures, without touching account or Room. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class VisualReviewActivity : AppCompatActivity(), zechs.drive.stream.ui.player.PlayerModalHost {
    private val poster = "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
    private val backdrop = "https://images.justwatch.com/backdrop/308526637/s1440/sousou-no-frieren.jpg"
    private val jujutsuBackdrop = "https://images.justwatch.com/backdrop/300823240/s1440/jujutsu-kaisen.jpg"
    private var playerFixtureControls: View? = null
    private var homePreviewScheduler: FeaturedPreviewScheduler? = null
    private var restorePlayerFixtureControls = false
    private var playerFixtureFocus: View? = null

    override fun onPlayerModalVisibilityChanged(isVisible: Boolean) {
        val controls = playerFixtureControls ?: return
        if (isVisible) {
            restorePlayerFixtureControls = controls.visibility == View.VISIBLE
            playerFixtureFocus = controls.findFocus() ?: currentFocus
            if (restorePlayerFixtureControls) controls.visibility = View.INVISIBLE
            TvFocusRing.clear(findViewById(android.R.id.content))
        } else if (restorePlayerFixtureControls) {
            controls.visibility = View.VISIBLE
            val focusToRestore = playerFixtureFocus
            focusToRestore?.post { focusToRestore.requestFocus() }
            restorePlayerFixtureControls = false
            playerFixtureFocus = null
        }
    }
    private fun text(id: Int, value: String) { findViewById<TextView>(id)?.text = value }
    private fun show(id: Int) { findViewById<View>(id)?.visibility = View.VISIBLE }
    private fun art(id: Int, url: String = backdrop) { findViewById<ImageView>(id)?.let { Glide.with(this).load(url).into(it) } }
    private fun scheduleHomePreview(folder: DriveFile, focusAnchor: View) {
        homePreviewScheduler?.schedule(folder) {
            focusAnchor.isAttachedToWindow && focusAnchor.hasFocus()
        }
    }
    private fun files(count: Int, video: Boolean) = (1..count).map {
        FilesDataModel.File(DriveFile("fixture-$it", if(video) "Frieren - E${it.toString().padStart(2,'0')} - O fim da viagem.mkv" else "Sousou no Frieren",
            if(video) 1400000000L else null, if(video) "video/x-matroska" else "application/vnd.google-apps.folder",
            null, if(video) backdrop else poster, ShortcutDetails(), Starred.UNSTARRED, if(video) null else poster))
    }
    private fun homeFolders(count: Int, starred: Boolean = false) = listOf(
        "Sousou no Frieren", "Jujutsu Kaisen", "Chainsaw Man", "Bakemonogatari",
        "Dungeon Meshi", "Mob Psycho 100", "Spy x Family", "Vinland Saga"
    ).take(count).mapIndexed { index, title ->
        DriveFile(
            id = "fixture-home-folder-$index-${if (starred) "starred" else "library"}",
            name = title,
            size = null,
            mimeType = "application/vnd.google-apps.folder",
            iconLink = null,
            thumbnailLink = when {
                index == 1 -> jujutsuBackdrop
                index % 2 == 0 -> backdrop
                else -> poster
            },
            shortcutDetails = ShortcutDetails(),
            starred = if (starred) Starred.STARRED else Starred.UNSTARRED,
            posterUrl = when {
                index == 1 -> jujutsuBackdrop
                index % 2 == 0 -> backdrop
                else -> poster
            },
            modifiedTime = "2026-10-${(6 - index).coerceAtLeast(1).toString().padStart(2, '0')}T12:00:00Z"
        )
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (intent.getBooleanExtra("reduced_motion_preview", false)) {
            zechs.drive.stream.utils.MotionPolicy.updateActiveProfilePreference(
                zechs.drive.stream.utils.MotionPreference.REDUCED
            )
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        when(intent.getStringExtra("screen")) {
            "profiles" -> profiles()
            "files" -> fileScreen()
            "library_root_picker" -> libraryRootPickerFixture()
            "files_auth_error" -> filesAuthError()
            "details" -> details()
            "details_episode_focus" -> details(focusEpisode = true)
            "details_episode_description" -> details(focusEpisode = true, episodeDescriptions = true)
            "details_episode_names" -> details(episodeNameVariants = true)
            "details_long_synopsis" -> details(longSynopsis = true)
            "details_synopsis_rebind" -> details(longSynopsis = true, rebindOnDisclosureFocus = true)
            "player" -> player()
            "player_exo_activity" -> launchActualPlayer(zechs.drive.stream.ui.player.PlayerActivity::class.java)
            "player_mpv_activity" -> launchActualPlayer(zechs.drive.stream.ui.player2.MPVActivity::class.java)
            "player_exo_multitrack" -> launchActualPlayer(
                zechs.drive.stream.ui.player.PlayerActivity::class.java,
                R.raw.review_multitrack,
                "mkv"
            )
            "player_mpv_multitrack" -> launchActualPlayer(
                zechs.drive.stream.ui.player2.MPVActivity::class.java,
                R.raw.review_multitrack,
                "mkv"
            )
            "settings" -> settingsScreen()
            "signin" -> signInScreen()
            "files_access_denied" -> filesAccessDenied()
            "files_page_error" -> filesPageError()
            "home_refresh_order" -> homeRefreshOrder()
            else -> home()
        }
        val dialogType = intent.getStringExtra("dialog")
        if (dialogType != null) {
            window.decorView.postDelayed({
                when(dialogType) {
                    "subtitles" -> zechs.drive.stream.ui.player.SubtitleStyleDialog.show(this, zechs.drive.stream.utils.SubtitleStyle()) {}
                    "rating" -> zechs.drive.stream.ui.player.MalRatingDialog.show(this, "Sousou no Frieren", 28, {})
                    "theme" -> showThemeDialog()
                    "glass_menu" -> showGlassMenu()
                    "profile_editor" -> showProfileEditorFixture()
                }
            }, 800)
        }
        val root = findViewById<ViewGroup>(android.R.id.content)
        TvFocusRing.install(root)
        // Visible watermark prevents fixture screenshots being confused with signed-in production data.
        val label = TextView(this).apply { text = "PRÉVIA · DADOS DE TESTE"; textSize = 8f; setTextColor(0xff9bb2c9.toInt()); setPadding(8,2,8,2); setBackgroundColor(0xcc030c16.toInt()) }
        addContentView(label, android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM or android.view.Gravity.END))
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
    private fun signInScreen() {
        val container = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(container)
        supportFragmentManager.beginTransaction()
            .replace(container.id, zechs.drive.stream.ui.signin.SignInFragment())
            .commitNow()
    }

    /** Exercises ListAdapter focus continuity with a delayed synthetic library refresh. */
    private fun homeRefreshOrder() {
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            setBackgroundColor(0xff06111d.toInt())
        }
        val heading = TextView(this).apply {
            text = "Prateleira focada · atualização de teste"
            textSize = 22f
            setTextColor(0xfff3f7fc.toInt())
        }
        val instruction = TextView(this).apply {
            text = "Use ←/→. Ao focar Jujutsu Kaisen, um snapshot em ordem diferente chegará após 1,5 s."
            textSize = 14f
            setTextColor(0xffa9bbce.toInt())
            setPadding(0, 12, 0, 12)
        }
        val status = TextView(this).apply {
            textSize = 13f
            setTextColor(0xff29d7ed.toInt())
            setPadding(0, 8, 0, 12)
        }
        val shelf = RecyclerView(this).apply {
            id = View.generateViewId()
            layoutManager = LinearLayoutManager(this@VisualReviewActivity, RecyclerView.HORIZONTAL, false)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(heading)
        root.addView(instruction)
        root.addView(status)
        root.addView(shelf, android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            260
        ))
        setContentView(root)

        val adapter = StarredShelfAdapter(onClick = {})
        shelf.adapter = adapter
        var refreshScheduled = false
        fun updateStatus(prefix: String) {
            status.text = "$prefix · ordem: " + adapter.currentList.joinToString(" → ") { it.name }
        }
        adapter.onFocusItemListener = { focused ->
            val position = shelf.getChildAdapterPosition(focused)
            val item = adapter.currentList.getOrNull(position)
            if (item != null) {
                updateStatus("Foco: ${item.name}")
                if (position == 1 && !refreshScheduled) {
                    refreshScheduled = true
                    shelf.postDelayed({
                        val visibleIds = adapter.currentList.map { it.id }.toSet()
                        val refreshed = homeFolders(4).reversed().map { file ->
                            if (file.id in visibleIds) file.copy(name = "${file.name} atualizado") else file
                        }
                        val next = if (shelf.hasFocus()) {
                            preserveFocusedShelfOrder(adapter.currentList, refreshed)
                        } else {
                            refreshed
                        }
                        adapter.submitList(next) {
                            updateStatus("Snapshot recebido · foco ativo: ${shelf.hasFocus()}")
                        }
                    }, 1_500)
                }
            }
        }
        val initial = homeFolders(3)
        adapter.submitList(initial) {
            updateStatus("Inicial")
            shelf.post { shelf.getChildAt(0)?.requestFocusFromTouch() }
        }
    }

    private fun home() {
        val homeRoot = LayoutInflater.from(this).inflate(R.layout.fragment_home, null, false)
        setContentView(homeRoot)
        val heroBinder = FeaturedHeroBinder(FragmentHomeBinding.bind(homeRoot), this)
        heroBinder.bind(
            HomeViewModel.FeaturedAnime(
                title = getString(R.string.home_preview_fixture_title),
                synopsis = getString(R.string.home_preview_fixture_synopsis),
                backdropUrl = backdrop,
                folderId = "fixture-featured-frieren",
                posterUrl = poster,
                year = 2023,
                episodeCount = 28
            ),
            reduceMotionEnabled = false
        )
        homePreviewScheduler = FeaturedPreviewScheduler(lifecycleScope) { folder ->
            heroBinder.bind(
                HomeViewModel.FeaturedAnime(
                    title = folder.name,
                    synopsis = getString(R.string.home_preview_synopsis, folder.name),
                    backdropUrl = folder.thumbnailLarge ?: folder.thumbnailLink ?: folder.posterUrl,
                    folderId = folder.id,
                    posterUrl = folder.posterUrl ?: folder.thumbnailLink,
                    year = 2026,
                    episodeCount = 5,
                    score = 8.4f,
                    ageRating = "12+",
                    isFocusedPreview = true
                ),
                reduceMotionEnabled = false
            )
        }
        listOf(
            R.id.rvNewEpisodesShelf,
            R.id.rvStarredShelf,
            R.id.rvRecentlyAddedShelf
        ).forEach { id ->
            findViewById<RecyclerView>(id)?.let { shelf ->
                shelf.isNestedScrollingEnabled = false
                shelf.layoutParams = shelf.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }
        }
        show(R.id.topHeaderBar)
        findViewById<View>(R.id.btnFeaturedPlay)?.nextFocusUpId = R.id.etSearchAnime
        findViewById<View>(R.id.btnFeaturedInfo)?.nextFocusUpId = R.id.etSearchAnime
        TvAccessibilityLayout.adaptHomeHero(
            this,
            findViewById(R.id.featuredHeroContainer),
            findViewById(R.id.btnFeaturedPlay),
            findViewById(R.id.btnFeaturedInfo)
        )
        show(R.id.featuredHeroContainer);show(R.id.shelfHeaderRow);show(R.id.rvContinueWatchingShelf)
        findViewById<View>(R.id.containerViewToggle)?.visibility=View.GONE
        findViewById<View>(R.id.containerItemCount)?.visibility=View.GONE
        findViewById<ImageView>(R.id.ivUserAvatar)?.let { avatar ->
            avatar.imageTintList = null
            val artCatalog = EntryPointAccessors.fromApplication(
                applicationContext,
                VisualReviewDependencies::class.java
            ).profileArtCatalog()
            Glide.with(this)
                .load(artCatalog.defaultAvatarForProfile("fixture-caua")?.url)
                .placeholder(R.drawable.avatar_caua)
                .error(R.drawable.avatar_caua)
                .circleCrop()
                .into(avatar)
        }
        text(R.id.tvUserName,"Cauã")
        val grid=findViewById<RecyclerView>(R.id.rvAnimeLibrary)
        grid.layoutManager=GridLayoutManager(this,((resources.configuration.screenWidthDp-172)/132).coerceIn(3,7))
        val focusHomeNavigation = {
            findViewById<View>(R.id.btnNavInicio)?.requestFocus()
            true
        }
        val gridAdapter = FilesAdapter({},{},{_,_->},compactCatalog=true).apply {
            isGridMode = true
            submitList(files(14,false))
        }
        grid.adapter = gridAdapter
        gridAdapter.onFocusItemListener = { focused ->
            HomeShelfScroll.alignHeading(
                findViewById(R.id.contentScrollView),
                findViewById(R.id.tvHomeCatalogTitle),
                focused
            )
            val position = grid.getChildAdapterPosition(focused)
            (gridAdapter.currentList.getOrNull(position) as? FilesDataModel.File)
                ?.driveFile
                ?.takeIf { it.isFolder || it.isShortcutFolder }
                ?.let { scheduleHomePreview(it, focused) }
        }
        gridAdapter.onDpadLeftListener = { itemView ->
            val position = grid.getChildAdapterPosition(itemView)
            val spanCount = (grid.layoutManager as? GridLayoutManager)?.spanCount ?: 1
            if (position != RecyclerView.NO_POSITION && position % spanCount == 0) {
                focusHomeNavigation()
            } else {
                false
            }
        }
        val continueShelf = findViewById<RecyclerView>(R.id.rvContinueWatchingShelf)
        continueShelf.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        continueShelf.adapter = ContinueWatchingAdapter {}.apply {
            onDpadLeftListener = { focusHomeNavigation() }
            onFocusItemListener = { focused ->
                HomeShelfScroll.alignHeading(
                    findViewById(R.id.contentScrollView),
                    findViewById(R.id.tvShelfLabel),
                    focused
                )
            }
            onLongClickListener = { item, anchor ->
                zechs.drive.stream.ui.home.ContinueWatchingContextMenu.show(
                    context = this@VisualReviewActivity,
                    watchItem = item,
                    focusAnchor = anchor
                ) { action ->
                    Toast.makeText(this@VisualReviewActivity, action.name, Toast.LENGTH_SHORT).show()
                }
            }
            submitList((1..6).map { episode ->
                WatchList(
                    name = "Sousou no Frieren - Ep. ${episode.toString().padStart(2, '0')}",
                    videoId = "fixture-watch-$episode",
                    watchedDuration = 17 * 60_000L,
                    // Preserve a visible unknown-duration case for truthful Home metadata QA.
                    totalDuration = if (episode == 1) 0L else 24 * 60_000L,
                    thumbnailLink = if (episode % 2 == 0) poster else backdrop,
                    profileId = "fixture-profile"
                )
            })
        }

        val queueShelf = findViewById<RecyclerView>(R.id.rvWatchQueueShelf)
        queueShelf?.let { row ->
            row.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            row.adapter = WatchQueueShelfAdapter {}.apply {
                onDpadLeftListener = { focusHomeNavigation() }
                onFocusItemListener = { focused ->
                    HomeShelfScroll.alignHeading(
                        findViewById(R.id.contentScrollView),
                        findViewById(R.id.tvQueueShelfLabel),
                        focused
                    )
                }
                submitList(homeFolders(5).mapIndexed { index, file ->
                    WatchQueueItem(
                        profileId = "fixture-profile",
                        fileId = file.id,
                        name = "${file.name} - Ep. ${(index + 1).toString().padStart(2, '0')}",
                        posterUrl = file.posterUrl,
                        orderIndex = index
                    )
                })
            }
            row.visibility = View.VISIBLE
            findViewById<View>(R.id.tvQueueShelfLabel)?.visibility = View.VISIBLE
        }

        val followedShelf = findViewById<RecyclerView>(R.id.rvNewEpisodesShelf)
        followedShelf?.let { row ->
            row.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            val adapter = StarredShelfAdapter(
                onClick = {},
                folderBadgeTextResId = R.string.home_followed_type,
                folderSubtitleTextResId = R.string.home_followed_subtitle
            ).apply {
                onDpadLeftListener = { focusHomeNavigation() }
                submitList(homeFolders(5))
                onFocusItemListener = { focused ->
                    HomeShelfScroll.alignHeading(
                        findViewById(R.id.contentScrollView),
                        findViewById(R.id.tvNewEpisodesShelfLabel),
                        focused
                    )
                    val position = row.getChildAdapterPosition(focused)
                    currentList.getOrNull(position)?.let { scheduleHomePreview(it, focused) }
                }
            }
            row.adapter = adapter
            row.visibility = View.VISIBLE
            findViewById<View>(R.id.tvNewEpisodesShelfLabel)?.visibility = View.VISIBLE
        }

        val favoritesShelf = findViewById<RecyclerView>(R.id.rvStarredShelf)
        favoritesShelf?.let { row ->
            row.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            val adapter = StarredShelfAdapter(onClick = {}).apply {
                onDpadLeftListener = { focusHomeNavigation() }
                submitList(homeFolders(5, starred = true))
                onFocusItemListener = { focused ->
                    HomeShelfScroll.alignHeading(
                        findViewById(R.id.contentScrollView),
                        findViewById(R.id.tvMyListShelfLabel),
                        focused
                    )
                    val position = row.getChildAdapterPosition(focused)
                    currentList.getOrNull(position)?.let { scheduleHomePreview(it, focused) }
                }
            }
            row.adapter = adapter
            row.visibility = View.VISIBLE
            findViewById<View>(R.id.tvMyListShelfLabel)?.visibility = View.VISIBLE
        }

        val recentlyAddedShelf = findViewById<RecyclerView>(R.id.rvRecentlyAddedShelf)
        recentlyAddedShelf?.let { row ->
            row.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            val adapter = StarredShelfAdapter(
                onClick = {},
                folderBadgeTextResId = R.string.home_recently_added_type_series,
                folderSubtitleTextResId = R.string.home_recently_added_subtitle
            ).apply {
                onDpadLeftListener = { focusHomeNavigation() }
                submitList(homeFolders(5))
                onFocusItemListener = { focused ->
                    HomeShelfScroll.alignHeading(
                        findViewById(R.id.contentScrollView),
                        findViewById(R.id.tvRecentlyAddedShelfLabel),
                        focused
                    )
                    val position = row.getChildAdapterPosition(focused)
                    currentList.getOrNull(position)?.let { scheduleHomePreview(it, focused) }
                }
            }
            row.adapter = adapter
            row.visibility = View.VISIBLE
            findViewById<View>(R.id.tvRecentlyAddedShelfLabel)?.visibility = View.VISIBLE
        }

        val focusRows = listOfNotNull(
            continueShelf,
            queueShelf?.takeIf { it.visibility == View.VISIBLE },
            followedShelf?.takeIf { it.visibility == View.VISIBLE },
            favoritesShelf?.takeIf { it.visibility == View.VISIBLE },
            recentlyAddedShelf?.takeIf { it.visibility == View.VISIBLE },
            grid
        )
        val firstRowId = focusRows.firstOrNull()?.id ?: View.NO_ID
        findViewById<View>(R.id.btnFeaturedPlay)?.nextFocusDownId = firstRowId
        findViewById<View>(R.id.btnFeaturedInfo)?.nextFocusDownId = firstRowId
        focusRows.forEachIndexed { index, row ->
            row.nextFocusUpId = focusRows.getOrNull(index - 1)?.id
                ?: R.id.btnFeaturedPlay
            row.nextFocusDownId = focusRows.getOrNull(index + 1)?.id ?: View.NO_ID
        }
        val heroPlay = findViewById<View>(R.id.btnFeaturedPlay)
        val onVertical = { source: RecyclerView, focused: View, keyCode: Int ->
            focusAdjacentHomeShelf(focusRows, heroPlay, source, focused, keyCode)
        }
        (continueShelf.adapter as? ContinueWatchingAdapter)?.onDpadVerticalListener = { focused, key ->
            onVertical(continueShelf, focused, key)
        }
        (queueShelf?.adapter as? WatchQueueShelfAdapter)?.onDpadVerticalListener = { focused, key ->
            onVertical(queueShelf, focused, key)
        }
        (followedShelf.adapter as? StarredShelfAdapter)?.onDpadVerticalListener = { focused, key ->
            onVertical(followedShelf, focused, key)
        }
        (favoritesShelf?.adapter as? StarredShelfAdapter)?.onDpadVerticalListener = { focused, key ->
            onVertical(favoritesShelf, focused, key)
        }
        (recentlyAddedShelf?.adapter as? StarredShelfAdapter)?.onDpadVerticalListener = { focused, key ->
            onVertical(recentlyAddedShelf, focused, key)
        }
        FocusedItemScroll.install(findViewById(R.id.contentScrollView), focusRows)
        findViewById<View>(R.id.btnFeaturedPlay)?.postDelayed({ findViewById<View>(R.id.btnFeaturedPlay)?.requestFocus() }, 300)
    }

    private fun focusAdjacentHomeShelf(
        rows: List<RecyclerView>,
        heroPlay: View,
        source: RecyclerView,
        focusedChild: View,
        keyCode: Int
    ): Boolean {
        val sourceIndex = rows.indexOf(source)
        val targetIndex = when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> sourceIndex - 1
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> sourceIndex + 1
            else -> return false
        }
        if (sourceIndex < 0) return false
        if (targetIndex < 0) {
            heroPlay.requestFocus()
            return true
        }
        val target = rows.getOrNull(targetIndex) ?: return false
        val sourcePosition = source.getChildAdapterPosition(focusedChild)
            .takeIf { it != RecyclerView.NO_POSITION } ?: 0
        val column = (target.layoutManager as? GridLayoutManager)?.spanCount ?: 1
        val targetPosition = (sourcePosition % column).coerceIn(0, (target.adapter?.itemCount ?: 1) - 1)
        target.scrollToPosition(targetPosition)
        target.postDelayed({
            val card = target.findViewHolderForAdapterPosition(targetPosition)?.itemView
            if (card?.requestFocus() != true) {
                target.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
            }
        }, 64)
        return true
    }
    private fun profiles() {
        setContentView(R.layout.fragment_profile_selection)
        findViewById<View?>(R.id.btnLogOut)?.let { btn ->
            TvAccessibilityLayout.adaptProfileActions(this, listOf(btn))
            btn.setOnClickListener {
                android.widget.Toast.makeText(this, "Log out clicked", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val rv=findViewById<RecyclerView>(R.id.rvProfiles)
        rv.layoutParams=rv.layoutParams.apply { height=ViewGroup.LayoutParams.WRAP_CONTENT }
        rv.layoutManager=LinearLayoutManager(this,RecyclerView.HORIZONTAL,false)
        val artCatalog = EntryPointAccessors.fromApplication(
            applicationContext,
            VisualReviewDependencies::class.java
        ).profileArtCatalog()
        val adapter=ProfilesAdapter({},{},{}, profileArtCatalog = artCatalog).apply {
            submitList(listOf(ProfileUiModel.ProfileItem(UserProfile("one","Cauã",avatarResName="avatar_anime",isAdmin=true),true),ProfileUiModel.ProfileItem(UserProfile("two","Visitante",avatarResName="avatar_anime"),false),ProfileUiModel.ProfileItem(UserProfile("three","Kids / Shounen",avatarResName="avatar_anime",isKids=true),false),ProfileUiModel.AddProfileItem))
        }
        rv.adapter=adapter
        rv.postDelayed({rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()},500)
    }

    private fun showProfileEditorFixture() {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            VisualReviewDependencies::class.java
        )
        val fragment = ProfileEditorFixtureFragment()
        supportFragmentManager.beginTransaction()
            .add(fragment, "profile-editor-visual-review")
            .commitNow()
        ProfileEditorDialog(fragment, dependencies.profileManager(), dependencies.profileArtCatalog()).show(null)
    }
    private fun fileScreen() {
        setContentView(R.layout.fragment_files)
        TvAccessibilityLayout.adaptFilesChrome(
            this,
            findViewById(R.id.filesNavRail),
            listOf(
                findViewById(R.id.filesHome),
                findViewById(R.id.filesCatalog),
                findViewById(R.id.filesFolders),
                findViewById(R.id.filesFavorites),
                findViewById(R.id.filesSettings)
            ),
            findViewById(R.id.searchContainer),
            findViewById(R.id.etSearch),
            listOf(findViewById(R.id.btnClearSearch), findViewById(R.id.btnToggleGrid))
        )
        // Match FilesFragment: the toolbar shows the current folder name, not the whole path.
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title="Temporada 01"
        val rv=findViewById<RecyclerView>(R.id.rvList)
        val entries = files(12,true)
        val adapter = FilesAdapter({},{},{_,_->}).apply { isGridMode=true }
        var gridMode = true
        fun render(query: String = "") {
            val matching = entries.filter { (it as? FilesDataModel.File)?.driveFile?.name?.contains(query, true) != false }
            adapter.submitList(matching)
            val search = findViewById<android.widget.EditText>(R.id.etSearch)
            val clearSearch = findViewById<View>(R.id.btnClearSearch)
            val stateView = findViewById<zechs.drive.stream.ui.common.StateView>(R.id.stateView)
            val error = findViewById<View>(R.id.error)
            val isEmptySearch = matching.isEmpty() && query.isNotBlank() && entries.isNotEmpty()
            error.visibility = View.GONE
            if (matching.isEmpty()) {
                rv.visibility = View.INVISIBLE
                stateView.visibility = View.VISIBLE
                if (isEmptySearch) {
                    stateView.showState(
                        iconRes = R.drawable.ic_search_24,
                        title = getString(R.string.files_search_empty_title),
                        message = getString(R.string.files_search_empty_message),
                        actionText = getString(R.string.files_search_clear_action),
                        onActionClick = {
                            search.text?.clear()
                            search.requestFocus()
                            search.setSelection(search.text?.length ?: 0)
                        }
                    )
                    search.nextFocusDownId = R.id.btnStateAction
                    clearSearch.nextFocusDownId = R.id.btnStateAction
                    stateView.findViewById<View>(R.id.btnStateAction).nextFocusUpId = R.id.etSearch
                } else {
                    stateView.setState(zechs.drive.stream.ui.common.StateView.StateType.EMPTY_FOLDER)
                    search.nextFocusDownId = rv.id
                    clearSearch.nextFocusDownId = rv.id
                }
            } else {
                stateView.visibility = View.GONE
                rv.visibility = View.VISIBLE
                search.nextFocusDownId = rv.id
                clearSearch.nextFocusDownId = rv.id
            }
        }
        fun updateMode() {
            adapter.isGridMode = gridMode
            rv.layoutManager = if (gridMode) {
                GridLayoutManager(this,TvAccessibilityLayout.gridColumnsForFontScale(4,resources.configuration.fontScale))
            } else LinearLayoutManager(this)
            findViewById<ImageView>(R.id.btnToggleGrid).setImageResource(
                if (gridMode) R.drawable.ic_list_view_24 else R.drawable.ic_grid_view_24
            )
            findViewById<ImageView>(R.id.btnToggleGrid).contentDescription = getString(
                if (gridMode) R.string.files_switch_to_list else R.string.files_switch_to_grid
            )
        }
        rv.adapter=adapter
        rv.isFocusable = true
        findViewById<View>(R.id.etSearch).nextFocusDownId = rv.id
        findViewById<View>(R.id.btnClearSearch).nextFocusDownId = rv.id
        findViewById<View>(R.id.btnToggleGrid).nextFocusDownId = rv.id
        rv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val position = rv.getChildAdapterPosition(view)
                val firstRowSize = (rv.layoutManager as? GridLayoutManager)?.spanCount ?: 1
                if (position in 0 until firstRowSize) view.nextFocusUpId = R.id.etSearch
            }
            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })
        render()
        updateMode()
        findViewById<View>(R.id.btnToggleGrid).setOnClickListener {
            gridMode = !gridMode
            updateMode()
        }
        findViewById<android.widget.EditText>(R.id.etSearch).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                findViewById<View>(R.id.btnClearSearch).visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                render(query)
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        findViewById<View>(R.id.btnClearSearch).setOnClickListener {
            val search = findViewById<android.widget.EditText>(R.id.etSearch)
            search.text.clear()
            search.requestFocus()
            search.setSelection(search.text.length)
        }
        rv.postDelayed({rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()},500)
    }
    private fun filesAuthError() {
        setContentView(R.layout.fragment_files)
        TvAccessibilityLayout.adaptFilesChrome(
            this,
            findViewById(R.id.filesNavRail),
            listOf(
                findViewById(R.id.filesHome),
                findViewById(R.id.filesCatalog),
                findViewById(R.id.filesFolders),
                findViewById(R.id.filesFavorites),
                findViewById(R.id.filesSettings)
            ),
            findViewById(R.id.searchContainer),
            findViewById(R.id.etSearch),
            listOf(findViewById(R.id.btnClearSearch), findViewById(R.id.btnToggleGrid))
        )
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title="oneblacki"
        findViewById<View>(R.id.rvList).visibility = View.INVISIBLE
        findViewById<View>(R.id.error).visibility = View.VISIBLE
        findViewById<TextView>(R.id.error_txt).text = "Sessão do Google Drive ausente ou expirada. Conecte sua conta para continuar."
        findViewById<View>(R.id.btnRetry).visibility = View.VISIBLE
        findViewById<View>(R.id.btnConnectDrive).visibility = View.VISIBLE
        findViewById<View>(R.id.btnConnectDrive).apply {
            isFocusableInTouchMode = true
            postDelayed({ requestFocusFromTouch() }, 500)
        }
    }

    /** Renders the production Files permission-denied state without connecting to Drive. */
    private fun filesAccessDenied() {
        setContentView(R.layout.fragment_files)
        TvAccessibilityLayout.adaptFilesChrome(
            this,
            findViewById(R.id.filesNavRail),
            listOf(
                findViewById(R.id.filesHome),
                findViewById(R.id.filesCatalog),
                findViewById(R.id.filesFolders),
                findViewById(R.id.filesFavorites),
                findViewById(R.id.filesSettings)
            ),
            findViewById(R.id.searchContainer),
            findViewById(R.id.etSearch),
            listOf(findViewById(R.id.btnClearSearch), findViewById(R.id.btnToggleGrid))
        )
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title = "oneblacki"
        findViewById<View>(R.id.rvList).visibility = View.INVISIBLE
        findViewById<zechs.drive.stream.ui.common.StateView>(R.id.stateView).apply {
            visibility = View.VISIBLE
            setState(zechs.drive.stream.ui.common.StateView.StateType.ERROR_ACCESS_DENIED) { finish() }
            postDelayed({ findViewById<View>(R.id.btnStateAction)?.requestFocus() }, 500)
        }
    }

    /** Renders the production inline retry row while keeping the first Drive page visible. */
    private fun filesPageError() {
        setContentView(R.layout.fragment_files)
        TvAccessibilityLayout.adaptFilesChrome(
            this,
            findViewById(R.id.filesNavRail),
            listOf(
                findViewById(R.id.filesHome),
                findViewById(R.id.filesCatalog),
                findViewById(R.id.filesFolders),
                findViewById(R.id.filesFavorites),
                findViewById(R.id.filesSettings)
            ),
            findViewById(R.id.searchContainer),
            findViewById(R.id.etSearch),
            listOf(findViewById(R.id.btnClearSearch), findViewById(R.id.btnToggleGrid))
        )
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title = "oneblacki"
        findViewById<View>(R.id.stateView).visibility = View.GONE
        val status = findViewById<TextView>(R.id.tvItemCount)
        val adapter = FilesAdapter(
            onClickListener = {},
            onLongClickListener = {},
            onStarClickListener = { _, _ -> },
            onPageErrorAction = { status.text = getString(R.string.files_page_retry_requested) }
        )
        val rv = findViewById<RecyclerView>(R.id.rvList).apply {
            layoutManager = LinearLayoutManager(this@VisualReviewActivity)
            this.adapter = adapter
            visibility = View.VISIBLE
        }
        adapter.submitList(
            files(5, true) + FilesDataModel.PageError(ResourceErrorKind.NETWORK)
        ) {
            rv.scrollToPosition(adapter.itemCount - 1)
            rv.postDelayed({
                rv.findViewHolderForAdapterPosition(adapter.itemCount - 1)
                    ?.itemView
                    ?.findViewById<View>(R.id.btnPageErrorAction)
                    ?.requestFocus()
            }, 350)
        }
    }

    private fun libraryRootPickerFixture() {
        setContentView(R.layout.fragment_settings)
        val foldersByParent = mapOf(
            "root" to listOf(folderFixture("folder-oneblacki", "oneblacki"), folderFixture("folder-shared", "Shared")),
            "folder-oneblacki" to listOf(folderFixture("folder-bakemonogatari", "Bakemonogatari"), folderFixture("folder-frieren", "Sousou no Frieren")),
            "folder-bakemonogatari" to listOf(folderFixture("folder-season-01", "Temporada 01")),
            "folder-season-01" to listOf(folderFixture("folder-episodes", "Episódios")),
            "folder-shared" to emptyList(),
            "folder-frieren" to emptyList(),
            "folder-episodes" to emptyList()
        )
        DriveFolderPickerDialog(
            context = this,
            lifecycleScope = lifecycleScope,
            initialSelectionName = "Biblioteca de demonstração",
            loadPage = { parentId, _ ->
                val parentKey = parentId ?: "root"
                Resource.Success(FilesResponse(foldersByParent[parentKey].orEmpty(), nextPageToken = null))
            },
            onSelected = { id, name ->
                val label = if (id == null) getString(R.string.my_drive) else name.orEmpty()
                Toast.makeText(this, "Pasta de teste selecionada: $label", Toast.LENGTH_SHORT).show()
            }
        ).show()
    }

    private fun folderFixture(id: String, name: String) = zechs.drive.stream.data.model.File(
        id = id,
        name = name,
        size = null,
        iconLink = "",
        mimeType = "application/vnd.google-apps.folder"
    )

    private fun details(
        focusEpisode: Boolean = false,
        longSynopsis: Boolean = false,
        rebindOnDisclosureFocus: Boolean = false,
        episodeDescriptions: Boolean = false,
        episodeNameVariants: Boolean = false
    ) {
        setContentView(R.layout.fragment_series_detail)
        // The fixture has no confirmed or filename-inferred media specs. Match production's
        // unknown state instead of showing empty icon-only metadata pills.
        findViewById<View>(R.id.metaRow2).visibility = View.GONE
        val synopsis = findViewById<TextView>(R.id.tvSynopsis)
        val synopsisMore = findViewById<TextView>(R.id.tvSynopsisMore)
        TvAccessibilityLayout.adaptDetailsSynopsis(this, synopsis)
        val synopsisDisclosure = SeriesSynopsisDisclosure(
            scrollView = findViewById(R.id.scrollView),
            synopsis = synopsis,
            toggle = synopsisMore,
            fallbackFocus = findViewById(R.id.btnPrimaryAction),
            expandLabel = getString(R.string.series_synopsis_expand),
            collapseLabel = getString(R.string.series_synopsis_collapse)
        ) { hasDisclosure ->
            val upTarget = if (hasDisclosure) R.id.tvSynopsisMore else R.id.btnBack
            listOf(R.id.btnPrimaryAction, R.id.btnFavorite, R.id.btnMoreActions)
                .forEach { findViewById<View>(it).nextFocusUpId = upTarget }
            synopsisMore.nextFocusUpId = R.id.btnBack
            synopsisMore.nextFocusDownId = R.id.btnPrimaryAction
        }
        TvAccessibilityLayout.adaptSeriesActions(
            this,
            findViewById<View>(R.id.actionsRow)?.parent as? android.widget.HorizontalScrollView,
            findViewById(R.id.actionsRow),
            listOf(
                findViewById(R.id.btnPrimaryAction),
                findViewById(R.id.btnFavorite),
                findViewById(R.id.btnMoreActions)
            )
        )
        findViewById<View>(R.id.btnMoreActions).setOnClickListener { anchor ->
            SeriesMoreActionsDialog.show(
                context = this,
                focusAnchor = anchor,
                isFollowed = false,
                onTrailer = { Toast.makeText(this, R.string.trailer_not_available, Toast.LENGTH_SHORT).show() },
                onFollowToggle = { Toast.makeText(this, R.string.action_follow, Toast.LENGTH_SHORT).show() },
                onMarkWatched = { Toast.makeText(this, R.string.season_marked_watched, Toast.LENGTH_SHORT).show() }
            )
        }
        findViewById<View>(R.id.btnPrimaryAction).nextFocusRightId = R.id.btnFavorite
        findViewById<View>(R.id.btnFavorite).nextFocusRightId = R.id.btnMoreActions
        findViewById<View>(R.id.btnMoreActions).nextFocusLeftId = R.id.btnFavorite
        findViewById<View>(R.id.btnMoreActions).nextFocusDownId = R.id.rvSeasonTabs
        art(R.id.ivHeroBackdrop);art(R.id.ivSeriesPosterCard,poster)
        text(
            R.id.tvRomajiTitle,
            if (longSynopsis) "SOUSOU NO FRIEREN — uma jornada através do tempo, da memória e das despedidas" else "SOUSOU NO FRIEREN"
        )
        text(R.id.tvJapaneseTitle,"葬送のフリーレン");show(R.id.tvJapaneseTitle)
        val synopsisText = if (longSynopsis) {
            "Após a derrota do Rei Demônio, a elfa Frieren parte em uma jornada que atravessa décadas para compreender melhor as pessoas que fizeram parte de sua vida. " +
                "Ao lado de Fern e Stark, ela revisita lugares conhecidos, encontra novos companheiros e descobre que pequenos momentos podem guardar um significado profundo. " +
                "Entre ruínas, vilarejos e caminhos esquecidos, cada encontro revela uma lembrança diferente e muda a forma como Frieren enxerga o tempo, a amizade e as despedidas. " +
                "Esta prévia longa verifica a expansão da sinopse sem esconder os controles principais nem prender o foco fora da área visível."
        } else {
            "Após a derrota do Rei Demônio, Frieren parte em uma nova jornada para compreender as pessoas que fizeram parte de sua vida."
        }
        findViewById<View>(R.id.tvSynopsisMore).let { more ->
            more.isFocusable = true
            more.isFocusableInTouchMode = true
        }
        synopsisDisclosure.bind(synopsisText)
        if (rebindOnDisclosureFocus) {
            synopsisMore.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus) {
                    focusedView.postDelayed({
                        synopsisDisclosure.bind("Sinopse curta após atualização de metadados.")
                    }, 250)
                }
            }
        }
        text(R.id.tvReleaseYear,"2023");text(R.id.tvSeriesStatus,"Completo");show(R.id.tvSeriesStatus);text(R.id.tvMalScore,"9.4");show(R.id.layoutRating)
        text(R.id.tvPrimaryActionTitle,"Reproduzir S01E01")
        text(R.id.tvAgeRating,"12+")
        val seasons=findViewById<RecyclerView>(R.id.rvSeasonTabs)
        seasons.adapter=FixtureAdapter(R.layout.item_series_season_tab,2) { v,p ->
            v.findViewById<TextView>(R.id.tvTabTitle).text=listOf("1ª temporada", "Especiais e OVAs")[p]
            v.isSelected = p == 0
        }
        val rv=findViewById<RecyclerView>(R.id.rvEpisodes)
        rv.isFocusable = true
        rv.isFocusableInTouchMode = true
        if (episodeDescriptions) {
            val titles = listOf("O fim da viagem", "A magia da memória", "Uma nova jornada")
            rv.adapter = SeriesDetailEpisodeAdapter(onEpisodeClick = {}).apply {
                submitList((0 until 12).map { index ->
                    val file = DriveFile(
                        id = "fixture-episode-$index",
                        name = "Episódio ${index + 1}.mkv",
                        size = null,
                        mimeType = "video/x-matroska",
                        iconLink = null,
                        thumbnailLink = backdrop,
                        shortcutDetails = ShortcutDetails(),
                        starred = Starred.UNSTARRED,
                        description = if (index == 0) {
                            "Uma descrição fornecida nos metadados do arquivo revela um encontro inesperado e a pista que leva Frieren, Fern e Stark ao próximo destino. " +
                                "O texto fica limitado a duas linhas enquanto o card está focado."
                        } else null
                    )
                    SeriesEpisodeItem(
                        file = file,
                        displayTitle = titles[index % titles.size],
                        episodeNumber = index + 1,
                        durationText = "24 min",
                        audioBadgeText = "",
                        progressPercent = 0,
                        watchedDuration = 0L,
                        totalDuration = 0L
                    )
                })
            }
        } else if (episodeNameVariants) {
            val releases = listOf(
                "Bakemonogatari_OP4a_.mkv" to null,
                "Bakemonogatari_OP4b_.mkv" to null,
                "Bakemonogatari - 01.mkv" to "Hitagi Crab, Part One"
            )
            rv.adapter = SeriesDetailEpisodeAdapter(onEpisodeClick = {}).apply {
                submitList(releases.mapIndexed { index, (fileName, canonicalTitle) ->
                    val presentation = presentSeriesEpisode(fileName, canonicalTitle)
                    val file = DriveFile(
                        id = "fixture-episode-name-$index",
                        name = fileName,
                        size = null,
                        mimeType = "video/x-matroska",
                        iconLink = null,
                        thumbnailLink = backdrop,
                        shortcutDetails = ShortcutDetails(),
                        starred = Starred.UNSTARRED
                    )
                    SeriesEpisodeItem(
                        file = file,
                        displayTitle = presentation.displayTitle,
                        episodeNumber = presentation.episodeNumber,
                        durationText = "24 min",
                        audioBadgeText = "",
                        progressPercent = 0,
                        watchedDuration = 0L,
                        totalDuration = 0L
                    )
                })
            }
        } else {
            rv.adapter=FixtureAdapter(R.layout.item_series_episode_card,12) { v,p ->
                v.findViewById<TextView>(R.id.tvEpisodeTitle).text=listOf("O fim da viagem", "A magia da memória", "Uma nova jornada")[p % 3]
                v.findViewById<TextView>(R.id.tvEpisodeDuration).text="24 min"
                v.findViewById<View>(R.id.pbEpisodeProgress).visibility=View.GONE
                v.findViewById<TextView>(R.id.tvEpNumberBadge).text="Ep. ${p+1}"
                Glide.with(this).load(backdrop).into(v.findViewById(R.id.ivEpisodeThumb))
                v.setOnLongClickListener {
                    val episodeTitle = v.findViewById<TextView>(R.id.tvEpisodeTitle).text.toString()
                    SeriesEpisodeContextMenu.show(
                        context = this,
                        focusAnchor = v,
                        title = episodeTitle,
                        options = listOf(
                            getString(R.string.episode_action_play),
                            getString(R.string.episode_action_mark_watched),
                            getString(R.string.episode_action_copy_filename)
                        ),
                        onAction = { Toast.makeText(this, "Ação de teste", Toast.LENGTH_SHORT).show() }
                    )
                    true
                }
            }
        }
        if (focusEpisode) {
            rv.postDelayed({
                val episode = rv.findViewHolderForAdapterPosition(0)?.itemView
                if (episode != null) {
                    episode.isFocusable = true
                    episode.isFocusableInTouchMode = true
                    episode.requestFocusFromTouch()
                }
            }, 1000)
        } else {
            findViewById<View>(R.id.btnPrimaryAction)?.postDelayed({ findViewById<View>(R.id.btnPrimaryAction)?.requestFocus() }, 300)
        }
    }
    private fun player() {
        val frame=android.widget.FrameLayout(this)
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP }
        frame.addView(image,ViewGroup.LayoutParams(-1,-1));Glide.with(this).load(backdrop).into(image)
        val controls=layoutInflater.inflate(R.layout.player_control_view,frame,false)
        playerFixtureControls = controls
        frame.addView(controls);setContentView(frame);PlayerQuickOptions.bind(controls)
        controls.findViewById<View>(R.id.btnPlayPause)?.postDelayed({
            controls.findViewById<View>(R.id.btnPlayPause)?.requestFocusFromTouch()
        }, 300)
        controls.findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        text(R.id.tvPlayerTitle,"SOUSOU NO FRIEREN")
        text(androidx.media3.ui.R.id.exo_position,"12:45");text(androidx.media3.ui.R.id.exo_duration,"24:10")
        findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress).apply {
            setDuration(1450000);setPosition(765000)
        }
    }
    private fun launchActualPlayer(
        activityClass: Class<out Activity>,
        fixtureResource: Int = R.raw.vinheta,
        fixtureExtension: String = "mp4"
    ) {
        // Keep a real review screen under the player so BACK can validate the return
        // path instead of leaving this harness on a blank Activity.
        home()
        val mediaFile = File(filesDir, "visual-review-player-${fixtureResource}.$fixtureExtension")
        if (!mediaFile.exists() || mediaFile.length() == 0L) {
            resources.openRawResource(fixtureResource).use { input ->
                mediaFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Log.d("VisualReviewActivity", "Opening local player fixture (${mediaFile.length()} bytes)")
        window.decorView.post {
            startActivity(android.content.Intent(this, activityClass).apply {
                putExtra("reviewMediaUri", Uri.fromFile(mediaFile).toString())
                putExtra("title", "Makimono · revisão local")
            })
        }
    }
    private fun settingsScreen() {
        setContentView(R.layout.fragment_settings)
        TvAccessibilityLayout.adaptSettingsRows(
            this,
            listOf(
                R.id.settingSelectProfile,
                R.id.settingLibraryRoot,
                R.id.settingSelectTheme,
                R.id.settingDefaultPlayer,
                R.id.settingSubtitleSize,
                R.id.settingClearCache,
                R.id.settingCheckForUpdate,
                R.id.settingMalAccount,
                R.id.settingMalSyncToggle,
                R.id.settingAutoSkipToggle,
                R.id.settingGesturesToggle,
                R.id.settingExportBackup,
                R.id.settingImportBackup,
                R.id.settingLogOut
            ).map { findViewById<View>(it) }
        )
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            title = "Configurações"
            navigationContentDescription = "Voltar"
        }
        text(R.id.tvCurrentProfileName, "Perfil ativo: Cauã (Admin)")
        text(R.id.tvLibraryRootValue, "Meu Drive › Animes")
        text(R.id.tvThemeCurrentValue, "Obsidian (Cinema escuro / Ciano)")
        text(R.id.tvPlayerCurrentValue, "ExoPlayer (Nativo Android)")
        text(R.id.tvSubtitleSizeValue, "Grande (24sp · Branco · Borda)")
        text(R.id.tvCacheSizeValue, "142.4 MB em cache (Posters, capas e legendas)")
        text(R.id.lastCheckedLabel, "Última verificação: hoje, 16:30")
        text(R.id.tvMalSubtitle, "Conectado como: CauaVitor • Toque para desconectar")
        findViewById<ImageView>(R.id.ivSettingProfileAvatar)?.setImageResource(R.drawable.avatar_caua)
        findViewById<View>(R.id.settingSelectProfile)?.apply {
            isFocusableInTouchMode = true
            postDelayed({ requestFocus() }, 300)
        }
    }
    private fun showThemeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_selector, null)
        val container = dialogView.findViewById<android.widget.LinearLayout>(R.id.llThemeContainer)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_DriveStream_Dialog)
            .setView(dialogView)
            .create()
        dialog.setOnShowListener {
            dialog.window?.let { w -> TvFocusRing.install(w.decorView) }
            container.getChildAt(0)?.requestFocus()
        }
        zechs.drive.stream.utils.AppTheme.entries.forEach { theme ->
            val itemBinding = zechs.drive.stream.databinding.ItemThemePreviewBinding.inflate(layoutInflater, container, false)
            itemBinding.apply {
                tvThemeName.text = theme.displayName
                tvThemeSubtitle.text = theme.subtitle
                swatchBase.background = androidx.core.content.ContextCompat.getDrawable(this@VisualReviewActivity, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                    setTint(android.graphics.Color.parseColor(theme.bgBaseHex))
                }
                swatchSurface.background = androidx.core.content.ContextCompat.getDrawable(this@VisualReviewActivity, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                    setTint(android.graphics.Color.parseColor(theme.bgSurfaceHex))
                }
                swatchAccent.background = androidx.core.content.ContextCompat.getDrawable(this@VisualReviewActivity, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                    setTint(android.graphics.Color.parseColor(theme.accentPrimaryHex))
                }
                swatchSecondary.background = androidx.core.content.ContextCompat.getDrawable(this@VisualReviewActivity, R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                    setTint(android.graphics.Color.parseColor(theme.accentSecondaryHex))
                }
                ivSelected.visibility = if (theme == zechs.drive.stream.utils.AppTheme.OBSIDIAN) View.VISIBLE else View.GONE
                cardTheme.setOnClickListener { dialog.dismiss() }
            }
            container.addView(itemBinding.root)
        }
        dialog.show()
    }

    private fun showGlassMenu() {
        findViewById<View>(R.id.btnMoreOptions)?.performClick()
    }
    private inner class FixtureAdapter(val layout: Int,val count: Int,val bind: (View,Int)->Unit):RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount()=count
        override fun onCreateViewHolder(parent:ViewGroup,type:Int)=object:RecyclerView.ViewHolder(layoutInflater.inflate(layout,parent,false)){}
        override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int)=bind(holder.itemView,position)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VisualReviewDependencies {
    fun profileManager(): ProfileManager
    fun profileArtCatalog(): ProfileArtCatalog
}

class ProfileEditorFixtureFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
