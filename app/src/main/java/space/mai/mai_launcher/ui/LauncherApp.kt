package space.mai.mai_launcher.ui

import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import space.mai.mai_launcher.domain.BoardTemplates
import space.mai.mai_launcher.domain.Boards
import space.mai.mai_launcher.domain.Forecast
import space.mai.mai_launcher.domain.PageRef
import space.mai.mai_launcher.ui.boards.BoardActions
import space.mai.mai_launcher.ui.boards.BoardScreen
import space.mai.mai_launcher.ui.boards.BoardState
import space.mai.mai_launcher.ui.settings.PageEntry
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.unit.Density
import space.mai.mai_launcher.design.ComfortState
import space.mai.mai_launcher.design.toAccent
import space.mai.mai_launcher.domain.AppearanceRules
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.LocalFeedback
import space.mai.mai_launcher.ui.settings.SettingsActions
import space.mai.mai_launcher.ui.settings.SettingsScreen
import space.mai.mai_launcher.ui.settings.SettingsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import space.mai.mai_launcher.design.MaiBackground
import space.mai.mai_launcher.domain.ContactRanking
import space.mai.mai_launcher.ui.comms.CommsActions
import space.mai.mai_launcher.ui.comms.CommsScreen
import space.mai.mai_launcher.ui.comms.CommsState
import space.mai.mai_launcher.ui.deck.DeckActions
import space.mai.mai_launcher.ui.media.MediaScreen
import space.mai.mai_launcher.ui.media.MediaScreenActions
import space.mai.mai_launcher.ui.media.MediaState
import space.mai.mai_launcher.ui.deck.DeckScreen
import space.mai.mai_launcher.ui.deck.DeckState
import space.mai.mai_launcher.ui.deck.MediaActions
import space.mai.mai_launcher.ui.deck.RailActions
import space.mai.mai_launcher.ui.deck.WidgetSlot
import space.mai.mai_launcher.ui.deck.systemUses24h
import space.mai.mai_launcher.ui.index.IndexActions
import space.mai.mai_launcher.ui.index.IndexScreen
import space.mai.mai_launcher.ui.index.IndexState
import space.mai.mai_launcher.ui.widgets.WidgetChoice
import space.mai.mai_launcher.ui.widgets.WidgetPickerScreen
import space.mai.mai_launcher.widgets.WidgetHost

/** Was gerade angezeigt wird; beim Board zusätzlich welches (für die Übergänge). */
private data class ScreenKey(val screen: Screen, val board: String?)

