package space.mai.mai_launcher.ui.comms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.CommandShape
import space.mai.mai_launcher.design.GlowLevel
import space.mai.mai_launcher.design.GripLine
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiDims
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiPanel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.design.glowBorder
import space.mai.mai_launcher.domain.Contact
import space.mai.mai_launcher.domain.Keypad
import space.mai.mai_launcher.ui.index.SmallChip

@Immutable
data class CommsState(
    val access: Boolean,
    /** Alle bzw. gefilterte Kontakte. */
    val contacts: List<Contact>,
    val favourites: List<Contact>,
    val frequent: List<Contact>,
    val total: Int,
    val query: String = "",
    val keypadOpen: Boolean = false,
    val dialed: String = "",
    val rufDurch: Boolean = false,
)

class CommsActions(
    val onAllow: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onCall: (Contact, String?) -> Unit = { _, _ -> },
    val onText: (Contact, String?) -> Unit = { _, _ -> },
    val onOpenContact: (Contact) -> Unit = {},
    val onRufDurch: () -> Unit = {},
    val onKeypad: (Boolean) -> Unit = {},
    val onKey: (Char) -> Unit = {},
    val onBackspace: () -> Unit = {},
    val onClearDial: () -> Unit = {},
    val onDial: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Comms: Favoriten, Häufige, alle Kontakte, Wähltastatur. Kontextfarbe Signalgrün. */
@Composable
fun CommsScreen(state: CommsState, actions: CommsActions, modifier: Modifier = Modifier) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    Box(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 14.dp, top = 6.dp, bottom = 8.dp)
                // Nach links wischen = zurück zum Deck.
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -threshold) actions.onClose() },
                    ) { _, dx -> total += dx }
                },
        ) {
            MainFrame(
                title = stringResource(R.string.tab_comms),
                meta = if (state.access) pluralStringResource(R.plurals.contacts_count, state.total, state.total) else null,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!state.access) {
                        item { AllowPanel(actions.onAllow) }
                    } else if (state.query.isBlank()) {
                        if (state.favourites.isNotEmpty()) item(key = "fav") { Favourites(state.favourites, actions) }
                        if (state.frequent.isNotEmpty()) {
                            item(key = "freq_h") { SectionLabel(stringResource(R.string.comms_frequent)) }
                            items(state.frequent, key = { "f_" + it.lookupKey }) { ContactLine(it, state.rufDurch, actions) }
                        }
                        item(key = "all_h") { SectionLabel(stringResource(R.string.comms_all)) }
                    }
                    if (state.access) {
                        if (state.contacts.isEmpty()) {
                            item { MaiText(stringResource(R.string.no_results), MaiType.Body, Modifier.padding(top = 16.dp)) }
                        }
                        items(state.contacts, key = { "a_" + it.lookupKey }) { ContactLine(it, state.rufDurch, actions) }
                    }
                }
                GripLine()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SearchField(state.query, actions.onQuery) }
                    Spacer(Modifier.width(10.dp))
                    HexToken(
                        contentDescription = stringResource(R.string.comms_keypad),
                        size = 44.dp,
                        glyph = { c -> drawKeypadGlyph(c) },
                        onClick = { actions.onKeypad(true) },
                    )
                }
            }
            Spacer(Modifier.width(MaiDims.RailWidth))
        }
        if (state.keypadOpen) KeypadSheet(state.dialed, actions, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        MaiLabel(text, color = LocalAccent.current.main)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaiColors.Line))
    }
}

@Composable
private fun AllowPanel(onAllow: () -> Unit) {
    MaiPanel(stringResource(R.string.comms_contacts), Modifier.fillMaxWidth().padding(top = 12.dp)) {
        MaiText(stringResource(R.string.comms_allow_body), MaiType.Body, Modifier.padding(top = 8.dp), maxLines = 4)
        Spacer(Modifier.height(10.dp))
        SmallChip(stringResource(R.string.comms_allow), LocalAccent.current.main, onAllow)
    }
}

