package space.mai.mai_launcher.ui.index

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.CommandShape
import space.mai.mai_launcher.design.GlowLevel
import space.mai.mai_launcher.design.GripLine
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.design.MicGlyph
import space.mai.mai_launcher.design.glowBorder
import space.mai.mai_launcher.domain.AlphaIndex
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.ui.AppTile
import space.mai.mai_launcher.ui.AppToken
import space.mai.mai_launcher.ui.rememberAppIcon
import space.mai.mai_launcher.ui.toTile

@Immutable
data class IndexState(
    val apps: List<AppInfo>,
    val results: List<AppTile>,
    val query: String,
    val likely: List<AppTile>,
    val likelyWindow: Pair<Int, Int>,
    val pickingDock: Boolean = false,
)

class IndexActions(
    val onApp: (AppTile) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onAppInfo: (AppKey) -> Unit = {},
    val onUninstall: (AppKey) -> Unit = {},
)

/** Eine Zeile der Liste: Buchstaben-Kopf oder App. */
private sealed interface ListRow {
    val id: String
    data class Header(val letter: Char) : ListRow { override val id = "h_$letter" }
    data class App(val tile: AppTile) : ListRow { override val id = tile.key.encode() }
}

@Composable
fun IndexScreen(
    state: IndexState,
    actions: IndexActions,
    focusSignal: Int,
    modifier: Modifier = Modifier,
) {
    val sections = remember(state.apps) { AlphaIndex.sections(state.apps) }
    val rows = remember(sections) {
        sections.flatMap { s -> listOf<ListRow>(ListRow.Header(s.letter)) + s.apps.map { ListRow.App(it.toTile()) } }
    }
    val letterPos = remember(rows) {
        rows.withIndex().filter { it.value is ListRow.Header }.associate { (it.value as ListRow.Header).letter to it.index }
    }
    val listState = rememberLazyListState()
    val searching = state.query.isNotBlank()

    // Am Listenanfang weiter nach unten ziehen = zurück zum Deck.
    val closeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val pullToClose = remember(actions) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0) {
                    pulled += available.y
                    if (pulled > closeThreshold) { pulled = 0f; actions.onClose() }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }
        }
    }

    Row(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 14.dp, top = 6.dp, bottom = 8.dp),
    ) {
        MainFrame(
            title = stringResource(R.string.tab_index),
            meta = if (state.pickingDock) stringResource(R.string.pick_dock_hint)
            else pluralStringResource(R.plurals.apps_count, state.apps.size, state.apps.size),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().nestedScroll(pullToClose),
                state = listState,
            ) {
                if (searching) {
                    if (state.results.isEmpty()) {
                        item { MaiText(stringResource(R.string.no_results), MaiType.Body, Modifier.padding(top = 24.dp)) }
                    }
                    items(state.results, key = { it.key.encode() }) { tile ->
                        AppRow(tile, highlighted = tile == state.results.first(), actions)
                    }
                } else {
                    if (state.likely.isNotEmpty()) {
                        item(key = "likely") { LikelyRow(state, actions) }
                    }
                    items(rows, key = { it.id }) { row ->
                        when (row) {
                            is ListRow.Header -> LetterHeader(row.letter)
                            is ListRow.App -> AppRow(row.tile, highlighted = false, actions)
                        }
                    }
                }
            }
            GripLine()
            Spacer(Modifier.height(10.dp))
            SearchField(state.query, actions, focusSignal)
        }
        LetterRail(
            letters = sections.map { it.letter },
            enabled = !searching,
            onLetter = { letter ->
                val offset = if (state.likely.isNotEmpty()) 1 else 0
                letterPos[letter]?.let { it + offset }
            },
            listState = listState,
            modifier = Modifier.width(MaiDims.RailWidth).fillMaxHeight(),
        )
    }
}

@Composable
private fun LikelyRow(state: IndexState, actions: IndexActions) {
    val (from, to) = state.likelyWindow
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp)) {
        MaiLabel(
            stringResource(R.string.panel_likely_now) + " · " +
                stringResource(R.string.panel_likely_window, "%02d".format(from), "%02d".format(to)),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.likely.take(4).forEach { AppToken(it, Modifier.weight(1f), size = 42.dp) { actions.onApp(it) } }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaiColors.Line))
    }
}