/** Wurzel: Deck, Index und Widget-Auswahl. */
@Composable
fun LauncherApp(vm: LauncherViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val sys by vm.system.state.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dock by vm.dock.collectAsStateWithLifecycle()
    val likely by vm.likely.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val picking by vm.pickingSlot.collectAsStateWithLifecycle()
    val focus by vm.focusSearch.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val nowPlaying by vm.media.nowPlaying.collectAsStateWithLifecycle()
    val art by vm.media.art.collectAsStateWithLifecycle()
    val navigation by vm.navigation.collectAsStateWithLifecycle()
    val events by vm.calendar.events.collectAsStateWithLifecycle()
    val destinations by vm.destinations.collectAsStateWithLifecycle()
    val widgetIds by vm.widgets.ids.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val news by vm.feeds.state.collectAsStateWithLifecycle()
    val commands by vm.commands.collectAsStateWithLifecycle()
    val contactHits by vm.contactResults.collectAsStateWithLifecycle()
    val allContacts by vm.contacts.contacts.collectAsStateWithLifecycle()
    val commsLog by vm.contacts.log.collectAsStateWithLifecycle()
    val commsResults by vm.commsResults.collectAsStateWithLifecycle()
    val commsQuery by vm.commsQuery.collectAsStateWithLifecycle()
    val keypadOpen by vm.keypadOpen.collectAsStateWithLifecycle()
    val dialed by vm.dialed.collectAsStateWithLifecycle()
    val signals by vm.signals.collectAsStateWithLifecycle()
    val signalsText by vm.signalsShowText.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val listening by vm.voice.listening.collectAsStateWithLifecycle()
    val queue by vm.media.queue.collectAsStateWithLifecycle()
    val musicApps by vm.musicApps.collectAsStateWithLifecycle()
    val settingsDock by vm.settingsDock.collectAsStateWithLifecycle()
    val haptics by vm.haptics.collectAsStateWithLifecycle()
    val sounds by vm.sounds.collectAsStateWithLifecycle()
    val backupStatus by vm.backupStatus.collectAsStateWithLifecycle()
    val forecast by vm.forecast.collectAsStateWithLifecycle()
    val forecastApps by vm.forecastApps.collectAsStateWithLifecycle()
    val forecastOn by vm.forecastOn.collectAsStateWithLifecycle()
    val use24h = systemUses24h()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val indexApps by vm.indexApps.collectAsStateWithLifecycle()
    val indexTab by vm.indexTab.collectAsStateWithLifecycle()
    val indexTabs by vm.indexTabs.collectAsStateWithLifecycle()
    val hiddenApps by vm.hiddenApps.collectAsStateWithLifecycle()
    val aliases by vm.aliases.collectAsStateWithLifecycle()
    val likelyReasons by vm.likelyReasons.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val pendingRemoval by vm.pendingWidgetRemoval.collectAsStateWithLifecycle()
    val boards by vm.boards.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val boardId by vm.boardId.collectAsStateWithLifecycle()
    val boardPick by vm.boardPick.collectAsStateWithLifecycle()
    val areas = remember(appearance.accent) { AppearanceRules.areas(appearance.accent) }
    val comfort = ComfortState.current

    val artBitmap = remember(art) { art?.asImageBitmap() }
    val slotsOf: (List<Int>) -> List<WidgetSlot> = { ids ->
        ids.filterNot { it in pendingRemoval }.mapNotNull { id ->
            val info = vm.widgets.info(id) ?: return@mapNotNull null
            val label = info.loadLabel(vm.getApplication<android.app.Application>().packageManager) ?: ""
            val dm = vm.getApplication<android.app.Application>().resources.displayMetrics
            val h = (info.minHeight / dm.density).toInt().coerceIn(72, 360)
            WidgetSlot(id, label, null, h)
        }
    }
    val widgets = remember(widgetIds, pendingRemoval) { slotsOf(widgetIds) }

    val iconProvider = remember(vm) { IconProvider { vm.icons.monochrome(it) } }
    val deckActions = remember(vm) {
        DeckActions(
            onApp = { vm.onAppTapped(it.key) },
            onDockLongPress = vm::startPickingDock,
            onCommand = { vm.openIndex(withKeyboard = true) },
            onSwipeRight = { vm.swipePage(-1) },
            onSwipeLeft = { vm.swipePage(1) },
            onMic = vm::startVoice,
            onSignal = vm::openSignal,
            onToggleSignalText = vm::toggleSignalText,
            onClock = vm.actions::alarms,
            onBattery = vm.actions::batterySettings,
            onStorage = vm.actions::storageSettings,
            rail = RailActions(
                onWifi = vm.actions::wifiPanel,
                onWifiLong = vm.actions::wifiSettings,
                onBluetooth = vm.actions::bluetoothSettings,
                onDnd = vm::toggleDnd,
                onDndLong = vm.actions::dndSettings,
                onSound = vm::cycleSound,
                onSoundLong = vm.actions::soundSettings,
                onTorch = vm.system::toggleTorch,
                onRotate = vm.actions::displaySettings,
                onNotifications = vm.actions::expandNotifications,
                onSettings = vm::openSettings,
            ),
            onSetup = vm::onSetup,
            onDismissSetup = vm::dismissSetup,
            onEditMode = vm::setEditMode,
            media = MediaActions(
                elapsedNow = SystemClock::elapsedRealtime,
                onOpen = vm.media::openPlayer,
                onPrevious = vm.media::previous,
                onPlayPause = vm.media::playPause,
                onNext = vm.media::next,
            ),
            onNavigation = vm::openNavigation,
            onDestination = vm::navigateTo,
            onSetDestination = vm::setDestination,
            onEvent = vm.calendar::openEvent,
            onCalendar = vm.calendar::openCalendar,
            onAddWidget = vm::openWidgetPicker,
            onMoveWidget = vm.widgets::move,
            onRemoveWidget = vm::removeWidget,
            widgetContent = { slot -> HostedWidget(vm.widgets, slot) },
            onOpenLink = vm::openLink,
            onRefreshNews = { vm.feeds.refresh(force = true) },
            onAddFeed = vm.feeds::add,
            onRemoveFeed = vm::removeFeed,
            onNoteDone = vm::noteDone,
            onUndo = vm::undoLast,
            onHideLikely = vm::hideFromLikely,
            onBoards = vm::openSettings,
        )
    }
    val indexActions = remember(vm) {
        IndexActions(
            onApp = { vm.onAppTapped(it.key) },
            onQuery = vm::setQuery,
            onSubmit = vm::launchFirstResult,
            onClose = vm::closeIndex,
            onAppInfo = vm::appInfo,
            onUninstall = vm::uninstall,
            onCommand = vm::runCommand,
            onMic = vm::startVoice,
            onCall = { vm.call(it); vm.closeIndex() },
            onText = { vm.text(it); vm.closeIndex() },
            onTab = vm::selectIndexTab,
            loadShortcuts = vm::shortcuts,
            onShortcut = vm::startShortcut,
            onPinDock = vm::pinToDock,
            onHide = vm::setHidden,
            onAlias = vm::setAlias,
            onHideLikely = vm::hideFromLikely,
        )
    }
    val mediaActions = remember(vm) {
        MediaScreenActions(
            elapsedNow = SystemClock::elapsedRealtime,
            onPlayPause = vm.media::playPause,
            onPrevious = vm.media::previous,
            onNext = vm.media::next,
            onSeek = vm.media::seekTo,
            onOpenPlayer = vm.media::openPlayer,
            onOutput = vm.media::openOutput,
            onApp = { vm.onAppTapped(it.key) },
            onAllow = { vm.host?.openNotificationAccess() },
            onClose = vm::onBack,
            onSwipe = vm::swipePage,
        )
    }
    val commsActions = remember(vm) {
        CommsActions(
            onAllow = { vm.host?.requestContacts() },
            onQuery = vm::setCommsQuery,
            onCall = { c, n -> vm.call(c, n) },
            onText = { c, n -> vm.text(c, n) },
            onOpenContact = vm.contacts::openContact,
            onRufDurch = vm.contacts::openRufDurch,
            onKeypad = vm::openKeypad,
            onKey = vm::keypadPress,
            onBackspace = vm::keypadBack,
            onClearDial = vm::keypadClear,
            onDial = vm::dialTyped,
            onClose = vm::closeComms,
            onSwipe = vm::swipePage,
        )
    }

    val settingsActions = remember(vm) {
        SettingsActions(
            onDockSlot = vm::pickDockFromSettings,
            onDockReset = vm::resetDock,
            onArrangeDeck = vm::arrangeDeck,
            onAddWidget = vm::openWidgetPicker,
            onToggleSignalText = vm::toggleSignalText,
            onForecast = vm::toggleForecast,
            onHaptics = vm::cycleHaptics,
            onSounds = vm::cycleSounds,
            onHome = vm::settingsHome,
            onNotifications = { vm.host?.openNotificationAccess() },
            onCalendar = vm::settingsCalendar,
            onContacts = vm::settingsContacts,
            onAssistant = vm::settingsAssistant,
            onSystemSettings = vm.actions::systemSettings,
            onAppInfo = vm.actions::launcherInfo,
            onExport = vm::exportLayout,
            onImport = vm::importLayout,
            onClose = vm::onBack,
            onAccent = vm::setAccent,
            onContrast = vm::toggleContrast,
            onReduceMotion = vm::toggleReduceMotion,
            onLarge = vm::toggleLarge,
            onMovePage = vm::movePage,
            onOpenPage = vm::goToPage,
            onNewBoard = vm::createBoard,
        )
    }
    // Aktionen je Board (beim Übergang zwischen zwei Boards behält jedes seine eigenen).
    val boardActionsFor: (String) -> BoardActions = { id ->
        BoardActions(
            onApp = { vm.onAppTapped(it.key) },
            onSwipe = vm::swipePage,
            onEditMode = vm::setEditMode,
            onAddApp = { vm.startPickingForBoard(id) },
            onRemoveApp = { vm.removeBoardApp(id, it) },
            onMoveApp = { k, d -> vm.moveBoardApp(id, k, d) },
            onAddWidget = { vm.openBoardWidgetPicker(id) },
            onMoveWidget = { w, d -> vm.moveBoardWidget(id, w, d) },
            onRemoveWidget = { vm.removeBoardWidget(id, it) },
            widgetContent = { slot -> HostedWidget(vm.widgets, slot) },
            onRename = { vm.renameBoard(id, it) },
            onAccent = { vm.cycleBoardAccent(id) },
            onMovePage = { vm.movePage(PageRef.OfBoard(id), it) },
            onDelete = { vm.deleteBoard(id) },
            onCommand = { vm.openIndex(withKeyboard = true) },
            onMic = vm::startVoice,
            onUndo = vm::undoLast,
        )
    }

    // Wisch-Seiten: Stelle in der Reihenfolge je Bildschirm (null = keine Wisch-Seite).
    val pageOf: (ScreenKey) -> PageRef? = { k ->
        when (k.screen) {
            Screen.Deck -> PageRef.Deck
            Screen.Comms -> PageRef.Comms
            Screen.Media -> PageRef.Media
            Screen.Board -> k.board?.let { PageRef.OfBoard(it) }
            else -> null
        }
    }
    val key = ScreenKey(screen, if (screen == Screen.Board) boardId else null)

    // Große Ansicht: Schrift um 15 % größer (zusätzlich zur Systemeinstellung).
    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, comfort.large) {
        if (comfort.large) Density(baseDensity.density, baseDensity.fontScale * AppearanceRules.LARGE_FONT_SCALE) else baseDensity
    }

    // Leiser Ton/Ticken beim Wechsel des Bildschirms (nicht beim ersten Anzeigen).
    var lastScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        if (screen != lastScreen) vm.feedback.open()
        lastScreen = screen
    }

    BackHandler(enabled = screen != Screen.Deck || editMode) { vm.onBack() }

    CompositionLocalProvider(
        LocalAccent provides areas.deck.toAccent(),
        LocalDensity provides density,
        LocalIconProvider provides iconProvider,
        LocalFeedback provides vm.feedback,
    ) {
        MaiBackground {
            AnimatedContent(
                targetState = key,
                transitionSpec = {
                    val from = pageOf(initialState)?.let { pages.indexOf(it) } ?: -1
                    val to = pageOf(targetState)?.let { pages.indexOf(it) } ?: -1
                    if (comfort.reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (from >= 0 && to >= 0 && from != to) {
                        // Zwischen Wisch-Seiten: seitlich gleiten, in Wischrichtung.
                        val dir = if (to > from) 1 else -1
                        (slideInHorizontally(tween(220)) { dir * it / 4 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(200)) { -dir * it / 4 } + fadeOut(tween(140)))
                    } else if (targetState.screen != Screen.Deck) {
                        (slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(160))) togetherWith fadeOut(tween(120))
                    } else {
                        fadeIn(tween(160)) togetherWith (slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(140)))
                    }
                },
                label = "screen",
            ) { target ->
                when (target.screen) {
                    Screen.Deck -> DeckScreen(
                        DeckState(
                            system = sys,
                            dock = dock,
                            likely = likely,
                            likelyWindow = vm.likelyWindow,
                            appCount = apps.size,
                            use24h = use24h,
                            setup = setup,
                            nowPlaying = nowPlaying,
                            art = artBitmap,
                            navigation = navigation?.info,
                            events = if (access.calendar) events else null,
                            destinations = destinations,
                            widgets = widgets,
                            editMode = editMode,
                            news = news,
                            signals = if (access.signals) signals else null,
                            signalsShowText = signalsText,
                            badges = badges,
                            forecast = forecast,
                            forecastApps = forecastApps,
                            likelyReasons = likelyReasons,
                            notes = notes,
                            undo = undo,
                            pageIndex = pages.indexOf(PageRef.Deck).coerceAtLeast(0),
                            pageCount = pages.size,
                        ),
                        deckActions,
                    )
                    Screen.Board -> {
                        val b = boards.firstOrNull { it.id == target.board }
                        if (b != null) {
                            val byKey = remember(apps) { apps.associateBy { it.key } }
                            val today = remember(sys.nowMillis) { java.time.LocalDate.now() }
                            val mai = BoardTemplates.maiPackage(b.kind)
                            CompositionLocalProvider(LocalAccent provides b.accent.toAccent()) {
                                BoardScreen(
                                    BoardState(
                                        id = b.id,
                                        title = b.title,
                                        kind = b.kind,
                                        apps = b.apps.mapNotNull { byKey[it]?.toTile() },
                                        widgets = remember(b.widgets, pendingRemoval) { slotsOf(b.widgets) },
                                        forecast = if (mai == null) emptyList() else forecast.filter { it.packageName == mai },
                                        forecastApps = forecastApps,
                                        daysLeftInWeek = Forecast.daysLeftInWeek(today),
                                        badges = badges,
                                        editMode = editMode,
                                        pageIndex = pages.indexOf(PageRef.OfBoard(b.id)).coerceAtLeast(0),
                                        pageCount = pages.size,
                                        undo = undo,
                                    ),
                                    remember(vm, b.id) { boardActionsFor(b.id) },
                                )
                            }
                        }
                    }
                    Screen.Index -> IndexScreen(
                        IndexState(
                            apps = indexApps,
                            results = results,
                            query = query,
                            likely = likely,
                            likelyWindow = vm.likelyWindow,
                            pickingDock = picking != null || boardPick != null,
                            pickingBoard = boardPick != null,
                            commands = if (picking != null || boardPick != null) emptyList() else commands,
                            contacts = if (picking != null || boardPick != null) emptyList() else contactHits,
                            listening = listening,
                            tab = indexTab,
                            tabs = indexTabs,
                            hidden = hiddenApps,
                            aliases = aliases,
                            likelyReasons = likelyReasons,
                            dock = dock,
                        ),
                        indexActions,
                        focusSignal = focus,
                    )
                    Screen.Widgets -> WidgetPicker(vm)
                    Screen.Settings -> SettingsScreen(
                        SettingsState(
                            dock = settingsDock,
                            isHome = access.home,
                            signals = access.signals,
                            calendar = access.calendar,
                            contacts = access.contacts,
                            assistant = access.assistant,
                            signalsShowText = signalsText,
                            forecast = forecastOn,
                            haptics = haptics,
                            sounds = sounds,
                            widgetCount = widgetIds.size,
                            feedCount = news.urls.size,
                            version = vm.version,
                            backup = backupStatus,
                            appearance = appearance,
                            pages = remember(pages, boards) {
                                pages.map { p ->
                                    val b = (p as? PageRef.OfBoard)?.let { r -> boards.firstOrNull { it.id == r.id } }
                                    PageEntry(
                                        page = p,
                                        title = b?.title,
                                        kind = b?.kind,
                                        accent = when (p) {
                                            PageRef.Comms -> areas.comms
                                            PageRef.Media -> areas.media
                                            PageRef.Deck -> areas.deck
                                            is PageRef.OfBoard -> b?.accent
                                        },
                                        appCount = b?.apps?.size ?: 0,
                                    )
                                }
                            },
                            canAddBoard = boards.size < Boards.MAX_BOARDS,
                        ),
                        settingsActions,
                    )
                    Screen.Media -> CompositionLocalProvider(LocalAccent provides areas.media.toAccent()) {
                        MediaScreen(
                            MediaState(
                                nowPlaying = nowPlaying,
                                art = artBitmap,
                                queue = queue,
                                musicApps = musicApps,
                                access = access.signals,
                            ),
                            mediaActions,
                        )
                    }
                    Screen.Comms -> CompositionLocalProvider(LocalAccent provides areas.comms.toAccent()) {
                        val now = remember(commsLog) { System.currentTimeMillis() }
                        CommsScreen(
                            CommsState(
                                access = access.contacts,
                                contacts = commsResults,
                                favourites = remember(allContacts) { ContactRanking.favourites(allContacts) },
                                frequent = remember(allContacts, commsLog) { ContactRanking.frequent(allContacts, commsLog, now) },
                                total = allContacts.size,
                                query = commsQuery,
                                keypadOpen = keypadOpen,
                                dialed = dialed,
                                rufDurch = remember { vm.contacts.rufDurchInstalled() },
                            ),
                            commsActions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPicker(vm: LauncherViewModel) {
    val options = remember { vm.widgetOptions() }
    val choices = remember(options) {
        options.map { WidgetChoice(it.provider.flattenToString(), it.label, it.appLabel, it.cellsWide, it.cellsHigh) }
    }
    val density = LocalDensity.current
    WidgetPickerScreen(
        choices = choices,
        preview = { c ->
            val opt = options.firstOrNull { it.provider.flattenToString() == c.key } ?: return@WidgetPickerScreen null
            withContext(Dispatchers.IO) {
                vm.widgets.preview(opt)?.let { d ->
                    val w = with(density) { 144.dp.roundToPx() }
                    val ratio = if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) d.intrinsicHeight.toFloat() / d.intrinsicWidth else 0.66f
                    runCatching { d.toBitmap(w, (w * ratio).toInt().coerceAtLeast(1)).asImageBitmap() }.getOrNull()
                }
            }
        },
        onPick = { c -> options.firstOrNull { it.provider.flattenToString() == c.key }?.let(vm::addWidget) },
    )
}

/** Echtes Android-Widget (AppWidgetHostView) in Compose. */
@Composable
private fun HostedWidget(host: WidgetHost, slot: WidgetSlot) {
    val density = LocalDensity.current
    AndroidView(
        factory = { ctx ->
            val view = host.createView(slot.id) ?: return@AndroidView android.view.View(ctx)
            view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Größe melden, sobald sie bekannt ist, damit das Widget passend zeichnet.
            view.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) {
                    with(density) { host.updateSize(v as android.appwidget.AppWidgetHostView, (r - l).toDp().value, (b - t).toDp().value) }
                }
            }
            view
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
