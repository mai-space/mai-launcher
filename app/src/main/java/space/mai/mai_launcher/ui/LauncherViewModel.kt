package space.mai.mai_launcher.ui

import android.app.Application
import android.content.ComponentName
import android.graphics.Rect
import android.net.Uri
import android.content.Intent
import androidx.core.net.toUri
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
import space.mai.mai_launcher.calendar.CalendarRepository
import space.mai.mai_launcher.data.LaunchLog
import space.mai.mai_launcher.data.LauncherPrefs
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.AppSearch
import space.mai.mai_launcher.domain.Predictor
import space.mai.mai_launcher.domain.launchCounts
import space.mai.mai_launcher.media.MediaHub
import space.mai.mai_launcher.signals.SignalsHub
import space.mai.mai_launcher.system.HomeRole
import space.mai.mai_launcher.system.SystemActions
import space.mai.mai_launcher.system.SystemStatus
import space.mai.mai_launcher.widgets.WidgetHost
import space.mai.mai_launcher.widgets.WidgetOption

/** Was die Activity für den Launcher erledigt (System-Dialoge mit Ergebnis). */
interface LauncherHost {
    fun requestHomeRole()
    fun requestCalendar()
    fun openNotificationAccess()
    fun bindWidget(id: Int, provider: ComponentName)
    fun configureWidget(id: Int)
}

/** Optionale Einrichtungsschritte; verschwinden, sobald erledigt oder weggeklickt. */
enum class SetupItem { Home, Signals, Calendar }

