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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import space.mai.mai_launcher.data.NotesStore
import space.mai.mai_launcher.domain.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.mai.mai_launcher.domain.LayoutBackupCodec
import space.mai.mai_launcher.ui.settings.BackupStatus
import android.os.SystemClock
import android.provider.Settings
import space.mai.mai_launcher.design.Comfort
import space.mai.mai_launcher.design.ComfortState
import space.mai.mai_launcher.domain.Appearance
import space.mai.mai_launcher.domain.AccentChoice
import space.mai.mai_launcher.domain.AppCatalog
import space.mai.mai_launcher.domain.AppShortcut
import space.mai.mai_launcher.domain.IndexTab
import space.mai.mai_launcher.domain.AppearanceRules
import space.mai.mai_launcher.ui.settings.DockSlot
import space.mai.mai_launcher.data.LauncherPrefs
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.Board
import space.mai.mai_launcher.domain.BoardBackup
import space.mai.mai_launcher.domain.BoardKind
import space.mai.mai_launcher.domain.Boards
import space.mai.mai_launcher.domain.PageOrder
import space.mai.mai_launcher.domain.PageRef
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
    private val notesStore = NotesStore(app, viewModelScope).also { it.load() }
    val notes: StateFlow<List<Note>> = notesStore.notes
    private val predictor = Predictor()
    val icons = IconLoader(repo, (48 * app.resources.displayMetrics.density).toInt())

    /** Vibration und Töne bei Interaktionen (einstellbar). */
    val haptics = prefs.haptics
    val sounds = prefs.sounds
    val feedback = FeedbackPlayer(app, prefs.haptics, prefs.sounds)

    // --- Darstellung: Grundfarbe und Komfort-Modi ---

    val appearance = prefs.appearance
    private var systemAnimatorScale = readAnimatorScale()
    private var lastPowerOn = 0L

    init {
        // Main.immediate: der erste Wert steht schon vor dem ersten Bild fest.
        viewModelScope.launch { prefs.appearance.collect { applyComfort(it) } }
    }

    private fun readAnimatorScale(): Float = runCatching {
        Settings.Global.getFloat(getApplication<Application>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    private fun applyComfort(a: Appearance) {
        ComfortState.current = Comfort(
            highContrast = a.highContrast,
            reduceMotion = AppearanceRules.reduceMotion(a.reduceMotion, systemAnimatorScale),
            large = a.large,
        )
    }

    fun setAccent(choice: AccentChoice) {
        prefs.setAppearance(prefs.appearance.value.copy(accent = choice))
    }

    fun toggleContrast() = prefs.setAppearance(prefs.appearance.value.let { it.copy(highContrast = !it.highContrast) })
    fun toggleReduceMotion() = prefs.setAppearance(prefs.appearance.value.let { it.copy(reduceMotion = !it.reduceMotion) })
    fun toggleLarge() = prefs.setAppearance(prefs.appearance.value.let { it.copy(large = !it.large) })

    /** Einschalt-Animation auslösen (entprellt: Home-Taste und Fortsetzen kommen oft zusammen). */
    private fun powerOn() {
        val now = SystemClock.uptimeMillis()
        // Vorgemerktes Einblenden nie verschlucken, sonst bliebe das Deck unsichtbar.
        if (now - lastPowerOn < 400 && !ComfortState.powerOnArmed) return
        lastPowerOn = now
        ComfortState.powerOn++
    }

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
        val assistant: Boolean = false,
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

    /** Ausgeblendete Apps und Apps, die nie vorgeschlagen werden sollen. */
    val hiddenApps = prefs.hiddenApps
    val aliases = prefs.aliases
    private val notSuggested = combine(prefs.hiddenApps, prefs.likelyHidden) { a, b -> a + b }

    val likely: StateFlow<List<AppTile>> = combine(apps, log.events, dock, system.state, notSuggested) { list, events, dockTiles, sys, never ->
        val byKey = list.associateBy { it.key }
        val exclude = dockTiles.mapNotNull { it?.key }.toSet() + never
        predictor.predict(events, sys.nowMillis, byKey.keys, exclude).mapNotNull { byKey[it]?.toTile() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Begründung je Vorschlag: Starts um diese Tageszeit in den letzten 4 Wochen. */
    val likelyReasons: StateFlow<Map<AppKey, Int>> = combine(likely, log.events) { tiles, events ->
        val now = System.currentTimeMillis()
        tiles.associate { it.key to predictor.reason(events, it.key, now) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val likelyWindow: Pair<Int, Int> get() = predictor.window(System.currentTimeMillis())

    val results: StateFlow<List<AppTile>> = combine(apps, query, log.events, prefs.hiddenApps, prefs.aliases) { list, q, events, hidden, alias ->
        AppSearch.filter(AppCatalog.searchable(list, hidden), q, launchCounts(events), alias).map { it.toTile() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Index: Reiter, Langdruck-Menü ---

    private val _indexTab = MutableStateFlow(IndexTab.All)
    val indexTab: StateFlow<IndexTab> = _indexTab.asStateFlow()

    val indexTabs: StateFlow<List<IndexTab>> = combine(apps, prefs.hiddenApps) { list, hidden -> AppCatalog.tabs(list, hidden) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(IndexTab.All))

    /** Apps des aktuellen Reiters (fällt auf „Alle“ zurück, wenn der Reiter leer wird). */
    val indexApps: StateFlow<List<AppInfo>> = combine(apps, prefs.hiddenApps, _indexTab, indexTabs) { list, hidden, tab, tabs ->
        AppCatalog.inTab(list, hidden, if (tab in tabs) tab else IndexTab.All)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectIndexTab(tab: IndexTab) { _indexTab.value = tab }

    suspend fun shortcuts(key: AppKey): List<AppShortcut> = withContext(Dispatchers.IO) { repo.shortcuts(key) }

    fun startShortcut(shortcut: AppShortcut) {
        if (repo.startShortcut(shortcut)) closeIndex()
    }

    fun pinToDock(key: AppKey, slot: Int) {
        prefs.setDockSlot(slot, key)
        feedback.tick()
    }

    fun setHidden(key: AppKey, hidden: Boolean) = prefs.setHidden(key, hidden)

    fun setAlias(key: AppKey, alias: String) = prefs.setAlias(key, alias)

    fun hideFromLikely(key: AppKey) = prefs.setLikelyHidden(prefs.likelyHidden.value + key)

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
        if (cmd is Command.Note) {
            notesStore.add(cmd.text)
            feedback.tick()
            closeIndex()
            return
        }
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

    /** Assistenten-Geste: Systemseite „Digitaler Assistent“ (dort Mai Launcher wählen). */
    fun settingsAssistant() {
        val ctx = getApplication<Application>()
        runCatching { ctx.startActivity(homeRole.assistantSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { actions.systemSettings() }
    }

    /** Assistenten-Geste (lange Home-Taste, Ecke wischen): Befehlszeile mit Tastatur. */
    fun onAssist() {
        _editMode.value = false
        _pickingSlot.value = null
        _query.value = ""
        openIndex(withKeyboard = true)
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
            accent = prefs.appearance.value.accent,
            highContrast = prefs.appearance.value.highContrast,
            reduceMotion = prefs.appearance.value.reduceMotion,
            large = prefs.appearance.value.large,
            hidden = prefs.hiddenApps.value,
            likelyHidden = prefs.likelyHidden.value,
            aliases = prefs.aliases.value,
            boards = boards.value.map { b ->
                BoardBackup(
                    id = b.id, title = b.title, kind = b.kind, accent = b.accent, apps = b.apps,
                    widgets = b.widgets.mapNotNull { id -> widgets.info(id)?.provider?.let { ProviderRef(it.packageName, it.className) } },
                )
            },
            pageOrder = PageOrder.encode(pages.value),
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
        prefs.setAppearance(b.appearanceOver(prefs.appearance.value))
        val installedApps = apps.value
        if (b.hidden.isNotEmpty()) {
            prefs.setHiddenApps(prefs.hiddenApps.value + LayoutBackupCodec.resolveKeys(b.hidden, installedApps).values)
        }
        if (b.likelyHidden.isNotEmpty()) {
            prefs.setLikelyHidden(prefs.likelyHidden.value + LayoutBackupCodec.resolveKeys(b.likelyHidden, installedApps).values)
        }
        if (b.aliases.isNotEmpty()) {
            val mapped = LayoutBackupCodec.resolveKeys(b.aliases.keys, installedApps)
            prefs.setAliases(prefs.aliases.value + b.aliases.mapNotNull { (k, a) -> mapped[k]?.let { it to a } })
        }
        // Nur installierte Widgets, die noch nicht auf dem Deck sind.
        val installed = widgets.installedRefs().toSet()
        val placed = widgets.ids.value.mapNotNull { id ->
            widgets.info(id)?.provider?.let { ProviderRef(it.packageName, it.className) }
        }.toSet()
        val todo: MutableList<Pair<ProviderRef, String?>> = b.widgets.filter { it in installed && it !in placed }.map { it to null }.toMutableList()
        // Boards: nur neue (gleiche ID = schon vorhanden); Apps wie beim Dock abbilden.
        val existing = boards.value
        val added = b.boards.filter { nb -> existing.none { it.id == nb.id } }.take((Boards.MAX_BOARDS - existing.size).coerceAtLeast(0))
        if (added.isNotEmpty()) {
            val restored = added.map { nb ->
                val mapped = LayoutBackupCodec.resolveKeys(nb.apps, installedApps)
                Board(
                    id = nb.id, title = nb.title, kind = nb.kind,
                    accent = nb.accent ?: space.mai.mai_launcher.domain.BoardTemplates.accent(nb.kind),
                    apps = nb.apps.mapNotNull { mapped[it] }.distinct().take(Boards.MAX_APPS),
                )
            }
            val all = existing + restored
            val order = b.pageOrder?.let { PageOrder.decode(it, all.map { x -> x.id }) } ?: pages.value
            prefs.setBoards(all, order)
            added.forEach { nb -> nb.widgets.filter { it in installed }.forEach { todo += it to nb.id } }
        }
        _backupStatus.value = BackupStatus.Imported(
            dock = dock.count { it != null },
            feeds = b.feeds.size,
            widgets = todo.size,
            boards = added.size,
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

    fun openMedia() = goToPage(PageRef.Media)

    // --- Seiten und eigene Dashboards (Boards) ---

    val boards: StateFlow<List<Board>> = prefs.boards
    val pages: StateFlow<List<PageRef>> = prefs.pages

    /** Gerade angezeigtes Board (nur gültig, solange [screen] = Board). */
    private val _boardId = MutableStateFlow<String?>(null)
    val boardId: StateFlow<String?> = _boardId.asStateFlow()

    /** Board, für das gerade eine App ausgewählt wird (Index im Auswahlmodus). */
    private val _boardPick = MutableStateFlow<String?>(null)
    val boardPick: StateFlow<String?> = _boardPick.asStateFlow()

    /** Ziel für das nächste Widget: Board-ID oder null = Deck. */
    private var widgetTarget: String? = null

    /** Seite des aktuellen Bildschirms; null = keine Wisch-Seite (Index, Einstellungen …). */
    fun currentPage(): PageRef? = when (_screen.value) {
        Screen.Deck -> PageRef.Deck
        Screen.Comms -> PageRef.Comms
        Screen.Media -> PageRef.Media
        Screen.Board -> _boardId.value?.let { PageRef.OfBoard(it) }
        else -> null
    }

    /** Wischen: −1 = Seite links davon (nach rechts gewischt), +1 = rechts davon. */
    fun swipePage(direction: Int) {
        val current = currentPage() ?: return
        PageOrder.neighbour(pages.value, current, direction)?.let(::goToPage)
    }

    fun goToPage(page: PageRef) {
        _editMode.value = false
        if (_screen.value == Screen.Comms && page != PageRef.Comms) resetComms()
        when (page) {
            PageRef.Deck -> _screen.value = Screen.Deck
            PageRef.Media -> _screen.value = Screen.Media
            PageRef.Comms -> { _screen.value = Screen.Comms; contacts.start() }
            is PageRef.OfBoard -> {
                if (boards.value.none { it.id == page.id }) { _screen.value = Screen.Deck; return }
                _boardId.value = page.id
                _screen.value = Screen.Board
            }
        }
    }

    private fun board(id: String): Board? = boards.value.firstOrNull { it.id == id }

    private fun updateBoard(id: String, change: (Board) -> Board) {
        val list = boards.value
        if (list.none { it.id == id }) return
        prefs.setBoards(list.map { if (it.id == id) change(it) else it })
    }

    /** Neues Dashboard aus einer Vorlage: Apps werden vorgeschlagen, dann öffnet es im Bearbeiten-Modus. */
    fun createBoard(kind: BoardKind) {
        val list = boards.value
        if (list.size >= Boards.MAX_BOARDS) return
        val b = Boards.create(kind, list.mapTo(HashSet()) { it.id }, apps.value.filterNot { it.key in prefs.hiddenApps.value })
        prefs.setBoards(list + b, pages.value + PageRef.OfBoard(b.id))
        feedback.tick()
        goToPage(PageRef.OfBoard(b.id))
        _editMode.value = true
    }

    fun renameBoard(id: String, title: String) = updateBoard(id) { it.copy(title = Boards.cleanTitle(title)) }

    fun cycleBoardAccent(id: String) = updateBoard(id) { it.copy(accent = it.accent.next()) }

    fun moveBoardApp(id: String, key: AppKey, delta: Int) = updateBoard(id) { Boards.moveApp(it, key, delta) }

    fun removeBoardApp(id: String, key: AppKey) = updateBoard(id) { Boards.removeApp(it, key) }

    fun moveBoardWidget(id: String, widgetId: Int, delta: Int) = updateBoard(id) { Boards.moveWidget(it, widgetId, delta) }

    /** Seite (auch Comms/Deck/Media) in der Wisch-Reihenfolge verschieben. */
    fun movePage(page: PageRef, delta: Int) {
        prefs.setPages(PageOrder.move(pages.value, page, delta))
        feedback.tick()
    }

    /** Index im Auswahlmodus öffnen; die gewählte App kommt aufs Board. */
    fun startPickingForBoard(id: String) {
        _pickingSlot.value = null
        _boardPick.value = id
        pickReturn = Screen.Board
        _editMode.value = false
        _screen.value = Screen.Index
    }

    fun openBoardWidgetPicker(id: String) {
        widgetTarget = id
        _screen.value = Screen.Widgets
    }

    /** Board löschen – 10 s rückgängig; erst danach werden seine Widgets freigegeben. */
    fun deleteBoard(id: String) {
        val list = boards.value
        val b = list.firstOrNull { it.id == id } ?: return
        val order = pages.value
        prefs.setBoards(list - b, order)
        if (_screen.value == Screen.Board && _boardId.value == id) goToPage(PageRef.Deck)
        offerUndo(
            UndoKind.Board,
            undo = { prefs.setBoards(boards.value + b, order) },
            commit = { b.widgets.forEach { widgets.discard(it) } },
        )
    }

    fun removeBoardWidget(id: String, widgetId: Int) {
        _pendingWidgetRemoval.value += widgetId
        offerUndo(
            UndoKind.Widget,
            undo = { _pendingWidgetRemoval.value -= widgetId },
            commit = {
                widgets.discard(widgetId)
                updateBoard(id) { it.copy(widgets = it.widgets - widgetId) }
                _pendingWidgetRemoval.value -= widgetId
            },
        )
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

    fun openComms() = goToPage(PageRef.Comms)

    fun closeComms() {
        _screen.value = Screen.Deck
        resetComms()
    }

    private fun resetComms() {
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
        systemAnimatorScale = readAnimatorScale()
        applyComfort(prefs.appearance.value)
        if (_screen.value == Screen.Deck) powerOn() else ComfortState.powerOnArmed = false
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

    // --- Rückgängig (10 s) ---

    private companion object {
        const val UNDO_MS = 10_000L
    }

    private val _undo = MutableStateFlow<UndoKind?>(null)
    val undo: StateFlow<UndoKind?> = _undo.asStateFlow()
    private var undoAction: (() -> Unit)? = null
    private var undoCommit: (() -> Unit)? = null
    private var undoJob: Job? = null

    /** Widgets, die entfernt, aber noch nicht gelöscht sind (Rückgängig möglich). */
    private val _pendingWidgetRemoval = MutableStateFlow<Set<Int>>(emptySet())
    val pendingWidgetRemoval: StateFlow<Set<Int>> = _pendingWidgetRemoval.asStateFlow()

    private fun offerUndo(kind: UndoKind, undo: () -> Unit, commit: () -> Unit = {}) {
        commitUndo()
        undoAction = undo
        undoCommit = commit
        _undo.value = kind
        undoJob = viewModelScope.launch {
            delay(UNDO_MS)
            commitUndo()
        }
    }

    /** Frist abgelaufen (oder nächste Aktion): endgültig ausführen. */
    private fun commitUndo() {
        val commit = undoCommit
        undoJob?.cancel()
        undoJob = null
        undoAction = null
        undoCommit = null
        _undo.value = null
        commit?.invoke()
    }

    fun undoLast() {
        val action = undoAction ?: return
        undoJob?.cancel()
        undoJob = null
        undoAction = null
        undoCommit = null
        _undo.value = null
        action()
        feedback.tick()
    }

    fun removeWidget(id: Int) {
        _pendingWidgetRemoval.value += id
        offerUndo(
            UndoKind.Widget,
            undo = { _pendingWidgetRemoval.value -= id },
            commit = { widgets.remove(id); _pendingWidgetRemoval.value -= id },
        )
    }

    fun removeFeed(url: String) {
        val before = feeds.state.value.urls
        feeds.remove(url)
        offerUndo(UndoKind.Feed, undo = { feeds.replaceAll(before) })
    }

    fun noteDone(note: Note) {
        notesStore.remove(note.id)
        offerUndo(UndoKind.Note, undo = { notesStore.restore(note) })
    }

    fun onHidden() {
        // Wer den Startbildschirm verlässt, hat sich entschieden.
        commitUndo()
        // Beim nächsten Anzeigen blendet das Deck von schwarz ein (kein Aufblitzen des alten Bildes).
        if (_screen.value == Screen.Deck) ComfortState.armPowerOn()
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
            assistant = homeRole.isAssistant(),
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
        _screen.value = if (_pickingSlot.value != null || _boardPick.value != null) pickReturn else Screen.Deck
        pickReturn = Screen.Deck
        _query.value = ""
        _pickingSlot.value = null
        _boardPick.value = null
        _indexTab.value = IndexTab.All
    }

    fun setQuery(q: String) { _query.value = q }

    fun onHome() {
        _editMode.value = false
        if (_screen.value == Screen.Comms) closeComms()
        if (_screen.value == Screen.Media || _screen.value == Screen.Settings || _screen.value == Screen.Board ||
            _screen.value == Screen.Widgets) _screen.value = Screen.Deck
        _boardPick.value = null
        pickReturn = Screen.Deck
        closeIndex()
        powerOn()
    }

    fun onBack(): Boolean {
        when {
            _screen.value == Screen.Widgets -> _screen.value = if (widgetTarget != null && _boardId.value == widgetTarget) Screen.Board else Screen.Deck
            _screen.value == Screen.Board && _editMode.value -> _editMode.value = false
            _screen.value == Screen.Board -> _screen.value = Screen.Deck
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
        _boardPick.value = null
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
        val pickBoard = _boardPick.value
        if (pickBoard != null) {
            updateBoard(pickBoard) { Boards.addApp(it, key) }
            feedback.tick()
            closeIndex()
            _editMode.value = true
            return
        }
        if (repo.launch(key, bounds)) {
            log.record(key)
            // Vom Dashboard aus gestartet: beim Zurückkehren wieder dort sein.
            if (_screen.value != Screen.Board) closeIndex()
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
        widgetTarget = null
        _screen.value = Screen.Widgets
    }

    fun widgetOptions(): List<WidgetOption> = widgets.options()

    fun addWidget(option: WidgetOption) {
        val (id, needsBind) = widgets.allocate(option.provider)
        pendingWidget = id
        val target = widgetTarget
        _screen.value = if (target != null && board(target) != null) { _boardId.value = target; Screen.Board } else Screen.Deck
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
    private var maiQueue: ArrayDeque<Pair<ProviderRef, String?>>? = null

    private fun addMaiWidgets() {
        maiQueue = ArrayDeque(_maiPending.value.map { it to null })
        nextMaiWidget()
    }

    private fun nextMaiWidget() {
        val queue = maiQueue ?: return
        val (ref, target) = queue.removeFirstOrNull() ?: run { maiQueue = null; autoAddMaiWidgets(); return }
        prefs.markMaiWidgetsSeen(listOf(ref.packageName))
        widgetTarget = target?.takeIf { board(it) != null }
        _maiPending.value = queue.filter { it.second == null }.map { it.first }
        val provider = ComponentName(ref.packageName, ref.className)
        val (id, needsBind) = widgets.allocate(provider)
        pendingWidget = id
        // Nach „Immer erlauben“ im ersten Dialog binden die übrigen ohne Rückfrage.
        if (needsBind) host?.bindWidget(id, provider) ?: onWidgetBound(false) else onWidgetBound(true)
    }

    fun onWidgetBound(ok: Boolean) {
        val id = pendingWidget ?: return
        if (!ok) {
            widgets.discard(id); pendingWidget = null; widgetTarget = null
            // Abgelehnt: übrige Mai-Widgets nicht weiter nachfragen.
            maiQueue?.let { q -> prefs.markMaiWidgetsSeen(q.map { it.first.packageName }); maiQueue = null; _maiPending.value = emptyList() }
            return
        }
        if (widgets.needsConfigure(id)) host?.configureWidget(id) ?: onWidgetConfigured(false) else onWidgetConfigured(true)
    }

    fun onWidgetConfigured(ok: Boolean) {
        val id = pendingWidget ?: return
        pendingWidget = null
        val target = widgetTarget
        widgetTarget = null
        when {
            !ok -> widgets.discard(id)
            target != null && board(target) != null && widgets.info(id) != null ->
                updateBoard(target) { if (id in it.widgets) it else it.copy(widgets = it.widgets + id) }
            target != null -> widgets.discard(id)
            else -> widgets.commit(id)
        }
        if (maiQueue != null) nextMaiWidget()
    }

    override fun onCleared() {
        onHidden()
        feedback.release()
    }
}
