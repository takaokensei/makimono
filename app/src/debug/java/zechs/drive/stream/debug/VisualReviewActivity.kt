package zechs.drive.stream.debug

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import zechs.drive.stream.utils.TvFocusRing

/** Renders production XML and adapters with explicit fixtures, without touching account or Room. */
class VisualReviewActivity : Activity() {
    private val poster = "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
    private val backdrop = "https://images.justwatch.com/backdrop/308526637/s1440/sousou-no-frieren.jpg"
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
            "details" -> details()
            "player" -> player()
            else -> home()
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
        show(R.id.featuredHeroContainer);show(R.id.shelfHeaderRow);show(R.id.rvContinueWatchingShelf)
        findViewById<View>(R.id.containerViewToggle)?.visibility=View.GONE
        findViewById<View>(R.id.containerItemCount)?.visibility=View.GONE
        text(R.id.tvFeaturedOverline,"SOUSOU NO FRIEREN")
        text(R.id.tvFeaturedTitle,"Sousou no Frieren: Beyond Journey’s End")
        text(R.id.tvFeaturedJapaneseTitle,"Fantasia  ·  Aventura")
        text(R.id.tvFeaturedSynopsis,"Após a derrota do Rei Demônio, a elfa Frieren continua sua jornada, descobrindo o mundo e compreendendo o verdadeiro significado das amizades.")
        val genres = findViewById<android.widget.LinearLayout>(R.id.layoutFeaturedGenres)
        listOf("Fantasia", "Aventura", "Drama").forEach { genre ->
            genres.addView(TextView(this).apply { text = genre; textSize = 10f; setTextColor(0xffb9cadf.toInt()); setPadding(8,4,12,4) })
        }
        art(R.id.ivFeaturedBackdrop)
        findViewById<ImageView>(R.id.ivUserAvatar)?.let { avatar ->
            avatar.imageTintList = null
            Glide.with(this).load(poster).circleCrop().into(avatar)
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
        findViewById<View>(R.id.btnFeaturedPlay).requestFocus()
    }
    private fun profiles() {
        setContentView(R.layout.fragment_profile_selection)
        val rv=findViewById<RecyclerView>(R.id.rvProfiles)
        rv.layoutParams=rv.layoutParams.apply { height=ViewGroup.LayoutParams.WRAP_CONTENT }
        rv.layoutManager=LinearLayoutManager(this,RecyclerView.HORIZONTAL,false)
        rv.adapter=ProfilesAdapter({},{},{}).apply {
            submitList(listOf(ProfileUiModel.ProfileItem(UserProfile("one","Cauã",avatarUrl=poster,isAdmin=true),true),ProfileUiModel.ProfileItem(UserProfile("two","Visitante",avatarUrl=poster),false),ProfileUiModel.ProfileItem(UserProfile("three","Kids / Shounen",avatarUrl=poster,isKids=true),false),ProfileUiModel.AddProfileItem))
        }
        rv.postDelayed({rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()},500)
    }
    private fun fileScreen() {
        setContentView(R.layout.fragment_files)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).title="Meu Drive  ›  Frieren  ›  Temporada 01"
        val rv=findViewById<RecyclerView>(R.id.rvList)
        rv.layoutManager=GridLayoutManager(this,4)
        rv.adapter=FilesAdapter({},{},{_,_->}).apply { isGridMode=true;submitList(files(12,true)) }
        rv.postDelayed({rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()},500)
    }
    private fun details() {
        setContentView(R.layout.fragment_series_detail)
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
        rv.adapter=FixtureAdapter(R.layout.item_series_episode_card,12) { v,p ->
            v.findViewById<TextView>(R.id.tvEpisodeTitle).text=listOf("O fim da viagem", "A magia da memória", "Uma nova jornada")[p % 3]
            v.findViewById<TextView>(R.id.tvEpisodeDuration).text="24 min"
            v.findViewById<View>(R.id.pbEpisodeProgress).visibility=View.GONE
            v.findViewById<TextView>(R.id.tvEpNumberBadge).text="Ep. ${p+1}"
            Glide.with(this).load(backdrop).into(v.findViewById(R.id.ivEpisodeThumb))
        }
        findViewById<View>(R.id.btnPrimaryAction).requestFocus()
    }
    private fun player() {
        val frame=android.widget.FrameLayout(this)
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP }
        frame.addView(image,ViewGroup.LayoutParams(-1,-1));Glide.with(this).load(backdrop).into(image)
        val controls=layoutInflater.inflate(R.layout.player_control_view,frame,false)
        frame.addView(controls);setContentView(frame);PlayerQuickOptions.bind(controls)
        text(R.id.tvPlayerTitle,"SOUSOU NO FRIEREN")
        text(com.google.android.exoplayer2.ui.R.id.exo_position,"12:45");text(com.google.android.exoplayer2.ui.R.id.exo_duration,"24:10")
        findViewById<com.google.android.exoplayer2.ui.DefaultTimeBar>(com.google.android.exoplayer2.ui.R.id.exo_progress).apply {
            setDuration(1450000);setPosition(765000)
        }
    }
    private inner class FixtureAdapter(val layout: Int,val count: Int,val bind: (View,Int)->Unit):RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount()=count
        override fun onCreateViewHolder(parent:ViewGroup,type:Int)=object:RecyclerView.ViewHolder(layoutInflater.inflate(layout,parent,false)){}
        override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int)=bind(holder.itemView,position)
    }
}
