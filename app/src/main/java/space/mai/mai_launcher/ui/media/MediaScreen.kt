package space.mai.mai_launcher.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import space.mai.mai_launcher.R
import space.mai.mai_launcher.design.ChamferShape
import space.mai.mai_launcher.design.GlowLevel
import space.mai.mai_launcher.design.Glyphs
import space.mai.mai_launcher.design.GripLine
import space.mai.mai_launcher.design.HexToken
import space.mai.mai_launcher.design.HexagonShape
import space.mai.mai_launcher.design.LocalAccent
import space.mai.mai_launcher.design.MaiColors
import space.mai.mai_launcher.design.MaiLabel
import space.mai.mai_launcher.design.MaiText
import space.mai.mai_launcher.design.MaiType
import space.mai.mai_launcher.design.MainFrame
import space.mai.mai_launcher.domain.NowPlaying
import space.mai.mai_launcher.domain.SeekRing
import space.mai.mai_launcher.ui.AppTile
import space.mai.mai_launcher.ui.AppToken
import space.mai.mai_launcher.ui.deck.duotone
import space.mai.mai_launcher.ui.deck.formatMs

@Immutable
data class MediaState(
    val nowPlaying: NowPlaying?,
    val art: ImageBitmap? = null,
    val queue: List<String> = emptyList(),
    /** Musik-Apps für den Schnellstart, wenn nichts läuft. */
    val musicApps: List<AppTile> = emptyList(),
    /** Ohne Benachrichtigungszugriff sieht der Launcher keine Wiedergabe. */
    val access: Boolean = true,
)

class MediaScreenActions(
    val elapsedNow: () -> Long = { 0L },
    val onPlayPause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onOpenPlayer: () -> Unit = {},
    val onOutput: () -> Unit = {},
    val onApp: (AppTile) -> Unit = {},
    val onAllow: () -> Unit = {},
    val onClose: () -> Unit = {},
    /** Wischen: −1 = Seite links davon (Standard: Deck), +1 = rechts davon. */
    val onSwipe: (Int) -> Unit = { if (it < 0) onClose() },
)

/** Media: was in irgendeiner App spielt. Kontextfarbe Bernstein. */
@Composable
fun MediaScreen(state: MediaState, actions: MediaScreenActions, modifier: Modifier = Modifier) {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val np = state.nowPlaying
    Row(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)
            // Wischen blättert zur Nachbarseite (Standard: nach rechts = zurück zum Deck).
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
            },
    ) {
        MainFrame(
            title = stringResource(R.string.tab_media),
            meta = np?.appLabel?.uppercase(),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (np == null) {
                    Idle(state, actions)
                } else {
                    Playing(np, state, actions)
                }
            }
            GripLine()
            Spacer(Modifier.height(10.dp))
            Controls(np, actions)
        }
    }
}

