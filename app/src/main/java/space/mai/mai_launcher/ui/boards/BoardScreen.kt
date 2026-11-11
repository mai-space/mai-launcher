package space.mai.mai_launcher.ui.boards

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.ComfortState
import space.mai.mai_launcher.design.GripLine
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiPanel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.design.glowBorder
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.BoardKind
import space.mai.mai_launcher.domain.Boards
import space.mai.mai_launcher.domain.ForecastLine
import space.mai.mai_launcher.domain.SignalDigest
import space.mai.mai_launcher.ui.AppTile
import space.mai.mai_launcher.ui.AppToken
import space.mai.mai_launcher.ui.PageTicks
import space.mai.mai_launcher.ui.UndoKind
import space.mai.mai_launcher.ui.deck.CommandBar
import space.mai.mai_launcher.ui.deck.EditChip
import space.mai.mai_launcher.ui.deck.ForecastPanel
import space.mai.mai_launcher.ui.deck.UndoBar
import space.mai.mai_launcher.ui.deck.WidgetPanel
import space.mai.mai_launcher.ui.deck.WidgetSlot

/** Alles, was ein eigenes Dashboard zeigt (reine Daten – testbar ohne Gerät). */
@Immutable
data class BoardState(
    val id: String,
    /** Eigener Name; null = Name der Vorlage. */
    val title: String?,
    val kind: BoardKind,
    /** Installierte Apps des Boards in gespeicherter Reihenfolge (fehlende fallen weg). */
    val apps: List<AppTile> = emptyList(),
    val widgets: List<WidgetSlot> = emptyList(),
    /** Stand der passenden Mai-App (leer = kein Panel). */
    val forecast: List<ForecastLine> = emptyList(),
    val forecastApps: Map<String, AppTile> = emptyMap(),
    val daysLeftInWeek: Int = 0,
    val badges: Map<String, Int> = emptyMap(),
    val editMode: Boolean = false,
    val pageIndex: Int = 0,
    val pageCount: Int = 1,
    val undo: UndoKind? = null,
)

class BoardActions(
    val onApp: (AppTile) -> Unit = {},
    /** −1 = Seite links davon (nach rechts gewischt), +1 = rechts davon. */
    val onSwipe: (Int) -> Unit = {},
    val onEditMode: (Boolean) -> Unit = {},
    val onAddApp: () -> Unit = {},
    val onRemoveApp: (AppKey) -> Unit = {},
    val onMoveApp: (AppKey, Int) -> Unit = { _, _ -> },
    val onAddWidget: () -> Unit = {},
    val onMoveWidget: (Int, Int) -> Unit = { _, _ -> },
    val onRemoveWidget: (Int) -> Unit = {},
    val widgetContent: @Composable (WidgetSlot) -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onAccent: () -> Unit = {},
    val onMovePage: (Int) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onCommand: () -> Unit = {},
    val onMic: () -> Unit = {},
    val onUndo: () -> Unit = {},
)

/**
 * Eigenes Dashboard: ein Rahmen in der Farbe des Boards, oben der Stand der
 * passenden Mai-App, darunter die Apps als Sechsecke und eingebettete Widgets.
 * Lange drücken = bearbeiten (umbenennen, Farbe, Apps, Widgets, Seite verschieben).
 */
@Composable
fun BoardScreen(state: BoardState, actions: BoardActions, modifier: Modifier = Modifier) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val title = state.title ?: boardKindName(state.kind)
    Row(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total > threshold -> actions.onSwipe(-1)
                            total < -threshold -> actions.onSwipe(1)
                        }
                    },
                ) { _, dx -> total += dx }
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { actions.onEditMode(true) }) },
    ) {
        MainFrame(
            title = title.uppercase(),
            meta = if (state.editMode) stringResource(R.string.edit_mode)
            else pluralStringResource(R.plurals.apps_count, state.apps.size, state.apps.size),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.editMode) BoardEditPanel(state, title, actions)
                if (state.forecast.isNotEmpty()) {
                    ForecastPanel(state.forecast, state.forecastApps, state.daysLeftInWeek, actions.onApp)
                }
                AppsPanel(state, actions)
                state.widgets.forEach { w ->
                    WidgetPanel(
                        w, state.editMode,
                        onUp = { actions.onMoveWidget(w.id, -1) },
                        onDown = { actions.onMoveWidget(w.id, 1) },
                        onRemove = { actions.onRemoveWidget(w.id) },
                        content = actions.widgetContent,
                    )
                }
                if (state.editMode) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditChip("+ " + stringResource(R.string.widget_add), actions.onAddWidget)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            state.undo?.let { UndoBar(it, actions.onUndo) }
            Spacer(Modifier.height(6.dp))
            PageTicks(state.pageIndex, state.pageCount, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GripLine()
            Spacer(Modifier.height(10.dp))
            CommandBar(actions.onCommand, actions.onMic)
        }
    }
}

