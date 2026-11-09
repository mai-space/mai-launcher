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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import space.mai.mai_launcher.apps.AppRepository
import space.mai.mai_launcher.apps.IconLoader
import space.mai.mai_launcher.calendar.CalendarRepository
import space.mai.mai_launcher.comms.ContactsRepository
import space.mai.mai_launcher.domain.Command
import space.mai.mai_launcher.domain.CommandParser
import space.mai.mai_launcher.domain.Contact
import space.mai.mai_launcher.domain.ContactRanking
import space.mai.mai_launcher.domain.ContactVerb
import space.mai.mai_launcher.domain.Keypad
import space.mai.mai_launcher.domain.SignalDigest
import space.mai.mai_launcher.domain.SignalGroup
import space.mai.mai_launcher.data.LaunchLog
import space.mai.mai_launcher.feeds.FeedRepository
import space.mai.mai_launcher.feedback.FeedbackPlayer
import space.mai.mai_launcher.domain.FeedbackKind
import space.mai.mai_launcher.domain.LayoutBackup
import space.mai.mai_launcher.domain.Forecast
import space.mai.mai_launcher.domain.ForecastLine
import space.mai.mai_launcher.domain.MaiSummaryApps
import space.mai.mai_launcher.summary.MaiSummaries
import space.mai.mai_launcher.summary.MaiSummaryReader
import kotlinx.coroutines.launch
import space.mai.mai_launcher.domain.LayoutBackupCodec
import space.mai.mai_launcher.ui.settings.BackupStatus
import space.mai.mai_launcher.ui.settings.DockSlot
import space.mai.mai_launcher.data.LauncherPrefs
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.AppSearch
import space.mai.mai_launcher.domain.MaiWidgets
import space.mai.mai_launcher.domain.ProviderRef
import space.mai.mai_launcher.domain.Predictor
import space.mai.mai_launcher.domain.launchCounts
import space.mai.mai_launcher.media.MediaHub
import space.mai.mai_launcher.signals.SignalsHub
import space.mai.mai_launcher.voice.VoiceInput
import space.mai.mai_launcher.system.HomeRole
import space.mai.mai_launcher.system.SystemActions
import space.mai.mai_launcher.system.SystemStatus
import space.mai.mai_launcher.widgets.WidgetHost
import space.mai.mai_launcher.widgets.WidgetOption

/** Was die Activity für den Launcher erledigt (System-Dialoge mit Ergebnis). */
interface LauncherHost {
    fun requestHomeRole()
    fun requestCalendar()
    fun requestContacts()
    fun requestMic()
    fun startSystemSpeech()
    fun openNotificationAccess()
    fun bindWidget(id: Int, provider: ComponentName)
    fun exportLayout(fileName: String, text: String)
    fun importLayout()
    fun configureWidget(id: Int)
}

/** Optionale Einrichtungsschritte; verschwinden, sobald erledigt oder weggeklickt. */
enum class SetupItem { Home, Signals, Calendar, MaiWidgets }

