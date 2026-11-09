package space.mai.mai_launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import space.mai.mai_launcher.domain.AppKey

/**
 * Einfache Einstellungen. Schlüssel nie umbenennen; Werte tolerant lesen.
 * Dock: 4 Plätze, leer = Standard-App des Systems.
 */
class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val _dock = MutableStateFlow(readDock())
    val dock: StateFlow<List<AppKey?>> = _dock.asStateFlow()

    private fun readDock(): List<AppKey?> = (0 until DOCK_SLOTS).map { AppKey.decode(prefs.getString("dock_$it", null)) }

    fun setDockSlot(slot: Int, key: AppKey?) {
        if (slot !in 0 until DOCK_SLOTS) return
        prefs.edit { putString("dock_$slot", key?.encode()) }
        _dock.value = readDock()
    }

    private val _destinations = MutableStateFlow(readDestinations())
    /** Schnellziele für die Navigation: [0] = Zuhause, [1] = Arbeit (Adresse als Text). */
    val destinations: StateFlow<List<String>> = _destinations.asStateFlow()

    private fun readDestinations() = listOf(prefs.getString("dest_home", "").orEmpty(), prefs.getString("dest_work", "").orEmpty())

    fun setDestination(index: Int, address: String) {
        val key = when (index) { 0 -> "dest_home"; 1 -> "dest_work"; else -> return }
        prefs.edit { putString(key, address.trim()) }
        _destinations.value = readDestinations()
    }

    private val _dismissed = MutableStateFlow(prefs.getStringSet("setup_dismissed", emptySet()).orEmpty())
    /** Einrichtungs-Hinweise, die der Nutzer weggeklickt hat. */
    val dismissedSetup: StateFlow<Set<String>> = _dismissed.asStateFlow()

    fun dismissSetup(id: String) {
        val next = _dismissed.value + id
        prefs.edit { putStringSet("setup_dismissed", next) }
        _dismissed.value = next
    }

    companion object {
        const val DOCK_SLOTS = 4
    }
}