/**
 * Apps als Sechsecke in Reihen zu vier. Im Bearbeiten-Modus wählt Tippen eine
 * App aus; darunter erscheinen dann Verschieben und Entfernen für genau diese.
 */
@Composable
private fun AppsPanel(state: BoardState, actions: BoardActions) {
    val size = if (ComfortState.current.large) 52.dp else 44.dp
    var selected by remember(state.editMode) { mutableStateOf<AppKey?>(null) }
    MaiPanel(
        title = stringResource(R.string.board_apps),
        meta = if (state.editMode) "${state.apps.size}/${Boards.MAX_APPS}" else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(10.dp))
        if (state.apps.isEmpty() && !state.editMode) {
            MaiText(stringResource(R.string.board_apps_empty), MaiType.Body, color = MaiColors.Text2, maxLines = 3)
            Spacer(Modifier.height(10.dp))
            EditChip("+ " + stringResource(R.string.board_add_app), { actions.onEditMode(true); actions.onAddApp() })
            return@MaiPanel
        }
        val cells: List<AppTile?> = if (state.editMode && state.apps.size < Boards.MAX_APPS) state.apps + null else state.apps
        cells.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                row.forEach { tile ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (tile == null) {
                            HexToken(
                                contentDescription = stringResource(R.string.board_add_app),
                                size = size, text = "+", color = MaiColors.Text3, filled = false,
                                onClick = actions.onAddApp,
                            )
                        } else {
                            AppToken(
                                tile, size = size,
                                badge = SignalDigest.badgeText(state.badges[tile.key.packageName] ?: 0),
                                onLongClick = { actions.onEditMode(true); selected = tile.key },
                            ) {
                                if (state.editMode) selected = if (selected == tile.key) null else tile.key else actions.onApp(tile)
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val sel = state.apps.firstOrNull { it.key == selected }
        if (state.editMode && sel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MaiText(sel.label, MaiType.Body, Modifier.weight(1f), color = LocalAccent.current.main)
                EditChip("‹", { actions.onMoveApp(sel.key, -1) })
                EditChip("›", { actions.onMoveApp(sel.key, 1) })
                EditChip(stringResource(R.string.board_remove), { actions.onRemoveApp(sel.key); selected = null }, MaiColors.Alert)
            }
        } else if (state.editMode && state.apps.isNotEmpty()) {
            MaiText(stringResource(R.string.board_apps_edit_hint), MaiType.Body, color = MaiColors.Text3, maxLines = 2)
        }
    }
}

/** Bearbeiten: Name, Farbe, Seite verschieben, löschen, fertig. */
@Composable
private fun BoardEditPanel(state: BoardState, title: String, actions: BoardActions) {
    MaiPanel(stringResource(R.string.board_edit), Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        NameField(state.title.orEmpty(), title, actions.onRename)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            EditChip(stringResource(R.string.board_color), actions.onAccent)
            if (state.pageIndex > 0) EditChip("◂ " + stringResource(R.string.board_page), { actions.onMovePage(-1) })
            if (state.pageIndex < state.pageCount - 1) EditChip(stringResource(R.string.board_page) + " ▸", { actions.onMovePage(1) })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditChip(stringResource(R.string.board_delete), actions.onDelete, MaiColors.Alert)
            Spacer(Modifier.weight(1f))
            EditChip(stringResource(R.string.edit_done), { actions.onEditMode(false) })
        }
    }
}

@Composable
private fun NameField(value: String, placeholder: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val accent = LocalAccent.current.main
    val shape = ChamferShape(6.dp)
    Column(Modifier.fillMaxWidth()) {
        MaiLabel(stringResource(R.string.board_name), color = accent)
        Box(
            Modifier.fillMaxWidth().height(44.dp).background(MaiColors.Panel2, shape).glowBorder(shape, MaiColors.Line).padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) MaiText(placeholder, MaiType.Body, color = MaiColors.Text3)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(24); onSave(text) },
                singleLine = true,
                textStyle = MaiType.Body.copy(color = MaiColors.Text),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Name der Vorlage in der Systemsprache. */
@Composable
fun boardKindName(kind: BoardKind): String = stringResource(
    when (kind) {
        BoardKind.Custom -> R.string.board_kind_custom
        BoardKind.Finance -> R.string.board_kind_finance
        BoardKind.Fitness -> R.string.board_kind_fitness
        BoardKind.Home -> R.string.board_kind_home
        BoardKind.Health -> R.string.board_kind_health
        BoardKind.Travel -> R.string.board_kind_travel
        BoardKind.Work -> R.string.board_kind_work
    },
)
