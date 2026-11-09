package space.mai.mai_launcher.ui.deck

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.Glyphs
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiBar
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiPanel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.glowBorder
import space.mai.mai_launcher.domain.CalendarEvent
import space.mai.mai_launcher.domain.FeedItem
import space.mai.mai_launcher.domain.NavInfo
import space.mai.mai_launcher.domain.NowPlaying
import space.mai.mai_launcher.domain.TimeLabels
import space.mai.mai_launcher.domain.Until
import space.mai.mai_launcher.ui.SetupItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- Einrichtung

@Composable
fun SetupPanel(items: List<SetupItem>, onItem: (SetupItem) -> Unit, onDismiss: (SetupItem) -> Unit) {
    MaiPanel(stringResource(R.string.setup_title), Modifier.fillMaxWidth(), accent = MaiColors.Amber) {
        items.forEach { item ->
            val (title, body) = when (item) {
                SetupItem.Home -> R.string.setup_home to R.string.setup_home_body
                SetupItem.Signals -> R.string.setup_signals to R.string.setup_signals_body
                SetupItem.Calendar -> R.string.setup_calendar to R.string.setup_calendar_body
                SetupItem.MaiWidgets -> R.string.setup_mai_widgets to R.string.setup_mai_widgets_body
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable(role = Role.Button) { onItem(item) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    MaiText(stringResource(title), MaiType.Title.copy(fontSize = MaiType.Body.fontSize * 1.15f))
                    MaiText(stringResource(body), MaiType.Body, maxLines = 2)
                }
                if (item != SetupItem.Home) {
                    // „Später“: Hinweis ausblenden, die Funktion bleibt in den Einstellungen erreichbar.
                    Box(
                        Modifier.size(48.dp).clickable(role = Role.Button) { onDismiss(item) },
                        contentAlignment = Alignment.Center,
                    ) { MaiText("×", MaiType.Title, color = MaiColors.Text3) }
                } else {
                    MaiText("›", MaiType.Title, color = MaiColors.Amber, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Musik

/** Graustufen und dann in die Akzentfarbe getönt (Duotone), damit Cover ruhig bleiben. */
private fun duotone(c: Color): ColorFilter {
    val r = 0.2126f; val g = 0.7152f; val b = 0.0722f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r * c.red, g * c.red, b * c.red, 0f, 0f,
                r * c.green, g * c.green, b * c.green, 0f, 0f,
                r * c.blue, g * c.blue, b * c.blue, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

@Composable
fun MediaPanel(
    np: NowPlaying,
    art: ImageBitmap?,
    elapsedNow: () -> Long,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val accent = MaiColors.Amber
    // Fortschritt nur sekündlich und nur während der Wiedergabe neu zeichnen.
    var now by remember { mutableLongStateOf(elapsedNow()) }
    LaunchedEffect(np) {
        now = elapsedNow()
        while (np.playing) { delay(1000); now = elapsedNow() }
    }
    MaiPanel(
        stringResource(R.string.panel_now_playing),
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
        meta = np.appLabel?.uppercase(),
        accent = accent,
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (art != null) {
                Image(
                    art, null,
                    Modifier.size(52.dp).clip(ChamferShape(8.dp)).glowBorder(ChamferShape(8.dp), accent.copy(alpha = 0.6f)),
                    contentScale = ContentScale.Crop,
                    colorFilter = duotone(accent),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                MaiText(np.title, MaiType.Title)
                np.artist?.let { MaiText(it, MaiType.Body) }
            }
        }
        Spacer(Modifier.height(10.dp))
        val pos = np.positionAt(now)
        val dur = np.durationMs
        if (pos != null && dur != null && dur > 0) {
            MaiBar(pos.toFloat() / dur, Modifier.fillMaxWidth(), accent)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                MaiText(formatMs(pos), MaiType.Mono.copy(fontSize = MaiType.Label.fontSize), Modifier.weight(1f), color = MaiColors.Text3)
                MaiText("-" + formatMs(dur - pos), MaiType.Mono.copy(fontSize = MaiType.Label.fontSize), color = MaiColors.Text3)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            HexToken(stringResource(R.string.media_previous), size = 34.dp, glyph = Glyphs.Previous, color = MaiColors.Text2, filled = false, onClick = onPrevious)
            HexToken(
                stringResource(if (np.playing) R.string.media_pause else R.string.media_play),
                size = 44.dp, glyph = if (np.playing) Glyphs.Pause else Glyphs.Play, color = accent,
                glow = space.mai.mai_launcher.design.GlowLevel.Focus, onClick = onPlayPause,
            )
            HexToken(stringResource(R.string.media_next), size = 34.dp, glyph = Glyphs.Next, color = MaiColors.Text2, filled = false, onClick = onNext)
        }
    }
}

fun formatMs(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- Navigation

@Composable
fun NavigationPanel(nav: NavInfo, onOpen: () -> Unit) {
    val accent = MaiColors.Signal
    MaiPanel(
        stringResource(R.string.panel_navigation),
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
        meta = nav.eta?.uppercase(),
        accent = accent,
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HexToken(stringResource(R.string.panel_navigation), size = 40.dp, glyph = Glyphs.Navigate, color = accent, glow = space.mai.mai_launcher.design.GlowLevel.Focus)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                nav.distance?.let { MaiText(it, MaiType.Title.copy(fontFamily = MaiType.Mono.fontFamily), color = accent) }
                MaiText(nav.instruction, MaiType.Body.copy(color = MaiColors.Text), maxLines = 2)
            }
        }
    }
}

/** Schnellziele (Zuhause, Arbeit). Im Bearbeiten-Modus mit Eingabefeldern. */
@Composable
fun DestinationsPanel(destinations: List<String>, editing: Boolean, onGo: (Int) -> Unit, onSet: (Int, String) -> Unit) {
    val names = listOf(stringResource(R.string.dest_home), stringResource(R.string.dest_work))
    MaiPanel(stringResource(R.string.panel_destinations), Modifier.fillMaxWidth(), accent = MaiColors.Signal) {
        Spacer(Modifier.height(6.dp))
        if (editing) {
            names.forEachIndexed { i, n -> DestinationField(n, destinations.getOrElse(i) { "" }) { onSet(i, it) } }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    val address = destinations.getOrElse(i) { "" }
                    if (address.isNotBlank()) {
                        val shape = ChamferShape(7.dp)
                        Row(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .background(MaiColors.Panel2, shape)
                                .glowBorder(shape, MaiColors.Signal)
                                .clickable(role = Role.Button) { onGo(i) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MaiLabel(n, color = MaiColors.Signal)
                            Spacer(Modifier.width(8.dp))
                            MaiText(address, MaiType.Body, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationField(name: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        MaiLabel(name, color = MaiColors.Signal)
        val shape = ChamferShape(6.dp)
        Box(
            Modifier.fillMaxWidth().height(44.dp).background(MaiColors.Panel2, shape).glowBorder(shape, MaiColors.Line).padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) MaiText(stringResource(R.string.dest_hint), MaiType.Body, color = MaiColors.Text3)
            BasicTextField(
                value = text,
                onValueChange = { text = it; onSave(it) },
                singleLine = true,
                textStyle = MaiType.Body.copy(color = MaiColors.Text),
                cursorBrush = SolidColor(MaiColors.Signal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------- Termine

@Composable
fun EventsPanel(events: List<CalendarEvent>, nowMillis: Long, zone: ZoneId, use24h: Boolean, onEvent: (Long) -> Unit, onCalendar: () -> Unit) {
    val locale = currentLocale()
    val accent = MaiColors.Amber
    MaiPanel(
        stringResource(R.string.panel_events),
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onCalendar),
        meta = stringResource(R.string.events_window),
        accent = accent,
    ) {
        if (events.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            MaiText(stringResource(R.string.events_none), MaiType.Body)
        }
        val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale)
        val dayFmt = DateTimeFormatter.ofPattern("EEE", locale)
        events.forEach { e ->
            val until = TimeLabels.until(e, nowMillis, zone)
            val start = TimeLabels.localStart(e, zone)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { onEvent(e.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val left = when {
                    e.allDay -> stringResource(R.string.events_all_day)
                    until is Until.Days || until is Until.Tomorrow -> start.format(dayFmt).uppercase(locale).replace(".", "") + " " + start.format(timeFmt)
                    else -> start.format(timeFmt)
                }
                MaiText(left, MaiType.Mono.copy(fontSize = MaiType.Label.fontSize * 1.2f), Modifier.width(96.dp), color = MaiColors.Text2)
                MaiText(e.title, MaiType.Body.copy(color = MaiColors.Text, fontSize = MaiType.Body.fontSize * 1.1f), Modifier.weight(1f))
                MaiLabel(untilLabel(until), color = if (until == Until.Now || until is Until.Minutes) accent else MaiColors.Text3)
            }
        }
    }
}

@Composable
private fun untilLabel(u: Until): String = when (u) {
    Until.Now -> stringResource(R.string.until_now)
    is Until.Minutes -> stringResource(R.string.until_minutes, u.m)
    is Until.Hours -> stringResource(R.string.until_hours, u.h)
    Until.Tomorrow -> stringResource(R.string.until_tomorrow)
    is Until.Days -> stringResource(R.string.until_days, u.d)
}

// ---------------------------------------------------------------- Widgets

/** Ein Android-Widget im Deck-Rahmen; Inhalt kommt über [content]. */
@Composable
fun WidgetPanel(
    widget: WidgetSlot,
    editing: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    content: @Composable (WidgetSlot) -> Unit,
) {
    MaiPanel(widget.label, Modifier.fillMaxWidth(), meta = widget.appLabel?.uppercase()) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(widget.heightDp.dp)) { content(widget) }
        if (editing) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditChip("↑", onUp)
                EditChip("↓", onDown)
                Spacer(Modifier.weight(1f))
                EditChip(stringResource(R.string.widget_remove), onRemove, MaiColors.Alert)
            }
        }
    }
}

@Composable
fun EditChip(text: String, onClick: () -> Unit, color: Color = LocalAccent.current.main) {
    val shape = ChamferShape(6.dp)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .background(MaiColors.Panel, shape)
            .glowBorder(shape, color)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { MaiLabel(text, color = color) }
}

// ---------------------------------------------------------------- News (RSS)

/** Neueste Einträge der eigenen Feeds (nur Text). Im Bearbeiten-Modus Feeds verwalten. */
@Composable
fun NewsPanel(
    items: List<FeedItem>,
    urls: List<String>,
    failed: Int,
    loading: Boolean,
    nowMillis: Long,
    editing: Boolean,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
) {
    val meta = when {
        loading -> stringResource(R.string.news_loading)
        failed > 0 -> stringResource(R.string.news_failed, failed)
        else -> null
    }
    MaiPanel(
        stringResource(R.string.panel_news),
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onRefresh),
        meta = meta,
        accent = if (failed > 0 && items.isEmpty()) MaiColors.Alert else LocalAccent.current.main,
    ) {
        if (editing) {
            urls.forEach { url ->
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaiText(url.removePrefix("https://"), MaiType.Body, Modifier.weight(1f))
                    EditChip(stringResource(R.string.widget_remove), { onRemove(url) }, MaiColors.Alert)
                }
            }
            FeedField(onAdd)
            return@MaiPanel
        }
        if (items.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            MaiText(stringResource(if (loading) R.string.news_loading_body else R.string.news_empty), MaiType.Body, maxLines = 2)
        }
        items.forEach { item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (item.link != null) Modifier.clickable(role = Role.Button) { onOpen(item.link) } else Modifier)
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaiLabel(item.source, Modifier.weight(1f), color = LocalAccent.current.main)
                    item.publishedMillis?.let { MaiLabel(ageLabel(nowMillis - it), color = MaiColors.Text3) }
                }
                MaiText(item.title, MaiType.Body.copy(color = MaiColors.Text, fontSize = MaiType.Body.fontSize * 1.1f), maxLines = 2)
            }
        }
    }
}

@Composable
private fun ageLabel(ms: Long): String {
    val min = (ms / 60_000).coerceAtLeast(0)
    return when {
        min < 60 -> stringResource(R.string.age_minutes, min)
        min < 48 * 60 -> stringResource(R.string.age_hours, min / 60)
        else -> stringResource(R.string.age_days, min / (24 * 60))
    }
}

@Composable
private fun FeedField(onAdd: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val shape = ChamferShape(6.dp)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        MaiLabel(stringResource(if (invalid) R.string.news_invalid else R.string.news_add), color = if (invalid) MaiColors.Alert else MaiColors.Text2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(44.dp).background(MaiColors.Panel2, shape).glowBorder(shape, MaiColors.Line).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) MaiText("https://…/feed.xml", MaiType.Body, color = MaiColors.Text3)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; invalid = false },
                    singleLine = true,
                    textStyle = MaiType.Body.copy(color = MaiColors.Text),
                    cursorBrush = SolidColor(LocalAccent.current.main),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    keyboardActions = KeyboardActions(onDone = { if (onAdd(text)) text = "" else invalid = true }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            EditChip("+", { if (onAdd(text)) text = "" else invalid = true })
        }
    }
}