/** Zustand und Aktionen des Launchers. */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val repo = AppRepository(app)
    val system = SystemStatus(app)
    val actions = SystemActions(app)
    val media = MediaHub(app)
    val calendar = CalendarRepository(app, viewModelScope)
    val widgets = WidgetHost(app)
    val feeds = FeedRepository(app, viewModelScope)
    val contacts = ContactsRepository(app, viewModelScope)
    val voice = VoiceInput(app)
    private val homeRole = HomeRole(app)
    private val prefs = LauncherPrefs(app)
    private val log = LaunchLog(app, viewModelScope).also { it.load() }
    private val predictor = Predictor()
    val icons = IconLoader(repo, (48 * app.resources.displayMetrics.density).toInt())

    /** Vibration und Töne bei Interaktionen (einstellbar). */
    val haptics = prefs.haptics
    val sounds = prefs.sounds
    val feedback = FeedbackPlayer(app, prefs.haptics, prefs.sounds)

    var host: LauncherHost? = null

    private val _screen = MutableStateFlow(Screen.Deck)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Wohin nach der Dock-Auswahl zurückgekehrt wird (Deck oder Einstellungen). */
    private var pickReturn = Screen.Deck

    private val _pickingSlot = MutableStateFlow<Int?>(null)
    val pickingSlot: StateFlow<Int?> = _pickingSlot.asStateFlow()

    private val _focusSearch = MutableStateFlow(0)
    val focusSearch: StateFlow<Int> = _focusSearch.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    /** Zustand der Freigaben; wird bei jedem Zurückkehren neu gelesen. */
    private val _access = MutableStateFlow(Access())
    val access: StateFlow<Access> = _access.asStateFlow()

    data class Access(
        val home: Boolean = true,
        val signals: Boolean = true,
        val calendar: Boolean = true,
        val contacts: Boolean = true,
    )

    val destinations = prefs.destinations
    val navigation = SignalsHub.navigation

    // --- Signale (Benachrichtigungen) ---

    val signalsShowText = prefs.signalsShowText
    val signals: StateFlow<List<SignalGroup>> = combine(SignalsHub.items, prefs.signalsShowText) { items, show ->
        SignalDigest.group(items.values, show)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val badges: StateFlow<Map<String, Int>> = SignalsHub.items.map { SignalDigest.badges(it.values) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun openSignal(key: String) {
        // Ohne eigene Aktion: die App öffnen.
        if (SignalsHub.open(key)) return
        val pkg = SignalsHub.items.value[key]?.packageName ?: return
        val ctx = getApplication<Application>()
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    fun toggleSignalText() = prefs.setSignalsShowText(!prefs.signalsShowText.value)

    /** Mai-Widgets, die nur mit Freigabe (System-Dialog) aufs Deck können. */
    private val _maiPending = MutableStateFlow<List<ProviderRef>>(emptyList())
    val maiPending: StateFlow<List<ProviderRef>> = _maiPending.asStateFlow()

    val setup: StateFlow<List<SetupItem>> = combine(_access, prefs.dismissedSetup, _maiPending) { a, dismissed, mai ->
        buildList {
            if (!a.home) add(SetupItem.Home)
            if (mai.isNotEmpty()) add(SetupItem.MaiWidgets)
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

    // --- Befehlszeile ---

    /** Befehle zur Eingabe (Rechner, Timer, Einstellungen, Websuche …). */
    val commands: StateFlow<List<Command>> = query.map { CommandParser.parse(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Kontakte zur Eingabe; mit Verb („call sam“) nur der Name. */
    val contactResults: StateFlow<List<Contact>> = combine(query, contacts.contacts, contacts.log) { q, list, log ->
        val name = ContactVerb.parse(q)?.second ?: q
        if (name.trim().length < 2) emptyList() else ContactRanking.search(list, name, log).take(3)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun runCommand(cmd: Command) {
        actions.run(cmd)
        if (cmd !is Command.Answer) closeIndex()
    }

    // --- Sprache ---

    /** Mikrofon: Index öffnen und zuhören (auf dem Gerät, sonst System-Dialog). */
    fun startVoice() {
        if (voice.listening.value) { voice.stop(); return }
        if (_screen.value != Screen.Index) openIndex(withKeyboard = false)
        when (voice.mode()) {
            VoiceInput.Mode.OnDevice -> if (voice.hasMic()) listenNow() else host?.requestMic()
            VoiceInput.Mode.SystemDialog -> host?.startSystemSpeech()
            VoiceInput.Mode.None -> Unit
        }
    }

    fun onMicPermission(granted: Boolean) { if (granted) listenNow() }

    private fun listenNow() {
        _query.value = ""
        voice.listen { text, _ -> _query.value = text }
    }

    fun onSpeechResult(text: String?) {
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { _query.value = it }
    }

    // --- Launcher-Einstellungen ---

    /** Dock-Plätze mit Hinweis, ob selbst gewählt oder System-Standard. */
    val settingsDock: StateFlow<List<DockSlot>> = combine(dock, prefs.dock, apps) { tiles, saved, list ->
        val keys = list.mapTo(HashSet()) { it.key }
        tiles.mapIndexed { i, t -> DockSlot(t, saved.getOrNull(i)?.let { it in keys } == true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val version: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull().orEmpty()

    fun openSettings() {
        _editMode.value = false
        _backupStatus.value = null
        _screen.value = Screen.Settings
        refreshAccess()
    }

    fun pickDockFromSettings(slot: Int) {
        startPickingDock(slot)
        pickReturn = Screen.Settings
    }

    fun resetDock() {
        repeat(LauncherPrefs.DOCK_SLOTS) { prefs.setDockSlot(it, null) }
    }

    fun arrangeDeck() {
        _screen.value = Screen.Deck
        _editMode.value = true
    }

    fun cycleHaptics() {
        prefs.setHaptics(prefs.haptics.value.next())
        feedback.tick()
    }

    fun cycleSounds() {
        prefs.setSounds(prefs.sounds.value.next())
        // Vorhören (spielt nur, wenn das Telefon auf „Klingeln“ steht).
        feedback.play(FeedbackKind.Tap)
    }

    /** Startbildschirm: anfragen, oder – wenn schon gesetzt – die System-Auswahl öffnen. */
    fun settingsHome() {
        if (_access.value.home) actions.homeSettings() else host?.requestHomeRole()
    }

    /** Freigegeben: App-Info (dort entziehen); sonst anfragen. */
    fun settingsCalendar() {
        if (_access.value.calendar) actions.launcherInfo() else host?.requestCalendar()
    }

    fun settingsContacts() {
        if (_access.value.contacts) actions.launcherInfo() else host?.requestContacts()
    }

    // --- Prognose aus den Mai-Apps ---

    private val summaryReader = MaiSummaryReader(app)
    private val _summaries = MutableStateFlow(MaiSummaries())
    val forecastOn = prefs.forecast

    /** Zeilen fürs Deck; leer ohne Mai-Apps, ohne Zugriff oder wenn ausgeschaltet. */
    val forecast: StateFlow<List<ForecastLine>> = combine(_summaries, prefs.forecast) { s, on ->
        if (!on) return@combine emptyList()
        val today = java.time.LocalDate.now()
        listOfNotNull(
            s.budget?.let { Forecast.budget(it, today) },
            s.fitness?.let { Forecast.fitness(it, today) },
            s.household?.let { Forecast.household(it, today) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Kacheln der Mai-Apps (zum Öffnen aus dem Prognose-Panel). */
    val forecastApps: StateFlow<Map<String, AppTile>> = apps.map { list ->
        MaiSummaryApps.all.mapNotNull { pkg -> list.firstOrNull { it.key.packageName == pkg && !it.isWork }?.let { pkg to it.toTile() } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun refreshForecast() {
        if (!prefs.forecast.value) return
        viewModelScope.launch { _summaries.value = summaryReader.read() }
    }

    fun toggleForecast() {
        prefs.setForecast(!prefs.forecast.value)
        refreshForecast()
    }

    // --- Sicherung von Layout und Einstellungen ---

    /** Rückmeldung nach Export/Import für die Einstellungen (null = nichts anzeigen). */
    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    fun exportLayout() {
        val providers = widgets.ids.value.mapNotNull { id ->
            widgets.info(id)?.provider?.let { ProviderRef(it.packageName, it.className) }
        }
        val backup = LayoutBackup(
            dock = prefs.dock.value,
            destinations = prefs.destinations.value,
            feeds = feeds.state.value.urls,
            widgets = providers,
            signalsShowText = prefs.signalsShowText.value,
            haptics = prefs.haptics.value,
            sounds = prefs.sounds.value,
        )
        host?.exportLayout(LayoutBackupCodec.fileName(java.time.LocalDate.now()), LayoutBackupCodec.encode(backup))
    }

    fun onLayoutExported(ok: Boolean) {
        _backupStatus.value = if (ok) BackupStatus.Exported else BackupStatus.Failed
    }

    fun importLayout() { host?.importLayout() }

    /** Sicherung anwenden: Dock, Ziele, Feeds, Einstellungen sofort; Widgets über die Freigabe-Dialoge. */
    fun onLayoutImported(text: String?) {
        val b = text?.let(LayoutBackupCodec::decode)
        if (b == null || b.isEmpty) { _backupStatus.value = BackupStatus.Failed; return }
        val dock = LayoutBackupCodec.resolveDock(b.dock, apps.value)
        dock.forEachIndexed { i, k -> if (b.dock.getOrNull(i) != null) prefs.setDockSlot(i, k) }
        b.destinations.forEachIndexed { i, d -> if (d.isNotBlank()) prefs.setDestination(i, d) }
        if (b.feeds.isNotEmpty()) feeds.replaceAll((feeds.state.value.urls + b.feeds).distinct())
        b.signalsShowText?.let(prefs::setSignalsShowText)
        b.haptics?.let(prefs::setHaptics)
        b.sounds?.let(prefs::setSounds)
        // Nur installierte Widgets, die noch nicht auf dem Deck sind.
        val installed = widgets.installedRefs().toSet()
        val placed = widgets.ids.value.mapNotNull { id ->
            widgets.info(id)?.provider?.let { ProviderRef(it.packageName, it.className) }
        }.toSet()
        val todo = b.widgets.filter { it in installed && it !in placed }
        _backupStatus.value = BackupStatus.Imported(
            dock = dock.count { it != null },
            feeds = b.feeds.size,
            widgets = todo.size,
        )
        if (todo.isNotEmpty() && pendingWidget == null) {
            maiQueue = ArrayDeque(todo)
            nextMaiWidget()
        }
    }

    // --- Media ---

    val musicApps: StateFlow<List<AppTile>> = apps.map { list ->
        val pkgs = repo.musicPackages()
        list.filter { it.key.packageName in pkgs && !it.isWork }.map { it.toTile() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openMedia() {
        _editMode.value = false
        _screen.value = Screen.Media
    }

    // --- Comms ---

    /** Wähltastatur: offen/zu und eingetippte Nummer. */
    private val _dialed = MutableStateFlow("")
    private val _keypadOpen = MutableStateFlow(false)
    val keypadOpen: StateFlow<Boolean> = _keypadOpen.asStateFlow()
    val dialed: StateFlow<String> = _dialed.asStateFlow()

    private val _commsQuery = MutableStateFlow("")
    val commsQuery: StateFlow<String> = _commsQuery.asStateFlow()

    val commsResults: StateFlow<List<Contact>> = combine(_commsQuery, contacts.contacts, contacts.log) { q, list, log ->
        if (q.isBlank()) list else ContactRanking.search(list, q, log)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openComms() {
        _editMode.value = false
        _screen.value = Screen.Comms
        contacts.start()
    }

    fun closeComms() {
        _screen.value = Screen.Deck
        _commsQuery.value = ""
        _keypadOpen.value = false
        _dialed.value = ""
    }

    fun setCommsQuery(q: String) { _commsQuery.value = q }
    fun openKeypad(open: Boolean) { _keypadOpen.value = open }
    fun keypadPress(c: Char) { _dialed.value = Keypad.press(_dialed.value, c) }
    fun keypadBack() { _dialed.value = Keypad.backspace(_dialed.value) }
    fun keypadClear() { _dialed.value = "" }

    fun dialTyped() {
        val n = _dialed.value
        if (n.isNotEmpty()) contacts.dial(n)
    }

    fun call(contact: Contact, number: String? = contact.primaryNumber) {
        number?.let { contacts.dial(it, contact) }
    }

    fun text(contact: Contact, number: String? = contact.primaryNumber) {
        number?.let { contacts.text(it, contact) }
    }

    /** Beim Anzeigen: Beobachter starten und Freigaben neu prüfen. */
    fun onVisible() {
        system.start()
        system.refresh()
        widgets.startListening()
        refreshAccess()
        feeds.refresh()
        autoAddMaiWidgets()
        refreshForecast()
    }

    /** Link eines News-Eintrags im Standard-Browser öffnen. */
    fun openLink(url: String) {
        val ctx = getApplication<Application>()
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun onHidden() {
        system.stop()
        media.stop()
        calendar.stop()
        widgets.stopListening()
        contacts.stop()
    }

    fun refreshAccess() {
        val a = Access(
            home = homeRole.isHome(),
            signals = media.hasAccess(),
            calendar = calendar.hasAccess(),
            contacts = contacts.hasAccess(),
        )
        _access.value = a
        if (a.contacts) contacts.start()
        if (a.signals) media.start()
        if (a.calendar) calendar.start()
    }

    fun onSetup(item: SetupItem) {
        val h = host ?: return
        when (item) {
            SetupItem.Home -> h.requestHomeRole()
            SetupItem.Signals -> h.openNotificationAccess()
            SetupItem.Calendar -> h.requestCalendar()
            SetupItem.MaiWidgets -> addMaiWidgets()
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

    fun dismissSetup(item: SetupItem) {
        if (item == SetupItem.MaiWidgets) {
            // „Später“ heißt hier: diese Apps nicht mehr automatisch anbieten (Auswahl bleibt).
            prefs.markMaiWidgetsSeen(_maiPending.value.map { it.packageName })
            _maiPending.value = emptyList()
            return
        }
        prefs.dismissSetup(item.name)
    }

    // --- Navigation zwischen Ansichten ---

    fun openIndex(withKeyboard: Boolean) {
        _editMode.value = false
        _screen.value = Screen.Index
        if (withKeyboard) _focusSearch.value++
    }

    fun closeIndex() {
        voice.stop()
        // Dock-Auswahl aus den Einstellungen: dorthin zurück.
        _screen.value = if (_pickingSlot.value != null) pickReturn else Screen.Deck
        pickReturn = Screen.Deck
        _query.value = ""
        _pickingSlot.value = null
    }

    fun setQuery(q: String) { _query.value = q }

    fun onHome() {
        _editMode.value = false
        if (_screen.value == Screen.Comms) closeComms()
        if (_screen.value == Screen.Media || _screen.value == Screen.Settings) _screen.value = Screen.Deck
        pickReturn = Screen.Deck
        closeIndex()
    }

    fun onBack(): Boolean {
        when {
            _screen.value == Screen.Widgets -> _screen.value = Screen.Deck
            _screen.value == Screen.Media -> _screen.value = Screen.Deck
            _screen.value == Screen.Settings -> _screen.value = Screen.Deck
            _screen.value == Screen.Comms && _keypadOpen.value -> _keypadOpen.value = false
            _screen.value == Screen.Comms && _commsQuery.value.isNotEmpty() -> _commsQuery.value = ""
            _screen.value == Screen.Comms -> closeComms()
            _screen.value == Screen.Index && _query.value.isNotEmpty() -> _query.value = ""
            _screen.value == Screen.Index -> closeIndex()
            _editMode.value -> _editMode.value = false
            else -> return false
        }
        return true
    }

    fun setEditMode(on: Boolean) { _editMode.value = on }

    fun startPickingDock(slot: Int) {
        pickReturn = Screen.Deck
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

    /** Enter: ausdrücklicher Befehl > App > Anruf beim Kontakt mit Verb. Websuche nie per Enter. */
    fun launchFirstResult() {
        val cmd = commands.value.firstOrNull()
        if (cmd != null && CommandParser.isExplicit(cmd)) { runCommand(cmd); return }
        val verb = ContactVerb.parse(query.value)
        val contact = contactResults.value.firstOrNull()
        if (verb != null && contact != null) {
            if (verb.first == ContactVerb.Kind.Call) call(contact) else text(contact)
            closeIndex()
            return
        }
        results.value.firstOrNull()?.let { onAppTapped(it.key); return }
        if (contact != null) { call(contact); closeIndex() }
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

    /**
     * Mai-Apps automatisch mit ihrem Standard-Widget aufs Deck legen. Klappt das
     * Binden ohne Dialog (Launcher hat die Freigabe), geschieht es still; sonst
     * erscheint ein Einrichtungs-Hinweis, der die Freigabe einmal anfragt.
     */
    fun autoAddMaiWidgets() {
        if (pendingWidget != null) return
        val placed = widgets.placedPackages()
        // Schon vorhandene Mai-Widgets gelten als gesehen: einmal entfernt, kommen sie nicht wieder.
        prefs.markMaiWidgetsSeen(placed.filter(MaiWidgets::isMai).filter { it !in prefs.maiWidgetsSeen.value })
        val todo = MaiWidgets.pending(widgets.installedRefs(), placed, prefs.maiWidgetsSeen.value)
        val needsDialog = mutableListOf<ProviderRef>()
        for (ref in todo) {
            val provider = ComponentName(ref.packageName, ref.className)
            val (id, needsBind) = widgets.allocate(provider)
            when {
                needsBind -> { widgets.discard(id); needsDialog += ref }
                // Widgets mit eigener Einrichtung nicht ungefragt öffnen; sie bleiben in der Auswahl.
                widgets.needsConfigure(id) -> { widgets.discard(id); prefs.markMaiWidgetsSeen(listOf(ref.packageName)) }
                else -> { widgets.commit(id); prefs.markMaiWidgetsSeen(listOf(ref.packageName)) }
            }
        }
        _maiPending.value = needsDialog
    }

    /** Warteschlange für „Mai-Widgets hinzufügen“: nacheinander über den System-Dialog. */
    private var maiQueue: ArrayDeque<ProviderRef>? = null

    private fun addMaiWidgets() {
        maiQueue = ArrayDeque(_maiPending.value)
        nextMaiWidget()
    }

    private fun nextMaiWidget() {
        val queue = maiQueue ?: return
        val ref = queue.removeFirstOrNull()
        if (ref == null) { maiQueue = null; autoAddMaiWidgets(); return }
        prefs.markMaiWidgetsSeen(listOf(ref.packageName))
        _maiPending.value = queue.toList()
        val provider = ComponentName(ref.packageName, ref.className)
        val (id, needsBind) = widgets.allocate(provider)
        pendingWidget = id
        // Nach „Immer erlauben“ im ersten Dialog binden die übrigen ohne Rückfrage.
        if (needsBind) host?.bindWidget(id, provider) ?: onWidgetBound(false) else onWidgetBound(true)
    }

    fun onWidgetBound(ok: Boolean) {
        val id = pendingWidget ?: return
        if (!ok) {
            widgets.discard(id); pendingWidget = null
            // Abgelehnt: übrige Mai-Widgets nicht weiter nachfragen.
            maiQueue?.let { q -> prefs.markMaiWidgetsSeen(q.map { it.packageName }); maiQueue = null; _maiPending.value = emptyList() }
            return
        }
        if (widgets.needsConfigure(id)) host?.configureWidget(id) ?: onWidgetConfigured(false) else onWidgetConfigured(true)
    }

    fun onWidgetConfigured(ok: Boolean) {
        val id = pendingWidget ?: return
        pendingWidget = null
        if (ok) widgets.commit(id) else widgets.discard(id)
        if (maiQueue != null) nextMaiWidget()
    }

    override fun onCleared() {
        onHidden()
        feedback.release()
    }
}
