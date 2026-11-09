package space.mai.mai_launcher.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.LaunchEvent
import space.mai.mai_launcher.domain.LaunchLogCodec
import java.io.File

/**
 * Lokaler Startverlauf für „Jetzt wahrscheinlich“ und die Such-Rangfolge.
 * Verschlüsselt in noBackupFilesDir; verlässt nie das Gerät.
 */
class LaunchLog(context: Context, private val scope: CoroutineScope) {
    private val store = SecureFile(File(context.noBackupFilesDir, FILE_NAME), KEY_ALIAS)
    private val mutex = Mutex()
    private val _events = MutableStateFlow<List<LaunchEvent>>(emptyList())
    val events: StateFlow<List<LaunchEvent>> = _events.asStateFlow()
    private var pendingWrite: Job? = null

    fun load() {
        scope.launch(Dispatchers.IO) {
            val text = store.read()?.toString(Charsets.UTF_8)
            // Starts, die während des Ladens schon dazukamen, nicht verlieren.
            mutex.withLock { _events.value = text?.let(LaunchLogCodec::decode).orEmpty() + _events.value }
        }
    }

    fun record(key: AppKey, now: Long = System.currentTimeMillis()) {
        scope.launch {
            mutex.withLock {
                _events.value = (_events.value + LaunchEvent(key, now)).takeLast(LaunchLogCodec.MAX_EVENTS)
            }
            scheduleWrite()
        }
    }

    /** Entfernt alle Starts einer App (z. B. nach Deinstallation oder „Vorschlag ausblenden“). */
    fun forget(packageName: String) {
        scope.launch {
            mutex.withLock { _events.value = _events.value.filterNot { it.key.packageName == packageName } }
            scheduleWrite()
        }
    }

    private fun scheduleWrite() {
        pendingWrite?.cancel()
        pendingWrite = scope.launch(Dispatchers.IO) {
            delay(1500)
            val text = mutex.withLock { LaunchLogCodec.encode(_events.value) }
            runCatching { store.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    private companion object {
        const val FILE_NAME = "launch_log.bin"
        const val KEY_ALIAS = "mai_launcher_log_key"
    }
}
