package space.mai.mai_launcher.ui.deck

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import space.mai.mai_launcher.domain.CalendarEvent
import space.mai.mai_launcher.domain.NavInfo
import space.mai.mai_launcher.domain.NowPlaying
import space.mai.mai_launcher.ui.SetupItem
import space.mai.mai_launcher.feeds.FeedState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.CommandShape
import space.mai.mai_launcher.design.GripLine
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiBar
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiPanel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.design.MicGlyph
import space.mai.mai_launcher.system.SystemState
import space.mai.mai_launcher.ui.AppTile
import space.mai.mai_launcher.ui.AppToken
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Ein eingebautes Android-Widget. */
@Immutable
data class WidgetSlot(val id: Int, val label: String, val appLabel: String?, val heightDp: Int)

/** Alles, was das Deck zeigt (reine Daten – testbar ohne Gerät). */
@Immutable
data class DeckState(
    val system: SystemState,
    val dock: List<AppTile?>,
    val likely: List<AppTile>,
    val likelyWindow: Pair<Int, Int>,
    val appCount: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
    val use24h: Boolean = true,
    val setup: List<SetupItem> = emptyList(),
    val nowPlaying: NowPlaying? = null,
    val art: ImageBitmap? = null,
    val navigation: NavInfo? = null,
    val events: List<CalendarEvent>? = null,
    val destinations: List<String> = emptyList(),
    val widgets: List<WidgetSlot> = emptyList(),
    val editMode: Boolean = false,
    val news: FeedState = FeedState(),
)

/** Aktionen des Decks. */
class DeckActions(
    val onApp: (AppTile) -> Unit = {},
    val onDockLongPress: (Int) -> Unit = {},
    val onCommand: () -> Unit = {},
    val onSwipeUp: () -> Unit = {},
    val onSwipeRight: () -> Unit = {},
    val onSwipeDown: () -> Unit = {},
    val onClock: () -> Unit = {},
    val onBattery: () -> Unit = {},
    val onStorage: () -> Unit = {},
    val rail: RailActions = RailActions(),
    val onSetup: (SetupItem) -> Unit = {},
    val onDismissSetup: (SetupItem) -> Unit = {},
    val onEditMode: (Boolean) -> Unit = {},
    val media: MediaActions = MediaActions(),
    val onNavigation: () -> Unit = {},
    val onDestination: (Int) -> Unit = {},
    val onSetDestination: (Int, String) -> Unit = { _, _ -> },
    val onEvent: (Long) -> Unit = {},
    val onCalendar: () -> Unit = {},
    val onAddWidget: () -> Unit = {},
    val onMoveWidget: (Int, Int) -> Unit = { _, _ -> },
    val onRemoveWidget: (Int) -> Unit = {},
    val widgetContent: @Composable (WidgetSlot) -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onRefreshNews: () -> Unit = {},
    val onAddFeed: (String) -> Boolean = { false },
    val onRemoveFeed: (String) -> Unit = {},
)

class MediaActions(
    val elapsedNow: () -> Long = { 0L },
    val onOpen: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
)

