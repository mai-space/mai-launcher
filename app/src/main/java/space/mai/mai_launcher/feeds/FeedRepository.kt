package space.mai.mai_launcher.feeds

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import space.mai.mai_launcher.domain.Feed
import space.mai.mai_launcher.domain.FeedItem
import space.mai.mai_launcher.domain.FeedParser
import space.mai.mai_launcher.domain.FeedUrls
import space.mai.mai_launcher.domain.mergeFeeds
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Stand des News-Panels. */
data class FeedState(
    val urls: List<String> = emptyList(),
    val items: List<FeedItem> = emptyList(),
    val failed: Int = 0,
    val loading: Boolean = false,
    val updatedAt: Long = 0,
)

/**
 * Der EINZIGE Netzwerkzugriff des Launchers: ruft die vom Nutzer eingetragenen
 * Feeds ab (nur https, ohne Cookies, ohne Bilder, generischer User-Agent).
 * Es wird nichts gesendet außer der Anfrage selbst.
 */
class FeedRepository(context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(FeedState(urls = FeedUrls.decode(prefs.getString(KEY, null))))
    val state: StateFlow<FeedState> = _state.asStateFlow()
    private var job: Job? = null

    fun add(raw: String): Boolean {
        val url = FeedUrls.normalize(raw) ?: return false
        if (url in _state.value.urls) return true
        save(_state.value.urls + url)
        refresh(force = true)
        return true
    }

    fun remove(url: String) {
        save(_state.value.urls - url)
        refresh(force = true)
    }

    private fun save(urls: List<String>) {
        prefs.edit { putString(KEY, FeedUrls.encode(urls)) }
        _state.value = _state.value.copy(urls = urls)
    }

    /** Lädt neu, wenn der letzte Abruf älter als 30 Minuten ist (oder erzwungen). */
    fun refresh(force: Boolean = false) {
        val s = _state.value
        if (s.urls.isEmpty()) { _state.value = s.copy(items = emptyList(), failed = 0); return }
        if (!force && System.currentTimeMillis() - s.updatedAt < MAX_AGE) return
        if (job?.isActive == true && !force) return
        job?.cancel()
        _state.value = s.copy(loading = true)
        job = scope.launch(Dispatchers.IO) {
            val results = s.urls.map { url -> async { runCatching { fetch(url) }.getOrNull() } }.awaitAll()
            val ok = results.filterNotNull()
            _state.value = _state.value.copy(
                items = mergeFeeds(ok),
                failed = results.size - ok.size,
                loading = false,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun fetch(url: String): Feed {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "MaiLauncher/1 (RSS)")
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            // Weiterleitung auf http wird nicht verfolgt (Android blockt Klartext ohnehin).
            return LimitedStream(conn.inputStream, MAX_BYTES).use { FeedParser.parse(it, conn.url.host) }
        } finally {
            conn.disconnect()
        }
    }

    /** Bricht ab, wenn ein Feed unvernünftig groß ist. */
    private class LimitedStream(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
        private fun bump(n: Long) { count += n; if (count > max) throw IOException("Feed zu groß") }
    }

    private companion object {
        const val KEY = "feeds"
        const val MAX_AGE = 30 * 60 * 1000L
        const val MAX_BYTES = 3L * 1024 * 1024
    }
}
