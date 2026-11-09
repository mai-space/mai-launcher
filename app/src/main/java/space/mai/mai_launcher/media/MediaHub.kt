package space.mai.mai_launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.mai.mai_launcher.domain.NowPlaying
import space.mai.mai_launcher.signals.MaiNotificationListener

/**
 * Steuert, was in irgendeiner App gerade spielt (Media-Sessions). Braucht den
 * Benachrichtigungszugriff; ohne ihn bleibt [nowPlaying] leer.
 */
class MediaHub(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MaiNotificationListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Cover (nicht Teil von [NowPlaying], damit dieses rein bleibt). */
    private val _art = MutableStateFlow<Bitmap?>(null)
    val art: StateFlow<Bitmap?> = _art.asStateFlow()

    /** Als Nächstes (nur wenn die App ihre Warteschlange teilt), höchstens fünf Titel. */
    private val _queue = MutableStateFlow<List<String>>(emptyList())
    val queue: StateFlow<List<String>> = _queue.asStateFlow()

    private var controller: MediaController? = null
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = publish()
        override fun onSessionDestroyed() = pick(currentSessions())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun start() {
        if (started || !hasAccess()) return
        started = runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            true
        }.getOrDefault(false)
        if (started) pick(currentSessions())
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private fun currentSessions(): List<MediaController> =
        runCatching { manager.getActiveSessions(component) }.getOrDefault(emptyList())

    /** Bevorzugt eine spielende Session, sonst die oberste (zuletzt aktive). */
    private fun pick(list: List<MediaController>) {
        val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = chosen
            chosen?.registerCallback(controllerCallback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || meta == null || title.isNullOrBlank()) {
            _nowPlaying.value = null
            _art.value = null
            _queue.value = emptyList()
            return
        }
        _queue.value = runCatching {
            val q = c.queue.orEmpty()
            val active = c.playbackState?.activeQueueItemId
            val i = q.indexOfFirst { it.queueId == active }
            q.drop(if (i >= 0) i + 1 else 0).mapNotNull { it.description.title?.toString() }.take(5)
        }.getOrDefault(emptyList())
        val state = c.playbackState
        val duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
        _nowPlaying.value = NowPlaying(
            title = title,
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            appLabel = appLabel(c.packageName),
            playing = state?.state == PlaybackState.STATE_PLAYING,
            positionMs = state?.position?.takeIf { it >= 0 },
            durationMs = duration,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: 0,
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
        _art.value = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }

    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms.coerceAtLeast(0)) }

    /** Lautstärke/Ausgabe: System-Panel (Ausgabegerät wählt Android dort). */
    fun openOutput() {
        val panel = android.content.Intent(android.provider.Settings.Panel.ACTION_VOLUME)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(panel) }
    }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    /** Öffnet die spielende App (ihre Session-Activity, sonst ihren Start-Eintrag). */
    fun openPlayer() {
        val c = controller ?: return
        val pi = c.sessionActivity
        if (pi != null) {
            runCatching { pi.send() }
            return
        }
        context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
