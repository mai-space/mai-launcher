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
import space.mai.mai_launcher.design.Accents
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
    val use24h = systemUses24h()

    val artBitmap = remember(art) { art?.asImageBitmap() }
    val widgets = remember(widgetIds) {
        widgetIds.mapNotNull { id ->
            val info = vm.widgets.info(id) ?: return@mapNotNull null
            val label = info.loadLabel(vm.getApplication<android.app.Application>().packageManager) ?: ""
            val dm = vm.getApplication<android.app.Application>().resources.displayMetrics
            val h = (info.minHeight / dm.density).toInt().coerceIn(72, 360)
            WidgetSlot(id, label, null, h)
        }
    }

    val iconProvider = remember(vm) { IconProvider { vm.icons.monochrome(it) } }
    val deckActions = remember(vm) {
        DeckActions(
            onApp = { vm.onAppTapped(it.key) },
            onDockLongPress = vm::startPickingDock,
            onCommand = { vm.openIndex(withKeyboard = true) },
            onSwipeRight = vm::openComms,
            onSwipeLeft = vm::openMedia,
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
            onRemoveWidget = vm.widgets::remove,
            widgetContent = { slot -> HostedWidget(vm.widgets, slot) },
            onOpenLink = vm::openLink,
            onRefreshNews = { vm.feeds.refresh(force = true) },
            onAddFeed = vm.feeds::add,
            onRemoveFeed = vm.feeds::remove,
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
        )
    }

    val settingsActions = remember(vm) {
        SettingsActions(
            onDockSlot = vm::pickDockFromSettings,
            onDockReset = vm::resetDock,
            onArrangeDeck = vm::arrangeDeck,
            onAddWidget = vm::openWidgetPicker,
            onToggleSignalText = vm::toggleSignalText,
            onHaptics = vm::cycleHaptics,
            onSounds = vm::cycleSounds,
            onHome = vm::settingsHome,
            onNotifications = { vm.host?.openNotificationAccess() },
            onCalendar = vm::settingsCalendar,
            onContacts = vm::settingsContacts,
            onSystemSettings = vm.actions::systemSettings,
            onAppInfo = vm.actions::launcherInfo,
            onExport = vm::exportLayout,
            onImport = vm::importLayout,
            onClose = vm::onBack,
        )
    }

    // Leiser Ton/Ticken beim Wechsel des Bildschirms (nicht beim ersten Anzeigen).
    var lastScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        if (screen != lastScreen) vm.feedback.open()
        lastScreen = screen
    }

    BackHandler(enabled = screen != Screen.Deck || editMode) { vm.onBack() }

    CompositionLocalProvider(
        LocalAccent provides Accents.Ion,
        LocalIconProvider provides iconProvider,
        LocalFeedback provides vm.feedback,
    ) {
        MaiBackground {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState != Screen.Deck) {
                        (slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(160))) togetherWith fadeOut(tween(120))
                    } else {
                        fadeIn(tween(160)) togetherWith (slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(140)))
                    }
                },
                label = "screen",
            ) { target ->
                when (target) {
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
                        ),
                        deckActions,
                    )
                    Screen.Index -> IndexScreen(
                        IndexState(
                            apps = apps,
                            results = results,
                            query = query,
                            likely = likely,
                            likelyWindow = vm.likelyWindow,
                            pickingDock = picking != null,
                            commands = if (picking != null) emptyList() else commands,
                            contacts = if (picking != null) emptyList() else contactHits,
                            listening = listening,
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
                            signalsShowText = signalsText,
                            haptics = haptics,
                            sounds = sounds,
                            widgetCount = widgetIds.size,
                            feedCount = news.urls.size,
                            version = vm.version,
                            backup = backupStatus,
                        ),
                        settingsActions,
                    )
                    Screen.Media -> CompositionLocalProvider(LocalAccent provides Accents.Amber) {
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
                    Screen.Comms -> CompositionLocalProvider(LocalAccent provides Accents.Signal) {
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