@Composable
private fun Favourites(list: List<Contact>, actions: CommsActions) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        SectionLabel(stringResource(R.string.comms_favourites))
        Spacer(Modifier.height(8.dp))
        list.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                row.forEach { c ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        HexToken(
                            contentDescription = c.name,
                            size = 56.dp,
                            text = c.monogram,
                            onClick = { actions.onCall(c, c.primaryNumber) },
                            onLongClick = { actions.onOpenContact(c) },
                        )
                        MaiLabel(c.name.substringBefore(' ').uppercase(), color = MaiColors.Text2)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Kontaktzeile; Tippen klappt Anrufen/Schreiben/Kontakt auf. */
@Composable
private fun ContactLine(contact: Contact, rufDurch: Boolean, actions: CommsActions) {
    val accent = LocalAccent.current.main
    var open by remember(contact.lookupKey) { mutableStateOf(false) }
    var number by remember(contact.lookupKey) { mutableStateOf(contact.primaryNumber) }
    val shape = ChamferShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (open) Modifier.background(MaiColors.Panel2, shape).glowBorder(shape, accent, 1.dp, GlowLevel.Focus) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MaiDims.ListRow)
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HexToken(contentDescription = contact.name, size = 30.dp, text = contact.monogram, filled = false, color = if (contact.starred) accent else MaiColors.Text3)
            Spacer(Modifier.width(8.dp))
            MaiText(contact.name, MaiType.Title, Modifier.weight(1f))
        }
        if (open) {
            if (contact.numbers.size > 1) {
                Row(Modifier.padding(start = 66.dp, end = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    contact.numbers.take(3).forEach { n ->
                        SmallChip(n, if (n == number) accent else MaiColors.Text3) { number = n }
                    }
                }
            } else if (number != null) {
                MaiText(number!!, MaiType.Mono, Modifier.padding(start = 66.dp, bottom = 6.dp))
            }
            Row(Modifier.padding(start = 66.dp, end = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (number != null) {
                    SmallChip(stringResource(R.string.comms_call), accent) { actions.onCall(contact, number) }
                    SmallChip(stringResource(R.string.comms_text), accent) { actions.onText(contact, number) }
                }
                SmallChip(stringResource(R.string.comms_contact), MaiColors.Text2) { actions.onOpenContact(contact) }
                if (rufDurch) SmallChip(stringResource(R.string.comms_ruf_durch), MaiColors.Text2, actions.onRufDurch)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val accent = LocalAccent.current.main
    CommandShape(active = query.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MaiText("›", MaiType.Mono.copy(fontSize = MaiType.Title.fontSize), color = accent)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) MaiLabel(stringResource(R.string.comms_search_hint), color = MaiColors.Text3)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaiType.Mono.copy(color = MaiColors.Text, fontSize = MaiType.Title.fontSize),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Search,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Wähltastatur als Blatt über dem unteren Bereich. */
@Composable
private fun KeypadSheet(dialed: String, actions: CommsActions, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.main
    val shape = ChamferShape(MaiDims.FrameChamfer, topStart = true, topEnd = true, bottomEnd = false, bottomStart = false)
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .background(MaiColors.Panel, shape)
            .glowBorder(shape, accent, 1.2.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MaiLabel(stringResource(R.string.comms_keypad), Modifier.weight(1f), color = accent)
            Box(Modifier.heightIn(min = MaiDims.MinTouch).clickable(role = Role.Button) { actions.onKeypad(false) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                MaiText("×", MaiType.Title, color = MaiColors.Text3)
            }
        }
        MaiText(
            if (dialed.isEmpty()) " " else Keypad.pretty(dialed),
            MaiType.Mono.copy(fontSize = MaiType.Title.fontSize * 1.5f, color = MaiColors.Text),
            Modifier.padding(vertical = 8.dp),
        )
        val keys = listOf("123", "456", "789", "*0#")
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { k ->
                    HexToken(
                        contentDescription = k.toString(),
                        size = 54.dp,
                        text = k.toString(),
                        color = MaiColors.Text,
                        onClick = { actions.onKey(k) },
                        onLongClick = if (k == '0') ({ actions.onKey('+') }) else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(54.dp))
            HexToken(
                contentDescription = stringResource(R.string.comms_call),
                size = 60.dp,
                text = stringResource(R.string.comms_call),
                color = accent,
                glow = GlowLevel.Focus,
                onClick = actions.onDial,
            )
            HexToken(
                contentDescription = stringResource(R.string.comms_delete),
                size = 44.dp,
                text = "⌫",
                color = MaiColors.Text2,
                filled = false,
                onClick = actions.onBackspace,
                onLongClick = actions.onClearDial,
            )
        }
    }
}

/** 3×3-Punkte als Glyphe für die Tastatur. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKeypadGlyph(color: androidx.compose.ui.graphics.Color) {
    val step = size.minDimension / 3f
    val r = step * 0.16f
    for (y in 0..2) for (x in 0..2) {
        drawCircle(color, r, androidx.compose.ui.geometry.Offset(step * (x + 0.5f), step * (y + 0.5f)))
    }
}
