package space.mai.mai_launcher.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Rechteck mit 45°-Fasen statt runder Ecken. Standard: oben rechts und unten
 * links gefast (wie im Konzept). Die Fase wird auf die halbe kürzere Seite begrenzt.
 */
class ChamferShape(
    private val cut: Dp,
    private val topStart: Boolean = false,
    private val topEnd: Boolean = true,
    private val bottomEnd: Boolean = false,
    private val bottomStart: Boolean = true,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(chamferPath(size, with(density) { cut.toPx() }, layoutDirection))

    fun chamferPath(size: Size, cutPx: Float, layoutDirection: LayoutDirection = LayoutDirection.Ltr): Path {
        val c = min(cutPx, min(size.width, size.height) / 2f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        // In RTL spiegeln, damit die Fase am „Ende“ bleibt.
        val tl = if (rtl) topEnd else topStart
        val tr = if (rtl) topStart else topEnd
        val br = if (rtl) bottomStart else bottomEnd
        val bl = if (rtl) bottomEnd else bottomStart
        val w = size.width
        val h = size.height
        return Path().apply {
            moveTo(if (tl) c else 0f, 0f)
            lineTo(if (tr) w - c else w, 0f)
            if (tr) lineTo(w, c)
            lineTo(w, if (br) h - c else h)
            if (br) lineTo(w - c, h)
            lineTo(if (bl) c else 0f, h)
            if (bl) lineTo(0f, h - c)
            lineTo(0f, if (tl) c else 0f)
            close()
        }
    }
}

/** Sechseck mit Spitze oben/unten – im System heißt Sechseck immer „tippbar“. */
object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(hexPath(size))

    fun hexPath(size: Size): Path {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        return Path().apply {
            for (i in 0 until 6) {
                val a = Math.toRadians(30.0 + 60.0 * i)
                val x = cx + r * cos(a).toFloat()
                val y = cy + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}
