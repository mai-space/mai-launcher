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

    companion object {
        const val DOCK_SLOTS = 4
    }
}
