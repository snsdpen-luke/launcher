package com.snsdpen.launcher.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.snsdpen.launcher.notif.NotifListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 再生中(または直前)の 1 セッション */
data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
)

/**
 * メディアセッション(Spotify / YouTube 等の再生情報)。通知アクセスがあれば読める(権限は NotifListener と同じ)。
 * 表示中だけ start/stop する。package → NowPlaying を流す。操作は control()
 */
object MediaNow {
    private val _sessions = MutableStateFlow<Map<String, NowPlaying>>(emptyMap())
    val sessions: StateFlow<Map<String, NowPlaying>> = _sessions

    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { list -> bind(list.orEmpty()) }

    fun start(context: Context) {
        if (manager != null) return
        val m = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val cn = ComponentName(context, NotifListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(listener, cn)
            manager = m
            bind(m.getActiveSessions(cn))
        }.onFailure { android.util.Log.w("MediaNow", "no access: $it") }
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(listener) }
        manager = null
        callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        callbacks.clear()
        controllers = emptyList()
    }

    private fun bind(list: List<MediaController>) {
        callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        callbacks.clear()
        controllers = list
        for (c in list) {
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
                override fun onPlaybackStateChanged(state: PlaybackState?) { publish() }
            }
            runCatching { c.registerCallback(cb) }
            callbacks[c] = cb
        }
        publish()
    }

    private fun publish() {
        val map = LinkedHashMap<String, NowPlaying>()
        for (c in controllers) {
            val md = c.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
            val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            if (title.isBlank() && bmp == null) continue
            // 同じアプリの複数セッションは、再生中を優先
            val prev = map[c.packageName]
            if (prev == null || (playing && !prev.playing)) {
                map[c.packageName] = NowPlaying(c.packageName, title, artist.orEmpty(), bmp?.asImageBitmap(), playing)
            }
        }
        _sessions.value = map
    }

    /** action = play / pause / toggle / next / prev */
    fun control(pkg: String, action: String) {
        val c = controllers.firstOrNull { it.packageName == pkg } ?: return
        val t = c.transportControls
        runCatching {
            when (action) {
                "play" -> t.play()
                "pause" -> t.pause()
                "toggle" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
                "next" -> t.skipToNext()
                "prev" -> t.skipToPrevious()
            }
        }
    }
}
