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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import space.mai.mai_launcher.design.LocalFeedback
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.design.MicGlyph
import space.mai.mai_launcher.design.glowBorder
import space.mai.mai_launcher.domain.AlphaIndex
import space.mai.mai_launcher.domain.AliasCodec
import space.mai.mai_launcher.domain.AppShortcut
import space.mai.mai_launcher.domain.IndexTab
import space.mai.mai_launcher.ui.LikelyTokens
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.selected
import space.mai.mai_launcher.domain.AppInfo
import space.mai.mai_launcher.domain.AppKey
import space.mai.mai_launcher.domain.Command
import space.mai.mai_launcher.domain.CommandParser
import space.mai.mai_launcher.domain.Contact
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
    /** Auswahl für ein Dashboard statt fürs Dock (nur andere Überschrift). */
    val pickingBoard: Boolean = false,
    /** Befehle zur Eingabe (Rechner, Timer, …); Websuche immer zuletzt. */
    val commands: List<Command> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    /** Spracheingabe hört gerade zu. */
    val listening: Boolean = false,
    val tab: IndexTab = IndexTab.All,
    /** Verfügbare Reiter; nur „Alle“ = keine Reiterleiste. */
    val tabs: List<IndexTab> = listOf(IndexTab.All),
    val hidden: Set<AppKey> = emptySet(),
    val aliases: Map<AppKey, String> = emptyMap(),
    val likelyReasons: Map<AppKey, Int> = emptyMap(),
    /** Aktuelles Dock (für „Ins Dock“). */
    val dock: List<AppTile?> = emptyList(),
)

