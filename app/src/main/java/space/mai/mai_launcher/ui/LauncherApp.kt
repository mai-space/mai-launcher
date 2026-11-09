package space.mai.mai_launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.mai.mai_launcher.design.Accents
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiBackground
import space.mai.mai_launcher.ui.deck.DeckActions
import space.mai.mai_launcher.ui.deck.DeckScreen
import space.mai.mai_launcher.ui.deck.DeckState
import space.mai.mai_launcher.ui.deck.RailActions
import space.mai.mai_launcher.ui.deck.systemUses24h
import space.mai.mai_launcher.ui.index.IndexActions
import space.mai.mai_launcher.ui.index.IndexScreen
import space.mai.mai_launcher.ui.index.IndexState

/** Wurzel: Deck und Index mit Übergang aus der Daumenzone. */
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
    val use24h = systemUses24h()

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
                onDnd = vm.actions::dndSettings,
                onTorch = vm.system::toggleTorch,
                onRotate = vm.actions::displaySettings,
            ),
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

    BackHandler(enabled = screen != Screen.Deck) { vm.onBack() }

    CompositionLocalProvider(LocalAccent provides Accents.Ion, LocalIconProvider provides iconProvider) {
        MaiBackground {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (targetState == Screen.Index) {
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
                }
            }
        }
    }
}
