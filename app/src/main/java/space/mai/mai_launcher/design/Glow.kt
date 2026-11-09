package space.mai.mai_launcher.design

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Leucht-Budget aus dem Konzept: 0 = Ruhe, 1 = Fokus, 2 = aktiver Rahmen.
 * Höchstens EIN Rahmen pro Bildschirm bekommt Stufe 2.
 */
enum class GlowLevel(val blur: Dp, val layers: Int) {
    None(0.dp, 0),
    Focus(2.dp, 1),
    Frame(4.dp, 2),
}

/**
 * Kontur in Form [shape] mit optionalem Leuchten. Das Leuchten ist Schmuck und
 * trägt nie allein Bedeutung. Pfad und Paint werden je Größe zwischengespeichert.
 */
fun Modifier.glowBorder(
    shape: Shape,
    color: Color,
    width: Dp = 1.dp,
    glow: GlowLevel = GlowLevel.None,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokePx = width.toPx()
    val effectiveGlow = glow
    val glowPaint = if (effectiveGlow != GlowLevel.None) {
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = strokePx + 1.dp.toPx()
        }.also {
            it.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = color.copy(alpha = 0.9f).toArgb()
                maskFilter = BlurMaskFilter(effectiveGlow.blur.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
        }
    } else null
    onDrawWithContent {
        drawContent()
        if (glowPaint != null) {
            drawIntoCanvas { canvas ->
                repeat(effectiveGlow.layers) { canvas.drawOutline(outline, glowPaint) }
            }
        }
        drawOutline(outline, color, style = Stroke(width = strokePx))
    }
}