@Composable
private fun LetterHeader(letter: Char) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        MaiText(letter.toString(), MaiType.Letter, color = LocalAccent.current.main)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaiColors.Line))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(tile: AppTile, highlighted: Boolean, actions: IndexActions) {
    val accent = LocalAccent.current.main
    val haptics = LocalHapticFeedback.current
    var expanded by remember(tile.key) { mutableStateOf(false) }
    val shape = ChamferShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, top = 1.dp, bottom = 1.dp)
            .then(
                if (highlighted || expanded) Modifier
                    .background(MaiColors.Panel2, shape)
                    .glowBorder(shape, accent, 1.dp, GlowLevel.Focus)
                else Modifier,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(MaiDims.MinTouch)
                .combinedClickable(
                    role = Role.Button,
                    onClick = { actions.onApp(tile) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        expanded = !expanded
                    },
                )
                .semantics(mergeDescendants = true) { contentDescription = tile.label }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyph(tile, active = highlighted || expanded)
            Spacer(Modifier.width(12.dp))
            MaiText(tile.label.uppercase(), MaiType.ListCaps, Modifier.weight(1f))
            if (tile.isWork) MaiLabel(stringResource(R.string.work_badge), color = MaiColors.Amber)
            if (highlighted) MaiLabel("↵", color = accent)
        }
        if (expanded) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionChip(stringResource(R.string.action_app_info)) { expanded = false; actions.onAppInfo(tile.key) }
                ActionChip(stringResource(R.string.action_uninstall), MaiColors.Alert) { expanded = false; actions.onUninstall(tile.key) }
            }
        }
    }
}

/** Kleines Kästchen mit Symbol oder Anfangsbuchstabe (Liste bleibt ruhig). */
@Composable
private fun AppGlyph(tile: AppTile, active: Boolean) {
    val accent = LocalAccent.current.main
    val color = if (active) accent else MaiColors.Text2
    val icon = rememberAppIcon(tile.key)
    Box(
        Modifier.size(22.dp).glowBorder(ChamferShape(4.dp), if (active) accent else MaiColors.Text3, 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(
                icon, null, Modifier.size(16.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
            )
        } else {
            MaiText(tile.monogram.take(1), MaiType.Label.copy(fontSize = MaiType.Label.fontSize * 1.2f), color = color)
        }
    }
}

@Composable
private fun ActionChip(text: String, color: androidx.compose.ui.graphics.Color = LocalAccent.current.main, onClick: () -> Unit) {
    val shape = ChamferShape(6.dp)
    Box(
        Modifier
            .height(36.dp)
            .background(MaiColors.Panel, shape)
            .glowBorder(shape, color)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        MaiLabel(text, color = color)
    }
}

@Composable
private fun SearchField(query: String, actions: IndexActions, focusSignal: Int) {
    val accent = LocalAccent.current.main
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    CommandShape(active = query.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MaiText("›", MaiType.Mono.copy(fontSize = MaiType.Title.fontSize), color = accent)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) MaiLabel(stringResource(R.string.filter_hint), color = MaiColors.Text3)
                BasicTextField(
                    value = query,
                    onValueChange = actions.onQuery,
                    singleLine = true,
                    textStyle = MaiType.Mono.copy(color = MaiColors.Text, fontSize = MaiType.Title.fontSize),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { actions.onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            MicGlyph()
        }
    }
}

/** A–Z-Leiste in der Seitenschiene: tippen oder mit dem Daumen ziehen. */
@Composable
private fun LetterRail(
    letters: List<Char>,
    enabled: Boolean,
    onLetter: (Char) -> Int?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<Char?>(null) }
    val accent = LocalAccent.current.main

    fun select(y: Float) {
        if (letters.isEmpty()) return
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[i]
        if (letter != active) {
            active = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(letter)?.let { scope.launch { listState.scrollToItem(it) } }
        }
    }

    Column(
        modifier
            .padding(top = 60.dp, bottom = 80.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .then(
                if (enabled) Modifier
                    .pointerInput(letters) { detectTapGestures { select(it.y); active = null } }
                    .pointerInput(letters) {
                        detectVerticalDragGestures(
                            onDragStart = { select(it.y) },
                            onDragEnd = { active = null },
                            onDragCancel = { active = null },
                        ) { change, _ -> select(change.position.y) }
                    }
                else Modifier,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        letters.forEach { letter ->
            val on = letter == active
            MaiText(
                letter.toString(),
                MaiType.Label.copy(fontSize = MaiType.Label.fontSize * 1.2f),
                color = if (!enabled) MaiColors.Line else if (on) accent else MaiColors.Text3,
            )
        }
    }
}
