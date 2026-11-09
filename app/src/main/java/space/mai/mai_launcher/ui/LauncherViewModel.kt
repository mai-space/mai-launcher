package space.mai.mai_launcher.ui

import android.app.Application
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import space.mai.mai_launcher.apps.AppRepository
import space.mai.mai_launcher.apps.IconLoader
import space.mai.mai_launcher.data.LaunchLog
import space.mai.mai_launcher.data.LauncherPrefs
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.AppSearch
import space.mai.mai_launcher.domain.Predictor
import space.mai.mai_launcher.domain.launchCounts
import space.mai.mai_launcher.system.SystemActions
import space.mai.mai_launcher.system.SystemStatus

/** Zustand und Aktionen des Launchers (Deck + Index). */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val repo = AppRepository(app)
    val system = SystemStatus(app)
    val actions = SystemActions(app)
    private val prefs = LauncherPrefs(app)
    private val log = LaunchLog(app, viewModelScope).also { it.load() }
    private val predictor = Predictor()
    val icons = IconLoader(repo, (48 * app.resources.displayMetrics.density).toInt())

    private val _screen = MutableStateFlow(Screen.Deck)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Dock-Platz, für den gerade eine App gewählt wird (null = normaler Modus). */
    private val _pickingSlot = MutableStateFlow<Int?>(null)
    val pickingSlot: StateFlow<Int?> = _pickingSlot.asStateFlow()

    /** Erhöht sich, wenn die Suche mit Tastatur geöffnet werden soll. */
    private val _focusSearch = MutableStateFlow(0)
    val focusSearch: StateFlow<Int> = _focusSearch.asStateFlow()

    val apps: StateFlow<List<AppInfo>> =
        repo.apps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dock: StateFlow<List<AppTile?>> = combine(apps, prefs.dock) { list, saved ->
        val byKey = list.associateBy { it.key }
        val defaults = repo.defaultDock(list)
        List(LauncherPrefs.DOCK_SLOTS) { i ->
            (saved.getOrNull(i)?.let(byKey::get) ?: defaults.getOrNull(i)?.let(byKey::get))?.toTile()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, List(LauncherPrefs.DOCK_SLOTS) { null })

    /** „Jetzt wahrscheinlich“: aus dem Startverlauf, ohne Dock-Apps. */
    val likely: StateFlow<List<AppTile>> = combine(apps, log.events, dock, system.state) { list, events, dockTiles, sys ->
        val byKey = list.associateBy { it.key }
        val exclude = dockTiles.mapNotNull { it?.key }.toSet()
        predictor.predict(events, sys.nowMillis, byKey.keys, exclude).mapNotNull { byKey[it]?.toTile() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val likelyWindow: Pair<Int, Int> get() = predictor.window(System.currentTimeMillis())

    val results: StateFlow<List<AppTile>> = combine(apps, query, log.events) { list, q, events ->
        AppSearch.filter(list, q, launchCounts(events)).map { it.toTile() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openIndex(withKeyboard: Boolean) {
        _screen.value = Screen.Index
        if (withKeyboard) _focusSearch.value++
    }

    fun closeIndex() {
        _screen.value = Screen.Deck
        _query.value = ""
        _pickingSlot.value = null
    }

    fun setQuery(q: String) { _query.value = q }

    /** Home-Taste: aus jeder Ansicht zurück zum Deck. */
    fun onHome() = closeIndex()

    /** Zurück: erst Eingabe leeren, dann zum Deck. */
    fun onBack(): Boolean {
        if (_screen.value == Screen.Deck) return false
        if (_query.value.isNotEmpty()) _query.value = "" else closeIndex()
        return true
    }

    fun startPickingDock(slot: Int) {
        _pickingSlot.value = slot
        openIndex(withKeyboard = false)
    }

    /** Tipp auf eine App: im Dock-Wählmodus zuweisen, sonst starten. */
    fun onAppTapped(key: AppKey, bounds: Rect? = null) {
        val slot = _pickingSlot.value
        if (slot != null) {
            prefs.setDockSlot(slot, key)
            closeIndex()
            return
        }
        if (repo.launch(key, bounds)) {
            log.record(key)
            // Nach dem Start ruhig zum Deck zurück, damit Home wieder dort beginnt.
            closeIndex()
        }
    }

    fun launchFirstResult() {
        results.value.firstOrNull()?.let { onAppTapped(it.key) }
    }

    fun appInfo(key: AppKey) = repo.openAppInfo(key)
    // Verlauf bleibt; deinstallierte Apps filtert die Vorhersage ohnehin heraus.
    fun uninstall(key: AppKey) = repo.uninstall(key)

    override fun onCleared() {
        system.stop()
    }
}