/** Zustand und Aktionen des Launchers. */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val repo = AppRepository(app)
    val system = SystemStatus(app)
    val actions = SystemActions(app)
    val media = MediaHub(app)
    val calendar = CalendarRepository(app, viewModelScope)
    val widgets = WidgetHost(app)
    private val homeRole = HomeRole(app)
    private val prefs = LauncherPrefs(app)
    private val log = LaunchLog(app, viewModelScope).also { it.load() }
    private val predictor = Predictor()
    val icons = IconLoader(repo, (48 * app.resources.displayMetrics.density).toInt())

    var host: LauncherHost? = null

    private val _screen = MutableStateFlow(Screen.Deck)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _pickingSlot = MutableStateFlow<Int?>(null)
    val pickingSlot: StateFlow<Int?> = _pickingSlot.asStateFlow()

    private val _focusSearch = MutableStateFlow(0)
    val focusSearch: StateFlow<Int> = _focusSearch.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    /** Zustand der Freigaben; wird bei jedem Zurückkehren neu gelesen. */
    private val _access = MutableStateFlow(Access())
    val access: StateFlow<Access> = _access.asStateFlow()

    data class Access(val home: Boolean = true, val signals: Boolean = true, val calendar: Boolean = true)

    val destinations = prefs.destinations
    val navigation = SignalsHub.navigation

    val setup: StateFlow<List<SetupItem>> = combine(_access, prefs.dismissedSetup) { a, dismissed ->
        buildList {
            if (!a.home) add(SetupItem.Home)
            if (!a.signals && SetupItem.Signals.name !in dismissed) add(SetupItem.Signals)
            if (!a.calendar && SetupItem.Calendar.name !in dismissed) add(SetupItem.Calendar)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val apps: StateFlow<List<AppInfo>> =
        repo.apps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dock: StateFlow<List<AppTile?>> = combine(apps, prefs.dock) { list, saved ->
        val byKey = list.associateBy { it.key }
        val defaults = repo.defaultDock(list)
        List(LauncherPrefs.DOCK_SLOTS) { i ->
            (saved.getOrNull(i)?.let(byKey::get) ?: defaults.getOrNull(i)?.let(byKey::get))?.toTile()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, List(LauncherPrefs.DOCK_SLOTS) { null })

    val likely: StateFlow<List<AppTile>> = combine(apps, log.events, dock, system.state) { list, events, dockTiles, sys ->
        val byKey = list.associateBy { it.key }
        val exclude = dockTiles.mapNotNull { it?.key }.toSet()
        predictor.predict(events, sys.nowMillis, byKey.keys, exclude).mapNotNull { byKey[it]?.toTile() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val likelyWindow: Pair<Int, Int> get() = predictor.window(System.currentTimeMillis())

    val results: StateFlow<List<AppTile>> = combine(apps, query, log.events) { list, q, events ->
        AppSearch.filter(list, q, launchCounts(events)).map { it.toTile() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Beim Anzeigen: Beobachter starten und Freigaben neu prüfen. */
    fun onVisible() {
        system.start()
        system.refresh()
        widgets.startListening()
        refreshAccess()
    }

    fun onHidden() {
        system.stop()
        media.stop()
        calendar.stop()
        widgets.stopListening()
    }

    fun refreshAccess() {
        val a = Access(home = homeRole.isHome(), signals = media.hasAccess(), calendar = calendar.hasAccess())
        _access.value = a
        if (a.signals) media.start()
        if (a.calendar) calendar.start()
    }

    fun onSetup(item: SetupItem) {
        val h = host ?: return
        when (item) {
            SetupItem.Home -> h.requestHomeRole()
            SetupItem.Signals -> h.openNotificationAccess()
            SetupItem.Calendar -> h.requestCalendar()
        }
    }

    /** Tonmodus weiterschalten; fehlt die Freigabe für Stumm, deren Einstellungsseite öffnen. */
    fun cycleSound() {
        if (!system.cycleSound()) actions.policyAccessSettings()
    }

    /** „Nicht stören“ direkt schalten; ohne Freigabe deren Einstellungsseite öffnen. */
    fun toggleDnd() {
        if (!system.toggleDnd()) actions.policyAccessSettings()
    }

    fun dismissSetup(item: SetupItem) = prefs.dismissSetup(item.name)

    // --- Navigation zwischen Ansichten ---

    fun openIndex(withKeyboard: Boolean) {
        _editMode.value = false
        _screen.value = Screen.Index
        if (withKeyboard) _focusSearch.value++
    }

    fun closeIndex() {
        _screen.value = Screen.Deck
        _query.value = ""
        _pickingSlot.value = null
    }

    fun setQuery(q: String) { _query.value = q }

    fun onHome() {
        _editMode.value = false
        closeIndex()
    }

    fun onBack(): Boolean {
        when {
            _screen.value == Screen.Widgets -> _screen.value = Screen.Deck
            _screen.value == Screen.Index && _query.value.isNotEmpty() -> _query.value = ""
            _screen.value == Screen.Index -> closeIndex()
            _editMode.value -> _editMode.value = false
            else -> return false
        }
        return true
    }

    fun setEditMode(on: Boolean) { _editMode.value = on }

    fun startPickingDock(slot: Int) {
        _pickingSlot.value = slot
        openIndex(withKeyboard = false)
    }

    fun onAppTapped(key: AppKey, bounds: Rect? = null) {
        val slot = _pickingSlot.value
        if (slot != null) {
            prefs.setDockSlot(slot, key)
            closeIndex()
            return
        }
        if (repo.launch(key, bounds)) {
            log.record(key)
            closeIndex()
        }
    }

    fun launchFirstResult() {
        results.value.firstOrNull()?.let { onAppTapped(it.key) }
    }

    fun appInfo(key: AppKey) = repo.openAppInfo(key)
    // Verlauf bleibt; deinstallierte Apps filtert die Vorhersage ohnehin heraus.
    fun uninstall(key: AppKey) = repo.uninstall(key)

    // --- Navigation (Karten) ---

    fun setDestination(index: Int, address: String) = prefs.setDestination(index, address)

    /** Startet die Navigation zum Schnellziel in der Standard-Karten-App. */
    fun navigateTo(index: Int) {
        val address = destinations.value.getOrNull(index)?.takeIf { it.isNotBlank() } ?: return
        val ctx = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun openNavigation() {
        runCatching { navigation.value?.open?.send() }
    }

    // --- Widgets ---

    private var pendingWidget: Int? = null

    fun openWidgetPicker() {
        _screen.value = Screen.Widgets
    }

    fun widgetOptions(): List<WidgetOption> = widgets.options()

    fun addWidget(option: WidgetOption) {
        val (id, needsBind) = widgets.allocate(option.provider)
        pendingWidget = id
        _screen.value = Screen.Deck
        _editMode.value = true
        if (needsBind) host?.bindWidget(id, option.provider) ?: onWidgetBound(false) else onWidgetBound(true)
    }

    fun onWidgetBound(ok: Boolean) {
        val id = pendingWidget ?: return
        if (!ok) { widgets.discard(id); pendingWidget = null; return }
        if (widgets.needsConfigure(id)) host?.configureWidget(id) ?: onWidgetConfigured(false) else onWidgetConfigured(true)
    }

    fun onWidgetConfigured(ok: Boolean) {
        val id = pendingWidget ?: return
        pendingWidget = null
        if (ok) widgets.commit(id) else widgets.discard(id)
    }

    override fun onCleared() {
        onHidden()
    }
}
