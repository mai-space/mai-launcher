package space.mai.mai_launcher.signals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.mai.mai_launcher.domain.NavInfo

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

    internal fun setConnected(value: Boolean) {
        _connected.value = value
        if (!value) _navigation.value = null
    }

    internal fun setNavigation(entry: NavEntry?) { _navigation.value = entry }

    internal fun removeNavigation(key: String) {
        if (_navigation.value?.key == key) _navigation.value = null
    }
}