class IndexActions(
    val onApp: (AppTile) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onAppInfo: (AppKey) -> Unit = {},
    val onUninstall: (AppKey) -> Unit = {},
    val onCommand: (Command) -> Unit = {},
    val onCall: (Contact) -> Unit = {},
    val onText: (Contact) -> Unit = {},
    val onMic: () -> Unit = {},
    val onTab: (IndexTab) -> Unit = {},
    val loadShortcuts: suspend (AppKey) -> List<AppShortcut> = { emptyList() },
    val onShortcut: (AppShortcut) -> Unit = {},
    val onPinDock: (AppKey, Int) -> Unit = { _, _ -> },
    val onHide: (AppKey, Boolean) -> Unit = { _, _ -> },
    val onAlias: (AppKey, String) -> Unit = { _, _ -> },
    val onHideLikely: (AppKey) -> Unit = {},
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
            meta = if (state.pickingBoard) stringResource(R.string.pick_board_hint)
            else if (state.pickingDock) stringResource(R.string.pick_dock_hint)
            else pluralStringResource(R.plurals.apps_count, state.apps.size, state.apps.size),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().nestedScroll(pullToClose),
                state = listState,
            ) {
                if (searching) {
                    // Reihenfolge = Rang: ausdrückliche Befehle, Apps, Kontakte, Einstellungen, Websuche.
                    val explicit = state.commands.filter(CommandParser::isExplicit)
                    val rest = state.commands.filterNot(CommandParser::isExplicit)
                    val commandFirst = explicit.isNotEmpty()
                    if (explicit.isEmpty() && state.results.isEmpty() && state.contacts.isEmpty() && rest.isEmpty()) {
                        item { MaiText(stringResource(R.string.no_results), MaiType.Body, Modifier.padding(top = 24.dp)) }
                    }
                    items(explicit, key = { "c_" + it.toString() }) { cmd ->
                        CommandRow(cmd, highlighted = cmd == explicit.first(), onRun = actions.onCommand)
                    }
                    items(state.results, key = { it.key.encode() }) { tile ->
                        AppRow(tile, highlighted = !commandFirst && tile == state.results.first(), state, actions)
                    }
                    items(state.contacts, key = { "k_" + it.lookupKey }) { c ->
                        ContactRow(
                            c,
                            highlighted = !commandFirst && state.results.isEmpty() && c == state.contacts.first(),
                            onCall = actions.onCall,
                            onText = actions.onText,
                        )
                    }
                    items(rest, key = { "r_" + it.toString() }) { cmd ->
                        CommandRow(cmd, highlighted = false, onRun = actions.onCommand)
                    }
                } else {
                    if (state.tabs.size > 1) {
                        item(key = "tabs") { TabRow(state, actions.onTab) }
                    }
                    if (state.likely.isNotEmpty() && state.tab == IndexTab.All) {
                        item(key = "likely") { LikelyRow(state, actions) }
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty") { MaiText(stringResource(R.string.index_tab_empty), MaiType.Body, Modifier.padding(top = 24.dp)) }
                    }
                    items(rows, key = { it.id }) { row ->
                        when (row) {
                            is ListRow.Header -> LetterHeader(row.letter)
                            is ListRow.App -> AppRow(row.tile, highlighted = false, state, actions)
                        }
                    }
                }
            }
            GripLine()
            Spacer(Modifier.height(10.dp))
            SearchField(state.query, state.listening, actions, focusSignal)
        }
        LetterRail(
            letters = sections.map { it.letter },
            enabled = !searching,
            onLetter = { letter ->
                val offset = (if (state.likely.isNotEmpty() && state.tab == IndexTab.All) 1 else 0) +
                    (if (state.tabs.size > 1) 1 else 0)
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
        LikelyTokens(state.likely, state.likelyReasons, state.likelyWindow, 42.dp, actions.onApp, actions.onHideLikely)
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

/** Reiter: ALLE · ARBEIT · AUSGEBLENDET. */
@Composable
private fun TabRow(state: IndexState, onTab: (IndexTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.tabs.forEach { t ->
            val on = t == state.tab
            ActionChip(
                stringResource(
                    when (t) {
                        IndexTab.All -> R.string.index_tab_all
                        IndexTab.Work -> R.string.index_tab_work
                        IndexTab.Hidden -> R.string.index_tab_hidden
                    },
                ),
                color = if (on) LocalAccent.current.main else MaiColors.Text3,
                selected = on,
            ) { onTab(t) }
        }
    }
}

/** Was das Langdruck-Menü gerade zeigt. */
private enum class RowMenu { Closed, Main, Dock, Alias }

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AppRow(tile: AppTile, highlighted: Boolean, state: IndexState, actions: IndexActions) {
    val accent = LocalAccent.current.main
    val feedback = LocalFeedback.current
    var menu by remember(tile.key) { mutableStateOf(RowMenu.Closed) }
    val expanded = menu != RowMenu.Closed && !state.pickingDock
    val shape = ChamferShape(6.dp)
    val hidden = tile.key in state.hidden
    val alias = state.aliases[tile.key]
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
                .height(MaiDims.ListRow)
                .combinedClickable(
                    role = Role.Button,
                    onClick = { feedback.tap(); actions.onApp(tile) },
                    onLongClickLabel = stringResource(R.string.action_menu),
                    onLongClick = {
                        feedback.longPress()
                        menu = if (menu == RowMenu.Closed) RowMenu.Main else RowMenu.Closed
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(tile.label, alias).joinToString(", ")
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyph(tile, active = highlighted || expanded)
            Spacer(Modifier.width(12.dp))
            MaiText(tile.label.uppercase(), MaiType.ListCaps, Modifier.weight(1f, fill = false))
            if (alias != null) {
                Spacer(Modifier.width(8.dp))
                MaiLabel("· " + alias, color = MaiColors.Text3)
            }
            Spacer(Modifier.weight(1f))
            if (tile.isWork) MaiLabel(stringResource(R.string.work_badge), color = MaiColors.Amber)
            if (highlighted) MaiLabel("↵", color = accent)
        }
        if (expanded) {
            val close = { menu = RowMenu.Closed }
            when (menu) {
                RowMenu.Main -> {
                    val shortcuts by produceState(emptyList<AppShortcut>(), tile.key) { value = actions.loadShortcuts(tile.key) }
                    shortcuts.forEach { sc ->
                        ShortcutRow(sc.label) { close(); actions.onShortcut(sc) }
                    }
                    FlowRow(
                        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!hidden) ActionChip(stringResource(R.string.action_pin_dock)) { menu = RowMenu.Dock }
                        ActionChip(stringResource(R.string.action_alias)) { menu = RowMenu.Alias }
                        ActionChip(stringResource(if (hidden) R.string.action_unhide else R.string.action_hide), MaiColors.Amber) {
                            close(); actions.onHide(tile.key, !hidden)
                        }
                        ActionChip(stringResource(R.string.action_app_info)) { close(); actions.onAppInfo(tile.key) }
                        ActionChip(stringResource(R.string.action_uninstall), MaiColors.Alert) { close(); actions.onUninstall(tile.key) }
                    }
                }
                RowMenu.Dock -> {
                    MaiLabel(stringResource(R.string.action_pin_dock_hint), Modifier.padding(start = 10.dp, top = 2.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(4) { i ->
                            val current = state.dock.getOrNull(i)
                            val label = "${i + 1} · " + (current?.monogram ?: "+")
                            ActionChip(label, selected = current?.key == tile.key) {
                                close(); actions.onPinDock(tile.key, i)
                            }
                        }
                    }
                }
                RowMenu.Alias -> AliasEditor(alias.orEmpty(), onDone = { close(); actions.onAlias(tile.key, it) })
                RowMenu.Closed -> Unit
            }
        }
    }
}

/** Eine App-Verknüpfung im Menü (ganze Zeile tippbar). */
@Composable
private fun ShortcutRow(label: String, onClick: () -> Unit) {
    val feedback = LocalFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MaiDims.MinTouch)
            .combinedClickable(role = Role.Button) { feedback.tap(); onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaiText("›", MaiType.Mono, color = LocalAccent.current.main)
        Spacer(Modifier.width(12.dp))
        MaiText(label, MaiType.Body, color = MaiColors.Text)
    }
}

/** Eigener Suchname: Eingabe, Enter oder OK speichert, leer entfernt. */
@Composable
private fun AliasEditor(initial: String, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(40.dp).background(MaiColors.Panel, ChamferShape(6.dp)).glowBorder(ChamferShape(6.dp), MaiColors.Line).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) MaiLabel(stringResource(R.string.alias_hint), color = MaiColors.Text3)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(AliasCodec.MAX_LENGTH) },
                singleLine = true,
                textStyle = MaiType.Mono.copy(color = MaiColors.Text),
                cursorBrush = SolidColor(LocalAccent.current.main),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.width(8.dp))
        ActionChip(stringResource(R.string.likely_ok)) { onDone(text) }
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
private fun ActionChip(
    text: String,
    color: androidx.compose.ui.graphics.Color = LocalAccent.current.main,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = ChamferShape(6.dp)
    val feedback = LocalFeedback.current
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .semantics { this.selected = selected }
            .background(if (selected) MaiColors.Panel2 else MaiColors.Panel, shape)
            .glowBorder(shape, color, if (selected) 1.4.dp else 1.dp)
            .combinedClickable(role = Role.Button) { feedback.tap(); onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        MaiLabel(text, color = color)
    }
}

@Composable
private fun SearchField(query: String, listening: Boolean, actions: IndexActions, focusSignal: Int) {
    val accent = LocalAccent.current.main
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    CommandShape(active = query.isNotEmpty() || listening) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MaiText("›", MaiType.Mono.copy(fontSize = MaiType.Title.fontSize), color = accent)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    MaiLabel(
                        stringResource(if (listening) R.string.voice_listening else R.string.filter_hint),
                        color = if (listening) accent else MaiColors.Text3,
                    )
                }
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
            Box(
                Modifier.size(MaiDims.MinTouch).combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (listening) R.string.voice_stop else R.string.voice_start),
                    onClick = actions.onMic,
                ),
                contentAlignment = Alignment.Center,
            ) { MicGlyph(color = accent) }
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
    val feedback = LocalFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<Char?>(null) }
    val accent = LocalAccent.current.main

    fun select(y: Float) {
        if (letters.isEmpty()) return
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[i]
        if (letter != active) {
            active = letter
            feedback.tick()
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
