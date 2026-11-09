package space.mai.mai_launcher.signals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.mai.mai_launcher.domain.NavInfo
import space.mai.mai_launcher.domain.SignalItem

/**
 * Verbindung zwischen [MaiNotificationListener] (vom System gestartet) und der
 * Oberfläche. Alles bleibt im Prozess; nichts wird gespeichert.
 */
object SignalsHub {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _navigation = MutableStateFlow<NavEntry?>(null)
    val navigation: StateFlow<NavEntry?> = _navigation.asStateFlow()

    /** Laufende Navigation samt Antippen-Aktion der Karten-App. */
    data class NavEntry(val key: String, val info: NavInfo, val open: android.app.PendingIntent?)

    private val _items = MutableStateFlow<Map<String, SignalItem>>(emptyMap())
    /** Aktuelle Benachrichtigungen (ohne laufende, Medien, Navigation, Gruppen-Kopf). */
    val items: StateFlow<Map<String, SignalItem>> = _items.asStateFlow()
    private val intents = mutableMapOf<String, android.app.PendingIntent?>()

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) {
            _navigation.value = null
            _items.value = emptyMap()
            synchronized(intents) { intents.clear() }
        }
    }

    internal fun put(item: SignalItem, open: android.app.PendingIntent?) {
        synchronized(intents) { intents[item.key] = open }
        _items.value = _items.value + (item.key to item)
    }

    internal fun remove(key: String) {
        synchronized(intents) { intents.remove(key) }
        if (key in _items.value) _items.value = _items.value - key
    }

    /** Antippen: die Aktion der Benachrichtigung selbst ausführen. */
    fun open(key: String): Boolean {
        val pi = synchronized(intents) { intents[key] } ?: return false
        return runCatching { pi.send(); true }.getOrDefault(false)
    }

    internal fun setNavigation(entry: NavEntry?) { _navigation.value = entry }

    internal fun removeNavigation(key: String) {
        if (_navigation.value?.key == key) _navigation.value = null
    }
}