@Composable
fun DeckScreen(state: DeckState, actions: DeckActions, modifier: Modifier = Modifier) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val scroll = rememberScrollState()
    // Über das Ende der Panels hinaus ziehen = gleiche Gesten wie auf freier Fläche.
    val overscroll = remember(actions) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) pulled += available.y else if (consumed.y != 0f) pulled = 0f
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                when {
                    pulled < -threshold -> actions.onSwipeUp()
                    pulled > threshold -> actions.onSwipeDown()
                }
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    Row(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 6.dp, bottom = 8.dp)
            // Wischen: hoch = alle Apps, runter = Benachrichtigungen. Halten = Bearbeiten.
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total < -threshold -> actions.onSwipeUp()
                            total > threshold -> actions.onSwipeDown()
                        }
                    },
                ) { _, dy -> total += dy }
            }
            // Nach rechts wischen = Comms (Kontakte, Wähltastatur).
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > threshold) actions.onSwipeRight() },
                ) { _, dx -> total += dx }
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { actions.onEditMode(true) }) },
    ) {
        MainFrame(
            title = stringResource(R.string.tab_deck),
            meta = if (state.editMode) stringResource(R.string.edit_mode)
            else pluralStringResource(R.plurals.apps_count, state.appCount, state.appCount),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            ClockBlock(state, actions.onClock, onLongClock = { actions.onEditMode(true) })
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(overscroll)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.editMode) EditBar(actions)
                if (state.setup.isNotEmpty()) SetupPanel(state.setup, actions.onSetup, actions.onDismissSetup)
                state.navigation?.let { NavigationPanel(it, actions.onNavigation) }
                state.nowPlaying?.let {
                    MediaPanel(it, state.art, actions.media.elapsedNow, actions.media.onOpen,
                        actions.media.onPrevious, actions.media.onPlayPause, actions.media.onNext)
                }
                state.events?.let {
                    EventsPanel(it, state.system.nowMillis, state.zone, state.use24h, actions.onEvent, actions.onCalendar)
                }
                if (state.editMode || state.destinations.any { it.isNotBlank() }) {
                    DestinationsPanel(state.destinations, state.editMode, actions.onDestination, actions.onSetDestination)
                }
                LikelyPanel(state, actions.onApp)
                if (state.editMode || state.news.urls.isNotEmpty()) {
                    val n = state.news
                    NewsPanel(n.items, n.urls, n.failed, n.loading, state.system.nowMillis, state.editMode,
                        actions.onOpenLink, actions.onRefreshNews, actions.onAddFeed, actions.onRemoveFeed)
                }
                state.widgets.forEach { w ->
                    WidgetPanel(
                        w, state.editMode,
                        onUp = { actions.onMoveWidget(w.id, -1) },
                        onDown = { actions.onMoveWidget(w.id, 1) },
                        onRemove = { actions.onRemoveWidget(w.id) },
                        content = actions.widgetContent,
                    )
                }
                SystemPanel(state.system, actions.onBattery, actions.onStorage)
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            GripLine()
            Spacer(Modifier.height(10.dp))
            Dock(state.dock, actions.onApp, actions.onDockLongPress)
            Spacer(Modifier.height(10.dp))
            CommandBar(actions.onCommand)
        }
        SideRail(state.system, actions.rail, Modifier.width(MaiDims.RailWidth).fillMaxHeight())
    }
}

@Composable
private fun EditBar(actions: DeckActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EditChip("+ " + stringResource(R.string.widget_add), actions.onAddWidget)
        Spacer(Modifier.weight(1f))
        EditChip(stringResource(R.string.edit_done), { actions.onEditMode(false) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockBlock(state: DeckState, onClock: () -> Unit, onLongClock: () -> Unit) {
    val locale = currentLocale()
    val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(state.system.nowMillis), state.zone)
    val timeFmt = remember(state.use24h) { DateTimeFormatter.ofPattern(if (state.use24h) "HH:mm" else "h:mm") }
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE · dd MMM", locale) }
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClock, onLongClick = onLongClock)
            .padding(top = 8.dp),
    ) {
        MaiText(now.format(timeFmt), MaiType.Clock)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MaiText(now.format(dateFmt).uppercase(locale).replace(".", ""), MaiType.Mono, Modifier.weight(1f))
            val alarm = state.system.nextAlarmMillis
            if (alarm != null) {
                val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(alarm), state.zone)
                // Mehr als 24 h entfernt: Wochentag dazu.
                val pattern = if (alarm - state.system.nowMillis > 86_400_000L) "EEE " else ""
                val txt = at.format(DateTimeFormatter.ofPattern(pattern + if (state.use24h) "HH:mm" else "h:mm a", locale))
                MaiText(stringResource(R.string.alarm_label, txt.uppercase(locale).replace(".", "")), MaiType.Mono, color = MaiColors.Amber)
            } else {
                MaiText(stringResource(R.string.alarm_none), MaiType.Mono, color = MaiColors.Text3)
            }
        }
    }
}

