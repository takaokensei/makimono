package zechs.drive.stream.debug

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import zechs.drive.stream.R
import zechs.drive.stream.data.model.*
import zechs.drive.stream.ui.files.adapter.FilesAdapter
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.profile.ProfilesAdapter
import zechs.drive.stream.ui.profile.ProfileUiModel
import zechs.drive.stream.ui.player.PlayerQuickOptions
import zechs.drive.stream.ui.FocusedItemScroll
import zechs.drive.stream.utils.TvFocusRing
import zechs.drive.stream.utils.TvAccessibilityLayout
import zechs.drive.stream.data.remote.ProfileArtCatalog
import zechs.drive.stream.ui.profile.ProfileEditorDialog
import zechs.drive.stream.utils.ProfileManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Renders production XML and adapters with explicit fixtures, without touching account or Room. */
class VisualReviewActivity : FragmentActivity(), zechs.drive.stream.ui.player.PlayerModalHost {
    private val poster = "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
    private val backdrop = "https://images.justwatch.com/backdrop/308526637/s1440/sousou-no-frieren.jpg"
    private var playerFixtureControls: View? = null
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
    private fun files(count: Int, video: Boolean) = (1..count).map {
        FilesDataModel.File(DriveFile("fixture-$it", if(video) "Frieren - E${it.toString().padStart(2,'0')} - O fim da viagem.mkv" else "Sousou no Frieren",
            if(video) 1400000000L else null, if(video) "video/x-matroska" else "application/vnd.google-apps.folder",
            null, if(video) backdrop else poster, ShortcutDetails(), Starred.UNSTARRED, if(video) null else poster))
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        when(intent.getStringExtra("screen")) {
            "profiles" -> profiles()
            "files" -> fileScreen()
            "files_auth_error" -> filesAuthError()
            "details" -> details()
            "details_episode_focus" -> details(focusEpisode = true)
            "player" -> player()
            "player_exo_activity" -> launchActualPlayer(zechs.drive.stream.ui.player.PlayerActivity::class.java)
            "settings" -> settingsScreen()
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
    private fun home() {
        setContentView(R.layout.fragment_home)
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
        text(R.id.tvFeaturedOverline,"SOUSOU NO FRIEREN")
        text(R.id.tvFeaturedTitle,"Sousou no Frieren: Beyond Journey’s End")
        text(R.id.tvFeaturedJapaneseTitle,"葬送のフリーレン")
        text(R.id.tvFeaturedSynopsis,"Após a derrota do Rei Demônio, a elfa Frieren continua sua jornada, descobrindo o mundo e compreendendo o verdadeiro significado das amizades.")
        val genres = findViewById<android.widget.LinearLayout>(R.id.layoutFeaturedGenres)
        genres?.removeAllViews()
        listOf("Fantasia", "Aventura", "Drama").forEach { genre ->
            genres?.addView(TextView(this).apply {
                text = genre
                textSize = 10.5f
                setTextColor(0xFFCBD5E1.toInt())
                setBackgroundResource(R.drawable.tag_genre_pill_bg)
                setPadding(16, 6, 16, 6)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 14 }
            })
        }
        art(R.id.ivFeaturedBackdrop)
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
        grid.adapter=FilesAdapter({},{},{_,_->},compactCatalog=true).apply { isGridMode=true;submitList(files(14,false)) }
        val shelf=findViewById<RecyclerView>(R.id.rvContinueWatchingShelf)
        shelf.layoutManager=LinearLayoutManager(this,RecyclerView.HORIZONTAL,false)
        shelf.adapter=FixtureAdapter(R.layout.item_continue_watching_shelf,6) { v,p ->
            v.findViewById<TextView>(R.id.tvShelfItemTitle).text="Ep. ${p+1} · O fim da viagem"
            v.findViewById<TextView>(R.id.tvShelfItemRemaining).text="7 min restantes"
            Glide.with(this).load(backdrop).into(v.findViewById(R.id.ivShelfItemThumb))
        }
        FocusedItemScroll.install(findViewById(R.id.contentScrollView), listOf(shelf, grid))
        findViewById<View>(R.id.btnFeaturedPlay)?.postDelayed({ findViewById<View>(R.id.btnFeaturedPlay)?.requestFocus() }, 300)
    }
    private fun profiles() {
        setContentView(R.layout.fragment_profile_selection)
        TvAccessibilityLayout.adaptProfileActions(
            this,
            listOf(findViewById(R.id.btnManageProfiles), findViewById(R.id.btnProfileSettings))
        )
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
        var managing = false
        findViewById<View>(R.id.btnManageProfiles).setOnClickListener { button ->
            managing = !managing
            adapter.isManageMode = managing
            (button as? TextView)?.text = if (managing) "CONCLUÍDO" else "GERENCIAR PERFIS"
        }
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
            findViewById<View>(R.id.error).visibility = if (matching.isEmpty()) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.error_txt).text = getString(
                if (query.isNotBlank() && entries.isNotEmpty()) R.string.no_search_results else R.string.no_files_found
            )
            findViewById<View>(R.id.btnRetry).visibility = View.GONE
            findViewById<View>(R.id.btnConnectDrive).visibility = View.GONE
            rv.visibility = if (matching.isEmpty()) View.INVISIBLE else View.VISIBLE
        }
        fun updateMode() {
            adapter.isGridMode = gridMode
            rv.layoutManager = if (gridMode) {
                GridLayoutManager(this,TvAccessibilityLayout.gridColumnsForFontScale(4,resources.configuration.fontScale))
            } else LinearLayoutManager(this)
            findViewById<ImageView>(R.id.btnToggleGrid).setImageResource(
                if (gridMode) R.drawable.ic_list_view_24 else R.drawable.ic_grid_view_24
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
    private fun details(focusEpisode: Boolean = false) {
        setContentView(R.layout.fragment_series_detail)
        TvAccessibilityLayout.adaptDetailsSynopsis(this, findViewById(R.id.tvSynopsis))
        TvAccessibilityLayout.adaptSeriesActions(
            this,
            findViewById<View>(R.id.actionsRow)?.parent as? android.widget.HorizontalScrollView,
            findViewById(R.id.actionsRow),
            listOf(
                findViewById(R.id.btnPrimaryAction),
                findViewById(R.id.btnTrailer),
                findViewById(R.id.btnFavorite),
                findViewById(R.id.btnFollow),
                findViewById(R.id.btnMarkWatched)
            )
        )
        art(R.id.ivHeroBackdrop);art(R.id.ivSeriesPosterCard,poster)
        text(R.id.tvRomajiTitle,"SOUSOU NO FRIEREN")
        text(R.id.tvJapaneseTitle,"葬送のフリーレン");show(R.id.tvJapaneseTitle)
        text(R.id.tvSynopsis,"Após a derrota do Rei Demônio, Frieren parte em uma nova jornada para compreender as pessoas que fizeram parte de sua vida.")
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
        rv.adapter=FixtureAdapter(R.layout.item_series_episode_card,12) { v,p ->
            v.findViewById<TextView>(R.id.tvEpisodeTitle).text=listOf("O fim da viagem", "A magia da memória", "Uma nova jornada")[p % 3]
            v.findViewById<TextView>(R.id.tvEpisodeDuration).text="24 min"
            v.findViewById<View>(R.id.pbEpisodeProgress).visibility=View.GONE
            v.findViewById<TextView>(R.id.tvEpNumberBadge).text="Ep. ${p+1}"
            Glide.with(this).load(backdrop).into(v.findViewById(R.id.ivEpisodeThumb))
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
        text(com.google.android.exoplayer2.ui.R.id.exo_position,"12:45");text(com.google.android.exoplayer2.ui.R.id.exo_duration,"24:10")
        findViewById<com.google.android.exoplayer2.ui.DefaultTimeBar>(com.google.android.exoplayer2.ui.R.id.exo_progress).apply {
            setDuration(1450000);setPosition(765000)
        }
    }
    private fun launchActualPlayer(activityClass: Class<out Activity>) {
        setContentView(View(this))
        window.decorView.post {
            startActivity(android.content.Intent(this, activityClass))
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