@Composable
private fun Playing(np: NowPlaying, state: MediaState, actions: MediaScreenActions) {
    val accent = LocalAccent.current.main
    // Fortschritt sekündlich fortschreiben, nur während der Wiedergabe.
    var now by remember { mutableLongStateOf(actions.elapsedNow()) }
    LaunchedEffect(np) {
        now = actions.elapsedNow()
        while (np.playing) { delay(1000); now = actions.elapsedNow() }
    }
    var duo by remember { mutableStateOf(true) }
    val pos = np.positionAt(now)
    val dur = np.durationMs
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (pos != null && dur != null && dur > 0) pos.toFloat() / dur else 0f

    Spacer(Modifier.height(16.dp))
    Box(
        Modifier
            .fillMaxWidth(0.82f)
            .aspectRatio(1f)
            .pointerInput(dur) {
                // Am Ring ziehen = spulen (nur mit bekannter Länge).
                if (dur == null || dur <= 0) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragFraction = SeekRing.fraction(it.x, it.y, size.width.toFloat(), size.height.toFloat()) },
                    onDragEnd = { dragFraction?.let { f -> actions.onSeek((f * dur).toLong()) }; dragFraction = null },
                    onDragCancel = { dragFraction = null },
                ) { change, _ ->
                    dragFraction = SeekRing.fraction(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke * 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(MaiColors.Line, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Box(
            Modifier
                .fillMaxSize(0.78f)
                .clip(HexagonShape)
                .background(MaiColors.Panel2)
                .pointerInput(Unit) { detectTapGestures(onTap = { duo = !duo }, onLongPress = { actions.onOpenPlayer() }) },
            contentAlignment = Alignment.Center,
        ) {
            if (state.art != null) {
                Image(
                    state.art, null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = if (duo) duotone(accent) else null,
                )
            } else {
                MaiText(np.title.take(2).uppercase(), MaiType.Clock.copy(fontSize = MaiType.Clock.fontSize * 0.6f, color = accent))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    MaiText(np.title, MaiType.Title.copy(fontSize = MaiType.Title.fontSize * 1.25f, textAlign = TextAlign.Center), maxLines = 2)
    np.artist?.let { MaiText(it, MaiType.Body.copy(textAlign = TextAlign.Center)) }
    Spacer(Modifier.height(8.dp))
    if (pos != null && dur != null && dur > 0) {
        val shown = dragFraction?.let { (it * dur).toLong() } ?: pos
        Row(Modifier.fillMaxWidth()) {
            MaiText(formatMs(shown), MaiType.Mono, Modifier.weight(1f), color = MaiColors.Text2)
            MaiText("-" + formatMs(dur - shown), MaiType.Mono, color = MaiColors.Text3)
        }
    }
    if (state.queue.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MaiLabel(stringResource(R.string.media_up_next), color = accent)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(MaiColors.Line))
        }
        state.queue.forEachIndexed { i, t ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                MaiText("%02d".format(i + 1), MaiType.Mono, Modifier.widthIn(min = 32.dp), color = MaiColors.Text3)
                MaiText(t, MaiType.Body.copy(color = MaiColors.Text))
            }
        }
    }
}

@Composable
private fun Idle(state: MediaState, actions: MediaScreenActions) {
    Spacer(Modifier.height(40.dp))
    MaiText(stringResource(R.string.media_idle), MaiType.Title)
    Spacer(Modifier.height(6.dp))
    MaiText(
        stringResource(if (state.access) R.string.media_idle_body else R.string.media_no_access),
        MaiType.Body.copy(textAlign = TextAlign.Center), maxLines = 3,
    )
    if (!state.access) {
        Spacer(Modifier.height(12.dp))
        val accent = LocalAccent.current.main
        val shape = ChamferShape(6.dp)
        Box(
            Modifier.heightIn(min = 40.dp)
                .background(MaiColors.Panel, shape)
                .clickable(role = Role.Button, onClick = actions.onAllow)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) { MaiLabel(stringResource(R.string.media_allow), color = accent) }
    }
    if (state.musicApps.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        state.musicApps.take(8).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                row.forEach { AppToken(it, Modifier.weight(1f), size = 48.dp) { actions.onApp(it) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Controls(np: NowPlaying?, actions: MediaScreenActions) {
    val accent = LocalAccent.current.main
    val enabled = np != null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        HexToken(stringResource(R.string.media_output), size = 40.dp, glyph = Glyphs.Speaker, color = MaiColors.Text2, filled = false, onClick = actions.onOutput)
        HexToken(stringResource(R.string.media_previous), size = 48.dp, glyph = Glyphs.Previous, color = if (enabled) MaiColors.Text2 else MaiColors.Line, filled = false, onClick = actions.onPrevious.takeIf { enabled })
        HexToken(
            stringResource(if (np?.playing == true) R.string.media_pause else R.string.media_play),
            size = 64.dp, glyph = if (np?.playing == true) Glyphs.Pause else Glyphs.Play,
            color = if (enabled) accent else MaiColors.Line,
            glow = if (enabled) GlowLevel.Focus else GlowLevel.None,
            onClick = actions.onPlayPause.takeIf { enabled },
        )
        HexToken(stringResource(R.string.media_next), size = 48.dp, glyph = Glyphs.Next, color = if (enabled) MaiColors.Text2 else MaiColors.Line, filled = false, onClick = actions.onNext.takeIf { enabled })
        HexToken(stringResource(R.string.media_open_app), size = 40.dp, glyph = Glyphs.Open, color = MaiColors.Text2, filled = false, onClick = actions.onOpenPlayer.takeIf { enabled })
    }
}
