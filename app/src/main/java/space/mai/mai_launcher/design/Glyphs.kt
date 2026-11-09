package space.mai.mai_launcher.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Einfache Linien-/Flächen-Glyphen für Hex-Tokens (füllen die Zeichenfläche). */
object Glyphs {
    val Play: DrawScope.(Color) -> Unit = { c ->
        val w = size.width
        val h = size.height
        drawPath(Path().apply { moveTo(w * 0.22f, h * 0.1f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.22f, h * 0.9f); close() }, c)
    }

    val Pause: DrawScope.(Color) -> Unit = { c ->
        val w = size.width
        val h = size.height
        drawRect(c, Offset(w * 0.2f, h * 0.12f), Size(w * 0.2f, h * 0.76f))
        drawRect(c, Offset(w * 0.6f, h * 0.12f), Size(w * 0.2f, h * 0.76f))
    }

    val Next: DrawScope.(Color) -> Unit = { c -> skip(c, forward = true) }
    val Previous: DrawScope.(Color) -> Unit = { c -> skip(c, forward = false) }

    private fun DrawScope.skip(c: Color, forward: Boolean) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (forward) { moveTo(w * 0.15f, h * 0.15f); lineTo(w * 0.7f, h * 0.5f); lineTo(w * 0.15f, h * 0.85f) }
            else { moveTo(w * 0.85f, h * 0.15f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.85f, h * 0.85f) }
            close()
        }
        drawPath(p, c)
        drawRect(c, Offset(if (forward) w * 0.72f else w * 0.13f, h * 0.15f), Size(w * 0.14f, h * 0.7f))
    }

    /** Pfeil nach oben rechts (Navigation). */
    val Navigate: DrawScope.(Color) -> Unit = { c ->
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.5f, h * 0.05f); lineTo(w * 0.9f, h * 0.95f); lineTo(w * 0.5f, h * 0.72f); lineTo(w * 0.1f, h * 0.95f); close()
        }
        drawPath(p, c, style = Stroke(width = w * 0.08f, join = StrokeJoin.Miter))
    }

    val Plus: DrawScope.(Color) -> Unit = { c ->
        val w = size.width
        val s = w * 0.09f
        drawLine(c, Offset(w * 0.5f, w * 0.15f), Offset(w * 0.5f, w * 0.85f), s)
        drawLine(c, Offset(w * 0.15f, w * 0.5f), Offset(w * 0.85f, w * 0.5f), s)
    }

    /** Lautsprecher (Ausgabe/Lautstärke). */
    val Speaker: DrawScope.(Color) -> Unit = { c ->
        val w = size.width
        val h = size.height
        drawPath(Path().apply {
            moveTo(w * 0.1f, h * 0.36f); lineTo(w * 0.32f, h * 0.36f); lineTo(w * 0.58f, h * 0.12f)
            lineTo(w * 0.58f, h * 0.88f); lineTo(w * 0.32f, h * 0.64f); lineTo(w * 0.1f, h * 0.64f); close()
        }, c)
        drawArc(c, -45f, 90f, false, Offset(w * 0.5f, h * 0.25f), Size(w * 0.4f, h * 0.5f), style = Stroke(w * 0.08f))
    }

    /** Kästchen mit Pfeil (App öffnen). */
    val Open: DrawScope.(Color) -> Unit = { c ->
        val w = size.width
        val s = w * 0.08f
        drawPath(Path().apply {
            moveTo(w * 0.45f, w * 0.15f); lineTo(w * 0.15f, w * 0.15f); lineTo(w * 0.15f, w * 0.85f)
            lineTo(w * 0.85f, w * 0.85f); lineTo(w * 0.85f, w * 0.55f)
        }, c, style = Stroke(s))
        drawLine(c, Offset(w * 0.45f, w * 0.55f), Offset(w * 0.88f, w * 0.12f), s)
        drawLine(c, Offset(w * 0.6f, w * 0.12f), Offset(w * 0.88f, w * 0.12f), s)
        drawLine(c, Offset(w * 0.88f, w * 0.12f), Offset(w * 0.88f, w * 0.4f), s)
    }
}