@Composable
private fun LikelyPanel(state: DeckState, onApp: (AppTile) -> Unit) {
    val (from, to) = state.likelyWindow
    MaiPanel(
        title = stringResource(R.string.panel_likely_now),
        meta = stringResource(R.string.panel_likely_window, "%02d".format(from), "%02d".format(to)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(8.dp))
        if (state.likely.isEmpty()) {
            MaiText(stringResource(R.string.panel_likely_learning), MaiType.Body, maxLines = 2)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.likely.take(4).forEach { AppToken(it, Modifier.weight(1f), size = 42.dp) { onApp(it) } }
            }
        }
    }
}

@Composable
private fun SystemPanel(sys: SystemState, onBattery: () -> Unit, onStorage: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val battery = sys.batteryPercent
        val low = battery != null && battery <= 15 && !sys.charging
        MaiPanel(
            title = stringResource(R.string.battery),
            meta = if (sys.charging) stringResource(R.string.battery_charging) else null,
            accent = if (low) MaiColors.Alert else LocalAccent.current.main,
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onBattery),
        ) {
            Spacer(Modifier.height(6.dp))
            MaiText(battery?.let { "$it %" } ?: "–", MaiType.Title.copy(fontFamily = MaiType.Mono.fontFamily),
                color = if (low) MaiColors.Alert else MaiColors.Text)
            Spacer(Modifier.height(8.dp))
            MaiBar((battery ?: 0) / 100f, Modifier.fillMaxWidth(), if (low) MaiColors.Alert else LocalAccent.current.main)
        }
        val free = sys.storageFreeBytes
        val total = sys.storageTotalBytes
        MaiPanel(
            title = stringResource(R.string.storage),
            meta = stringResource(R.string.storage_free),
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onStorage),
        ) {
            Spacer(Modifier.height(6.dp))
            val gb = free?.let { (it / 1_000_000_000.0 * 10).roundToInt() / 10.0 }
            MaiText(gb?.let { formatGb(it) + " GB" } ?: "–",
                MaiType.Title.copy(fontFamily = MaiType.Mono.fontFamily))
            Spacer(Modifier.height(8.dp))
            val used = if (free != null && total != null && total > 0) 1f - free.toFloat() / total else 0f
            MaiBar(used, Modifier.fillMaxWidth())
        }
    }
}

private fun formatGb(v: Double): String = if (v >= 100) v.roundToInt().toString() else "%.1f".format(v)

@Composable
private fun Dock(dock: List<AppTile?>, onApp: (AppTile) -> Unit, onLongPress: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        dock.forEachIndexed { i, tile ->
            if (tile != null) {
                AppToken(tile, Modifier.weight(1f), size = 40.dp, showName = false, onLongClick = { onLongPress(i) }) { onApp(tile) }
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    HexToken(
                        contentDescription = stringResource(R.string.dock_slot_empty),
                        size = 40.dp, text = "+", color = MaiColors.Text3, filled = false,
                        onLongClick = { onLongPress(i) }, onClick = { onLongPress(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandBar(onClick: () -> Unit) {
    CommandShape(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MaiText("›", MaiType.Mono.copy(fontSize = MaiType.Title.fontSize), color = LocalAccent.current.main)
            Spacer(Modifier.width(10.dp))
            MaiLabel(stringResource(R.string.command_hint), Modifier.weight(1f), color = MaiColors.Text3)
            MicGlyph()
        }
    }
}

@Composable
fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.getDefault()

/** 24-h-Format nach Systemeinstellung. */
@Composable
fun systemUses24h(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
