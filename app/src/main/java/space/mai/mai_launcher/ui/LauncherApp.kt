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
import space.mai.mai_launcher.design.MaiBackground
import space.mai.mai_launcher.ui.deck.DeckActions
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
            onSwipeUp = { vm.openIndex(withKeyboard = false) },
            onSwipeDown = vm.actions::expandNotifications,
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
        )
    }

    BackHandler(enabled = screen != Screen.Deck || editMode) { vm.onBack() }

    CompositionLocalProvider(LocalAccent provides Accents.Ion, LocalIconProvider provides iconProvider) {
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
                        ),
                        indexActions,
                        focusSignal = focus,
                    )
                    Screen.Widgets -> WidgetPicker(vm)
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
