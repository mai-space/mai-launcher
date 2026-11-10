package space.mai.mai_launcher.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hintergrund: fast schwarz mit leichtem Verlauf und feinen Scanlinien (statisch). */
@Composable
fun MaiBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaiColors.VoidTop, MaiColors.Void)))
            .drawScanlines(),
        content = content,
    )
}

private fun Modifier.drawScanlines(): Modifier = drawBehind {
    val step = 4.dp.toPx()
    val line = Color.White.copy(alpha = 0.022f)
    var y = 0f
    while (y < size.height) {
        drawRect(line, topLeft = Offset(0f, y), size = Size(size.width, 1f))
        y += step
    }
}

/** Kleine Versal-Beschriftung. */
@Composable
fun MaiLabel(text: String, modifier: Modifier = Modifier, color: Color = MaiColors.Text2, align: TextAlign? = null) {
    BasicText(
        text.uppercase(),
        modifier,
        style = MaiType.Label.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

@Composable
fun MaiText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = style.color,
    maxLines: Int = 1,
) {
    BasicText(text, modifier, style = style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/**
 * Hauptrahmen eines Bildschirms: gefaster Rahmen mit Leuchten (Stufe 2),
 * Titel-Lasche oben links und Kennwert oben rechts.
 */
@Composable
fun MainFrame(
    title: String,
    meta: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val frameShape = ChamferShape(MaiDims.FrameChamfer)
    val powerOn = LocalPowerOn.current
    Box(modifier.padding(top = 10.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaiColors.Void.copy(alpha = 0.6f), frameShape)
                .glowBorder(frameShape, accent.main, 1.6.dp, GlowLevel.Frame, trim = { powerOnFrame(powerOn()) })
                .padding(start = MaiDims.Gutter, end = MaiDims.Gutter, top = 18.dp, bottom = 14.dp),
            content = content,
        )
        // Titel-Lasche
        val tabShape = ChamferShape(8.dp, topStart = true, topEnd = true, bottomEnd = false, bottomStart = false)
        Box(
            Modifier
                .offset(x = 16.dp, y = (-10).dp)
                .width(150.dp)
                .height(22.dp)
                .background(MaiColors.Panel2, tabShape)
                .glowBorder(tabShape, accent.main, 1.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(title, Modifier.padding(start = 14.dp), style = MaiType.Tab)
        }
        if (meta != null) {
            MaiLabel(
                meta,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 30.dp),
                color = MaiColors.Text3,
            )
        }
    }
}

/** Panel = die ganze Widget-Hülle: Fase, Akzentstrich oben links, Titel, Kennwert. */
@Composable
fun MaiPanel(
    title: String?,
    modifier: Modifier = Modifier,
    meta: String? = null,
    accent: Color = LocalAccent.current.main,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = ChamferShape(MaiDims.PanelChamfer)
    Box(
        modifier
            .background(MaiColors.Panel.copy(alpha = if (ComfortState.current.highContrast) 1f else 0.85f), shape)
            .glowBorder(shape, MaiColors.Line),
    ) {
        Box(Modifier.size(3.dp, 16.dp).background(accent))
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp)) {
            if (title != null || meta != null) {
                Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MaiLabel(title ?: "", Modifier.weight(1f))
                    if (meta != null) MaiLabel(meta, color = MaiColors.Text3)
                }
            }
            content()
        }
    }
}

/**
 * Sechseck-Token: alles Tippbare im System. Zeigt ein (einfarbiges) App-Symbol
 * oder ein Monogramm. Mindest-Tippfläche 48 dp, auch wenn das Sechseck kleiner ist.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HexToken(
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    text: String? = null,
    icon: ImageBitmap? = null,
    glyph: (DrawScope.(Color) -> Unit)? = null,
    color: Color = LocalAccent.current.main,
    filled: Boolean = true,
    badge: String? = null,
    glow: GlowLevel = GlowLevel.None,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val feedback = LocalFeedback.current
    val touch = maxOf(size, MaiDims.MinTouch)
    Box(
        modifier
            .size(touch)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClick = {
                            feedback.tap()
                            onClick?.invoke()
                        },
                        onLongClick = onLongClick?.let {
                            {
                                feedback.longPress()
                                it()
                            }
                        },
                    )
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .then(if (filled) Modifier.background(MaiColors.Panel2, HexagonShape) else Modifier)
                .glowBorder(HexagonShape, color, 1.2.dp, glow),
            contentAlignment = Alignment.Center,
        ) {
            when {
                glyph != null -> Canvas(Modifier.size(size * 0.46f)) { glyph(color) }
                icon != null -> Image(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.5f),
                    colorFilter = ColorFilter.tint(color),
                )
                text != null -> BasicText(
                    text,
                    style = MaiType.Token.copy(color = color, fontSize = (size.value * 0.38f).sp),
                    maxLines = 1,
                )
            }
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 4.dp)
                    .background(MaiColors.Amber)
                    .padding(horizontal = 4.dp),
            ) {
                BasicText(badge, style = MaiType.Mono.copy(color = MaiColors.Void, fontSize = 10.sp))
            }
        }
    }
}

/** Kerbenlinie über der Daumenzone. */
@Composable
fun GripLine(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current.main
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val y = size.height - 1.dp.toPx()
        val notch = 6.dp.toPx()
        val w = size.width
        val a = w * 0.3f
        val b = w * 0.7f
        val p = Path().apply {
            moveTo(0f, y); lineTo(a, y); lineTo(a + notch, y - notch)
            lineTo(b - notch, y - notch); lineTo(b, y); lineTo(w, y)
        }
        drawPath(p, accent.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
        drawRect(accent, topLeft = Offset(w / 2 - 8.dp.toPx(), y - notch - 3.dp.toPx()), size = Size(16.dp.toPx(), 2.dp.toPx()))
    }
}

/** Dünner Fortschrittsbalken mit leuchtendem Anteil; wächst zum neuen Wert. */
@Composable
fun MaiBar(fraction: Float, modifier: Modifier = Modifier, color: Color = LocalAccent.current.main) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), motion(400), label = "bar")
    Box(modifier.height(3.dp).background(MaiColors.Line)) {
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(3.dp)
                .background(color),
        )
    }
}

/** Gefaste Fläche als Knopf/Eingabezeile (Befehlszeile). */
@Composable
fun CommandShape(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = LocalAccent.current.main
    val shape = ChamferShape(8.dp, topStart = true, topEnd = false, bottomEnd = true, bottomStart = false)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(MaiColors.Panel2, shape)
            .glowBorder(shape, if (active) accent else MaiColors.Line, 1.dp, if (active) GlowLevel.Focus else GlowLevel.None),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}

/** Mikrofon-Glyphe (Linien), für die Befehlszeile. */
@Composable
fun MicGlyph(modifier: Modifier = Modifier, color: Color = LocalAccent.current.main) {
    Canvas(modifier.size(20.dp)) {
        val s = size.minDimension / 20f
        val stroke = Stroke(1.4f * s)
        drawRoundRect(
            color, topLeft = Offset(6.5f * s, 1f * s),
            size = Size(7f * s, 11f * s),
            cornerRadius = CornerRadius(3.5f * s), style = stroke,
        )
        drawArc(color, 0f, 180f, false, topLeft = Offset(3f * s, 3f * s), size = Size(14f * s, 14f * s), style = stroke)
        drawLine(color, Offset(10f * s, 17f * s), Offset(10f * s, 19.5f * s), 1.4f * s)
    }
}

/** Waagerechter Abstandshalter in Zeilen. */
val RowGap = Arrangement.spacedBy(8.dp)
