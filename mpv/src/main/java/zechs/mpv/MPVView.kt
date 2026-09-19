package zechs.mpv

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import zechs.mpv.MPVLib.mpvFormat.*
import java.util.*
import kotlin.reflect.KProperty

class MPVView(
    context: Context,
    attrs: AttributeSet
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    companion object {
        internal const val TAG = "mpv"
    }

    fun initialize(configDir: String) {
        MPVLib.create(this.context)
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)

        initOptions(configDir)
        MPVLib.init()

        holder.addCallback(this)
        observeProperties()
    }

    private fun initOptions(configDir: String) {

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display!!
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay
        }
        val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            display.mode.refreshRate
        } else 60.0F

        Log.d(TAG, "Display ${display.displayId} reports FPS of $refreshRate")

        // Preferred languages: Japanese audio first, Portuguese subtitles first
        MPVLib.setOptionString("alang", "ja,jpn,jp,Japanese,por,pt,pt-BR,pt_BR,pob,Portuguese,Português,en,eng")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", "mediacodec,mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "all")
        MPVLib.setOptionString("ao", "audiotrack")
        MPVLib.setOptionString("framedrop", "vo")
        MPVLib.setOptionString("video-sync", "audio")
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${configDir}/cacert.pem")
        MPVLib.setOptionString("input-default-bindings", "yes")
        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("force-window", "no")

        // Zero-copy direct rendering and multithreaded software fallback
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        MPVLib.setOptionString("vd-lavc-threads", numCores.toString())

        // Optimized network streaming cache for Android TV
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${32 * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-readahead-secs", "30")

        // Subtitle typesetting (Unified PotPlayer style: crisp white text, thick black border, bold sans-serif, transparent box, natural 96% bottom height)
        MPVLib.setOptionString("sub-font", "sans-serif")
        MPVLib.setOptionString("sub-font-size", "48")
        MPVLib.setOptionString("sub-color", "#FFFFFFFF")
        MPVLib.setOptionString("sub-border-color", "#FF000000")
        MPVLib.setOptionString("sub-border-size", "3.2")
        MPVLib.setOptionString("sub-shadow-offset", "0")
        MPVLib.setOptionString("sub-back-color", "#00000000")
        MPVLib.setOptionString("sub-bold", "yes")
        MPVLib.setOptionString("sub-pos", "96")
        MPVLib.setOptionString("sub-margin-y", "36")
        MPVLib.setOptionString("sub-ass-override", "force")
        MPVLib.setOptionString("sub-ass-style-overrides", "Fontname=sans-serif,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,BackColour=&H00000000,BorderStyle=1,Outline=3.2,Shadow=0,Bold=1")
        MPVLib.setOptionString("sub-fix-timing", "yes")
        MPVLib.setOptionString("sub-codepage", "utf-8,cp1252")
        MPVLib.setOptionString("slang", "pt,por,pt-BR,pt_BR,pob,Portuguese,Português,en,eng,ja,jpn")
    }

    fun play(path: String) {
        this.playUri = path
    }

    // Called when back button is pressed, or app is shutting down
    fun destroy() {
        // Disable surface callbacks to avoid using uninitialized mpv state
        holder.removeCallback(this)
        MPVLib.destroy()
    }

    private fun observeProperties() {
        // This observes all properties needed by MPVView, MPVActivity or other classes
        data class Property(
            val name: String,
            val format: Int = MPV_FORMAT_NONE
        )

        val properties = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("duration", MPV_FORMAT_INT64),
            Property("pause", MPV_FORMAT_FLAG),
            Property("track-list"),
            Property("chapter-list"),
            Property("chapters", MPV_FORMAT_INT64),
            Property("chapter", MPV_FORMAT_INT64),
            Property("video-params"),
            Property("video-format"),
        )

        properties.forEach { (name, format) ->
            MPVLib.observeProperty(name, format)
        }
    }

    fun addObserver(eventObserver: MPVLib.EventObserver) {
        MPVLib.addObserver(eventObserver)
    }

    fun removeObserver(eventObserver: MPVLib.EventObserver) {
        MPVLib.removeObserver(eventObserver)
    }

    data class Track(
        val mpvId: Int,
        val name: String,
        val lang: String? = null,
        val title: String? = null
    )

    var tracks = mapOf<String, MutableList<Track>>(
        "audio" to arrayListOf(),
        "video" to arrayListOf(),
        "sub" to arrayListOf()
    )

    fun loadTracks() {
        for (list in tracks.values) {
            list.clear()
            // pseudo-track to allow disabling audio/subs
            list.add(Track(-1, context.getString(R.string.track_off)))
        }
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!tracks.containsKey(type)) {
                Log.w(TAG, "Got unknown track type: $type")
                continue
            }
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val title = MPVLib.getPropertyString("track-list/$i/title")

            val trackName = if (!lang.isNullOrEmpty() && !title.isNullOrEmpty()) {
                context.getString(R.string.ui_track_title_lang, mpvId, title, lang)
            } else if (!lang.isNullOrEmpty() || !title.isNullOrEmpty()) {
                context.getString(R.string.ui_track_text, mpvId, (lang ?: "") + (title ?: ""))
            } else {
                context.getString(R.string.ui_track, mpvId)
            }

            tracks.getValue(type).add(
                Track(mpvId = mpvId, name = trackName, lang = lang, title = title)
            )
        }
    }

    data class Chapter(
        val index: Int,
        val title: String?,
        val time: Double
    )

    fun loadChapters(): MutableList<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val count = MPVLib.getPropertyInt("chapters")
            ?: MPVLib.getPropertyInt("chapter-list/count")
            ?: 0
        for (i in 0 until count) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title") ?: ""
            val time = MPVLib.getPropertyDouble("chapter-list/$i/time") ?: 0.0
            chapters.add(
                Chapter(index = i, title = title, time = time)
            )
        }
        return chapters
    }

    private var playUri: String? = null

    // Property getters/setters

    var paused: Boolean?
        get() = MPVLib.getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

    var timePos: Int?
        get() = MPVLib.getPropertyInt("time-pos")
        set(progress) = MPVLib.setPropertyInt("time-pos", progress!!)

    // no setter only getter
    var duration: Int?
        get() = MPVLib.getPropertyInt("duration")
        private set(duration) {
            // do nothing
        }

    val videoW: Int?
        get() = MPVLib.getPropertyInt("video-params/w") ?: MPVLib.getPropertyInt("dwidth")

    val videoH: Int?
        get() = MPVLib.getPropertyInt("video-params/h") ?: MPVLib.getPropertyInt("dheight")

    class TrackDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(property.name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.setPropertyString(property.name, "no")
            else
                MPVLib.setPropertyInt(property.name, value)
        }
    }

    // video id
    // var vid: Int by TrackDelegate()

    // audio id
    var aid: Int by TrackDelegate()

    // subtitle id
    var sid: Int by TrackDelegate()

    // Commands

    fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))
    fun cycleScale() = MPVLib.command(arrayOf("cycle-values", "panscan", "1.0", "0.0"))

    // Surface callbacks
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")

        if (playUri != null) {
            MPVLib.command(arrayOf("loadfile", playUri!!))
            playUri = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo", "gpu")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
    }
}
